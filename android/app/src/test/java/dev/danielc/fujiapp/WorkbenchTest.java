package dev.danielc.fujiapp;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.Toolbar;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
/** Real Android resource inflation and bounded layout checks; no camera protocol mocks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WorkbenchTest {
 private Activity activity;
 @Before public void setUp(){activity=Robolectric.buildActivity(Activity.class).setup().get();activity.setTheme(R.style.Theme_FujiWifiApp_NoActionBar);}
 private View render(int layout,int page,String title,String file)throws Exception{View root=LayoutInflater.from(activity).inflate(layout,null,false);activity.setContentView(root);((Toolbar)root.findViewById(R.id.ui_toolbar)).setTitle(title);WorkbenchNav.attach(activity,page);if(layout==R.layout.activity_main){root.findViewById(R.id.connect_mode_0).setBackgroundResource(R.drawable.light_button);if("connected".equals(file)){root.findViewById(R.id.connection_options).setVisibility(View.GONE);root.findViewById(R.id.connected_dashboard).setVisibility(View.VISIBLE);((TextView)root.findViewById(R.id.camera_connection_state)).setText("● 已连接 · Wi-Fi · XApp 传图");((TextView)root.findViewById(R.id.camera_dashboard_name)).setText("X-E5");((TextView)root.findViewById(R.id.camera_dashboard_detail)).setText("228 个文件\n192.168.0.1");}}
 if(layout==R.layout.gallery){root.findViewById(R.id.download_actions).setVisibility(View.GONE);((TextView)root.findViewById(R.id.gallery_count)).setText("228 张照片");((TextView)root.findViewById(R.id.camera_status)).setText("● 已连接 · Wi-Fi · XApp 传图 · 相机信息 ›");WorkbenchNav.chips(activity,root.findViewById(R.id.gallery_chips),new String[]{"全部","照片","RAW","视频","未下载"},0,value->{});androidx.recyclerview.widget.RecyclerView grid=new androidx.recyclerview.widget.RecyclerView(activity);grid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(activity,3));grid.setAdapter(new DemoPhotos());if("selection".equals(file)){root.findViewById(R.id.download_actions).setVisibility(View.VISIBLE);root.findViewById(R.id.selection_actions).setVisibility(View.VISIBLE);root.findViewById(R.id.range_photos).setVisibility(View.VISIBLE);((Button)root.findViewById(R.id.import_button)).setText("完成");((TextView)root.findViewById(R.id.gallery_count)).setText("已选择 3 张");((TextView)root.findViewById(R.id.transfer_caption)).setText("已选 3 个 · 约 38.4 MB");((Button)root.findViewById(R.id.download_selected)).setText("下载 3 张");}root.findViewById(R.id.gallery_empty).setVisibility(View.GONE);((ViewGroup)root.findViewById(R.id.fileView)).addView(grid,new android.widget.FrameLayout.LayoutParams(-1,-1));}
 if(layout==R.layout.library){WorkbenchNav.chips(activity,root.findViewById(R.id.library_chips),new String[]{"全部","照片","RAW","视频"},0,value->{});((TextView)root.findViewById(R.id.library_count)).setText("0 个文件 · 可离线浏览");((TextView)root.findViewById(R.id.library_empty)).setText("还没有已下载照片\n连接相机并保存原文件后，会显示在这里。");}
 if(layout==R.layout.activity_settings){((androidx.appcompat.widget.SwitchCompat)root.findViewById(R.id.setting_skip)).setChecked(true);((androidx.appcompat.widget.SwitchCompat)root.findViewById(R.id.setting_details)).setChecked(true);((EditText)root.findViewById(R.id.client_name)).setText("Fudge-XE5");((EditText)root.findViewById(R.id.ip_address_text)).setText("192.168.0.1");}
 int width=Math.round(activity.getResources().getConfiguration().screenWidthDp*activity.getResources().getDisplayMetrics().density),height=Math.round((activity.getResources().getConfiguration().screenHeightDp-52)*activity.getResources().getDisplayMetrics().density);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);View nav=root.findViewById(R.id.workbench_nav),toolbar=root.findViewById(R.id.ui_toolbar);assertTrue("Navigation must have four destinations",((LinearLayout)nav).getChildCount()==4);assertTrue("Toolbar cannot overlap bottom navigation",toolbar.getBottom()<nav.getTop());assertTrue("Navigation remains inside screen",nav.getBottom()<=height);if(file!=null){Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File out=new File(System.getProperty("user.dir"),"build/ui-preview/"+file+".png");out.getParentFile().mkdirs();try(OutputStream stream=new FileOutputStream(out)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}bitmap.recycle();}return root;}
 @Test public void connectedCameraLayout()throws Exception{render(R.layout.activity_main,0,"相机","connected");}
 @Test public void navigationPreservesTheExistingActivityStack(){WorkbenchNav.open(activity,WorkbenchNav.LIBRARY);android.content.Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(FileGallery.class.getName(),intent.getComponent().getClassName());assertTrue((intent.getFlags()&android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)!=0);assertEquals(0,intent.getFlags()&android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);}
 @Test public void cameraLayout()throws Exception{View root=render(R.layout.activity_main,0,"相机","camera");assertTrue(root.findViewById(R.id.connect_bluetooth).getHeight()>0);assertTrue(root.findViewById(R.id.home_status).getBottom()<=root.findViewById(R.id.workbench_nav).getTop());}
 @Test public void selectionLayout()throws Exception{View root=render(R.layout.gallery,1,"X-E5","selection");assertTrue(root.findViewById(R.id.fileView).getHeight()>160);}
 @Test @Config(qualifiers="w320dp-h640dp-xhdpi") public void smallScreenLayouts()throws Exception{render(R.layout.activity_main,0,"相机",null);View gallery=render(R.layout.gallery,1,"X-E5","small-gallery");assertTrue("Small-screen photo viewport: "+gallery.findViewById(R.id.fileView).getHeight(),gallery.findViewById(R.id.fileView).getHeight()>160);render(R.layout.activity_settings,3,"设置",null);render(R.layout.library,2,"已下载",null);}
 @Test public void galleryLayout()throws Exception{View root=render(R.layout.gallery,1,"X-E5","gallery");assertTrue("Photo viewport remains usable",root.findViewById(R.id.fileView).getHeight()>300);assertTrue(root.findViewById(R.id.transfer_panel).getBottom()<=root.findViewById(R.id.workbench_nav).getTop());}
 @Test public void offlineAndSettingsLayouts()throws Exception{render(R.layout.library,2,"已下载","library");render(R.layout.activity_settings,3,"设置","settings");}
 @Test @Config(sdk=35) public void android15Layouts()throws Exception{render(R.layout.activity_main,0,"相机",null);render(R.layout.gallery,1,"X-E5",null);render(R.layout.activity_settings,3,"设置",null);render(R.layout.library,2,"已下载",null);}
 @Test public void fileKindsAndSearch(){assertEquals(2,MediaFile.kind("DSCF.RAF"));assertEquals(3,MediaFile.kind("CLIP.MOV"));assertEquals("image/heif",MediaFile.mime("PHOTO.HEIC"));assertTrue(MediaFile.matches("DSCF0001.JPG",1,"dscf"));assertFalse(MediaFile.matches("DSCF0001.RAF",1,""));assertFalse(MediaFile.supported("download.part"));}
 @Test public void durableIndexKeepsForcedCopiesAndDeduplicatesSameUri()throws Exception{SavedLibrary library=SavedLibrary.get(activity);String tag="test-"+UUID.randomUUID();File original=new File(activity.getFilesDir(),tag+".JPG");try(OutputStream out=new FileOutputStream(original)){out.write(new byte[]{1,2,3});}String path=original.getAbsolutePath();library.record(path,original.getName(),3,10);library.record(path,original.getName(),3,11);library.record(path+"-copy",original.getName(),3,12);List<SavedLibrary.Photo> rows=library.list();int count=0;SavedLibrary.Photo first=null;for(SavedLibrary.Photo row:rows)if(row.target.startsWith(path)){count++;if(row.target.equals(path))first=row;}assertEquals(2,count);assertNotNull(first);assertTrue(library.exists(first));assertTrue(original.delete());assertFalse("Removed originals must not appear available",library.exists(first));}
 @Test public void privateSaveRetainsOriginalAndRecordsIndex()throws Exception{activity.getSharedPreferences(activity.getPackageName(),0).edit().putInt("save_private",1).commit();File original=new File(activity.getFilesDir(),"private-"+UUID.randomUUID()+".JPG");try(OutputStream out=new FileOutputStream(original)){out.write(new byte[]{4,5,6});}assertEquals(original.getAbsolutePath(),PhotoStorage.publish(activity,original.getAbsolutePath()));assertTrue(original.exists());boolean found=false;for(SavedLibrary.Photo row:SavedLibrary.get(activity).list())if(row.target.equals(original.getAbsolutePath()))found=true;assertTrue(found);}
 private class DemoPhotos extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>{Bitmap sample;DemoPhotos(){try(InputStream input=activity.getAssets().open("pexels-thevibrantmachine-3066867.jpg")){android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=8;sample=android.graphics.BitmapFactory.decodeStream(input,null,options);}catch(Exception ignored){}}public int getItemCount(){return 18;}public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){View row=LayoutInflater.from(activity).inflate(R.layout.item_image,parent,false);row.findViewById(R.id.imageView).getLayoutParams().height=Math.round(115*activity.getResources().getDisplayMetrics().density);return new androidx.recyclerview.widget.RecyclerView.ViewHolder(row){};}public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder,int position){((ImageView)holder.itemView.findViewById(R.id.frame_image)).setImageBitmap(sample);((TextView)holder.itemView.findViewById(R.id.frame_label)).setText(String.format(Locale.ROOT,"DSCF%04d.JPG\n12.8 MB · 2026/10/10",position+1));}}

 @Test public void savedDestinationIsFrozenPerBatch(){DownloadTransfer.Run run=new DownloadTransfer.Run("live","camera",new int[]{1},null,false,Collections.emptyMap(),true);activity.getSharedPreferences(activity.getPackageName(),0).edit().putInt("save_private",0).commit();assertTrue(run.privateSave);}
 @Test @Config(sdk=35) public void mediaStorePublishCopiesBytesAndIndexesUri()throws Exception{TestMediaProvider provider=new TestMediaProvider(activity,false);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider);File source=new File(activity.getFilesDir(),"public-"+UUID.randomUUID()+".JPG");try(OutputStream out=new FileOutputStream(source)){out.write(new byte[]{1,3,5,7});}String target=PhotoStorage.publish(activity,source.getAbsolutePath(),false);assertTrue(target.startsWith("content://media/"));assertEquals(4,provider.output.length());assertTrue(provider.completed);SavedLibrary.Photo saved=null;for(SavedLibrary.Photo photo:SavedLibrary.get(activity).list())if(photo.target.equals(target))saved=photo;assertNotNull(saved);assertTrue(SavedLibrary.get(activity).exists(saved));}
 @Test @Config(sdk=35) public void failedPublishRollsBackPendingMediaFile()throws Exception{TestMediaProvider provider=new TestMediaProvider(activity,true);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider);File source=new File(activity.getFilesDir(),"failure-"+UUID.randomUUID()+".JPG");try(OutputStream out=new FileOutputStream(source)){out.write(9);}try{PhotoStorage.publish(activity,source.getAbsolutePath(),false);fail("Publish must fail when destination cannot open");}catch(IOException expected){}assertTrue(provider.deleted);assertTrue("Camera original is still available after publish fails",source.exists());}

 @Test public void orderingKeepsDuplicateNamesStable(){
  SavedLibrary.Photo a=new SavedLibrary.Photo(1,"/a","DSCF2.JPG",100,10),b=new SavedLibrary.Photo(2,"/b","DSCF1.JPG",300,20),c=new SavedLibrary.Photo(3,"/c","DSCF1.JPG",200,20);
  List<SavedLibrary.Photo> rows=new ArrayList<>(Arrays.asList(a,b,c));Collections.sort(rows,LibraryOrdering.comparator(1));assertEquals(Arrays.asList(c,b,a),rows);Collections.sort(rows,LibraryOrdering.comparator(2));assertEquals(Arrays.asList(b,c,a),rows);Collections.sort(rows,LibraryOrdering.comparator(0));assertEquals(Arrays.asList(c,b,a),rows);
 }
 @Test public void partialDownloadsAreRejectedBeforePublication()throws Exception{
  File file=new File(activity.getFilesDir(),"partial-"+UUID.randomUUID()+".JPG");try(OutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3});}
  try{PhotoStorage.validateDownload(file,8);fail("A short file cannot be marked saved");}catch(IOException expected){assertTrue(expected.getMessage().contains("未完整下载"));}
  PhotoStorage.validateDownload(file,3);PhotoStorage.validateDownload(file,0);try(OutputStream out=new FileOutputStream(file)){}try{PhotoStorage.validateDownload(file,0);fail("Unknown length must still reject empty files");}catch(IOException expected){}
 }
 @Test public void privateDestinationOnAndroid9UsesAppDirectory(){assertEquals(activity.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),PhotoStorage.stagingRoot(activity,true));assertEquals(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),PhotoStorage.stagingRoot(activity,false));}
 @Test public void indexRevisionChangesOnlyForNewFiles(){SavedLibrary library=SavedLibrary.get(activity);String target="/revision/"+UUID.randomUUID();long before=SavedLibrary.revision();library.record(target,"photo.JPG",4,10);assertEquals(before+1,SavedLibrary.revision());library.record(target,"photo.JPG",4,11);assertEquals(before+1,SavedLibrary.revision());}
 @Test @Config(sdk=35) public void thumbnailFallsBackWhenProviderDoesNotSupportThumbnailRequests()throws Exception{
  TestMediaProvider provider=new TestMediaProvider(activity,false);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider);Bitmap original=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);try(OutputStream out=new FileOutputStream(provider.output)){original.compress(Bitmap.CompressFormat.PNG,100,out);}original.recycle();
  Bitmap thumbnail=LocalMedia.thumbnail(activity,new SavedLibrary.Photo(1,"content://media/test/"+provider.id,"photo.JPG",provider.output.length(),1));assertNotNull(thumbnail);assertEquals(32,thumbnail.getWidth());thumbnail.recycle();
 }
 @Test @Config(sdk=35) public void cleanupFailureDoesNotHideOriginalSaveError()throws Exception{
  TestMediaProvider provider=new TestMediaProvider(activity,true);provider.denyDelete=true;org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider);File file=new File(activity.getFilesDir(),"rollback-"+UUID.randomUUID()+".JPG");try(OutputStream out=new FileOutputStream(file)){out.write(1);}
  try{PhotoStorage.publish(activity,file.getAbsolutePath(),false);fail("Destination must fail");}catch(IOException expected){assertEquals("Test destination blocked",expected.getMessage());assertEquals(1,expected.getSuppressed().length);assertTrue(expected.getSuppressed()[0] instanceof SecurityException);}
 }

 @Test public void offlinePageRefreshesNewlySavedFilesWithoutReopening()throws Exception{
  SavedLibrary store=SavedLibrary.get(activity);org.robolectric.android.controller.ActivityController<FileGallery> controller=Robolectric.buildActivity(FileGallery.class).setup();FileGallery page=controller.get();
  try{
   awaitLibrary(page,-1);androidx.recyclerview.widget.RecyclerView grid=page.findViewById(R.id.library_grid);int before=grid.getAdapter().getItemCount();
   File file=new File(activity.getFilesDir(),"live-"+UUID.randomUUID()+".JPG");Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);try(OutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,80,out);}bitmap.recycle();store.record(file.getAbsolutePath(),file.getName(),file.length(),System.currentTimeMillis());
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));awaitLibrary(page,before+1);assertEquals(before+1,grid.getAdapter().getItemCount());
  }finally{controller.pause().stop().destroy();}
 }
 private void awaitLibrary(FileGallery page,int count)throws Exception{
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);androidx.recyclerview.widget.RecyclerView grid=page.findViewById(R.id.library_grid);
  while(System.nanoTime()<deadline){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();if(count<0?!((TextView)page.findViewById(R.id.library_count)).getText().toString().startsWith("正在"):grid.getAdapter().getItemCount()==count)return;Thread.sleep(10);}
  fail("Offline library did not finish refreshing");
 }

 @Test public void remoteOwnershipRejectsAnotherTransfer(){assertTrue(RemoteSession.acquire());try{assertFalse(RemoteSession.acquire());assertTrue(RemoteSession.isBusy());try{DownloadTransfer.start(activity,"test","camera",new int[]{1},null,false,Collections.emptyMap());fail("Monitor and download cannot share the camera");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("监看"));}}finally{RemoteSession.release();}assertFalse(RemoteSession.isBusy());}
 @Test public void backgroundBluetoothDoesNotRetainAnActivity()throws Exception{
  FujiBluetooth engine=new FujiBluetooth(activity,message->{});try{
   java.lang.reflect.Field ui=FujiBluetooth.class.getDeclaredField("activity");ui.setAccessible(true);assertNull(ui.get(engine));
   java.lang.reflect.Field context=FujiBluetooth.class.getDeclaredField("context");context.setAccessible(true);assertSame(activity.getApplicationContext(),context.get(engine));
  }finally{engine.close();}
 }
 @Test public void backgroundReconnectBackoffIsBounded(){assertEquals(10000,BackgroundCamera.retryDelay(0));assertEquals(20000,BackgroundCamera.retryDelay(1));assertEquals(80000,BackgroundCamera.retryDelay(100));assertEquals(10000,BackgroundCamera.retryDelay(-1));}
 @Test @Config(sdk=35,shadows={MinimalWifiManager.class}) public void backgroundServiceUsesConnectedDeviceAndIsNotExported()throws Exception{
  android.content.pm.ServiceInfo info=activity.getPackageManager().getServiceInfo(new android.content.ComponentName(activity,BackgroundCamera.class),0);assertFalse(info.exported);assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,info.getForegroundServiceType());
 }
 @Test public void stoppedCameraQueueCannotBeResurrected()throws Exception{
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
  dev.danielc.views.DownloadQueue<Integer> queue=new dev.danielc.views.DownloadQueue<Integer>(){public void perform(Integer value){calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}public boolean nothingToDo(){return true;}};
  queue.enqueue(1);queue.startRequestThread();try{assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));queue.enqueue(2);queue.stopRequestThread();queue.start();queue.startRequestThread();queue.enqueue(3);assertTrue(queue.stopDownloading);assertTrue(queue.requests.isEmpty());release.countDown();assertEquals(1,calls.get());}finally{release.countDown();queue.stopRequestThread();}
 }
 @Test public void repeatedWorkerStartDoesNotRunParallelCameraRequests()throws Exception{
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1),second=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger(),maximum=new java.util.concurrent.atomic.AtomicInteger();
  dev.danielc.views.DownloadQueue<Integer> queue=new dev.danielc.views.DownloadQueue<Integer>(){public void perform(Integer value){int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);entered.countDown();try{if(value==1)release.await();else second.countDown();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{active.decrementAndGet();}}public boolean nothingToDo(){return true;}};
  queue.enqueue(1);queue.startRequestThread();try{assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));queue.enqueue(2);queue.startRequestThread();assertFalse(second.await(100,java.util.concurrent.TimeUnit.MILLISECONDS));release.countDown();assertTrue(second.await(2,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,maximum.get());}finally{release.countDown();queue.stopRequestThread();}
 }
 @Test public void queuedCameraReadMustFinishBeforeModeChange()throws Exception{
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger performed=new java.util.concurrent.atomic.AtomicInteger();
  dev.danielc.views.DownloadQueue<Integer> queue=new dev.danielc.views.DownloadQueue<Integer>(){public void perform(Integer value){performed.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}public boolean nothingToDo(){return true;}};
  queue.enqueue(1);queue.startRequestThread();try{assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));queue.enqueue(2);queue.pause();assertFalse(queue.awaitPaused(20));release.countDown();assertTrue(queue.awaitPaused(2000));assertEquals(1,performed.get());}finally{release.countDown();queue.stopRequestThread();}
 }
 @Test public void cameraParametersFormatValuesAndKeepFailureReasons(){long[] raw=new long[32];raw[2]=280;raw[6]=40;raw[10]=65535;raw[14]=2;raw[18]=-333;raw[22]=3;raw[26]=2;raw[30]=74;CameraParameters data=new CameraParameters(raw,1);raw[2]=99;assertEquals("f/2.8",data.display(0));assertEquals("1/250 s",data.display(1));assertEquals("自动 ISO",data.display(2));assertEquals("自动",data.display(3));assertEquals("-0.3 EV",data.display(4));assertEquals("A · 光圈优先",data.display(5));assertEquals("74%",data.display(7));assertEquals(0,data.failure());raw[0]=-8;raw[1]=0x200a;data=new CameraParameters(raw,2);assertEquals("此连接不支持",data.display(0));assertEquals(0,data.failure());raw[4]=-5;assertEquals(-5,new CameraParameters(raw,2).failure());}
 @Test public void controlConsoleShowsOnlyUnreadValuesUntilRefresh()throws Exception{LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);CameraConsole console=new CameraConsole(activity,root);console.render(new CameraInfo(new String[]{"X-E5","X-E5"},10,228,1));root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,640,root.getMeasuredHeight());assertEquals(7,root.getChildCount());for(int row=3;row<7;row++){LinearLayout line=(LinearLayout)root.getChildAt(row);assertEquals(2,line.getChildCount());for(int col=0;col<2;col++){TextView card=(TextView)line.getChildAt(col);assertTrue(card.getText().toString().contains("待读取"));assertTrue(card.getWidth()>250);assertTrue(card.getHeight()>=200);}}Bitmap bitmap=Bitmap.createBitmap(640,root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File output=new File(System.getProperty("user.dir"),"build/ui-preview/console-parameters.png");output.getParentFile().mkdirs();try(OutputStream stream=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();}

 @Test public void monitorRotationPreservesDisplayedFrameAndCountdown()throws Exception{
  org.robolectric.android.controller.ActivityController<Liveview> controller=Robolectric.buildActivity(Liveview.class).create();Liveview monitor=controller.get();Bitmap frame=Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888);
  java.lang.reflect.Field shown=Liveview.class.getDeclaredField("shown");shown.setAccessible(true);shown.set(monitor,frame);
  java.lang.reflect.Field state=Liveview.class.getDeclaredField("shutter");state.setAccessible(true);ShutterScheduler scheduler=(ShutterScheduler)state.get(monitor);scheduler.frameReady();assertTrue(scheduler.request(10,android.os.SystemClock.elapsedRealtime()));
  RuntimeEnvironment.setQualifiers("w640dp-h320dp-land-xhdpi");android.content.res.Configuration config=new android.content.res.Configuration(monitor.getResources().getConfiguration());monitor.onConfigurationChanged(config);
  assertSame(frame,((android.graphics.drawable.BitmapDrawable)((ImageView)monitor.findViewById(R.id.monitor_image)).getDrawable()).getBitmap());assertTrue(scheduler.pending());assertFalse(frame.isRecycled());
  android.content.pm.ActivityInfo info=monitor.getPackageManager().getActivityInfo(new android.content.ComponentName(monitor,Liveview.class),0);assertTrue((info.configChanges&android.content.pm.ActivityInfo.CONFIG_ORIENTATION)!=0);assertTrue((info.configChanges&android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE)!=0);controller.destroy();assertTrue(frame.isRecycled());assertFalse(scheduler.pending());
 }

 @Test public void shutterRequiresAFrameAndSendsOnlyOnce(){ShutterScheduler scheduler=new ShutterScheduler();scheduler.reset();assertFalse(scheduler.request(0,100));scheduler.frameReady();assertTrue(scheduler.request(0,100));assertFalse(scheduler.request(0,100));assertTrue(scheduler.take(100));assertFalse(scheduler.take(100));assertFalse(scheduler.canRequest(200));scheduler.complete(200);assertFalse(scheduler.canRequest(1699));assertTrue(scheduler.canRequest(1700));}
 @Test public void countdownDoesNotFireEarlyAndCanBeCancelled(){ShutterScheduler scheduler=new ShutterScheduler();scheduler.frameReady();assertTrue(scheduler.request(3,100));assertEquals(3,scheduler.secondsLeft(100));assertEquals(1,scheduler.secondsLeft(2100));assertFalse(scheduler.take(3099));scheduler.cancel();assertFalse(scheduler.take(3100));assertFalse(scheduler.canRequest(3100));scheduler.frameReady();assertTrue(scheduler.request(10,5000));assertFalse(scheduler.take(14999));assertTrue(scheduler.take(15000));assertFalse(scheduler.take(16000));}
 @Test public void sessionResetDropsOldQueuedShutter(){ShutterScheduler scheduler=new ShutterScheduler();scheduler.frameReady();assertTrue(scheduler.request(10,0));scheduler.reset();scheduler.frameReady();assertFalse(scheduler.take(11000));assertTrue(scheduler.canRequest(11000));}
 @Test public void gridStaysInsideTheCameraImage(){MonitorGrid grid=new MonitorGrid(activity,null);grid.setFrameSize(600,300);grid.layout(0,0,300,300);Bitmap bitmap=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888);grid.draw(new Canvas(bitmap));assertEquals(0,bitmap.getPixel(100,20));assertTrue(android.graphics.Color.alpha(bitmap.getPixel(100,100))>0);assertEquals(0,bitmap.getPixel(100,280));bitmap.recycle();}

 @Test @Config(sdk={28,35},qualifiers="w320dp-h640dp-xhdpi") public void monitorControlsRemainInsideSmallScreen(){View root=LayoutInflater.from(activity).inflate(R.layout.activity_monitor,null,false);int width=640,height=1176;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);assertTrue(root.findViewById(R.id.monitor_frame).getHeight()>300);assertFalse(root.findViewById(R.id.monitor_capture).isEnabled());assertTrue(root.findViewById(R.id.monitor_panel).getBottom()<=height);assertTrue(root.findViewById(R.id.monitor_diagnostic).isEnabled());assertTrue(root.findViewById(R.id.monitor_options).getHeight()>=96);}

 @Test @Config(qualifiers="w640dp-h320dp-land-xhdpi") public void landscapeMonitorKeepsViewAndShutterAvailable()throws Exception{View root=LayoutInflater.from(activity).inflate(R.layout.activity_monitor,null,false);root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(536,View.MeasureSpec.EXACTLY));root.layout(0,0,1280,536);View frame=root.findViewById(R.id.monitor_frame),panel=root.findViewById(R.id.monitor_panel);assertTrue(frame.getRight()<=panel.getLeft());assertTrue(frame.getHeight()>300);assertTrue(root.findViewById(R.id.monitor_diagnostic).getBottom()<=panel.getHeight());assertTrue(root.findViewById(R.id.monitor_options).getTop()>=0);assertTrue(root.findViewById(R.id.monitor_status).getHeight()>=96);Bitmap bitmap=Bitmap.createBitmap(1280,536,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File out=new File(System.getProperty("user.dir"),"build/ui-preview/monitor-landscape.png");out.getParentFile().mkdirs();try(OutputStream stream=new FileOutputStream(out)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();}
 public static final class TestMediaProvider extends android.content.ContentProvider {final File output;final boolean fail;boolean completed,deleted,denyDelete;final String id=UUID.randomUUID().toString();TestMediaProvider(Context context,boolean fail){output=new File(context.getFilesDir(),"media-"+id+".JPG");this.fail=fail;android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="media";info.exported=true;attachInfo(context,info);}public boolean onCreate(){return true;}public String getType(android.net.Uri uri){return "image/jpeg";}public android.net.Uri insert(android.net.Uri uri,android.content.ContentValues values){return android.net.Uri.parse("content://media/test/"+id);}public android.os.ParcelFileDescriptor openFile(android.net.Uri uri,String mode)throws FileNotFoundException{if(fail)throw new FileNotFoundException("Test destination blocked");return android.os.ParcelFileDescriptor.open(output,"r".equals(mode)?android.os.ParcelFileDescriptor.MODE_READ_ONLY:android.os.ParcelFileDescriptor.MODE_CREATE|android.os.ParcelFileDescriptor.MODE_TRUNCATE|android.os.ParcelFileDescriptor.MODE_READ_WRITE);}public android.content.res.AssetFileDescriptor openTypedAssetFile(android.net.Uri uri,String mime,android.os.Bundle opts)throws FileNotFoundException{if("image/*".equals(mime))throw new FileNotFoundException("Provider has no thumbnail implementation");return super.openTypedAssetFile(uri,mime,opts);}public int update(android.net.Uri uri,android.content.ContentValues values,String where,String[] args){completed=values.getAsInteger(android.provider.MediaStore.MediaColumns.IS_PENDING)==0;return 1;}public int delete(android.net.Uri uri,String where,String[] args){deleted=true;if(denyDelete)throw new SecurityException("Cannot roll back destination");output.delete();return 1;}public android.database.Cursor query(android.net.Uri uri,String[] projection,String where,String[] args,String order){android.database.MatrixCursor cursor=new android.database.MatrixCursor(new String[]{android.provider.MediaStore.MediaColumns.SIZE});if(output.isFile())cursor.addRow(new Object[]{output.length()});return cursor;}}

 @Test public void fujiExposureIsNotDecodedUsingGenericPtpUnits(){
  long[] raw=new long[32];raw[6]=3906;CameraParameters fuji=new CameraParameters(raw,1,true);
  assertEquals("1/250 s",fuji.display(1));raw[6]=1000000;assertEquals("1 s",new CameraParameters(raw,1,true).display(1));
  assertEquals("30 s",FujiExposure.display(32000000));assertEquals("60 分钟",FujiExposure.display(64000180));
  assertEquals("B · 长曝光",FujiExposure.display(0xffffffffL));assertEquals("1/8000 s",FujiExposure.display(122));
  assertEquals("1/32000 s",FujiExposure.display(30));assertTrue(FujiExposure.display(1234567).startsWith("未适配"));
  raw[6]=40;assertEquals("1/250 s",new CameraParameters(raw,1,false).display(1));
 }
 @Test public void fujiRecognitionUsesCameraIdentity(){assertTrue(new CameraInfo(new String[]{"","X-E5",""},10,0,0).isFuji());assertTrue(new CameraInfo(new String[]{"","GFX100 II",""},4,0,0).isFuji());assertFalse(new CameraInfo(new String[]{"","Z8","Nikon"},4,0,0).isFuji());}
 @Test public void captureReceiptUsesActualHandlesAndDoesNotClaimShotsFromFileCount(){
  int[] before={1,2,3};CaptureReceipt receipt=new CaptureReceipt(before);before[0]=999;
  assertEquals(2,receipt.added(new int[]{2,3,4,5,5}));assertEquals(0,receipt.added(new int[]{1,2,3}));
  assertTrue(receipt.summary(1,new int[]{2,3,4,5}).contains("新增 2 个文件"));
  assertTrue(receipt.summary(1,new int[]{1,2,3}).contains("尚未发现"));
  assertEquals(-1,new CaptureReceipt(null).added(new int[]{1}));assertEquals(-1,receipt.added(null));
 }
 @Test public void vendorParameterFallbackUsesItsOwnUnitsAndUnknownAccess(){
  long[] raw=new long[48];for(int i=0;i<8;i++)raw[i*6]=-8;
  raw[0]=0;raw[2]=30;raw[3]=2;raw[4]=0xd218;raw[6]=0;raw[8]=48;raw[10]=0xd219;
  raw[12]=0;raw[14]=-1;raw[16]=0x500f;
  CameraParameters data=new CameraParameters(raw,1,true);
  assertEquals("f/5.6",data.display(0));assertEquals("1/250 s",data.display(1));assertEquals("自动 ISO",data.display(2));
  assertFalse(data.writable(0));assertTrue(data.access(0).contains("未查询"));assertEquals(3,data.readCount());assertTrue(data.summary().contains("3 / 8"));
  assertEquals("30 s",FujiExposure.legacy(-30));assertEquals("1 s",FujiExposure.legacy(0));assertEquals("B · 长曝光",FujiExposure.legacy(-31));assertTrue(FujiExposure.aperture(999).contains("未适配"));
 }
 @Test public void remoteEventParameterIsReadOnlyAndUsesFujiUnits(){
  long[] raw=new long[56];for(int i=0;i<8;i++)raw[i*7]=-9;
  raw[7]=0;raw[9]=3906;raw[10]=3;raw[11]=0x500d;raw[12]=6;raw[13]=4;
  CameraParameters data=new CameraParameters(raw,1,true);assertEquals("1/250 s",data.display(1));assertFalse(data.writable(1));assertEquals("遥控状态事件（只读）",data.access(1));assertEquals(1,data.readCount());
 }
 @Test public void consoleDistinguishesEmptyResponseFromUnknownFormatAndFujiFocus(){
  long[] raw=new long[56];for(int i=0;i<8;i++){raw[i*7]=-7;raw[i*7+4]=0x5001;}
  CameraParameters data=new CameraParameters(raw,1,true);assertEquals("此模式未提供数值",data.display(7));assertEquals(0,data.failure());
  raw[7*7+6]=6;assertEquals("格式待适配",new CameraParameters(raw,1,true).display(7));
  raw[6*7]=0;raw[6*7+2]=0x8001;assertEquals("AF-S · 单次对焦",new CameraParameters(raw,1,true).display(6));
  raw[6*7+2]=0x8002;assertEquals("AF-C · 连续对焦",new CameraParameters(raw,1,true).display(6));
  assertEquals("模式 32770",new CameraParameters(raw,1,false).display(6));
 }
 @Test public void unavailableConsoleDoesNotPresentEmptySnapshotAsSuccess(){long[] raw=new long[48];for(int i=0;i<8;i++){raw[i*6]=-8;raw[i*6+1]=0x200a;raw[i*6+4]=0x5001;}CameraParameters data=new CameraParameters(raw,1,true);assertEquals(0,data.readCount());assertTrue(data.summary().contains("未读到参数"));assertEquals("此连接不支持",data.display(7));}
 @org.robolectric.annotation.Implements(android.net.wifi.WifiManager.class) public static class MinimalWifiManager { @org.robolectric.annotation.Implementation protected static void __staticInitializer__(){} }
 @Test @Config(sdk=35,shadows={MinimalWifiManager.class}) public void obsoleteWifiCallbacksCannotClearTheNewSelection(){
  dev.danielc.common.WiFiComm wifi=new dev.danielc.common.WiFiComm();
  android.net.ConnectivityManager manager=(android.net.ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);wifi.setConnectivityManager(manager);
  final int[] events={0,0};wifi.onWiFiSelectAvailable=()->events[0]++;wifi.onWiFiSelectCancel=()->events[1]++;
  wifi.connectToCameraAccessPoint(activity,"FUJIFILM-TEST","12345678");assertTrue(wifi.isSelecting());
  android.net.ConnectivityManager.NetworkCallback old=(android.net.ConnectivityManager.NetworkCallback)wifi.selectionToken();
  wifi.connectToCameraAccessPoint(activity,"FUJIFILM-TEST","12345678");
  android.net.ConnectivityManager.NetworkCallback current=(android.net.ConnectivityManager.NetworkCallback)wifi.selectionToken();
  old.onUnavailable();old.onAvailable(org.robolectric.shadows.ShadowNetwork.newInstance(1));old.onLost(org.robolectric.shadows.ShadowNetwork.newInstance(1));
  assertArrayEquals(new int[]{0,0},events);assertTrue(wifi.isSelecting());
  current.onAvailable(org.robolectric.shadows.ShadowNetwork.newInstance(2));assertEquals(1,events[0]);assertFalse(wifi.isSelecting());
  old.onLost(org.robolectric.shadows.ShadowNetwork.newInstance(2));current.onLost(org.robolectric.shadows.ShadowNetwork.newInstance(3));assertEquals(0,events[1]);
  current.onLost(org.robolectric.shadows.ShadowNetwork.newInstance(2));assertEquals(1,events[1]);assertEquals(-101,wifi.selectedNetworkHandle());
 }
 @Test @Config(sdk=35,shadows={MinimalWifiManager.class}) public void wifiWaitsForAddressAndIgnoresOldLinkUpdates()throws Exception{
  dev.danielc.common.WiFiComm wifi=new dev.danielc.common.WiFiComm();
  android.net.ConnectivityManager manager=(android.net.ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);wifi.setConnectivityManager(manager);
  wifi.connectToCameraAccessPoint(activity,"FUJIFILM-TEST","12345678");
  android.net.ConnectivityManager.NetworkCallback old=(android.net.ConnectivityManager.NetworkCallback)wifi.selectionToken();
  wifi.connectToCameraAccessPoint(activity,"FUJIFILM-TEST","12345678");
  android.net.ConnectivityManager.NetworkCallback current=(android.net.ConnectivityManager.NetworkCallback)wifi.selectionToken();
  android.net.Network network=org.robolectric.shadows.ShadowNetwork.newInstance(5);
  android.net.NetworkCapabilities caps=new android.net.NetworkCapabilities();
  android.net.NetworkCapabilities.class.getMethod("addTransportType",int.class).invoke(caps,android.net.NetworkCapabilities.TRANSPORT_WIFI);
  org.robolectric.Shadows.shadowOf(manager).setNetworkCapabilities(network,caps);
  final int[] available={0};wifi.onWiFiSelectAvailable=()->available[0]++;
  current.onAvailable(network);current.onAvailable(network);assertEquals(1,available[0]);assertFalse(wifi.selectedNetworkReady());
  android.net.LinkProperties links=testLinks("192.168.0.2/24");
  old.onLinkPropertiesChanged(network,links);assertFalse(wifi.selectedNetworkReady());
  current.onLinkPropertiesChanged(network,links);assertTrue(wifi.selectedNetworkReady());
  current.onLinkPropertiesChanged(org.robolectric.shadows.ShadowNetwork.newInstance(6),new android.net.LinkProperties());assertTrue(wifi.selectedNetworkReady());
  current.onLost(network);assertFalse(wifi.selectedNetworkReady());
 }
 @Test public void cameraNetworkNeedsUsableIpv4()throws Exception{
  android.net.LinkProperties links=new android.net.LinkProperties();assertFalse(dev.danielc.common.WiFiComm.hasCameraAddress(links));
  links=testLinks("fe80::1/64","169.254.2.2/16");assertFalse(dev.danielc.common.WiFiComm.hasCameraAddress(links));
  links=testLinks("192.168.0.2/24");assertTrue(dev.danielc.common.WiFiComm.hasCameraAddress(links));
 }
 @Test @Config(sdk=35,shadows={MinimalWifiManager.class}) public void wifiObserverIsReusedAndOldLossCannotClearNewNetwork()throws Exception{
  dev.danielc.common.WiFiComm wifi=new dev.danielc.common.WiFiComm();wifi.startNetworkListeners(activity.getApplicationContext());
  java.lang.reflect.Field field=dev.danielc.common.WiFiComm.class.getDeclaredField("networkListener");field.setAccessible(true);
  android.net.ConnectivityManager.NetworkCallback observer=(android.net.ConnectivityManager.NetworkCallback)field.get(wifi);assertNotNull(observer);
  wifi.startNetworkListeners(activity.getApplicationContext());assertSame(observer,field.get(wifi));
  java.lang.reflect.Field selected=dev.danielc.common.WiFiComm.class.getDeclaredField("wifiDevice");selected.setAccessible(true);
  android.net.Network before=org.robolectric.shadows.ShadowNetwork.newInstance(11),after=org.robolectric.shadows.ShadowNetwork.newInstance(12);
  observer.onAvailable(before);observer.onAvailable(after);observer.onLost(before);assertEquals(after,selected.get(null));observer.onLost(after);assertNull(selected.get(null));
 }

 private static android.net.LinkProperties testLinks(String... addresses)throws Exception{
  android.net.LinkProperties links=new android.net.LinkProperties();
  for(String address:addresses){
   String[] parts=address.split("/");
   android.net.LinkAddress link=android.net.LinkAddress.class.getConstructor(java.net.InetAddress.class,int.class).newInstance(java.net.InetAddress.getByName(parts[0]),Integer.parseInt(parts[1]));
   android.net.LinkProperties.class.getMethod("addLinkAddress",android.net.LinkAddress.class).invoke(links,link);
  }
  return links;
 }

 @Test public void standbyHandoffCannotInterruptAnActiveHandshake()throws Exception{
  FujiBluetooth engine=new FujiBluetooth(activity,message->{});
  java.lang.reflect.Field busy=FujiBluetooth.class.getDeclaredField("busy");busy.setAccessible(true);busy.set(engine,true);
  assertFalse(engine.releaseForStandby());assertTrue(engine.isBusy());
  busy.set(engine,false);assertTrue(engine.releaseForStandby());engine.close();
 }

 @Test public void standbyHandoffClearsTransientWifiCredentials()throws Exception{
  FujiBluetooth engine=new FujiBluetooth(activity,message->{});
  java.lang.reflect.Field secret=FujiBluetooth.class.getDeclaredField("wifiSecret"),ssid=FujiBluetooth.class.getDeclaredField("wifiSsid");
  secret.setAccessible(true);ssid.setAccessible(true);secret.set(engine,"temporary-secret");ssid.set(engine,"camera-test");
  assertTrue(engine.releaseForStandby());assertNull(secret.get(engine));assertNull(ssid.get(engine));engine.close();
 }

 @Test public void cameraTimeReplyUsesLittleEndianYearAndLocalCalendar(){
  java.util.Calendar now=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));now.clear();now.set(2024,java.util.Calendar.FEBRUARY,29,23,59,58);
  assertArrayEquals(new byte[]{(byte)0xe8,7,2,29,23,59,58},FujiBluetooth.localTimePayload(now));
 }

 @Test public void manualConnectionPausePreservesStandbyPreferenceButStopDisablesIt(){
  MySettings.setSetting(activity,"background_enabled",1);BackgroundCamera.pauseForForeground(activity);
  assertEquals(1,MySettings.getSetting(activity,"background_enabled",0));BackgroundCamera.stop(activity);
  assertEquals(0,MySettings.getSetting(activity,"background_enabled",1));
 }

 @Test public void transferEditionDisablesMonitorAndKeepsPhotoActions()throws Exception{
  android.content.pm.ActivityInfo info=activity.getPackageManager().getActivityInfo(new android.content.ComponentName(activity,Liveview.class),android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS);
  assertFalse(info.enabled);
  View root=LayoutInflater.from(activity).inflate(R.layout.activity_main,null,false);
  assertNotNull(root.findViewById(R.id.dashboard_gallery));assertNotNull(root.findViewById(R.id.dashboard_info));assertNotNull(root.findViewById(R.id.dashboard_disconnect));
  assertEquals(0,activity.getResources().getIdentifier("dashboard_monitor","id",activity.getPackageName()));
  assertEquals(0,activity.getResources().getIdentifier("dashboard_parameters","id",activity.getPackageName()));
 }
}
