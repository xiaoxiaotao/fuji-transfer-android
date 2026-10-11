// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "FujiGalleryCore", platforms: [.iOS(.v16), .macOS(.v13)], products: [.library(name: "FujiGalleryCore", targets: ["FujiGalleryCore"])], targets: [.target(name: "FujiGalleryCore", path: "Core"), .testTarget(name: "FujiGalleryCoreTests", dependencies: ["FujiGalleryCore"], path: "Tests", exclude: ["NativeSmoke.c"])])
