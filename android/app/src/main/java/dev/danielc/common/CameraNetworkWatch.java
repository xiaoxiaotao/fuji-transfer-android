package dev.danielc.common;

/** Tracks only the network used by the camera; unrelated Wi-Fi changes are ignored. */
public final class CameraNetworkWatch {
    private long handle=-1;
    private boolean lost;
    public synchronized void track(long handle){this.handle=handle;lost=false;}
    public synchronized void clear(){handle=-1;lost=false;}
    public synchronized boolean lost(long network){if(handle<0||network!=handle||lost)return false;lost=true;return true;}
    public synchronized boolean isLost(){return lost;}
}
