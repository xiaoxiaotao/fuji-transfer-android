import Foundation

struct CameraFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

final class RunLog {
    static let shared = RunLog()
    private let queue = DispatchQueue(label: "fuji.log")
    let url: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("FujiGallery/run.log")
    func write(_ message: String) {
        queue.async {
            do {
                try FileManager.default.createDirectory(at: self.url.deletingLastPathComponent(), withIntermediateDirectories: true)
                if ((try? self.url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) > 1_048_576 {
                    let previous = self.url.deletingLastPathComponent().appendingPathComponent("previous.log")
                    try? FileManager.default.removeItem(at: previous); try FileManager.default.moveItem(at: self.url, to: previous)
                }
                if !FileManager.default.fileExists(atPath: self.url.path) { FileManager.default.createFile(atPath: self.url.path, contents: nil) }
                let file = try FileHandle(forWritingTo: self.url); defer { try? file.close() }
                try file.seekToEnd(); try file.write(contentsOf: Data("\(Date().ISO8601Format()) \(message)\n".utf8))
            } catch { NSLog("Fuji log write failed: %@", error.localizedDescription) }
        }
    }
}

private final class WorkCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false
    func cancel() { lock.lock(); value = true; lock.unlock() }
    var cancelled: Bool { lock.lock(); defer { lock.unlock() }; return value }
}

final class CameraBackend {
    static let shared = CameraBackend()
    private let queue = DispatchQueue(label: "fuji.protocol", qos: .userInitiated)
    private let progressLock = NSLock()
    private var completed: UInt64 = 0, expected: UInt64 = 0
    private init() {
        fios_init({ text in if let text { RunLog.shared.write(String(cString: text)) } }, { done, total in CameraBackend.shared.updateProgress(done, total) })
    }
    private func updateProgress(_ done: UInt64, _ total: UInt64) { progressLock.lock(); completed = done; expected = total; progressLock.unlock() }
    func progress() -> Double { progressLock.lock(); defer { progressLock.unlock() }; return expected == 0 ? 0 : min(1, Double(completed) / Double(expected)) }
    private func perform<T>(_ body: @escaping () throws -> T) async throws -> T {
        let cancellation = WorkCancellation()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    do { if cancellation.cancelled { throw CancellationError() }; continuation.resume(returning: try body()) }
                    catch { continuation.resume(throwing: error) }
                }
            }
        }, onCancel: { cancellation.cancel() })
    }
    private func check(_ result: Int32, _ operation: String) throws { if result != 0 { throw CameraFailure(message: "\(operation)失败（\(result)），请检查相机热点和连接权限后重连") } }
    func connect(ip: String, protocolMode: Int) async throws {
        try await perform { try self.check(ip.withCString { fios_connect($0, Int32(protocolMode)) }, "相机会话连接") }
    }
    func disconnect() async { _ = try? await perform { fios_disconnect() } }
    func ping() async throws { try await perform { try self.check(fios_ping(), "相机通信") } }
    func handles() async throws -> [UInt32] {
        try await perform {
            var values: UnsafeMutablePointer<UInt32>?, count = 0
            try self.check(fios_handles(&values, &count), "读取图库索引")
            defer { fios_free(values) }
            guard let values else { return [] }; return Array(UnsafeBufferPointer(start: values, count: count))
        }
    }
    func info(_ handle: UInt32) async throws -> CameraPhoto {
        try await perform {
            var json: UnsafeMutablePointer<CChar>?
            try self.check(fios_info(handle, &json), "读取照片信息"); defer { fios_free(json) }
            guard let json else { throw CameraFailure(message: "相机未返回照片信息") }
            return try CameraPhoto.decode(id: handle, data: Data(String(cString: json).utf8))
        }
    }
    func thumbnail(_ handle: UInt32) async throws -> Data {
        try await perform {
            var bytes: UnsafeMutablePointer<UInt8>?, count = 0
            try self.check(fios_thumbnail(handle, &bytes, &count), "读取缩略图"); defer { fios_free(bytes) }
            guard let bytes else { return Data() }; return Data(bytes: bytes, count: count)
        }
    }
    func download(_ photo: CameraPhoto, to url: URL) async throws {
        updateProgress(0, photo.compressedSize)
        try await perform { try self.check(url.path.withCString { fios_download(photo.id, $0, photo.compressedSize) }, "下载照片") }
    }
}
