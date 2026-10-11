package dev.danielc.fujiapp;

import java.util.Arrays;

/** UI state comes from connection events, independently of diagnostic log text. */
final class ConnectionState {
    static final ConnectionState shared = new ConnectionState();
    enum Step { BLUETOOTH, WIFI, SESSION, GALLERY }
    enum Status { WAITING, CONNECTING, READY, RETRYING, ERROR, DISCONNECTED, SKIPPED }
    enum Mode { BLUETOOTH, WIFI, USB }
    private final Status[] steps = new Status[4];
    private boolean closing, ended;
    private String detail = "选择连接方式，开始连接相机";
    private String terminalTitle;
    ConnectionState() { Arrays.fill(steps, Status.WAITING); }

    synchronized void begin(Mode mode) {
        Arrays.fill(steps, Status.WAITING);
        closing = ended = false; terminalTitle = null;
        if(mode != Mode.BLUETOOTH) steps[0] = Status.SKIPPED;
        if(mode == Mode.USB) steps[1] = Status.SKIPPED;
        Step first = mode == Mode.BLUETOOTH ? Step.BLUETOOTH : mode == Mode.WIFI ? Step.WIFI : Step.SESSION;
        steps[first.ordinal()] = Status.CONNECTING;
        detail = mode == Mode.USB ? "正在检测 USB 相机并等待授权" : mode == Mode.WIFI ? "请选择相机热点，并允许系统连接" : "正在查找或连接已配对相机";
    }
    synchronized void update(Step step, Status status, String message) {
        if(closing || ended) return;
        steps[step.ordinal()] = status;
        detail = message;
    }
    synchronized void bluetoothLost() {
        if(closing || ended || steps[0] == Status.SKIPPED) return;
        steps[0] = Status.DISCONNECTED;
        detail = steps[3] == Status.READY ? "蓝牙已断开，Wi-Fi 传图连接仍有效" : "蓝牙已断开，正在确认相机连接状态";
    }
    synchronized void error(Step step, String reason) {
        if(closing || ended) return;
        steps[step.ordinal()] = Status.ERROR;
        // A failed session cannot leave the gallery marked as usable.
        if(step != Step.GALLERY && steps[3] == Status.READY) steps[3] = Status.DISCONNECTED;
        terminalTitle = "连接错误"; detail = reason; ended = true;
    }
    synchronized void closing(boolean savingPhoto) {
        closing = true; terminalTitle = "正在断开连接";
        detail = savingPhoto ? "当前照片保存完成后自动断开，请稍候" : "正在关闭相机会话并释放连接";
    }
    synchronized void disconnected() {
        for(int i=0;i<steps.length;i++) if(steps[i] != Status.SKIPPED) steps[i] = Status.DISCONNECTED;
        closing = false; ended = true; terminalTitle = "连接已断开";
        detail = "自动重连已停止，需要连接时请点击已配对相机";
    }
    synchronized Snapshot snapshot() {
        String title = terminalTitle;
        if(title == null) {
            if(steps[3] == Status.READY && steps[2] == Status.READY) title = "相机已连接 · 图库可用";
            else if(steps[3] == Status.CONNECTING) title = "相机会话已连接 · 正在读取图库";
            else if(steps[2] == Status.RETRYING) title = "正在恢复相机会话";
            else if(steps[2] == Status.CONNECTING) title = "正在连接相机会话";
            else if(steps[1] == Status.CONNECTING || steps[1] == Status.RETRYING) title = "正在连接相机 Wi-Fi";
            else if(steps[0] == Status.CONNECTING) title = "正在连接蓝牙";
            else title = "尚未连接相机";
        }
        return new Snapshot(steps.clone(), title, detail, closing, ended);
    }
    static final class Snapshot {
        final Status[] steps;
        final String title, detail;
        final boolean closing, ended;
        Snapshot(Status[] steps,String title,String detail,boolean closing,boolean ended) {
            this.steps=steps;this.title=title;this.detail=detail;this.closing=closing;this.ended=ended;
        }
        String label(Step step) {
            String name = new String[]{"蓝牙", "Wi-Fi", "相机会话", "图库"}[step.ordinal()];
            switch(steps[step.ordinal()]) {
                case CONNECTING: return "◌ "+name+" · 连接中";
                case RETRYING: return "↻ "+name+" · 重试中";
                case READY: return "✓ "+name+" · "+(step==Step.GALLERY?"可用":"已连接");
                case ERROR: return "! "+name+" · 错误";
                case DISCONNECTED: return "○ "+name+" · 已断开";
                case SKIPPED: return "— 无需"+name;
                default: return "○ "+name+" · 等待";
            }
        }
    }
}
