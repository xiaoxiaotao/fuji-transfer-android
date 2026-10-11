package dev.danielc.fujiapp;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Local history. Camera handles are retried within a live session or after verified photo identity matching. */
final class DownloadTasks extends SQLiteOpenHelper {
    private static DownloadTasks instance;
    static synchronized DownloadTasks get(Context context){if(instance==null)instance=new DownloadTasks(context.getApplicationContext());return instance;}
    private DownloadTasks(Context context){super(context,"photo-downloads.db",null,2);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT, session TEXT NOT NULL, handle INTEGER NOT NULL, name TEXT NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, attempts INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL, camera TEXT, photo_key TEXT)");
        db.execSQL("CREATE INDEX tasks_session_handle ON tasks(session,handle,_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion==1&&newVersion==2){db.execSQL("ALTER TABLE tasks ADD COLUMN camera TEXT");db.execSQL("ALTER TABLE tasks ADD COLUMN photo_key TEXT");;}else throw new IllegalStateException("Unsupported download history migration");}
    @Override public void onOpen(SQLiteDatabase db){super.onOpen(db);if(!db.isReadOnly())db.execSQL("UPDATE tasks SET state='STOPPED', error='上次任务未完成，连接同一相机后可核对恢复' WHERE state IN ('WAITING','RUNNING','SAVING')");}
    List<DownloadTask> create(String session,int[] handles){
        SQLiteDatabase db=getWritableDatabase();List<DownloadTask> result=new ArrayList<>();db.beginTransaction();
        try { for(int handle:handles){ContentValues v=new ContentValues();v.put("session",session);v.put("handle",handle);v.put("name","照片 #"+Integer.toUnsignedString(handle));v.put("state","WAITING");v.put("created",System.currentTimeMillis());long id=db.insertOrThrow("tasks",null,v);result.add(new DownloadTask(id,handle,v.getAsString("name"),DownloadTask.State.WAITING,0,0,""));}db.setTransactionSuccessful(); }
        finally {db.endTransaction();}return result;
    }
    void stopWaiting(String session){getWritableDatabase().execSQL("UPDATE tasks SET state='STOPPED', error='剩余任务已停止' WHERE session=? AND state='WAITING'",new Object[]{session});}
    void resetForRetry(List<DownloadTask> batch){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(DownloadTask task:batch){task.retry();save(task);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void save(DownloadTask task){ContentValues v=new ContentValues();v.put("camera",task.camera);v.put("photo_key",task.photoKey);v.put("name",task.name);v.put("state",task.state().name());v.put("progress",task.progress());v.put("attempts",task.attempts());v.put("error",task.error());getWritableDatabase().update("tasks",v,"_id=?",new String[]{Long.toString(task.id)});}
    // Latest attempt for each handle avoids retrying an older failure after a later success.
    static final String RETRY_SQL="SELECT * FROM tasks WHERE session=? AND state=? AND _id IN (SELECT MAX(_id) FROM tasks WHERE session=? GROUP BY handle) ORDER BY _id";
    List<DownloadTask> retryable(String session,DownloadTask.State state){List<DownloadTask> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(RETRY_SQL,new String[]{session,state.name(),session})){while(c.moveToNext())result.add(read(c));}return result;}
    int countRetryable(String session,DownloadTask.State state){try(Cursor c=getReadableDatabase().rawQuery(RETRY_SQL.replace("SELECT *","SELECT COUNT(*)"),new String[]{session,state.name(),session})){return c.moveToFirst()?c.getInt(0):0;}}
    static class Row {final DownloadTask task;final String session;Row(DownloadTask task,String session){this.task=task;this.session=session;}}
    List<Row> page(long before,int limit){List<Row> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM tasks WHERE _id<? ORDER BY _id DESC LIMIT ?",new String[]{Long.toString(before),Integer.toString(limit)})){while(c.moveToNext())result.add(new Row(read(c),c.getString(c.getColumnIndexOrThrow("session"))));}return result;}
    private DownloadTask read(Cursor c){DownloadTask task=new DownloadTask(c.getLong(c.getColumnIndexOrThrow("_id")),c.getInt(c.getColumnIndexOrThrow("handle")),c.getString(c.getColumnIndexOrThrow("name")),DownloadTask.State.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),c.getInt(c.getColumnIndexOrThrow("progress")),c.getInt(c.getColumnIndexOrThrow("attempts")),c.getString(c.getColumnIndexOrThrow("error")));task.camera=c.getString(c.getColumnIndexOrThrow("camera"));task.photoKey=c.getString(c.getColumnIndexOrThrow("photo_key"));return task;}
    private static final String RECOVERY_SQL="SELECT * FROM tasks WHERE camera=? AND photo_key IS NOT NULL AND session<>? AND state IN ('FAILED','STOPPED') AND _id IN (SELECT MAX(_id) FROM tasks WHERE camera=? AND photo_key IS NOT NULL GROUP BY photo_key) ORDER BY _id";
    List<DownloadTask> recoverable(String camera,String session){List<DownloadTask> result=new ArrayList<>();if(camera==null)return result;try(Cursor c=getReadableDatabase().rawQuery(RECOVERY_SQL,new String[]{camera,session,camera})){while(c.moveToNext())result.add(read(c));}return result;}
    List<DownloadTask> restore(String session,List<DownloadTask> pending,java.util.Map<String,Integer> matches){SQLiteDatabase db=getWritableDatabase();List<DownloadTask> result=new ArrayList<>();db.beginTransaction();try{for(DownloadTask previous:pending){Integer handle=matches.get(previous.photoKey);if(handle==null)continue;ContentValues v=new ContentValues();v.put("session",session);v.put("handle",handle);v.put("name",previous.name);v.put("state","WAITING");v.put("attempts",previous.attempts());v.put("camera",previous.camera);v.put("photo_key",previous.photoKey);v.put("created",System.currentTimeMillis());long id=db.insertOrThrow("tasks",null,v);DownloadTask task=new DownloadTask(id,handle,previous.name,DownloadTask.State.WAITING,0,previous.attempts(),"");task.camera=previous.camera;task.photoKey=previous.photoKey;result.add(task);}db.setTransactionSuccessful();}finally{db.endTransaction();}return result;}
}
