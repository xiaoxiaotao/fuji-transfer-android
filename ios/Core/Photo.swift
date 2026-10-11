import Foundation

public struct CameraPhoto: Codable, Identifiable, Equatable {
    public var id: UInt32
    public var filename: String
    public var compressedSize: UInt64
    public var dateCreated: String
    public var format: Int
    public init(id: UInt32, filename: String, compressedSize: UInt64, dateCreated: String, format: Int) {
        self.id = id; self.filename = filename; self.compressedSize = compressedSize; self.dateCreated = dateCreated; self.format = format
    }
    public static func decode(id: UInt32, data: Data) throws -> CameraPhoto {
        struct Metadata: Decodable { var filename: String; var compressedSize: UInt64; var dateCreated: String; var format_int: Int }
        let m = try JSONDecoder().decode(Metadata.self, from: data)
        return CameraPhoto(id: id, filename: m.filename, compressedSize: m.compressedSize, dateCreated: m.dateCreated, format: m.format_int)
    }
    public var safeFilename: String? {
        guard !filename.isEmpty, !filename.contains("/"), !filename.contains("\\"), !filename.contains("\0"), filename != ".", filename != ".." else { return nil }
        return filename
    }
    // Never depend on a camera handle, which can change after reconnecting.
    public func identity(camera: String?) -> String? {
        guard let camera, !camera.isEmpty, safeFilename != nil, compressedSize > 0, !dateCreated.isEmpty else { return nil }
        return [camera, filename, String(compressedSize), dateCreated, String(format)].map { "\($0.utf8.count):\($0)" }.joined()
    }
}

public enum TransferStatus: String, Codable { case waiting, downloading, saved, failed, stopped, needsReview }
public struct TransferRecord: Codable, Identifiable {
    public var id = UUID()
    public var camera: String?
    public var photo: CameraPhoto
    public var state: TransferStatus = .waiting
    public var assetID: String?
    public var error = ""
    public init(camera: String?, photo: CameraPhoto) { self.camera = camera; self.photo = photo }
}

public final class TransferLedger {
    public private(set) var records: [TransferRecord] = []
    private let url: URL
    public init(url: URL) throws {
        self.url = url
        if FileManager.default.fileExists(atPath: url.path) { records = try JSONDecoder().decode([TransferRecord].self, from: Data(contentsOf: url)) }
        for i in records.indices {
            if records[i].state == .downloading { records[i].state = .stopped }
            if records[i].state == .waiting { records[i].state = .stopped }
        }
        try persist()
    }
    private func persist() throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(records).write(to: url, options: .atomic)
    }
    public func add(camera: String?, photos: [CameraPhoto]) throws -> [TransferRecord] {
        let added = photos.map { TransferRecord(camera: camera, photo: $0) }
        records.append(contentsOf: added); try persist(); return added
    }
    public func set(_ id: UUID, state: TransferStatus, error: String = "", assetID: String? = nil) throws {
        guard let i = records.firstIndex(where: { $0.id == id }) else { return }
        records[i].state = state; records[i].error = error
        if let assetID { records[i].assetID = assetID }
        try persist()
    }
    public func alreadySaved(_ photo: CameraPhoto, camera: String?) -> Bool {
        guard let key = photo.identity(camera: camera) else { return false }
        return records.contains { $0.state == .saved && $0.photo.identity(camera: $0.camera) == key }
    }
    public func pending(camera: String?) -> [TransferRecord] {
        guard let camera else { return [] }
        var latest: [String: TransferRecord] = [:]
        for record in records where record.camera == camera {
            if let key = record.photo.identity(camera: camera) { latest[key] = record }
        }
        return latest.values.filter { [.stopped, .failed, .waiting].contains($0.state) }.sorted { $0.photo.filename < $1.photo.filename }
    }
    public func recoveryPhotos(camera: String?, current: [CameraPhoto]) -> (matched: [CameraPhoto], missing: Int) {
        let groups = Dictionary(grouping: current.compactMap { p -> (String, CameraPhoto)? in
            guard let key = p.identity(camera: camera) else { return nil }; return (key, p)
        }, by: { $0.0 })
        let pending = pending(camera: camera)
        let matched = pending.compactMap { record -> CameraPhoto? in
            guard let key = record.photo.identity(camera: camera), let group = groups[key], group.count == 1 else { return nil }; return group[0].1
        }
        return (matched, pending.count - matched.count)
    }
}
