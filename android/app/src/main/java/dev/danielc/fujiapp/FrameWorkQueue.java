package dev.danielc.fujiapp;

import java.util.concurrent.*;

/** One render runs at a time. Replacing pending work removes it, preventing a backlog of obsolete previews. */
final class FrameWorkQueue {
    private final ThreadPoolExecutor executor;
    private Future<?> pending;
    FrameWorkQueue(String name){executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(),job->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);job.run();},name));}
    synchronized void latest(Runnable work){if(executor.isShutdown())return;if(pending!=null){pending.cancel(false);if(pending instanceof Runnable)executor.remove((Runnable)pending);}pending=executor.submit(work);}
    synchronized void execute(Runnable work){if(!executor.isShutdown())executor.execute(work);}
    synchronized void finish(Runnable release){if(executor.isShutdown())return;executor.execute(release);executor.shutdown();}
}
