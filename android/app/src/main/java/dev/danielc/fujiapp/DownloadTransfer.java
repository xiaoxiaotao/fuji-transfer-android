package dev.danielc.fujiapp;
import android.app.*;
import android.content.*;
import android.os.*;
import android.net.wifi.WifiManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
/** A serial camera transfer is owned by a foreground service, never by an Activity. */
public final class DownloadTransfer extends Service {
 static final int NOTIFICATION=1801;
 static volatile Run current;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private Run owned;
 private PowerManager.WakeLock cpu;
 private WifiManager.WifiLock wifi;
 static final class Run {
  final String session,camera;final int[] handles;final DownloadTask.State retry;final boolean force,privateSave;
  final Map<Integer,Long> hints;
  volatile boolean running=true,stop;
  boolean recovery;
  volatile String phase="",recoveryNote="";
  volatile Runnable afterStop;
  volatile int done,failed,skipped,total;
  volatile DownloadTask active;
  volatile String error="";
  volatile TransferMeter meter;
  final Map<Integer,String> targets=new java.util.concurrent.ConcurrentHashMap<>();
  final Set<Integer> saved=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer,Boolean>());
  Run(String session,String camera,int[] handles,DownloadTask.State retry,boolean force,Map<Integer,Long> hints,boolean privateSave){this.privateSave=privateSave;this.session=session;this.camera=camera;this.handles=handles==null?null:handles.clone();this.retry=retry;this.force=force;this.hints=new HashMap<>(hints);}
  String summary(){if(running&&!phase.isEmpty())return phase;return recoveryNote+(running?"本次 "+total+" 张 · ":stop?"已停止 · ":"下载结束 · ")+"保存 "+done+" · 跳过 "+skipped+" · 失败 "+failed+(error.isEmpty()?"":" · "+error);}
 }
 static synchronized Run start(Context c,String session,String camera,int[] handles,DownloadTask.State retry,boolean force,Map<Integer,Long> hints){return start(c,session,camera,handles,retry,force,hints,false);}
 private static Run start(Context c,String session,String camera,int[] handles,DownloadTask.State retry,boolean force,Map<Integer,Long> hints,boolean recovery){if(RemoteSession.isBusy())throw new IllegalStateException("请先退出实时监看");if(current!=null&&current.running)throw new IllegalStateException("已有下载正在进行");Run run=new Run(session,camera,handles,retry,force,hints,MySettings.getSetting(c,"save_private",0)==1);run.recovery=recovery;current=run;try{androidx.core.content.ContextCompat.startForegroundService(c,new Intent(c,DownloadTransfer.class));}catch(RuntimeException e){run.running=false;run.error="系统未允许启动下载服务";throw e;}return run;}
 static synchronized Run startRecovery(Context c,String session,String camera,int[] currentHandles){if(camera==null)throw new IllegalArgumentException("请使用已配对相机连接后恢复");return start(c,session,camera,currentHandles,null,false,java.util.Collections.emptyMap(),true);}
 static void progress(int percent){Run run=current;if(run==null||!run.running)return;DownloadTask task=run.active;if(task!=null){task.progress(percent);if(run.meter!=null)run.meter.progress(percent,SystemClock.elapsedRealtime());}}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&"stop".equals(intent.getAction())){Run r=current;if(r!=null)r.stop=true;return START_NOT_STICKY;}
  Run run=current;if(run==null||!run.running){stopSelf();return START_NOT_STICKY;}
  if(owned==run)return START_NOT_STICKY;owned=run;
  try{if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("camera-transfer","相机照片下载",NotificationManager.IMPORTANCE_LOW));startForeground(NOTIFICATION,notification(run));}
  catch(RuntimeException e){run.running=false;run.error="下载服务启动失败";stopSelf();return START_NOT_STICKY;}
  try{cpu=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Fudge:Transfer");cpu.setReferenceCounted(false);cpu.acquire(30*60*1000L);
  WifiManager manager=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(manager!=null){wifi=manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"Fudge:Camera");wifi.acquire();}
  ui.post(tick);new Thread(()->heartbeat(run),"transfer-events").start();new Thread(()->execute(run,id),"camera-transfer-service").start();return START_NOT_STICKY;}catch(RuntimeException e){run.running=false;run.error="无法启动后台传输";releaseLocks();stopForeground(true);stopSelf();return START_NOT_STICKY;}
 }
 private final Runnable tick=new Runnable(){public void run(){Run r=current;if(r!=null&&r.running){try{getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(r));}catch(SecurityException ignored){}ui.postDelayed(this,1000);}}};
 private Notification notification(Run run){Intent open=new Intent(this,Gallery.class).putExtra("resume_transfer",true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,DownloadTransfer.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new NotificationCompat.Builder(this,"camera-transfer").setSmallIcon(R.drawable.baseline_download_24).setContentTitle(run.stop?"当前照片完成后停止":"正在下载相机照片").setContentText(run.summary()).setStyle(new NotificationCompat.BigTextStyle().bigText(run.summary()+"\n"+(run.meter==null?"准备中":run.meter.detail()))).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,run.meter==null?0:run.meter.progress(),run.meter==null).addAction(0,"停止剩余",stop).build();}
 private void heartbeat(Run run){
  while(run.running&&!run.stop&&current==run){
   if(Backend.cGetKillSwitch()){run.stop=true;return;}
   int rc=Backend.cPtpFujiPing();
   // A result arriving after an explicit stop must not force-close the session
   // while the transfer worker is still saving the current photo.
   if(!run.running||run.stop||current!=run)return;
   if(rc!=0){run.stop=true;run.error="相机连接中断";Backend.reportErrorNonBlock(Backend.PTP_IO_ERR,"相机没有响应");return;}
   try{Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
  }
 }
 private void execute(Run run,int startId){DownloadTasks store=DownloadTasks.get(this);File parent=PhotoStorage.stagingRoot(this,run.privateSave);File batchDir=new File(new File(parent,"fudge"),"batch-"+UUID.randomUUID());
  try{List<DownloadTask> batch=run.recovery?recover(run,store):run.handles==null?store.retryable(run.session,run.retry):store.create(run.session,run.handles);if(!run.recovery&&run.handles==null)store.resetForRetry(batch);run.total=batch.size();run.meter=new TransferMeter(batch.size());for(DownloadTask task:batch){Long hint=run.hints.get(task.handle);if(hint!=null)run.meter.expect(task.handle,hint);}
   // Capture every queued photo's identity before downloading the first file.
   for(int i=0;i<batch.size();i++){
    if(run.stop||Backend.cGetKillSwitch())break;
    DownloadTask task=batch.get(i);run.phase="正在准备下载清单 · "+(i+1)+"/"+batch.size();
    JSONObject photo=readInfo(task.handle);String key=PhotoReceipts.key(run.camera==null?run.session:run.camera,task.handle,photo);
    if(task.photoKey!=null&&!task.photoKey.equals(key))throw new IOException("照片信息已变化，请在图库重新选择");
    task.camera=run.camera;task.photoKey=key;task.name=new File(photo.getString("filename").replace('\\','/')).getName();store.save(task);run.meter.expect(task.handle,photo.optLong("compressedSize"));
   }
   run.phase="";
   final boolean[] stoppedSaved={false};
   DownloadBatchRunner.run(batch,()->run.stop||Backend.cGetKillSwitch(),task->download(run,store,task,batchDir),task->{
    if(task.state()==DownloadTask.State.STOPPED){if(!stoppedSaved[0]){store.stopWaiting(run.session);stoppedSaved[0]=true;}return;}
    store.save(task);if(task.state()==DownloadTask.State.RUNNING){run.active=task;run.meter.start(task.handle);}
    else if(task.state()==DownloadTask.State.DONE){run.done++;run.saved.add(task.handle);run.meter.finish(task.handle,false);}
    else if(task.state()==DownloadTask.State.SKIPPED){run.skipped++;run.saved.add(task.handle);run.meter.finish(task.handle,false);}
    else if(task.state()==DownloadTask.State.FAILED){run.failed++;run.meter.finish(task.handle,false);Frontend.print(task.name+"："+task.error());}
   });
  }catch(Exception e){run.error=e.getMessage()==null?"下载无法继续":e.getMessage();}
  finally{try{store.stopWaiting(run.session);}catch(RuntimeException e){Frontend.print("保存未完成下载状态失败："+e.getMessage());}run.phase="";batchDir.delete();run.active=null;ui.post(()->{ui.removeCallbacks(tick);releaseLocks();stopForeground(true);run.running=false;stopSelfResult(startId);Runnable finished=run.afterStop;run.afterStop=null;if(finished!=null)finished.run();else Gallery.resumeAll();});}
 }
 private JSONObject readInfo(int handle)throws Exception{String raw=Backend.cFujiBeginDownloadGetObjectInfo(handle);if(raw==null)throw new IOException("无法读取照片信息，请重新连接后恢复");return new JSONObject(raw);}
 private List<DownloadTask> recover(Run run,DownloadTasks store)throws Exception{
  List<DownloadTask> pending=store.recoverable(run.camera,run.session);run.total=pending.size();
  if(pending.isEmpty()){run.recoveryNote="没有可恢复的历史任务 · ";return new ArrayList<>();}
  DownloadRecovery matcher=new DownloadRecovery();
  for(int i=0;i<run.handles.length;i++){
   if(run.stop||Backend.cGetKillSwitch())throw new IOException("核对已停止，未完成清单已保留");
   run.phase="正在核对相机照片 · "+(i+1)+"/"+run.handles.length;
   JSONObject photo=readInfo(run.handles[i]);matcher.observe(PhotoReceipts.key(run.camera,run.handles[i],photo),run.handles[i]);
  }
  List<DownloadTask> result=store.restore(run.session,pending,matcher.matches());
  int unmatched=pending.size()-result.size();run.recoveryNote=unmatched>0?unmatched+" 个文件未能唯一匹配，记录已保留 · ":"";
  Frontend.print("恢复下载清单：匹配 "+result.size()+"，无法确认 "+unmatched);
  return result;
 }
 private void download(Run run,DownloadTasks store,DownloadTask task,File batchDir)throws Exception{
  if(cpu!=null)cpu.acquire(30*60*1000L);
  File itemDir=new File(batchDir,Integer.toUnsignedString(task.handle)),part=new File(itemDir,"download.part");
  try{String raw=Backend.cFujiBeginDownloadGetObjectInfo(task.handle);if(raw==null)throw new IOException("无法读取照片信息");JSONObject photo=new JSONObject(raw);run.meter.expect(task.handle,photo.optLong("compressedSize"));String key=PhotoReceipts.key(run.camera==null?run.session:run.camera,task.handle,photo);if(task.photoKey!=null&&!task.photoKey.equals(key))throw new IOException("相机照片信息已变化，已停止该文件下载");task.name=new File(photo.getString("filename").replace('\\','/')).getName();if(task.name.isEmpty()||task.name.equals(".")||task.name.equals(".."))throw new IOException("文件名无效");store.save(task);
   if(!run.force&&PhotoReceipts.exists(this,key)){String existing=PhotoReceipts.target(this,key);if(existing!=null)run.targets.put(task.handle,existing);task.skipped();return;}
   if(!itemDir.mkdirs())throw new IOException("无法创建保存位置");File cached=key==null?null:PhotoDiskCache.file(this,"original:"+key,".original");int rc=0;if(cached!=null&&cached.isFile()&&cached.length()==photo.optLong("compressedSize")){try(InputStream input=new FileInputStream(cached);OutputStream output=new FileOutputStream(part)){byte[] buf=new byte[65536];int n;while((n=input.read(buf))!=-1)output.write(buf,0,n);}run.active.progress(100);}else rc=Backend.cFujiDownloadFile(task.handle,part.getAbsolutePath());if(rc!=0)throw new IOException("下载失败，错误码="+rc);PhotoStorage.validateDownload(part,photo.optLong("compressedSize"));File file=new File(itemDir,task.name);if(!part.renameTo(file))throw new IOException("文件保存失败");task.saving();store.save(task);
   boolean keepOriginal=false;try{long size=file.length();String target=PhotoStorage.publish(this,file.getAbsolutePath(),run.privateSave);keepOriginal=!target.startsWith("content:");PhotoReceipts.saved(this,key,target,size);run.targets.put(task.handle,target);}finally{if(Build.VERSION.SDK_INT>=29&&!keepOriginal)file.delete();}
  }finally{part.delete();itemDir.delete();}
 }
 @Override public void onTimeout(int startId,int type){Run r=current;if(r!=null){r.stop=true;r.error="系统限制了后台下载时长，请重新连接后继续";}Backend.reportErrorNonBlock(0,"后台下载已超时");stopForeground(true);stopSelf();}
 private void releaseLocks(){if(cpu!=null&&cpu.isHeld())cpu.release();if(wifi!=null&&wifi.isHeld())wifi.release();}
 @Override public void onDestroy(){Run r=owned;if(r!=null&&r.running)r.stop=true;ui.removeCallbacksAndMessages(null);releaseLocks();super.onDestroy();}
}
