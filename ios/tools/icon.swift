import AppKit
import Foundation
let folder = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
let sizes = [(20,2),(20,3),(29,2),(29,3),(40,2),(40,3),(60,2),(60,3),(1024,1)]
var entries: [[String: String]] = []
for (size, scale) in sizes {
    let pixels = size * scale
    let bitmap = CGContext(data: nil, width: pixels, height: pixels, bitsPerComponent: 8, bytesPerRow: pixels * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(cgContext: bitmap, flipped: false)
    let factor = CGFloat(pixels)/1024
    let context = NSGraphicsContext.current!.cgContext; context.scaleBy(x: factor, y: factor)
    NSColor(calibratedRed: 0.055, green: 0.09, blue: 0.075, alpha: 1).setFill(); NSBezierPath(rect: NSRect(x: 0,y: 0,width: 1024,height: 1024)).fill()
    NSColor(calibratedWhite: 0.84, alpha: 1).setFill(); NSBezierPath(roundedRect: NSRect(x: 148,y: 274,width: 728,height: 448), xRadius: 54,yRadius: 54).fill()
    NSBezierPath(roundedRect: NSRect(x: 302,y: 704,width: 204,height: 66), xRadius: 15,yRadius: 15).fill()
    NSColor(calibratedWhite: 0.10, alpha: 1).setFill(); NSBezierPath(roundedRect: NSRect(x: 148,y: 288,width: 728,height: 304), xRadius: 40,yRadius: 40).fill()
    NSColor(calibratedWhite: 0.86, alpha: 1).setFill(); NSBezierPath(ovalIn: NSRect(x: 332,y: 300,width: 360,height: 360)).fill()
    NSColor(calibratedWhite: 0.07, alpha: 1).setFill(); NSBezierPath(ovalIn: NSRect(x: 366,y: 334,width: 292,height: 292)).fill()
    NSColor(calibratedRed: 0.12,green: 0.44,blue: 0.33,alpha: 1).setFill(); NSBezierPath(ovalIn: NSRect(x: 410,y: 378,width: 204,height: 204)).fill()
    NSColor(calibratedRed: 0.58,green: 0.85,blue: 0.74,alpha: 1).setFill(); NSBezierPath(ovalIn: NSRect(x: 445,y: 492,width: 50,height: 50)).fill()
    NSColor(calibratedRed: 0.91,green: 0.14,blue: 0.18,alpha: 1).setFill(); NSBezierPath(roundedRect: NSRect(x: 742,y: 629,width: 70,height: 29), xRadius: 5,yRadius: 5).fill()
    NSGraphicsContext.restoreGraphicsState()
    let rep = NSBitmapImageRep(cgImage: bitmap.makeImage()!)
    let file = "icon-\(size)@\(scale)x.png"; try rep.representation(using: .png, properties: [:])!.write(to: folder.appendingPathComponent(file))
    entries.append(["idiom": size == 1024 ? "ios-marketing" : "iphone", "size": "\(size)x\(size)", "scale": "\(scale)x", "filename": file])
}
let json: [String: Any] = ["images": entries, "info": ["author": "xcode", "version": 1]]
try JSONSerialization.data(withJSONObject: json, options: [.prettyPrinted,.sortedKeys]).write(to: folder.appendingPathComponent("Contents.json"))
