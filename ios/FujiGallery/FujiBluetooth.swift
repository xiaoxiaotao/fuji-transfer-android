import CoreBluetooth
import Combine
import Foundation

struct CameraCandidate: Identifiable {
    let id: UUID
    let name: String
    let peripheral: CBPeripheral
}
struct CameraHotspot { let ssid: String; let password: String; let camera: String }

@MainActor final class FujiBluetooth: NSObject, ObservableObject, @preconcurrency CBCentralManagerDelegate, @preconcurrency CBPeripheralDelegate {
    @Published var candidates: [CameraCandidate] = []
    @Published var message = "打开相机配对注册后，扫描附近相机"
    @Published var scanning = false
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var pending: CheckedContinuation<Data, Error>?
    private var expected = "", operation = UUID()
    private var timeout: Task<Void, Never>?
    private let pair = CBUUID(string: "123d8f06-62a1-4935-9322-833c531ee225")
    private let net = CBUUID(string: "4e941240-d01d-46b9-a5ea-67636806830b")
    private let status = CBUUID(string: "f557d96b-8284-4667-8793-b971c1deca2a")
    private let name = CBUUID(string: "85b9163e-62d1-49ff-a6f5-054b4630d4a1")
    private let ssid = CBUUID(string: "bf6dc9cf-3606-4ec9-a4c8-d77576e93ea4")
    private let password = CBUUID(string: "e809256a-915c-4967-92e8-53b7d4cad213")
    override init() { super.init(); central = CBCentralManager(delegate: self, queue: .main) }
    func scan() {
        guard central.state == .poweredOn else { message = "请在设置中开启蓝牙并允许 Fuji图库 使用蓝牙"; return }
        candidates.removeAll(); addPairedCamera(); scanning = true; message = "正在扫描，请在相机上进入配对注册"
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        Task { try? await Task.sleep(nanoseconds: 15_000_000_000); stopScan() }
    }
    private func addPairedCamera() {
        guard let value = UserDefaults.standard.string(forKey: "pairedCamera"), let id = UUID(uuidString: value), let p = central.retrievePeripherals(withIdentifiers: [id]).first else { return }
        if !candidates.contains(where: { $0.id == id }) { candidates.append(CameraCandidate(id: id, name: p.name ?? "已配对富士相机", peripheral: p)) }
    }
    func stopScan() { central.stopScan(); scanning = false }
    func disconnect() {
        stopScan(); finish(error: CameraFailure(message: "蓝牙操作已取消"))
        if let peripheral { central.cancelPeripheralConnection(peripheral) }; peripheral = nil
    }
    private func wait(_ event: String, start: () -> Void) async throws -> Data {
        guard pending == nil else { throw CameraFailure(message: "蓝牙请求正在进行") }
        return try await withCheckedThrowingContinuation { continuation in
            pending = continuation; expected = event; operation = UUID(); let token = operation
            timeout = Task { try? await Task.sleep(nanoseconds: 60_000_000_000); if !Task.isCancelled && operation == token { finish(error: CameraFailure(message: "蓝牙操作超时，请确认手机和相机的配对提示")); if let peripheral { central.cancelPeripheralConnection(peripheral) } } }
            start()
        }
    }
    private func finish(event: String? = nil, value: Data = Data(), error: Error? = nil) {
        if let event, expected != event { return }
        guard let continuation = pending else { return }; pending = nil; timeout?.cancel(); timeout = nil
        if let error { continuation.resume(throwing: error) } else { continuation.resume(returning: value) }
    }
    private func characteristic(_ service: CBUUID, _ id: CBUUID) throws -> CBCharacteristic {
        guard let c = peripheral?.services?.first(where: { $0.uuid == service })?.characteristics?.first(where: { $0.uuid == id }) else { throw CameraFailure(message: "未找到新版富士配对特征；可改用相机 Wi-Fi 直连") }; return c
    }
    private func read(_ service: CBUUID, _ id: CBUUID) async throws -> Data {
        let c = try characteristic(service, id); return try await wait("read:\(id)") { peripheral?.readValue(for: c) }
    }
    private func write(_ service: CBUUID, _ id: CBUUID, _ data: Data) async throws {
        let c = try characteristic(service, id)
        guard c.properties.contains(.write) else { throw CameraFailure(message: "相机不支持带确认的蓝牙写入") }
        _ = try await wait("write:\(id)") { peripheral?.writeValue(data, for: c, type: .withResponse) }
    }
    func prepare(_ candidate: CameraCandidate) async throws -> CameraHotspot {
        disconnect(); peripheral = candidate.peripheral; candidate.peripheral.delegate = self
        message = "正在连接蓝牙；请确认相机与 iPhone 的配对代码"
        _ = try await wait("connect") { central.connect(candidate.peripheral) }
        _ = try await wait("services") { candidate.peripheral.discoverServices(nil) }
        for s in candidate.peripheral.services ?? [] { _ = try await wait("characteristics:\(s.uuid)") { candidate.peripheral.discoverCharacteristics(nil, for: s) } }
        var ack = try await read(pair, status)
        guard ack.count == 4 else { throw CameraFailure(message: "相机安全状态长度不符合协议") }
        ack[3] = 0x20; try await write(pair, status, ack)
        try await write(pair, name, Data("Fudge-XE5".utf8))
        // Subscribe before enabling the hotspot, as in the proven Android handoff.
        let notifications = ["a68e3f66-0fcc-4395-8d4c-aa980b5877fa", "bd17ba04-b76b-4892-a545-b73ba1f74dae", "f9150137-5d40-4801-a8dc-f7fc5b01da50", "ad06c7b7-f41a-46f4-a29a-712055319122", "7170fd5a-56d9-4c19-b043-7a7047d8e1a0", "bf6dc9cf-3606-4ec9-a4c8-d77576e93ea4", "75823784-fbb7-4b71-abae-cd9a34072e3c", "e6692c5c-b7cd-44f4-95fc-eda07ce32560", "aab609c4-94dd-4d89-bc60-665d5090b828", "2a125640-706d-4dd1-b420-c0f4ab93c361", "82a9f452-c5ce-4ef5-8203-3fc9a47f8171", "deef7187-3f43-4364-9e22-11a8c8a15951", "c95d91ae-b247-4d6d-8661-7dd5d6a0f85b"].map(CBUUID.init(string:))
        for s in candidate.peripheral.services ?? [] {
            for c in s.characteristics ?? [] where notifications.contains(c.uuid) && (c.properties.contains(.notify) || c.properties.contains(.indicate)) {
                _ = try await wait("notify:\(c.uuid)") { candidate.peripheral.setNotifyValue(true, for: c) }
            }
        }
        let ssidValue = try await read(net, ssid)
        let hotspotName = String(decoding: ssidValue.prefix(while: { $0 != 0 }), as: UTF8.self)
        guard !hotspotName.isEmpty else { throw CameraFailure(message: "相机热点名称为空") }
        message = "蓝牙连接成功，正在开启相机 Wi-Fi"
        try await write(CBUUID(string: "6514eb81-4e8f-458d-aa2a-e691336cdfac"), CBUUID(string: "600655e6-3637-42f1-8fb2-44efc5c63b13"), Data([4, 0]))
        try await Task.sleep(nanoseconds: 1_000_000_000)
        let secret = try await read(net, password)
        UserDefaults.standard.set(candidate.id.uuidString, forKey: "pairedCamera")
        return CameraHotspot(ssid: hotspotName, password: String(decoding: secret.prefix(while: { $0 != 0 }), as: UTF8.self), camera: candidate.id.uuidString)
    }
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn { addPairedCamera() }
        if central.state != .poweredOn { stopScan(); finish(error: CameraFailure(message: "蓝牙不可用，请检查权限和开关")); message = "蓝牙不可用，请检查权限和开关" }
    }
    func centralManager(_ central: CBCentralManager, didDiscover p: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let title = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? p.name ?? ""
        let serviceIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        guard title.uppercased().contains("FUJI") || title.uppercased().hasPrefix("X-") || title.uppercased().hasPrefix("GFX") || serviceIDs.contains(pair) || serviceIDs.contains(net) else { return }
        if !candidates.contains(where: { $0.id == p.identifier }) { candidates.append(CameraCandidate(id: p.identifier, name: title.isEmpty ? "富士相机" : title, peripheral: p)) }
    }
    func centralManager(_ central: CBCentralManager, didConnect p: CBPeripheral) { guard p === peripheral else { return }; finish(event: "connect") }
    func centralManager(_ central: CBCentralManager, didFailToConnect p: CBPeripheral, error: Error?) { guard p === peripheral else { return }; finish(error: error ?? CameraFailure(message: "蓝牙连接失败")) }
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral p: CBPeripheral, error: Error?) { guard p === peripheral else { return }; finish(error: error ?? CameraFailure(message: "相机蓝牙已断开")); message = "蓝牙已断开；已建立的 Wi-Fi 会话可继续使用" }
    func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) { guard p === peripheral else { return }; finish(event: "services", error: error) }
    func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor s: CBService, error: Error?) { guard p === peripheral else { return }; finish(event: "characteristics:\(s.uuid)", error: error) }
    func peripheral(_ p: CBPeripheral, didUpdateValueFor c: CBCharacteristic, error: Error?) { guard p === peripheral else { return }; finish(event: "read:\(c.uuid)", value: c.value ?? Data(), error: error) }
    func peripheral(_ p: CBPeripheral, didWriteValueFor c: CBCharacteristic, error: Error?) { guard p === peripheral else { return }; finish(event: "write:\(c.uuid)", error: error) }
    func peripheral(_ p: CBPeripheral, didUpdateNotificationStateFor c: CBCharacteristic, error: Error?) { guard p === peripheral else { return }; finish(event: "notify:\(c.uuid)", error: error) }
}
