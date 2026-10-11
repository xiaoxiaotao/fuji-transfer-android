package dev.danielc.views;

abstract public class Idler {
    public volatile boolean stopDownloading = false;
    public volatile boolean pauseDownloading = false;
    private boolean inFlight;
    private boolean workerStarted;
    public synchronized boolean awaitPaused(long timeout)throws InterruptedException{long end=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeout);while(inFlight){long left=end-System.nanoTime();if(left<=0)return false;java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this,left);}return pauseDownloading;}

    public synchronized void stopRequestThread() {
        stopDownloading = true;
        notifyAll();
    }

    public synchronized void pause() {
        pauseDownloading = true;
        notifyAll();
    }

    public synchronized void start() {
        if(stopDownloading)return;
        pauseDownloading = false;
        notifyAll();
    }

    /** Return false if action was done, true if no action was done */
    public abstract boolean idle();

    public synchronized void startRequestThread() {
        if(workerStarted||stopDownloading)return;
        workerStarted=true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopDownloading) {
                    synchronized (Idler.this) {
                        while (pauseDownloading && !stopDownloading) {
                            try {
                                Idler.this.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        if(stopDownloading)return;
                        inFlight=true;
                    }
                    boolean empty;try{empty=idle();}finally{synchronized(Idler.this){inFlight=false;Idler.this.notifyAll();}}
                    if (empty) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();
    }
}
