import Foundation
@main struct CoreChecks {
    static func main() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString); defer { try? FileManager.default.removeItem(at: root) }
        let url = root.appendingPathComponent("tasks.json"), l = try TransferLedger(url: url)
        func photo(_ id: UInt32 = 1) -> CameraPhoto { CameraPhoto(id: id, filename: "DSCF0001.JPG", compressedSize: 120, dateCreated: "20261011T150000", format: 0x3801) }
        let first = try l.add(camera: "A", photos: [photo()])[0]; try l.set(first.id, state: .downloading)
        let reopened = try TransferLedger(url: url); precondition(reopened.records[0].state == .stopped)
        precondition(reopened.recoveryPhotos(camera: "A", current: [photo(99)]).matched[0].id == 99)
        precondition(reopened.recoveryPhotos(camera: "A", current: [photo(7),photo(8)]).matched.isEmpty)
        precondition(reopened.pending(camera: "B").isEmpty && reopened.pending(camera: nil).isEmpty)
        try reopened.set(first.id, state: .saved, assetID: "asset"); precondition(reopened.alreadySaved(photo(9), camera: "A")); precondition(reopened.pending(camera: "A").isEmpty)
        var unsafe = photo(); unsafe.filename = "../a"; precondition(unsafe.safeFilename == nil && unsafe.identity(camera: "A") == nil)
        try Data("invalid".utf8).write(to: url); do { _ = try TransferLedger(url: url); fatalError("corruption erased") } catch { let original = try Data(contentsOf: url); precondition(original == Data("invalid".utf8)) }
        print("7 iOS core recovery checks passed")
    }
}
