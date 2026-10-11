package dev.danielc.fujiapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Experimental XApp monitor. Displays decoded camera frames, with one queued UI frame. */
public class Liveview extends AppCompatActivity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ShutterScheduler shutter=new ShutterScheduler();
    private final AtomicReference<Bitmap> pendingFrame=new AtomicReference<>();
    private volatile boolean closed,stop,running;
    private boolean attempted,grid;
    private CaptureReceipt receipt;
    private int delaySeconds,frameWidth,frameHeight;
    private long lastFrameAt;
    private Bitmap shown;
    private String message="正在准备取景连接…",statistics="XApp Wi-Fi · 实验功能",empty="正在准备取景连接…";
    private final Runnable drainFrame=()->{
        Bitmap bitmap=pendingFrame.getAndSet(null);
        if(bitmap==null)return;
        if(closed||stop||!running){bitmap.recycle();return;}
        ImageView image=findViewById(R.id.monitor_image);Bitmap previous=shown;shown=bitmap;
        image.setImageBitmap(bitmap);findViewById(R.id.monitor_empty).setVisibility(View.GONE);
        frameWidth=bitmap.getWidth();frameHeight=bitmap.getHeight();lastFrameAt=SystemClock.elapsedRealtime();
        ((MonitorGrid)findViewById(R.id.monitor_grid)).setFrameSize(frameWidth,frameHeight);
        if(previous!=null)image.post(previous::recycle);
        shutter.frameReady();updateControls();
    };
    private final Runnable tick=new Runnable(){public void run(){if(closed)return;updateControls();ui.postDelayed(this,200);}};

    @Override protected void onCreate(Bundle state){super.onCreate(state);grid=MySettings.getSetting(this,"monitor_grid",0)==1;delaySeconds=MySettings.getSetting(this,"monitor_timer",0);if(delaySeconds!=3&&delaySeconds!=10)delaySeconds=0;bindLayout();}
    private void bindLayout(){
        setContentView(R.layout.activity_monitor);setSupportActionBar(findViewById(R.id.ui_toolbar));
        getSupportActionBar().setTitle("实时监看 · 实验");getSupportActionBar().setSubtitle(statistics);getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        MainActivity.applyInsets(getWindow(),findViewById(R.id.monitor_root));
        ((ImageView)findViewById(R.id.monitor_image)).setImageBitmap(shown);
        ((TextView)findViewById(R.id.monitor_empty)).setText(empty);
        findViewById(R.id.monitor_empty).setVisibility(shown==null?View.VISIBLE:View.GONE);
        ((MonitorGrid)findViewById(R.id.monitor_grid)).setFrameSize(frameWidth,frameHeight);
        findViewById(R.id.monitor_grid).setVisibility(grid?View.VISIBLE:View.GONE);
        status(message);
        findViewById(R.id.monitor_capture).setOnClickListener(v->{
            long now=SystemClock.elapsedRealtime();
            if(shutter.pending()){shutter.cancel();if(shown!=null)shutter.frameReady();status("自拍倒计时已取消");}
            else if(running&&!stop&&now-lastFrameAt<3000&&shutter.request(delaySeconds,now)){status(delaySeconds==0?"正在发送快门请求…":"自拍倒计时已开始，可再次点击取消");}
            updateControls();
        });
        findViewById(R.id.monitor_options).setOnClickListener(v->showOptions());
        findViewById(R.id.monitor_retry).setOnClickListener(v->startMonitor());
        findViewById(R.id.monitor_diagnostic).setOnClickListener(v->{String diagnostic="Fudge "+BuildConfig.VERSION_NAME+"\n实时监看诊断\n"+Frontend.getConnectionDiagnostic();((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Fudge 监看诊断",diagnostic));Toast.makeText(this,"诊断已复制",Toast.LENGTH_SHORT).show();});
        updateControls();
    }
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);bindLayout();}
    private void showOptions(){
        String[] items={grid?"关闭九宫格":"开启九宫格","立即拍摄"+(delaySeconds==0?" ✓":""),"3 秒自拍"+(delaySeconds==3?" ✓":""),"10 秒自拍"+(delaySeconds==10?" ✓":"")};
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("构图与自拍").setItems(items,(dialog,index)->{
            if(index==0){grid=!grid;MySettings.setSetting(this,"monitor_grid",grid?1:0);findViewById(R.id.monitor_grid).setVisibility(grid?View.VISIBLE:View.GONE);}
            else{delaySeconds=index==1?0:index==2?3:10;MySettings.setSetting(this,"monitor_timer",delaySeconds);}
            updateControls();
        }).setNegativeButton("关闭",null).show();
    }
    private void updateControls(){
        if(closed)return;long now=SystemClock.elapsedRealtime();Button capture=findViewById(R.id.monitor_capture);
        boolean pending=shutter.pending();capture.setEnabled(running&&!stop&&(pending||(now-lastFrameAt<3000&&shutter.canRequest(now))));
        capture.setText(pending?"取消倒计时":delaySeconds==0?"拍摄一张":delaySeconds+" 秒自拍");
        TextView countdown=findViewById(R.id.monitor_countdown);int seconds=shutter.secondsLeft(now);countdown.setText(String.valueOf(seconds));countdown.setVisibility(pending&&seconds>0?View.VISIBLE:View.GONE);
        findViewById(R.id.monitor_retry).setEnabled(!running&&!RemoteSession.isBusy());
        findViewById(R.id.monitor_options).setEnabled(!pending);
    }
    private void status(String text){message=text;if(!closed)((TextView)findViewById(R.id.monitor_status)).setText(text);}
    @Override protected void onResume(){super.onResume();ui.removeCallbacks(tick);ui.post(tick);if(!attempted){attempted=true;startMonitor();}}
    @Override protected void onStop(){stop=true;shutter.cancel();ui.removeCallbacks(tick);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onStop();}
    private void clearFrame(){
        Bitmap queued=pendingFrame.getAndSet(null);if(queued!=null)queued.recycle();
        ((ImageView)findViewById(R.id.monitor_image)).setImageDrawable(null);if(shown!=null){shown.recycle();shown=null;}lastFrameAt=0;frameWidth=frameHeight=0;((MonitorGrid)findViewById(R.id.monitor_grid)).setFrameSize(0,0);
    }
    private void startMonitor(){
        if(running||closed)return;CameraInfo camera=Gallery.currentInfo();DownloadTransfer.Run download=DownloadTransfer.current;
        if(camera==null||Backend.cGetKillSwitch()){status("请先连接相机并进入一次图库，再开启监看。");return;}
        if(camera.transport!=10){status("实时监看需要 XApp Wi-Fi 连接；USB 目前支持图库传图，请返回相机页切换连接方式。");return;}
        if((download!=null&&download.running)||Viewer.isOpen()){status("请先完成下载或关闭大图预览。");return;}
        if(!RemoteSession.acquire()){status("相机模式正在恢复，请稍后重试。");return;}
        receipt=new CaptureReceipt(Gallery.currentIndex());running=true;stop=false;shutter.reset();Gallery.pauseAll();clearFrame();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        empty="正在协商相机实时取景…";((TextView)findViewById(R.id.monitor_empty)).setText(empty);findViewById(R.id.monitor_empty).setVisibility(View.VISIBLE);
        status("正在等待图库请求结束并切换到取景模式…");updateControls();new Thread(this::runMonitor,"fuji-remote-monitor").start();
    }
    private void runMonitor(){
        String failure=null;int[] handles=null;boolean nativeAttempted=false;int frames=0,badFrames=0,accepted=0;
        long lastPing=SystemClock.elapsedRealtime(),firstFrameAt=0,lastStats=0;
        try{
            if(!Gallery.awaitPaused(15000))throw new Exception("图库仍在读取，请稍后重试");
            if(stop||closed)return;
            nativeAttempted=true;int rc=Backend.cRemoteStart();
            if(rc!=0){if(rc==Backend.PTP_IO_ERR)Backend.reportError(rc,"取景初始化通信失败，请重新连接");throw new Exception("取景初始化失败，错误码="+rc+"。请复制诊断。");}
            ui.post(()->status("取景端口已连接，正在等待相机画面…"));
            while(!stop&&!closed&&!Backend.cGetKillSwitch()){
                if(shutter.take(SystemClock.elapsedRealtime())){
                    // Never retry a shutter automatically: a lost response can still mean a saved photo.
                    int capture=Backend.cRemoteCapture();shutter.complete(SystemClock.elapsedRealtime());
                    if(capture==Backend.PTP_IO_ERR){Backend.reportError(capture,"快门通信失败，请重新连接");throw new Exception("快门结果未知，请检查相机照片，勿重复点击");}
                    if(capture==0)accepted++;final int requests=accepted;
                    ui.post(()->{if(closed)return;status(capture==0?"快门请求已接受 · 本次 "+requests+" 次\n退出监看后将核验图库新增文件。":capture==Backend.PTP_UNSUPPORTED?"相机暂不支持此快门指令，请复制诊断继续适配。":"快门未完成，错误码="+capture+"；查看诊断中的响应码。");updateControls();});
                }
                long pingNow=SystemClock.elapsedRealtime();
                if(pingNow-lastPing>=1000){rc=Backend.cPtpFujiPing();if(rc!=0){if(rc==Backend.PTP_IO_ERR)Backend.reportError(rc,"取景状态通信失败，请重新连接");throw new Exception("相机状态读取失败，错误码="+rc);}lastPing=pingNow;}
                byte[] bytes=Backend.cRemoteFrame();if(bytes==null||bytes.length==0)throw new Exception("取景流超时、关闭或格式不符，请复制诊断。");
                // Consume the stream promptly; avoid decoding more frames than the UI can display.
                if(pendingFrame.get()!=null)continue;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<=0||options.outHeight<=0||options.outWidth>8192||options.outHeight>8192){if(++badFrames>=3)throw new Exception("连续视频帧无法解码，请复制诊断。");continue;}
                options.inSampleSize=PreviewSizing.sample(options.outWidth,options.outHeight,1920);options.inJustDecodeBounds=false;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null){if(++badFrames>=3)throw new Exception("连续视频帧无法解码，请复制诊断。");continue;}badFrames=0;
                long now=SystemClock.elapsedRealtime();frames++;
                if(frames==1){firstFrameAt=now;Frontend.print("监看首帧已解码："+options.outWidth+"×"+options.outHeight+"，JPEG="+bytes.length+" 字节");ui.post(()->status("实时画面已就绪，可以拍摄或设置自拍倒计时。"));}
                if(now-lastStats>=1000){final String stats=String.format(Locale.CHINA,"%d × %d · %.1f fps",options.outWidth,options.outHeight,(frames-1)*1000.0/Math.max(1,now-firstFrameAt));ui.post(()->{statistics=stats;if(!closed)getSupportActionBar().setSubtitle(stats);});lastStats=now;}
                if(closed||stop)bitmap.recycle();else if(pendingFrame.compareAndSet(null,bitmap))ui.post(drainFrame);else bitmap.recycle();
            }
            if(Backend.cGetKillSwitch())failure="相机连接已断开，请重新连接。";
        }catch(Exception e){failure=e.getMessage()==null?"取景连接失败":e.getMessage();Frontend.print("监看未完成："+failure);}catch(OutOfMemoryError e){failure="取景解码内存不足，请重新连接";}
        finally{
            shutter.cancel();final String issue=failure;
            ui.post(()->{if(!closed){status("正在恢复图库模式，请稍候…");updateControls();}});
            java.util.concurrent.atomic.AtomicBoolean restoring=new java.util.concurrent.atomic.AtomicBoolean(true);
            Runnable recoveryTimeout=()->{if(restoring.compareAndSet(true,false)){Backend.cRemoteAbort();if(!closed)status("取景恢复超时，正在释放连接。请返回首页重新连接。");}};
            if(nativeAttempted)ui.postDelayed(recoveryTimeout,15000);
            int restored=0;
            try{
                if(nativeAttempted){
                    restored=Backend.cRemoteEnd();
                    if(restored==0&&!Backend.cGetKillSwitch()){
                        handles=Backend.cFujiGetObjectHandles();
                        if(handles==null){restored=Backend.PTP_IO_ERR;Backend.reportError(restored,"监看结束后照片索引读取失败，请重新连接");}
                        else{Backend.cPtpObjectServiceStart(handles);Frontend.print("监看结束：快门请求已接受="+accepted+" 次，图库文件="+handles.length);}
                    }else{restored=Backend.PTP_IO_ERR;Backend.reportError(restored,"监看退出后恢复图库失败，请重新连接");}
                }
            }catch(Exception|LinkageError e){restored=Backend.PTP_IO_ERR;Frontend.print("监看恢复失败："+e.getClass().getSimpleName());Backend.reportError(restored,"监看恢复失败，请重新连接");}
            finally{restoring.set(false);ui.removeCallbacks(recoveryTimeout);RemoteSession.release();running=false;}
            final int result=restored;final int[] refreshed=handles;
            final String captureResult=receipt.summary(accepted,result==0?handles:null);
            if(accepted>0)Frontend.print("拍摄核验："+captureResult);
            final boolean showReceipt=accepted>0;
            ui.post(()->{
                if(result==0){Gallery.acceptRemoteIndex(refreshed);Gallery.resumeAll();}
                if(showReceipt)Toast.makeText(getApplicationContext(),captureResult,Toast.LENGTH_LONG).show();
                if(closed)return;clearFrame();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                status(result!=0?"相机连接或图库恢复失败，请返回首页重新连接。":issue!=null?issue+"\n图库模式已恢复，可以重试取景。":"取景已停止。\n"+captureResult);
                empty=issue==null?"取景已停止":"暂时没有实时画面\n请复制诊断";((TextView)findViewById(R.id.monitor_empty)).setText(empty);findViewById(R.id.monitor_empty).setVisibility(View.VISIBLE);updateControls();
            });
        }
    }
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onDestroy(){closed=true;stop=true;shutter.cancel();ui.removeCallbacks(tick);clearFrame();super.onDestroy();}
}
