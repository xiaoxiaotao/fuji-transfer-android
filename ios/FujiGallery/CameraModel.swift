import SwiftUI
import NetworkExtension
import Photos

@MainActor final class CameraModel: ObservableObject {
    @Published var title = "尚未连接相机"
    @Published var detail = "选择蓝牙相机，或连接相机热点后使用 Wi-Fi 直连"
    @Published var connected = false
    @Published var busy = false
    @Published var photos: [UInt32] = []
    @Published var selection = Set<UInt32>()
    @Published var thumbnails: [UInt32: Data] = [:]
    @Published var progress: Double = 0
    @Published var transferText = ""
    @Published var records: [TransferRecord] = []
    let bluetooth = FujiBluetooth()
    private let backend = CameraBackend.shared
    private var ledger: TransferLedger?
    private var camera: String?
    private var session = UUID()
    private var stopRequested = false, disconnectAfter = false
    private var monitor: Task<Void, Never>?
    private var hotspot: String?
    private var loadingThumbs = Set<UInt32>()
    init() {
        do {
            let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("FujiGallery")
            ledger = try TransferLedger(url: root.appendingPathComponent("transfers.json")); refreshRecords()
        } catch { detail = "下载记录读取失败，原文件已保留：\(error.localizedDescription)"; RunLog.shared.write(detail) }
    }
    private func refreshRecords() { records = ledger?.records.reversed().map { $0 } ?? [] }
    func connectBluetooth(_ candidate: CameraCandidate, ip: String) {
        guard !busy && !connected else { return }
        busy = true; stopRequested = false; disconnectAfter = false; title = "正在连接蓝牙"
        Task {
            do {
                let config = try await bluetooth.prepare(candidate)
                guard !stopRequested else { throw CancellationError() }
                title = "蓝牙连接成功 · 正在连接 Wi-Fi"; detail = "请允许 iPhone 加入相机热点"
                try await join(config)
                guard !stopRequested else { throw CancellationError() }
                hotspot = config.ssid; camera = config.camera
                title = "Wi-Fi 已连接 · 正在建立相机会话"
                try await open(ip: ip, mode: 2)
            } catch { await fail(error) }
            busy = false
            if disconnectAfter || stopRequested { await close() }
        }
    }
    private func join(_ hotspot: CameraHotspot) async throws {
        let config = hotspot.password.isEmpty ? NEHotspotConfiguration(ssid: hotspot.ssid) : NEHotspotConfiguration(ssid: hotspot.ssid, passphrase: hotspot.password, isWEP: false)
        config.joinOnce = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            NEHotspotConfigurationManager.shared.apply(config) { error in
                if let error = error as NSError?, !(error.domain == NEHotspotConfigurationErrorDomain && error.code == NEHotspotConfigurationError.alreadyAssociated.rawValue) { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
        // Association completion may precede DHCP. Give the camera hotspot time to assign an address.
        try await Task.sleep(nanoseconds: 2_000_000_000)
    }
    func connectWiFi(ip: String, mode: Int) {
        guard !busy && !connected else { return }
        busy = true; stopRequested = false; disconnectAfter = false; camera = nil
        title = "正在连接相机 Wi-Fi"; detail = "请先在 iPhone 设置中加入相机热点，并允许本地网络访问"
        Task { do { try await open(ip: ip, mode: mode) } catch { await fail(error) }; busy = false; if disconnectAfter || stopRequested { await close() } }
    }
    private func open(ip: String, mode: Int) async throws {
        try await backend.connect(ip: ip, protocolMode: mode)
        guard !stopRequested else { throw CancellationError() }
        title = "相机会话连接成功 · 正在读取图库"
        photos = try await backend.handles()
        guard !stopRequested else { throw CancellationError() }
        thumbnails.removeAll(); selection.removeAll(); session = UUID(); connected = true
        title = "相机已连接 · 图库可用"; detail = "共 \(photos.count) 个文件，可多选保存到 iPhone 相册"
        RunLog.shared.write("相机已连接，图库文件数 \(photos.count)"); startMonitor()
    }
    private func startMonitor() {
        monitor?.cancel(); let token = session
        monitor = Task {
            while !Task.isCancelled && connected && session == token {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                guard !Task.isCancelled && connected && session == token else { return }
                if !busy { do { try await backend.ping() } catch { guard session == token else { return }; await fail(error); return } }
            }
        }
    }
    private func fail(_ error: Error) async {
        title = error is CancellationError ? "连接已取消" : "连接错误"
        detail = error is CancellationError ? "需要连接时请重新选择相机" : error.localizedDescription + "；未完成下载记录已保留"
        connected = false; monitor?.cancel(); bluetooth.disconnect(); RunLog.shared.write(detail); await backend.disconnect()
        if let hotspot { NEHotspotConfigurationManager.shared.removeConfiguration(forSSID: hotspot); self.hotspot = nil }
    }
    func disconnect() {
        stopRequested = true; disconnectAfter = true; monitor?.cancel(); title = busy ? "正在结束任务并断开" : "正在断开连接"
        if busy { if !connected { bluetooth.disconnect() }; detail = "当前文件处理结束后断开，剩余下载保留"; return }
        Task { await close() }
    }
    private func close() async {
        busy = true; connected = false; monitor?.cancel(); bluetooth.disconnect(); await backend.disconnect()
        if let hotspot { NEHotspotConfigurationManager.shared.removeConfiguration(forSSID: hotspot); self.hotspot = nil }
        session = UUID(); title = "连接已断开"; detail = "需要连接时请重新选择相机"; busy = false
    }
    func loadThumbnail(_ handle: UInt32) async {
        guard connected && !busy && thumbnails[handle] == nil && !loadingThumbs.contains(handle) else { return }
        let token = session; loadingThumbs.insert(handle); defer { loadingThumbs.remove(handle) }
        do { let data = try await backend.thumbnail(handle); if token == session && connected { thumbnails[handle] = data } }
        catch { if token == session && connected && !busy { await fail(error) } }
    }
    func stopRemaining() { stopRequested = true; transferText = "当前照片完成后停止剩余下载" }
    func saveSelection() { startTransfer(recovery: false) }
    func recover() { startTransfer(recovery: true) }
    private func startTransfer(recovery: Bool) {
        guard connected && !busy, let ledger else { return }
        if recovery && camera == nil { detail = "跨连接恢复需要从同一台已配对蓝牙相机连接"; return }
        let handles = recovery ? photos : photos.filter { selection.contains($0) }
        if !recovery && handles.isEmpty { return }
        busy = true; stopRequested = false; disconnectAfter = false
        Task {
            let meter = Task { while !Task.isCancelled { progress = backend.progress(); try? await Task.sleep(nanoseconds: 250_000_000) } }
            defer { meter.cancel(); busy = false; refreshRecords() }
            do {
                let authorization = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
                guard authorization == .authorized || authorization == .limited else { throw CameraFailure(message: "请在设置中允许添加照片；下载清单会保留") }
                var prepared: [CameraPhoto] = []
                for (i, handle) in handles.enumerated() {
                    if stopRequested { break }; transferText = "正在核对照片 \(i + 1)/\(handles.count)"
                    prepared.append(try await backend.info(handle))
                }
                if recovery {
                    guard !stopRequested else { throw CancellationError() }
                    let result = ledger.recoveryPhotos(camera: camera, current: prepared); prepared = result.matched
                    if result.missing > 0 { detail = "\(result.missing) 个文件未能唯一匹配，历史记录已保留" }
                }
                let queue = try ledger.add(camera: camera, photos: prepared); refreshRecords()
                var saved = 0
                for record in queue {
                    if stopRequested { try ledger.set(record.id, state: .stopped); continue }
                    if ledger.alreadySaved(record.photo, camera: camera) { try ledger.set(record.id, state: .saved); continue }
                    do {
                        guard let filename = record.photo.safeFilename else { throw CameraFailure(message: "相机文件名无效") }
                        guard [0x3801, 0xb103, 0xb101].contains(record.photo.format) || ["JPG", "JPEG", "RAF", "HEIF", "HEIC"].contains((filename as NSString).pathExtension.uppercased()) else { throw CameraFailure(message: "第一版暂只保存照片，视频请使用其他方式") }
                        try ledger.set(record.id, state: .downloading); refreshRecords()
                        transferText = "正在保存 \(filename) · \(saved + 1)/\(queue.count)"; progress = 0
                        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(record.id.uuidString)
                        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                        let file = folder.appendingPathComponent(filename)
                        defer { try? FileManager.default.removeItem(at: folder) }
                        // Revalidate identity immediately before downloading; never trust a previous session handle.
                        let current = try await backend.info(record.photo.id)
                        guard current.filename == record.photo.filename && current.compressedSize == record.photo.compressedSize && current.dateCreated == record.photo.dateCreated else { throw CameraFailure(message: "照片信息发生变化，请重新选择") }
                        try await backend.download(current, to: file)
                        // Mark ambiguity before publishing: a crash after Photos writes must not silently create duplicates.
                        try ledger.set(record.id, state: .needsReview, error: "相册保存结果待确认，请先检查相册")
                        let asset = try await publish(file)
                        try ledger.set(record.id, state: .saved, assetID: asset); saved += 1
                    } catch {
                        if ledger.records.first(where: { $0.id == record.id })?.state != .needsReview { try ledger.set(record.id, state: .failed, error: error.localizedDescription) }
                        transferText = error.localizedDescription; stopRequested = true
                    }
                    refreshRecords()
                }
                transferText = "\(stopRequested ? "已停止" : "下载结束") · 保存 \(saved) 张"; selection.removeAll(); progress = 0
            } catch { transferText = error.localizedDescription; RunLog.shared.write(transferText) }
            if disconnectAfter { await close() }
        }
    }
    private func publish(_ file: URL) async throws -> String {
        var assetID = ""
        try await PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset(); request.addResource(with: .photo, fileURL: file, options: nil)
            assetID = request.placeholderForCreatedAsset?.localIdentifier ?? ""
        }
        return assetID
    }
    func backgrounded() {
        // Temporary hotspot connections cannot support unlimited background transfer.
        if connected || busy { disconnect(); RunLog.shared.write("App 进入后台，结束当前任务并保留剩余清单") }
    }
}
