// Activity with a recycle view, populates with thumbnails - image is downloaded in Viewer activity.
// This does most of the connection initialization (maybe move somewhere else?)
// Copyright 2023 Daniel C - https://github.com/petabyt/fujiapp
package dev.danielc.fujiapp;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

public class Gallery extends AppCompatActivity {
    private static WeakReference<Gallery> instance = null;
    static Gallery getInstance() {
        if (instance == null) return null;
        if (instance.get() == null) return null;
        return instance.get();
    }
    final int GRID_SIZE = 3;
    private int[] objectHandles;
    private int[] visibleHandles;
    private CameraInfo cameraInfo;
    private final String cameraIdentity=MySettings.photoCameraIdentity;
    private final java.util.concurrent.ConcurrentHashMap<Integer,JSONObject> metadata=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> savedHandles=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer,Boolean>());
    private final java.util.concurrent.ExecutorService receiptWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int formatFilter, sortOrder;
    private String dateFilter="";
    private boolean onlyNew, forceDownload;
    private RecyclerView recyclerView;
    private PtpThumbAdapter imageAdapter;
    private ListView listView;
    private PtpInfoAdapter list;

    Handler handler;

    String downloadSession=java.util.UUID.randomUUID().toString();
    private volatile boolean alive=true,foreground;
    private boolean details;private int chipState=-1;
    boolean detailsMode(){return details;}
    static int[] currentIndex(){Gallery g=getInstance();return g==null||!g.alive||g.objectHandles==null?null:g.objectHandles.clone();}
    static CameraInfo currentInfo(){Gallery gallery=getInstance();return gallery==null||!gallery.alive||Backend.cGetKillSwitch()?null:gallery.cameraInfo;}
    static void discardPreviousSession(){Gallery old=getInstance();if(old!=null){old.alive=false;old.stopOwnQueues();old.finish();}connectionSnapshot=null;}
    private void stopOwnQueues(){if(imageAdapter!=null)imageAdapter.queue.stopRequestThread();if(list!=null)list.queue.stopRequestThread();}
    static void disconnectFrom(android.app.Activity owner){
        MainActivity.stopAutomaticConnection(owner);
        if(RemoteSession.isBusy()){android.widget.Toast.makeText(owner,"后台重连已停止，正在恢复相机模式，请稍后断开",android.widget.Toast.LENGTH_SHORT).show();return;}
        DownloadTransfer.Run run=DownloadTransfer.current;
        if(run!=null&&run.running){
            run.afterStop=()->finishDisconnect(owner);
            run.stop=true;
            android.widget.Toast.makeText(owner,"后台重连已停止，当前照片保存完成后自动断开",android.widget.Toast.LENGTH_LONG).show();return;
        }
        finishDisconnect(owner);
    }
    private static void finishDisconnect(android.app.Activity owner){
        Gallery old=getInstance();if(old!=null){old.alive=false;old.stopOwnQueues();old.finish();}
        connectionSnapshot=null;
        MainActivity.closeManualSession(()->{if(!owner.isDestroyed())WorkbenchNav.open(owner,WorkbenchNav.CAMERA);});
    }

    private DownloadTransfer.Run appliedRun;
    private final java.util.Set<Integer> appliedSaved=new java.util.HashSet<>();
    private static Snapshot connectionSnapshot;
    private static final class Snapshot {String session;int[] handles;CameraInfo info;java.util.Map<Integer,JSONObject> metadata;int format,sort;String date;boolean onlyNew;int[] selected;}
    private final Runnable filterRefresh=()->applyFilters();
    private final Runnable transferPoll=new Runnable(){public void run(){if(!alive)return;syncTransfer();handler.postDelayed(this,400);}};
    volatile DownloadTask activeTask;
    private DownloadTasks tasks;
    private final PhotoSelection selection = new PhotoSelection();
    private boolean selecting;
    private volatile boolean batchRunning, cancelRemaining;
    private volatile int completed, failed, skipped, batchTotal;

    static void downloadingFile(JSONObject oi) { }
    static void downloadedFile(String filepath) { }
    static void stopDownloading() { DownloadTransfer.Run run=DownloadTransfer.current;if(run!=null)run.stop=true; }
    boolean isSelecting() { return selecting; }
    boolean isSelected(int handle) { return selection.contains(handle); }
    boolean onPhotoTap(int handle) {
        if(batchRunning)return true;
        if(!selecting)return false;
        selection.toggle(handle);updateSelectionUi();return true;
    }
    void beginSelection(int handle) {
        if(batchRunning || objectHandles==null)return;
        selecting=true;selection.toggle(handle);updateSelectionUi();
    }
    private void updateSelectionUi() {
        ((TextView)findViewById(R.id.gallery_count)).setText(selecting?"已选择 "+selection.size()+" 张":objectHandles==null?"正在读取照片…":(visibleHandles.length==objectHandles.length?objectHandles.length+" 张照片":"筛选结果 "+visibleHandles.length+" / "+objectHandles.length+" 张"));
        ((Button)findViewById(R.id.filter_photos)).setText(formatFilter!=0||sortOrder!=0||onlyNew||!dateFilter.isEmpty()?"筛选 · 已启用":"筛选排序");
        findViewById(R.id.range_photos).setVisibility(selecting?View.VISIBLE:View.GONE);
        findViewById(R.id.filter_photos).setEnabled(!batchRunning);findViewById(R.id.range_photos).setEnabled(!batchRunning&&visibleCount()>0);
        ((Button)findViewById(R.id.import_button)).setText(selecting?"完成":"选择");
        findViewById(R.id.selection_actions).setVisibility(selecting?View.VISIBLE:View.GONE);
        findViewById(R.id.download_actions).setVisibility(selecting||batchRunning?View.VISIBLE:View.GONE);
        ProgressBar progress=findViewById(R.id.transfer_progress);
        if(!batchRunning){progress.setIndeterminate(objectHandles==null);progress.setVisibility(objectHandles==null?View.VISIBLE:View.GONE);}
        Button download=findViewById(R.id.download_selected);
        download.setText(batchRunning?"正在下载…":"下载 "+selection.size()+" 张");download.setEnabled(selection.size()>0&&!batchRunning);download.setAlpha(download.isEnabled()?1f:0.35f);
        ((TextView)findViewById(R.id.transfer_caption)).setText(batchRunning?"正在保存到手机":selecting?"已选 "+selection.size()+" 个 · "+selectedSize(selection.snapshot()):"长按照片进入多选，点击照片查看大图");
        findViewById(R.id.table_button).setEnabled(!selecting&&!batchRunning);
        findViewById(R.id.import_button).setEnabled(!batchRunning);
        findViewById(R.id.gallery_button).setEnabled(!batchRunning);
        findViewById(R.id.select_all).setEnabled(!batchRunning);
        findViewById(R.id.clear_selection).setEnabled(!batchRunning);
        TextView empty=findViewById(R.id.gallery_empty);empty.setText(objectHandles==null?"正在读取相机照片…":objectHandles.length==0?"相机中没有可浏览的文件":"没有匹配的照片\n试试其他筛选条件。");empty.setVisibility(visibleCount()==0?View.VISIBLE:View.GONE);empty.bringToFront();
        renderChips();
        if(imageAdapter!=null)imageAdapter.refreshSelection();
    }
    boolean isTransferring(){return batchRunning;}
    boolean canStartTransfers(){return !isFinishing()&&!isDestroyed()&&!batchRunning&&!RemoteSession.isBusy()&&objectHandles!=null&&!Backend.cGetKillSwitch();}
    void requestTransferStop(){cancelRemaining=true;DownloadTransfer.Run run=DownloadTransfer.current;if(run!=null&&run.session.equals(downloadSession))run.stop=true;}
    boolean isStopRequested(){return cancelRemaining;}
    String transferSummary(){DownloadTransfer.Run run=DownloadTransfer.current;if(run!=null&&run.session.equals(downloadSession))return run.summary()+(run.meter==null?"":"\n"+run.meter.detail());return batchRunning?"本次 "+batchTotal+" 张 · 已保存 "+completed+" · 跳过 "+skipped+" · 失败 "+failed:"已保存 "+completed+" 张 · 失败 "+failed+" 张";}
    void retryTasks(DownloadTask.State state){if(canStartTransfers())beginBatch(null,state);}
    static void notifyBatchProgress(int percent) {DownloadTransfer.progress(percent);}
    private void startSelectedDownload() {
        if(!canStartTransfers()||selection.size()==0)return;
        showDownloadConfirmation(selection.snapshot());
    }
    private void beginBatch(int[] handles,DownloadTask.State retryState) {
        if(!canStartTransfers())return;
        if(Build.VERSION.SDK_INT>=33&&!getSharedPreferences("transfer_preferences",0).getBoolean("notification_prompted",false)&&androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){getSharedPreferences("transfer_preferences",0).edit().putBoolean("notification_prompted",true).apply();androidx.core.app.ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1801);}
        java.util.Map<Integer,Long> hints=new java.util.HashMap<>();for(java.util.Map.Entry<Integer,JSONObject> entry:metadata.entrySet())hints.put(entry.getKey(),entry.getValue().optLong("compressedSize"));
        pauseAll();
        try{DownloadTransfer.start(this,downloadSession,cameraIdentity,handles,retryState,forceDownload,hints);syncTransfer();}
        catch(RuntimeException e){resumeAll();android.widget.Toast.makeText(this,"下载服务未能启动，请保持应用在前台后重试",android.widget.Toast.LENGTH_LONG).show();}
    }
    private void syncTransfer(){
        DownloadTransfer.Run run=DownloadTransfer.current;if(run==null||!run.session.equals(downloadSession))return;
        boolean wasRunning=batchRunning;batchRunning=run.running;cancelRemaining=run.stop;activeTask=run.active;completed=run.done;failed=run.failed;skipped=run.skipped;batchTotal=run.total;
        if(appliedRun!=run){appliedRun=run;appliedSaved.clear();}
        boolean changed=false;for(int handle:run.saved){if(!appliedSaved.add(handle))continue;if((batchRunning||wasRunning)&&savedHandles.add(handle))changed=true;if(selection.contains(handle)){selection.remove(handle);changed=true;}}
        if(batchRunning)pauseAll();
        if(wasRunning!=batchRunning||changed)updateSelectionUi();
        findViewById(R.id.cancel_transfer).setVisibility(batchRunning?View.VISIBLE:View.GONE);
        if(batchRunning){ProgressBar bar=findViewById(R.id.transfer_progress);bar.setVisibility(View.VISIBLE);bar.setIndeterminate(run.meter==null);bar.setProgress(run.meter==null?0:run.meter.progress());((TextView)findViewById(R.id.transfer_caption)).setText((activeTask==null?"准备下载":activeTask.name)+"\n"+run.summary()+(run.meter==null?"":"\n"+run.meter.detail()));}
        if(wasRunning&&!batchRunning){findViewById(R.id.transfer_progress).setVisibility(View.GONE);((TextView)findViewById(R.id.transfer_caption)).setText(run.summary());applyFilters();if(!Backend.cGetKillSwitch())resumeAll();}
    }

    int visiblePosition(int handle){if(visibleHandles!=null)for(int i=0;i<visibleHandles.length;i++)if(visibleHandles[i]==handle)return i+1;return 0;}
    int[] visibleSnapshot(){return visibleHandles==null?new int[0]:visibleHandles.clone();}
    int visibleCount(){return visibleHandles==null?0:visibleHandles.length;}
    String receiptKey(int handle,JSONObject info){return PhotoReceipts.key(cameraIdentity==null?downloadSession:cameraIdentity,handle,info);}
    void markSaved(int handle){savedHandles.add(handle);handler.post(()->{if(imageAdapter!=null)imageAdapter.refreshSelection();});}
    int visibleHandle(int position){return visibleHandles[position];}
    boolean isSaved(int handle){return savedHandles.contains(handle);}
    void observeInfo(int handle,JSONObject info){
        if(isDestroyed()||receiptWorker.isShutdown()||info==null||metadata.putIfAbsent(handle,info)!=null)return;
        try{receiptWorker.execute(()->{String key=PhotoReceipts.key(cameraIdentity==null?downloadSession:cameraIdentity,handle,info);if(PhotoReceipts.exists(this,key))savedHandles.add(handle);handler.post(()->{if(isDestroyed())return;if(imageAdapter!=null)imageAdapter.refreshSelection();handler.removeCallbacks(filterRefresh);handler.postDelayed(filterRefresh,250);});});}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    private void applyFilters(){
        if(objectHandles==null||batchRunning)return;
        java.util.Map<Integer,JSONObject> snapshot=new java.util.HashMap<>(metadata);
        java.util.ArrayList<Integer> filtered=new java.util.ArrayList<>();
        for(int handle:objectHandles){JSONObject info=snapshot.get(handle);if(PhotoFilter.matches(formatFilter,dateFilter,onlyNew,info==null?"":info.optString("filename"),info==null?"":info.optString("dateCreated"),savedHandles.contains(handle)))filtered.add(handle);}
        if(sortOrder!=0)java.util.Collections.sort(filtered,(a,b)->{JSONObject ia=snapshot.get(a),ib=snapshot.get(b);String va=ia==null?"":ia.optString(sortOrder==3?"filename":"dateCreated"),vb=ib==null?"":ib.optString(sortOrder==3?"filename":"dateCreated");if(va.isEmpty()!=vb.isEmpty())return va.isEmpty()?1:-1;int c=va.compareTo(vb);if(c==0)c=Integer.compareUnsigned(a,b);return sortOrder==1?-c:c;});
        int[] next=new int[filtered.size()];for(int i=0;i<next.length;i++)next[i]=filtered.get(i);
        if(!java.util.Arrays.equals(next,visibleHandles)){visibleHandles=next;if(imageAdapter!=null)imageAdapter.updateOrder();}
        updateSelectionUi();
    }
    private void showFilters(){
        if(!canStartTransfers())return;setupPage(Page.GALLERY);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(WorkbenchNav.dp(this,20),WorkbenchNav.dp(this,16),WorkbenchNav.dp(this,20),0);
        android.widget.Spinner formats=new android.widget.Spinner(this),sort=new android.widget.Spinner(this);
        formats.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"全部格式","JPEG 照片","RAW / RAF","视频"}));formats.setSelection(formatFilter);box.addView(formats);
        sort.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"相机原始顺序","拍摄时间 · 新到旧","拍摄时间 · 旧到新","文件名"}));sort.setSelection(sortOrder);box.addView(sort);
        android.widget.EditText date=new android.widget.EditText(this);date.setSingleLine(true);date.setHint("拍摄日期 YYYYMMDD，留空显示全部");date.setText(dateFilter);date.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);box.addView(date);
        android.widget.CheckBox fresh=new android.widget.CheckBox(this);fresh.setText("仅显示未下载");fresh.setChecked(onlyNew);box.addView(fresh);
        TextView note=new TextView(this);note.setText("照片信息会逐步读取，筛选结果会随读取更新。已选照片保留；全选只选择当前结果。");box.addView(note);
        androidx.appcompat.app.AlertDialog dialog=new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("筛选与排序").setView(box).setPositiveButton("应用",null).setNeutralButton("重置",(d,w)->{formatFilter=sortOrder=0;dateFilter="";onlyNew=false;applyFilters();}).setNegativeButton("取消",null).create();dialog.show();dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{String input=date.getText().toString().trim();if(!PhotoFilter.validDate(input)){date.setError("请填写有效的 YYYYMMDD");return;}formatFilter=formats.getSelectedItemPosition();sortOrder=sort.getSelectedItemPosition();dateFilter=input;onlyNew=fresh.isChecked();applyFilters();dialog.dismiss();});
    }
    private void showRange(){
        if(!canStartTransfers()||visibleCount()==0)return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(WorkbenchNav.dp(this,20),WorkbenchNav.dp(this,16),WorkbenchNav.dp(this,20),0);
        android.widget.EditText from=new android.widget.EditText(this),to=new android.widget.EditText(this);from.setHint("起始位置（从 1 开始）");to.setHint("结束位置（最多 "+visibleCount()+"）");from.setInputType(2);to.setInputType(2);box.addView(from);box.addView(to);
        from.setText("1");to.setText(Integer.toString(visibleCount()));
        androidx.appcompat.app.AlertDialog dialog=new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("按当前排列选择范围").setView(box).setPositiveButton("选择",null).setNegativeButton("取消",null).create();dialog.show();dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{try{int a=Integer.parseInt(from.getText().toString()),b=Integer.parseInt(to.getText().toString());if(a<1||b<a||b>visibleCount())throw new IllegalArgumentException();selection.selectRange(visibleHandles,a,b);selecting=true;setupPage(Page.GALLERY);updateSelectionUi();dialog.dismiss();}catch(Exception e){to.setError("范围应为 1 到 "+visibleCount()+"，结束位置不能小于起始位置");}});
    }

    private void renderChips(){LinearLayout row=findViewById(R.id.gallery_chips);if(row==null)return;int state=onlyNew?4:formatFilter;if(chipState==state)return;chipState=state;WorkbenchNav.chips(this,row,new String[]{"全部","照片","RAW","视频","未下载"},onlyNew?4:formatFilter,value->{if(batchRunning)return;formatFilter=value==4?0:value;onlyNew=value==4;setupPage(Page.GALLERY);applyFilters();});}
    private String selectedSize(int[] handles){long bytes=0;int known=0;for(int handle:handles){JSONObject info=metadata.get(handle);long size=info==null?0:info.optLong("compressedSize");if(size>0){known++;bytes+=size;}}return known==handles.length?String.format(java.util.Locale.CHINA,"约 %.1f MB",bytes/1048576.0):String.format(java.util.Locale.CHINA,"已知 %.1f MB · %d 个文件待读取大小",bytes/1048576.0,handles.length-known);}
    private void showDownloadConfirmation(int[] handles){if(handles.length==0)return;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=WorkbenchNav.dp(this,20);box.setPadding(pad,pad,pad,0);TextView note=new TextView(this);note.setText(handles.length+" 个文件 · "+selectedSize(handles)+"\n保存原文件，保留相机图像内容。\n保存位置："+(MySettings.getSetting(this,"save_private",0)==1?"应用目录":"系统相册 / 文件中的 Fudge 目录"));note.setTextSize(14);box.addView(note);android.widget.CheckBox skip=new android.widget.CheckBox(this);skip.setText("跳过已经保存的文件");skip.setChecked(MySettings.getSetting(this,"skip_existing",1)==1);box.addView(skip);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("保存到手机").setView(box).setPositiveButton("下载 "+handles.length+" 个文件",(d,w)->{forceDownload=!skip.isChecked();beginBatch(handles,null);}).setNegativeButton("取消",null).show();}
    private void showCameraInfo(){CameraDetails.show(this,cameraInfo);}

    public static void setLogText(String arg) {
        if (getInstance() == null) return;
        getInstance().handler.post(new Runnable() {
            @Override
            public void run() {
                if (getInstance() == null) return;
                TextView tv = getInstance().findViewById(R.id.gallery_logs);
                if (tv == null) return;
                tv.setVisibility(DebugUi.enabled(tv.getContext()) ? View.VISIBLE : View.GONE);
                tv.setText(arg);
            }
        });
    }

    void fail(int code, String reason) {
        alive=false;stopOwnQueues();
        ConnectionState.shared.error(ConnectionState.shared.snapshot().steps[2]==ConnectionState.Status.READY?ConnectionState.Step.GALLERY:ConnectionState.Step.SESSION,reason);
        Backend.reportError(code, reason);
        handler.post(this::finish);
    }

    static void setTitleCamName(String name) {
        if (getInstance() == null) return;
        getInstance().handler.post(new Runnable() {
            @Override
            public void run() {
                if (getInstance() == null) return;
                ActionBar actionBar = getInstance().getSupportActionBar();
                actionBar.setTitle(name);
            }
        });
    }

    static void pauseAll() {
        Gallery ctx = getInstance(); if (ctx == null) return;
        if (ctx.imageAdapter != null) {
            ctx.imageAdapter.queue.pause();
        }
        if (ctx.list != null) {
            ctx.list.queue.pause();
        }
    }

    static boolean awaitPaused(long timeout)throws InterruptedException{Gallery gallery=getInstance();if(gallery==null)return true;pauseAll();long end=android.os.SystemClock.elapsedRealtime()+timeout;if(gallery.imageAdapter!=null&&!gallery.imageAdapter.queue.awaitPaused(timeout))return false;return gallery.list==null||gallery.list.queue.awaitPaused(Math.max(1,end-android.os.SystemClock.elapsedRealtime()));}
    static void acceptRemoteIndex(int[] handles){Gallery gallery=getInstance();if(gallery==null||!gallery.alive||handles==null)return;gallery.objectHandles=handles;gallery.visibleHandles=handles;gallery.cameraInfo=new CameraInfo(Backend.cGetCachedCameraInfo(),Backend.cGetTransport(),handles.length,android.os.SystemClock.elapsedRealtime());gallery.applyFilters();gallery.renderCameraCard();gallery.saveSnapshot();}
    static void stopAll() {
        Gallery ctx = getInstance(); if (ctx == null) return;
        if (ctx.imageAdapter != null) {
            ctx.imageAdapter.queue.stopRequestThread();
        }
        if (ctx.list != null) {
            ctx.list.queue.stopRequestThread();
        }
    }

    static void resumeAll() {
        DownloadTransfer.Run run=DownloadTransfer.current;if((run!=null&&run.running)||Viewer.isOpen()||RemoteSession.isBusy())return;
        Gallery ctx = getInstance(); if (ctx == null||!ctx.foreground||!ctx.alive||Backend.cGetKillSwitch()) return;
        if (ctx.current==Page.GALLERY&&ctx.imageAdapter != null)
            ctx.imageAdapter.queue.start();
        if (ctx.current==Page.FILE_TABLE&&ctx.list != null)
            ctx.list.queue.start();
    }

    void createGallery() {
        if(!alive)return;
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SIZE));

        imageAdapter = new PtpThumbAdapter(this);
        imageAdapter.updateOrder();
        imageAdapter.queue.pause();imageAdapter.queue.startRequestThread();

        ViewGroup fileView = findViewById(R.id.fileView);
        if(!alive)return;
        fileView.addView(recyclerView,new android.widget.FrameLayout.LayoutParams(-1,-1));

        recyclerView.setAdapter(imageAdapter);
        resumeAll();
        recyclerView.setItemViewCacheSize(12);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setPadding(0, 0, 0, 16);
        recyclerView.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            recyclerView.setFocusable(View.FOCUSABLE);
        }
        recyclerView.setClickable(true);
    }

    void closeGallery() {
        if(imageAdapter!=null)imageAdapter.queue.stopRequestThread();
        if(recyclerView!=null&&recyclerView.getParent() instanceof ViewGroup)((ViewGroup)recyclerView.getParent()).removeView(recyclerView);
        imageAdapter=null;recyclerView=null;
    }

    void createList() {
        if(!alive)return;
        ViewGroup fileView = findViewById(R.id.fileView);
        listView = new ListView(this);
        listView.setPadding(0, 0, 0, 16);
        listView.setClipToPadding(false);
        list = new PtpInfoAdapter(objectHandles, listView);
        list.queue.pause();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.MATCH_PARENT
        );
        listView.setLayoutParams(params);
        if(!alive)return;
        fileView.addView(listView);
        listView.setAdapter(list);
        list.queue.startRequestThread();
        resumeAll();
        listView.invalidate();
    }

    void closeList() {
        if(list!=null)list.queue.stopRequestThread();
        if(listView!=null&&listView.getParent() instanceof ViewGroup)((ViewGroup)listView.getParent()).removeView(listView);
        list=null;listView=null;
    }

    enum Page {
        FILE_TABLE,
        GALLERY,
        REMOTE,
    };

    Page current = null;
    void setupPage(Page to) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if(!alive)return;
                if (to == Page.GALLERY && current != Page.GALLERY) {
                    if (current != null) closeList();
                    current = Page.GALLERY;
                    createGallery();
                } else if (to == Page.FILE_TABLE && current != Page.FILE_TABLE) {
                    if (current != null) closeGallery();
                    current = Page.FILE_TABLE;
                    createList();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();foreground=true;details=MySettings.getSetting(this,"gallery_details",1)==1;if(imageAdapter!=null)imageAdapter.refreshSelection();resumeAll();
        if(handler!=null&&!receiptWorker.isShutdown())receiptWorker.execute(()->{for(java.util.Map.Entry<Integer,JSONObject> entry:metadata.entrySet()){if(PhotoReceipts.exists(this,receiptKey(entry.getKey(),entry.getValue())))savedHandles.add(entry.getKey());else savedHandles.remove(entry.getKey());}handler.post(()->{if(!isDestroyed())applyFilters();});});
    }

    @Override protected void onStop(){foreground=false;if(imageAdapter!=null)imageAdapter.queue.pause();if(list!=null)list.queue.pause();super.onStop();}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gallery);
        setSupportActionBar(findViewById(R.id.ui_toolbar));
        WorkbenchNav.attach(this,WorkbenchNav.GALLERY);details=MySettings.getSetting(this,"gallery_details",1)==1;

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.gallery);
        instance = new WeakReference<>(this);
        handler = new Handler(Looper.getMainLooper());
        tasks=DownloadTasks.get(this);
        findViewById(R.id.connection_status_panel).setOnClickListener(v->showCameraInfo());
        findViewById(R.id.connection_status_panel).setOnLongClickListener(v->{if (!DebugUi.enabled(this)) return false; String diagnostic=Frontend.getConnectionDiagnostic();new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("连接诊断").setMessage(diagnostic).setPositiveButton("复制诊断",(d,w)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Fudge 连接诊断",diagnostic))).setNegativeButton("关闭",null).show();return true;});
        findViewById(R.id.filter_photos).setOnClickListener(v->showFilters());
        findViewById(R.id.range_photos).setOnClickListener(v->showRange());

        MainActivity.applyInsets(getWindow(), findViewById(R.id.gallery_main_view));

        if (Backend.cGetKillSwitch()){WorkbenchNav.open(this,WorkbenchNav.CAMERA);finish();return;}

        findViewById(R.id.import_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(batchRunning||objectHandles==null)return;
                selecting=!selecting;
                if(selecting)setupPage(Page.GALLERY);
                updateSelectionUi();
            }
        });

        findViewById(R.id.gallery_button).setOnClickListener(v->{android.widget.PopupMenu menu=new android.widget.PopupMenu(this,v);menu.getMenu().add(0,1,0,"紧凑网格");menu.getMenu().add(0,4,1,"详情网格");menu.getMenu().add(0,2,2,"文件列表");menu.getMenu().add(0,3,3,"清理缩略图缓存");menu.setOnMenuItemClickListener(item->{if(item.getItemId()==3){receiptWorker.execute(()->{long bytes=PhotoDiskCache.clearThumbnails(this);handler.post(()->{if(!isDestroyed())android.widget.Toast.makeText(this,String.format(java.util.Locale.CHINA,"已清理 %.1f MB 缩略图缓存",bytes/1048576.0),android.widget.Toast.LENGTH_LONG).show();});});}else if(item.getItemId()==1||item.getItemId()==4){details=item.getItemId()==4;MySettings.setSetting(this,"gallery_details",details?1:0);setupPage(Page.GALLERY);if(imageAdapter!=null)imageAdapter.refreshSelection();}else setupPage(Page.FILE_TABLE);return true;});menu.show();});

        findViewById(R.id.download_tasks).setOnClickListener(v->DownloadTaskCenter.show(this,this));
        findViewById(R.id.download_selected).setOnClickListener(v->startSelectedDownload());
        findViewById(R.id.select_all).setOnClickListener(v->{if(objectHandles!=null&&!batchRunning){selection.selectAll(visibleHandles);updateSelectionUi();}});
        findViewById(R.id.clear_selection).setOnClickListener(v->{if(!batchRunning){selection.clear();updateSelectionUi();}});
        findViewById(R.id.cancel_transfer).setOnClickListener(v->{requestTransferStop();((TextView)findViewById(R.id.transfer_caption)).setText("当前文件完成后停止剩余下载…");});
        renderChips();updateSelectionUi();
        Snapshot restored=connectionSnapshot;
        if((savedInstanceState!=null||getIntent().getBooleanExtra("resume_transfer",false))&&restored!=null&&!Backend.cGetKillSwitch()){
            downloadSession=restored.session;objectHandles=restored.handles;visibleHandles=objectHandles;cameraInfo=restored.info;metadata.putAll(restored.metadata);formatFilter=restored.format;sortOrder=restored.sort;dateFilter=restored.date;onlyNew=restored.onlyNew;selection.selectAll(restored.selected);selecting=selection.size()>0;renderCameraCard();setupPage(Page.GALLERY);applyFilters();startPingThread();
        }else if(savedInstanceState!=null||getIntent().getBooleanExtra("resume_transfer",false)){android.widget.Toast.makeText(this,"相机会话已失效，请重新连接",android.widget.Toast.LENGTH_LONG).show();finish();return;}
        else{connectionSnapshot=null;startGalleryThread();}
        handler.post(transferPoll);
    }

    void startGalleryThread() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Frontend.print("正在启动照片库");
                ConnectionState.shared.update(ConnectionState.Step.SESSION,ConnectionState.Status.CONNECTING,"正在建立相机会话");
                int rc = Backend.cFujiSetup();
                if(!alive||MainActivity.manualDisconnected||MainActivity.disconnecting)return;
                if (rc != 0) {
                    fail(rc, "初始化失败");
                    return;
                }

                // SINGLE/MULTIPLE downloader fuji will gracefully kill connection (done in cFujiSetup)
                if (Backend.cGetKillSwitch()) return;
                ConnectionState.shared.update(ConnectionState.Step.SESSION,ConnectionState.Status.READY,"相机会话连接成功");
                ConnectionState.shared.update(ConnectionState.Step.GALLERY,ConnectionState.Status.CONNECTING,"正在读取相机照片索引");

                Frontend.print("正在进入照片库…");
                rc = Backend.cFujiConfigImageGallery();
                if (rc != 0) {
                    fail(rc, "无法启动照片库");
                    return;
                }

                objectHandles = Backend.cFujiGetObjectHandles();
                visibleHandles=objectHandles;
                if(objectHandles!=null)ConnectionState.shared.update(ConnectionState.Step.GALLERY,ConnectionState.Status.READY,"图库已就绪 · "+objectHandles.length+" 个文件");
                else ConnectionState.shared.error(ConnectionState.Step.GALLERY,"无法读取相机照片索引，请重新连接");
                CameraInfo snapshot=new CameraInfo(Backend.cGetCachedCameraInfo(),Backend.cGetTransport(),objectHandles==null?0:objectHandles.length,android.os.SystemClock.elapsedRealtime());
                cameraInfo=snapshot;saveSnapshot();handler.post(()->{if(alive){renderCameraCard();updateSelectionUi();}});

                if (objectHandles == null || objectHandles.length == 0) {
                    Frontend.print(getString(R.string.noImages1));
                    int transport = Backend.cGetTransport();
                    if (transport == Backend.FUJI_FEATURE_WIRELESS_TETHER || transport == Backend.FUJI_FEATURE_USB_TETHER_SHOOT) {
                        Frontend.print("正在等待下载照片。");
                    } else {
                        Frontend.print(getString(R.string.getImages2));
                    }
                } else {
                    Backend.cPtpObjectServiceStart(objectHandles);
                    int f = Backend.cGetTransport();
                    if (f == Backend.FUJI_FEATURE_AUTOSAVE) {
                        setupPage(Page.FILE_TABLE);
                    } else {
                        setupPage(Page.GALLERY);
                    }
                }

                if(alive)startPingThread();
            }
        });
        thread.start();
    }

    private void renderCameraCard(){CameraInfo info=cameraInfo;if(info!=null)((TextView)findViewById(R.id.camera_status)).setText("● 已连接  ·  "+info.link()+"  ·  相机信息 ›");}
    private void saveSnapshot(){if(objectHandles==null||cameraInfo==null)return;Snapshot state=new Snapshot();state.session=downloadSession;state.handles=objectHandles.clone();state.info=cameraInfo;state.metadata=metadata;state.format=formatFilter;state.sort=sortOrder;state.date=dateFilter;state.onlyNew=onlyNew;state.selected=selection.snapshot();connectionSnapshot=state;}
    private void startPingThread(){new Thread(()->{while(alive&&!Backend.cGetKillSwitch()){DownloadTransfer.Run transfer=DownloadTransfer.current;if(RemoteSession.isBusy()||(transfer!=null&&transfer.running)){try{Thread.sleep(1000);}catch(InterruptedException e){return;}continue;}int rc=Backend.cPtpFujiPing();if(!alive)return;if(rc!=0){requestTransferStop();handler.post(()->{if(alive)fail(Backend.PTP_IO_ERR,"相机没有响应");});return;}try{Thread.sleep(1000);}catch(InterruptedException e){return;}}},"camera-events").start();}
    @Override protected void onSaveInstanceState(Bundle out){saveSnapshot();super.onSaveInstanceState(out);}
    @Override public void onDestroy(){
        saveSnapshot();alive=false;stopOwnQueues();handler.removeCallbacksAndMessages(null);receiptWorker.shutdown();
        listView=null;list=null;imageAdapter=null;recyclerView=null;
        if(instance!=null&&instance.get()==this)instance=null;
        super.onDestroy();
    }

    // When back pressed in gallery, do nothing
    @Override
    public void onBackPressed() {
        if(selecting&&!batchRunning){selecting=false;updateSelectionUi();return;}
        WorkbenchNav.open(this,WorkbenchNav.CAMERA);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if(item.getItemId()==100){if(canStartTransfers()){java.util.ArrayList<Integer> fresh=new java.util.ArrayList<>();for(int handle:visibleHandles)if(!isSaved(handle))fresh.add(handle);int[] handles=new int[fresh.size()];for(int i=0;i<handles.length;i++)handles[i]=fresh.get(i);if(handles.length==0)android.widget.Toast.makeText(this,"当前结果中的文件都已保存",android.widget.Toast.LENGTH_SHORT).show();else showDownloadConfirmation(handles);}return true;}
        if(item.getItemId()==101){showCameraInfo();return true;}if(item.getItemId()==102){disconnectFrom(this);return true;}
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,100,0,"下载未保存文件");menu.add(0,101,1,"相机信息");menu.add(0,102,2,"断开连接");
        return true;
    }
}
