package dev.danielc.fujiapp;
import java.util.List;


/** Serial transfers: stopping lets the current file finish and retains pending work. */
final class DownloadBatchRunner {
    interface Stop { boolean requested(); }
    interface Transfer { void save(DownloadTask task) throws Exception; }
    interface Observer { void changed(DownloadTask task); }
    static void run(List<DownloadTask> tasks,Stop stop,Transfer transfer,Observer observer) {
        for(DownloadTask task:tasks) {
            if(stop.requested()){task.stop();observer.changed(task);continue;}
            task.start();observer.changed(task);
            try { transfer.save(task);if(task.state()!=DownloadTask.State.SKIPPED)task.done(); }
            catch(Exception e){task.fail(e.getMessage());}
            observer.changed(task);
        }
    }
}
