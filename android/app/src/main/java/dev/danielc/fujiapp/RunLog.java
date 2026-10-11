package dev.danielc.fujiapp;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** App-private rolling operational history. Independent of debug UI and retained across launches. */
final class RunLog {
    private static File directory;
    private static final long LIMIT=1024*1024;
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(512),r->{Thread thread=new Thread(r,"fuji-run-log");thread.setDaemon(true);return thread;},
        new ThreadPoolExecutor.CallerRunsPolicy());
    static synchronized void initialize(Context context) {
        directory=new File(context.getNoBackupFilesDir(),"run-logs");
        record("Fuji图库 " + BuildConfig.VERSION_NAME + " · 应用启动");
    }
    static void record(String message) {
        String line=System.currentTimeMillis()+" " + message + "\n";
        writer.execute(()->append(line));
    }
    private static synchronized void append(String line) {
        if(directory==null)return;
        try {
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create log directory");
            File current=new File(directory,"current.log"),previous=new File(directory,"previous.log");
            byte[] bytes=line.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>LIMIT)bytes=java.util.Arrays.copyOfRange(bytes,bytes.length-(int)LIMIT,bytes.length);
            if(current.length()+bytes.length>LIMIT) {
                if(previous.exists()&&!previous.delete())throw new IOException("Cannot rotate previous log");
                if(current.exists()&&!current.renameTo(previous))throw new IOException("Cannot rotate current log");
            }
            try(FileOutputStream output=new FileOutputStream(current,true)){output.write(bytes);}
        } catch(IOException error) { Log.e("fudge","运行日志写入失败",error); }
    }
}
