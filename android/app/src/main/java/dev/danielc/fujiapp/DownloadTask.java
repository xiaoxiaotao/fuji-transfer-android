package dev.danielc.fujiapp;

/** One photo transfer. Success means publication to phone storage has completed. */
final class DownloadTask {
    enum State { WAITING, RUNNING, SAVING, DONE, SKIPPED, FAILED, STOPPED }
    final long id;
    final int handle;
    volatile String name;
    private State state;
    private int progress, attempts;
    private String error;
    DownloadTask(long id,int handle,String name,State state,int progress,int attempts,String error) {
        this.id=id;this.handle=handle;this.name=name;this.state=state;this.progress=progress;this.attempts=attempts;this.error=error;
    }
    synchronized State state(){return state;}
    synchronized int progress(){return progress;}
    synchronized int attempts(){return attempts;}
    synchronized String error(){return error;}
    synchronized void start(){state=State.RUNNING;progress=0;error="";attempts++;}
    synchronized void progress(int value){if(state==State.RUNNING)progress=Math.max(progress,Math.min(100,Math.max(0,value)));}
    synchronized void saving(){state=State.SAVING;progress=100;}
    synchronized void done(){state=State.DONE;progress=100;error="";}
    synchronized void skipped(){state=State.SKIPPED;progress=100;error="";}
    synchronized void fail(String reason){state=State.FAILED;error=reason==null?"下载失败":reason;}
    synchronized void stop(){if(state==State.WAITING){state=State.STOPPED;error="剩余任务已停止";}}
    synchronized void retry(){state=State.WAITING;progress=0;error="";}
}
