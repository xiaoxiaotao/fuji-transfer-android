package dev.danielc.fujiapp;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.jsibbold.zoomage.ZoomageView;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thumbnail first; full files stream to private disk and decode at screen resolution. */
public class Viewer extends AppCompatActivity {
 private static WeakReference<Viewer> instance;
 private static final java.util.concurrent.atomic.AtomicInteger nativeOperations=new java.util.concurrent.atomic.AtomicInteger();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile boolean closed;
 private volatile JSONObject info;
 private String key,name;
 private int handle;
 private volatile Uri savedUri;
 private Bitmap shown;
 private final String session=Gallery.getInstance()==null?java.util.UUID.randomUUID().toString():Gallery.getInstance().downloadSession;
 private final String camera=MySettings.photoCameraIdentity;
 static boolean isOpen(){Viewer v=instance==null?null:instance.get();return nativeOperations.get()>0||(v!=null&&!v.closed);}
 static void notifyDownloadSpeed(int kibps){Viewer v=instance==null?null:instance.get();if(v!=null&&!v.closed&&v.busy.get())v.ui.post(()->v.caption(String.format(Locale.CHINA,"正在传输 · %.1f MB/s",kibps/1024.0)));}
 static void notifyDownloadProgress(int percent){Viewer v=instance==null?null:instance.get();if(v!=null&&!v.closed&&v.busy.get())v.ui.post(()->v.caption("正在读取原文件 · "+percent+"%"));}
 @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_viewer);setSupportActionBar(findViewById(R.id.ui_toolbar));MainActivity.applyInsets(getWindow(),findViewById(R.id.viewer_root));getSupportActionBar().setDisplayHomeAsUpEnabled(true);instance=new WeakReference<>(this);handle=getIntent().getIntExtra("handle",0);findViewById(R.id.viewer_clear).setOnClickListener(v->operate(false,false));findViewById(R.id.viewer_save).setOnClickListener(v->operate(true,false));findViewById(R.id.viewer_share).setOnClickListener(v->shareAction());Gallery.pauseAll();busy.set(true);caption("正在读取预览…");nativeOperations.incrementAndGet();new Thread(this::loadThumbnail,"photo-preview").start();}
 private void caption(String text){if(closed)return;((TextView)findViewById(R.id.fileInfo)).setText(text);updateControls();}
 private void loadThumbnail(){try{String raw=Backend.cFujiBeginDownloadGetObjectInfo(handle);if(raw==null)throw new IOException("无法读取照片信息");info=new JSONObject(raw);name=new File(info.getString("filename").replace('\\','/')).getName();if(name.isEmpty()||name.equals(".")||name.equals(".."))throw new IOException("文件名无效");key=PhotoReceipts.key(camera==null?session:camera,handle,info);String cacheKey=key==null?session+":"+handle:key;byte[] thumb=PhotoDiskCache.thumb(this,cacheKey);if(thumb==null){thumb=Backend.cFujiGetThumb(handle);if(thumb!=null)PhotoDiskCache.thumb(this,cacheKey,thumb);}if(closed)return;Bitmap image=null;if(thumb!=null&&thumb.length>0){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(thumb,0,thumb.length,options);options.inSampleSize=PreviewSizing.sample(options.outWidth,options.outHeight,1280);options.inJustDecodeBounds=false;image=BitmapFactory.decodeByteArray(thumb,0,thumb.length,options);}show(image,"缩略图预览 · 可加载清晰图或保存原文件");}
 catch(Exception e){message("预览失败："+e.getMessage());}catch(OutOfMemoryError e){message("预览内存不足，仍可保存原文件");}finally{nativeOperations.decrementAndGet();busy.set(false);ui.post(this::updateControls);if(closed)Gallery.resumeAll();}}
 private void show(Bitmap image,String note){ui.post(()->{if(closed){if(image!=null)image.recycle();return;}getSupportActionBar().setTitle(name);ZoomageView view=findViewById(R.id.zoom_view);Bitmap old=shown;if(image!=null){shown=image;view.setImageBitmap(image);}if(image!=null&&old!=null&&old!=image){view.post(()->old.recycle());}caption(String.format(Locale.CHINA,"%.1f MB · %s\n%s",info.optLong("compressedSize")/1048576.0,name,note));});}
 private void message(String text){ui.post(()->{if(!closed){caption(text);Toast.makeText(this,text,Toast.LENGTH_LONG).show();}});}
 private File original()throws IOException{
  String cacheKey=key==null?session+":"+handle:key;
  File file=PhotoDiskCache.file(this,"original:"+cacheKey,".original");long size=info.optLong("compressedSize");
  if(file.isFile()&&size>0&&file.length()==size){file.setLastModified(System.currentTimeMillis());return file;}
  File part=new File(file.getPath()+".part");try{int rc=Backend.cFujiDownloadFile(handle,part.getAbsolutePath());if(rc!=0)throw new IOException("读取原文件失败，错误码="+rc);if(!part.isFile()||part.length()==0)throw new IOException("原文件为空");if(size>0&&part.length()!=size)throw new IOException("文件大小不匹配，请重新读取");if(!part.renameTo(file))throw new IOException("无法完成文件缓存");PhotoDiskCache.trim(this,file);return file;}finally{part.delete();}
 }
 private Bitmap decode(File file){BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),opt);int edge=Math.min(2560,Math.max(1280,getResources().getDisplayMetrics().heightPixels));opt.inSampleSize=PreviewSizing.sample(opt.outWidth,opt.outHeight,edge);opt.inJustDecodeBounds=false;return BitmapFactory.decodeFile(file.getPath(),opt);}
 private void operate(boolean save,boolean share){
  if(info==null||!busy.compareAndSet(false,true))return;
  updateControls();if(save){saveWithService(share);return;}
  Gallery.pauseAll();caption(save?"正在保存原文件…":"正在加载清晰图…");
  nativeOperations.incrementAndGet();new Thread(()->{try{File file=original();Bitmap image=decode(file);show(image,image==null?"此格式无法生成清晰预览，可保存原文件":"清晰预览 · 原文件尚未保存到手机");}
  catch(IOException|RuntimeException e){message(e.getMessage());}catch(OutOfMemoryError e){message("无法解码清晰图，原文件可直接保存");}finally{nativeOperations.decrementAndGet();busy.set(false);ui.post(this::updateControls);if(closed)Gallery.resumeAll();}},"photo-file-operation").start();
 }
 private void saveWithService(boolean share){
  if(Build.VERSION.SDK_INT>=33&&!getSharedPreferences("transfer_preferences",0).getBoolean("notification_prompted",false)&&androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){getSharedPreferences("transfer_preferences",0).edit().putBoolean("notification_prompted",true).apply();androidx.core.app.ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1801);}
  java.util.Map<Integer,Long> hints=new java.util.HashMap<>();hints.put(handle,info.optLong("compressedSize"));
  try{DownloadTransfer.Run run=DownloadTransfer.start(this,session,camera,new int[]{handle},null,false,hints);caption("正在保存原文件…");ui.post(new Runnable(){public void run(){if(closed)return;if(run.running){caption(run.summary()+"\n"+(run.meter==null?"准备中":run.meter.detail()));ui.postDelayed(this,400);return;}busy.set(false);String target=run.targets.get(handle);if(target!=null){savedUri=target.startsWith("content:")?Uri.parse(target):Uri.fromFile(new File(target));caption(run.skipped>0?"原文件已经保存，已跳过重复下载":"原文件已保存到手机");if(share)shareSaved();}else caption("保存未完成："+(run.error.isEmpty()?"请在下载任务中查看详情":run.error));}});}
  catch(RuntimeException e){busy.set(false);message("下载服务未能启动，请稍后重试");}
 }
 private void updateControls(){if(closed)return;boolean enabled=info!=null&&!busy.get();for(int id:new int[]{R.id.viewer_clear,R.id.viewer_save,R.id.viewer_share}){View view=findViewById(id);view.setEnabled(enabled);view.setAlpha(enabled?1f:0.4f);}((Button)findViewById(R.id.viewer_save)).setText(busy.get()?"处理中…":"保存原文件");}
 private void shareAction(){if(busy.get()||info==null)return;if(savedUri!=null)shareSaved();else operate(true,true);}
 private void shareSaved(){if(closed||savedUri==null)return;LocalMedia.share(this,savedUri,name);}
 @Override public boolean onCreateOptionsMenu(Menu menu){getMenuInflater().inflate(R.menu.viewer,menu);return true;}
 @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}if(item.getItemId()==R.id.action_clear_preview){operate(false,false);return true;}if(item.getItemId()==R.id.action_download){operate(true,false);return true;}if(item.getItemId()==R.id.action_share){shareAction();return true;}return super.onOptionsItemSelected(item);}
 @Override protected void onDestroy(){closed=true;ui.removeCallbacksAndMessages(null);if(instance!=null&&instance.get()==this)instance=null;if(shown!=null){((ZoomageView)findViewById(R.id.zoom_view)).setImageDrawable(null);shown.recycle();shown=null;}if(!busy.get())Gallery.resumeAll();super.onDestroy();}
}
