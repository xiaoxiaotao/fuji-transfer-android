package dev.danielc.fujiapp;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
/** Bluetooth standby only. Wi-Fi transfer is not triggered by unidentified notifications. */
public final class BackgroundCamera extends Service {
 static volatile boolean running,ready;
 static volatile String currentStatus="后台蓝牙未运行";
 private static BackgroundCamera activeService;
 private FujiBluetooth bluetooth;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private long retryAt;
 private int failures;
 private boolean attemptInFlight;
 private String message="正在准备后台蓝牙连接";
 private boolean registered;
 private final BroadcastReceiver state=new BroadcastReceiver(){public void onReceive(Context c,Intent i){retryAt=0;failures=0;attemptInFlight=false;if(bluetooth!=null)bluetooth.disconnect();}};
 private final Runnable tick=new Runnable(){public void run(){
  if(!running)return;
  if(MySettings.getSetting(BackgroundCamera.this,"background_enabled",0)!=1){shutdown();return;}
  try{
   if(!hasPermission(BackgroundCamera.this)){message="蓝牙权限已撤销，请返回应用授权";update();shutdown();return;}
   BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager.getAdapter();
   if(adapter==null||!adapter.isEnabled()){if(bluetooth!=null&&bluetooth.isConnected())bluetooth.disconnect();message="等待手机蓝牙开启";}
   else if(!Backend.cGetKillSwitch()){if(bluetooth.isConnected()&&!bluetooth.isBusy())bluetooth.disconnect();message="图库连接正在使用，后台蓝牙暂候";}
   else if(bluetooth.isConnected()&&!bluetooth.isBusy()){attemptInFlight=false;failures=0;message="后台蓝牙已连接 · 等待相机通知";}
   else if(!bluetooth.isBusy()){
    long now=SystemClock.elapsedRealtime();
    if(attemptInFlight){attemptInFlight=false;retryAt=now+retryDelay(failures++);message="蓝牙重连未完成，稍后自动重试";}
    else if(now>=retryAt){attemptInFlight=true;bluetooth.connectPaired(MySettings.getSetting(BackgroundCamera.this,"background_camera",""));}
   }
   update();
  }catch(SecurityException e){message="请重新授予蓝牙权限";update();shutdown();return;}
  ui.postDelayed(this,5000);
 }};
 static long retryDelay(int failures){return 10000L<<Math.min(3,Math.max(0,failures));}
 static boolean hasPermission(Context c){return Build.VERSION.SDK_INT>=31
  ? ContextCompat.checkSelfPermission(c,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(c,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
  : ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 static void pauseForForeground(Context c){Frontend.print("暂停后台蓝牙，交接给前台连接");c.stopService(new Intent(c,BackgroundCamera.class));}
 static void stop(Context c){MySettings.setSetting(c,"background_enabled",0);BackgroundCamera active=activeService;if(active!=null)active.releaseConnection();c.stopService(new Intent(c,BackgroundCamera.class));}
 static void start(Activity owner){
  try{
  if(!hasPermission(owner)){android.widget.Toast.makeText(owner,"请先在相机页完成蓝牙授权和配对",android.widget.Toast.LENGTH_LONG).show();return;}
  if(!Backend.cGetKillSwitch()){android.widget.Toast.makeText(owner,"请先断开图库连接，再开启后台蓝牙",android.widget.Toast.LENGTH_LONG).show();return;}
  if(MainActivity.blockConnect||(MainActivity.wifi!=null&&MainActivity.wifi.isSelecting())||!MainActivity.releaseBluetoothForStandby()){Frontend.print("后台蓝牙未启动：前台连接尚未结束");android.widget.Toast.makeText(owner,"请等待当前连接结束，再开启后台蓝牙",android.widget.Toast.LENGTH_LONG).show();return;}
  String address=MySettings.getSetting(owner,"background_camera","");
  if(address.isEmpty()){
   BluetoothManager manager=(BluetoothManager)owner.getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager.getAdapter();BluetoothDevice match=null;
   if(adapter!=null)for(BluetoothDevice device:adapter.getBondedDevices()){String name=device.getName();if(name!=null&&(name.toUpperCase(java.util.Locale.ROOT).contains("FUJI")||name.toUpperCase(java.util.Locale.ROOT).contains("X-E5"))){if(match!=null){android.widget.Toast.makeText(owner,"发现多台相机，请先在相机页连接目标相机",android.widget.Toast.LENGTH_LONG).show();return;}match=device;}}
   if(match==null){android.widget.Toast.makeText(owner,"请先在相机页配对一次相机",android.widget.Toast.LENGTH_LONG).show();return;}address=match.getAddress();MySettings.setSetting(owner,"background_camera",address);
  }
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(owner,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){androidx.core.app.ActivityCompat.requestPermissions(owner,new String[]{Manifest.permission.POST_NOTIFICATIONS},1802);android.widget.Toast.makeText(owner,"允许通知后再次开启后台蓝牙",android.widget.Toast.LENGTH_LONG).show();return;}
  try{MySettings.setSetting(owner,"background_enabled",1);ContextCompat.startForegroundService(owner,new Intent(owner,BackgroundCamera.class));Frontend.print("已请求启动后台蓝牙服务");}
  catch(RuntimeException e){MySettings.setSetting(owner,"background_enabled",0);android.widget.Toast.makeText(owner,"系统未允许启动后台蓝牙，请保持应用在前台后重试",android.widget.Toast.LENGTH_LONG).show();}
  }catch(SecurityException e){android.widget.Toast.makeText(owner,"蓝牙权限已撤销，请重新授权",android.widget.Toast.LENGTH_LONG).show();}
 }
 private Notification notification(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,BackgroundCamera.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new NotificationCompat.Builder(this,"camera-standby").setSmallIcon(R.drawable.baseline_landscape_24).setContentTitle("Fudge 后台蓝牙").setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"停止",stop).build();
 }
 private void update(){currentStatus=message;ready=bluetooth!=null&&bluetooth.isConnected()&&!bluetooth.isBusy();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1802,notification());}
 private void shutdown(){Frontend.print("后台蓝牙服务停止");MySettings.setSetting(this,"background_enabled",0);stopSelf();}
 private void releaseConnection(){running=false;ready=false;ui.removeCallbacksAndMessages(null);if(bluetooth!=null)bluetooth.close();}
 @Override public void onCreate(){super.onCreate();activeService=this;}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(i!=null&&"stop".equals(i.getAction())){shutdown();return START_NOT_STICKY;}
  if(MySettings.getSetting(this,"background_enabled",0)!=1||!hasPermission(this)){stopSelf();return START_NOT_STICKY;}
  if(running)return START_STICKY;
  try{
   if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("camera-standby","后台相机连接",NotificationManager.IMPORTANCE_LOW));
   if(Build.VERSION.SDK_INT>=29)startForeground(1802,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(1802,notification());
   Backend.suspendLanDiscovery();
   Frontend.print("后台蓝牙服务已启动，自动连接已配对相机");bluetooth=new FujiBluetooth(this,status->{message=status;update();});running=true;
   if(Build.VERSION.SDK_INT>=33)registerReceiver(state,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),Context.RECEIVER_EXPORTED);else registerReceiver(state,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));registered=true;ui.post(tick);return START_STICKY;
  }catch(RuntimeException e){Frontend.print("后台蓝牙启动未完成："+e.getClass().getSimpleName());shutdown();return START_NOT_STICKY;}
 }
 @Override public void onDestroy(){if(activeService==this)activeService=null;running=false;ready=false;currentStatus="后台蓝牙已停止";ui.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(state);if(bluetooth!=null)bluetooth.close();stopForeground(true);super.onDestroy();}
}
