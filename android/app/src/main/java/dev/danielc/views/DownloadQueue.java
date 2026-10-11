package dev.danielc.views;
import java.util.ArrayList;

abstract public class DownloadQueue<T> extends Idler {
    public final ArrayList<T> requests = new ArrayList<>();
    private boolean inFlight;
    @Override public void pause(){synchronized(requests){super.pause();requests.notifyAll();}}
    public boolean awaitPaused(long timeout)throws InterruptedException{long end=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeout);synchronized(requests){while(inFlight){long left=end-System.nanoTime();if(left<=0)return false;java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(requests,left);}return pauseDownloading;}}

    public abstract void perform(T request);
    /** Return false if action was done, true if no action was done */
    public abstract boolean nothingToDo();

    public void enqueue(T req) {
        synchronized (requests) {
            if(stopDownloading)return;
            requests.add(req);
            requests.notifyAll();
        }
    }

    public boolean idle() {
        T request = null;
        synchronized (requests) {
            if (requests.isEmpty() && !stopDownloading) {
                try { requests.wait(100); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); stopDownloading=true; return false; }
            }
            if (stopDownloading || pauseDownloading) return false;
            if (!requests.isEmpty()) request = requests.remove(requests.size()-1);
            inFlight=true;
        }
        // Never hold the UI enqueue lock while waiting for camera network I/O.
        try {if (request != null) perform(request);else return nothingToDo();}
        finally {synchronized(requests){inFlight=false;requests.notifyAll();}}
        return false;
    }

    @Override public void stopRequestThread(){synchronized(requests){super.stopRequestThread();requests.clear();requests.notifyAll();}}
}
