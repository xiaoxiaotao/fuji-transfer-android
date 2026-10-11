import SwiftUI
import UIKit

@main @MainActor struct FujiGalleryApp: App {
    @StateObject private var model = CameraModel()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup { RootView(model: model).tint(Color(red: 0.08, green: 0.38, blue: 0.29)).onChange(of: phase) { if $0 == .background { model.backgrounded() } } }
    }
}
@MainActor struct RootView: View {
    @ObservedObject var model: CameraModel
    @AppStorage("debugUI") private var debugUI = false
    @AppStorage("cameraIP") private var ip = "192.168.0.1"
    @State private var mode = 0
    var body: some View {
        TabView {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        connectionCard
                        Text("让照片，即刻相遇").font(.largeTitle.bold())
                        Text("富士相机里的瞬间，保存到你的 iPhone。").foregroundStyle(.secondary)
                        BluetoothPanel(model: model, bluetooth: model.bluetooth, ip: ip)
                        GroupBox("相机 Wi-Fi 直连") {
                            VStack(alignment: .leading, spacing: 14) {
                                Text("先在系统设置中加入相机热点，再点击连接。首次使用请允许本地网络访问。").font(.subheadline).foregroundStyle(.secondary)
                                TextField("相机地址", text: $ip).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                                Picker("协议", selection: $mode) { Text("自动").tag(0); Text("传统").tag(1); Text("XApp").tag(2) }.pickerStyle(.segmented)
                                Button("连接相机 Wi-Fi") { model.connectWiFi(ip: ip, mode: mode) }.buttonStyle(.borderedProminent).disabled(model.busy || model.connected)
                            }.padding(.top, 8)
                        }
                        if model.connected || model.busy { Button("断开连接", role: .destructive) { model.disconnect() }.buttonStyle(.bordered) }
                    }.padding(20)
                }.background(Color(.systemGroupedBackground)).navigationTitle("Fuji图库")
            }.tabItem { Label("相机", systemImage: "camera") }
            NavigationStack {
                VStack(spacing: 0) {
                    connectionCard.padding()
                    if model.connected {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 105), spacing: 5)], spacing: 5) {
                                ForEach(model.photos, id: \.self) { handle in
                                    Button { if !model.busy { if model.selection.contains(handle) { model.selection.remove(handle) } else { model.selection.insert(handle) } } } label: {
                                        ZStack(alignment: .topTrailing) {
                                            Rectangle().fill(Color(.secondarySystemBackground)).aspectRatio(1, contentMode: .fit)
                                            if let data = model.thumbnails[handle], let image = UIImage(data: data) {
                                                GeometryReader { geometry in Image(uiImage: image).resizable().scaledToFill().frame(width: geometry.size.width, height: geometry.size.height).clipped() }
                                            } else { Image(systemName: "photo").foregroundStyle(.secondary).frame(maxWidth: .infinity, maxHeight: .infinity) }
                                            Image(systemName: model.selection.contains(handle) ? "checkmark.circle.fill" : "circle").padding(7).foregroundStyle(model.selection.contains(handle) ? .green : .white)
                                        }.clipped()
                                    }.buttonStyle(.plain).task(id: "\(handle)-\(model.busy)") { await model.loadThumbnail(handle) }.accessibilityLabel("照片 \(handle)").accessibilityAddTraits(model.selection.contains(handle) ? .isSelected : [])
                                }
                            }.padding(.horizontal)
                        }
                        VStack(spacing: 8) {
                            if !model.transferText.isEmpty { Text(model.transferText).font(.caption); if model.busy { ProgressView(value: model.progress) } }
                            HStack {
                                Button("保存 \(model.selection.count) 张") { model.saveSelection() }.buttonStyle(.borderedProminent).disabled(model.selection.isEmpty || model.busy)
                                Button(model.busy ? "停止剩余" : "全选") { if model.busy { model.stopRemaining() } else { model.selection = Set(model.photos) } }.buttonStyle(.bordered)
                            }
                        }.padding()
                    } else { Spacer(); Image(systemName: "camera.metering.unknown").font(.system(size: 50)).foregroundStyle(.secondary); Text("连接相机后浏览照片").padding(); Spacer() }
                }.navigationTitle("相机图库")
            }.tabItem { Label("图库", systemImage: "square.grid.2x2") }
            NavigationStack {
                List {
                    Section { Button("核对并恢复未完成下载") { model.recover() }.disabled(!model.connected || model.busy); Text("从同一台蓝牙相机连接后恢复；中断文件会重新完整下载。").font(.caption).foregroundStyle(.secondary) }
                    ForEach(model.records) { r in VStack(alignment: .leading, spacing: 5) { Text(r.photo.filename); Text(status(r.state)).font(.caption).foregroundStyle(.secondary); if !r.error.isEmpty { Text(r.error).font(.caption).foregroundStyle(.orange) } } }
                }.navigationTitle("下载记录")
            }.tabItem { Label("下载", systemImage: "arrow.down.circle") }
            NavigationStack {
                Form {
                    Section("显示") { Toggle("调试信息", isOn: $debugUI); Text("正常使用默认关闭；运行日志始终保存在手机本地。").font(.caption).foregroundStyle(.secondary) }
                    if debugUI { Section("诊断") { ShareLink(item: RunLog.shared.url) { Label("导出运行日志", systemImage: "square.and.arrow.up") }; Text(model.bluetooth.message).font(.caption) } }
                    Section("关于") { Text("Fuji图库 · iOS 0.1.0"); Text("首次 iPhone 版本，连接与传图仍需真机验证。保存的照片可在系统相册中查看。").font(.caption).foregroundStyle(.secondary) }
                }.navigationTitle("设置")
            }.tabItem { Label("设置", systemImage: "gearshape") }
        }
    }
    private var connectionCard: some View {
        VStack(alignment: .leading, spacing: 8) { Label(model.title, systemImage: model.connected ? "checkmark.circle.fill" : "antenna.radiowaves.left.and.right").font(.headline); Text(model.detail).font(.caption).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading).padding().background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
    }
    private func status(_ state: TransferStatus) -> String { switch state { case .waiting: return "等待保存"; case .downloading: return "正在下载"; case .saved: return "已保存"; case .failed: return "下载失败"; case .stopped: return "未完成，可核对恢复"; case .needsReview: return "保存结果待确认，请先检查系统相册" } }
}
@MainActor struct BluetoothPanel: View {
    @ObservedObject var model: CameraModel
    @ObservedObject var bluetooth: FujiBluetooth
    let ip: String
    var body: some View {
        GroupBox("蓝牙自动连接") {
            VStack(alignment: .leading, spacing: 12) {
                Text(bluetooth.message).font(.subheadline).foregroundStyle(.secondary)
                Button(bluetooth.scanning ? "停止扫描" : "扫描富士相机") { if bluetooth.scanning { bluetooth.stopScan() } else { bluetooth.scan() } }.buttonStyle(.bordered).disabled(model.busy || model.connected)
                ForEach(bluetooth.candidates) { c in Button { model.connectBluetooth(c, ip: ip) } label: { Label(c.name, systemImage: "camera.fill").frame(maxWidth: .infinity, alignment: .leading) }.buttonStyle(.bordered).disabled(model.busy || model.connected) }
            }.padding(.top, 8).frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
