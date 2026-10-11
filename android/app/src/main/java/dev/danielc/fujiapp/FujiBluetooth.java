package dev.danielc.fujiapp;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.os.PatternMatcher;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Experimental secure pairing/handoff, adapted from petabyt/libfuji bluetooth.c (MIT).
 * GATT operations are serialized; Wi-Fi credentials never enter logs or preferences.
 */
public final class FujiBluetooth {
    private static final String PAIR="123d8f06-62a1-4935-9322-833c531ee225";
    private static final String STATUS="f557d96b-8284-4667-8793-b971c1deca2a";
    private static final String NAME="85b9163e-62d1-49ff-a6f5-054b4630d4a1";
    private static final String NET="4e941240-d01d-46b9-a5ea-67636806830b";
    private static final String SSID="bf6dc9cf-3606-4ec9-a4c8-d77576e93ea4";
    private static final String PASSWORD="e809256a-915c-4967-92e8-53b7d4cad213";
    private static final String CONF="4c0020fe-f3b6-40de-acc9-77d129067b14";
    private final MainActivity activity;
    private final Context context;
    private final boolean standby;
    interface Listener {void status(String message);}
    private final Listener listener;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private BluetoothLeScanner scanner;
    private volatile BluetoothGatt gatt;
    private final Map<String,BluetoothDevice> devices=new LinkedHashMap<>();
    private final Map<String,Long> lastSeen=new HashMap<>();
    private volatile CountDownLatch pending;
    private GattOperation pendingOperation;
    private volatile BluetoothLeScanner reconnectScanner;
    private volatile ScanCallback reconnectCallback;
    private volatile int result;
    private volatile byte[] value;
    private volatile boolean connected, busy, closed;
    private volatile int disconnectReason;
    private volatile boolean timeRequested;
    private String wifiSsid,wifiSecret;
    private int scanSeconds;
    private final Set<String> seen=new HashSet<>();
    private final Runnable scanTick=new Runnable(){public void run(){
        if(scanner==null)return;
        scanSeconds++;
        scanStatus();
        ui.postDelayed(this,1000);
    }};
    private void scanStatus(){
        String message="蓝牙扫描 "+scanSeconds+"/15 秒：收到 "+seen.size()+" 台设备，富士候选 "+devices.size()+" 台。\n请让相机停留在配对注册画面；下方发现的相机可直接点击。";
        ((android.widget.TextView)activity.findViewById(R.id.bluetooth_status)).setText(message);
    }
    private void addCamera(BluetoothDevice device,String name,boolean bonded){
        String address=device.getAddress();
        if(devices.containsKey(address))return;
        devices.put(address,device);
        android.widget.Button button=new android.widget.Button(activity);
        button.setBackgroundResource(R.drawable.light_button);
        button.setTextColor(androidx.core.content.ContextCompat.getColor(activity,R.color.ui_text));
        button.setTextSize(14);button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
        int pad=Math.round(16*activity.getResources().getDisplayMetrics().density);button.setPadding(pad,pad/2,pad,pad/2);
        android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(-1,-2);params.topMargin=pad/2;button.setLayoutParams(params);
        button.setAllCaps(false);
        button.setMinHeight((int)(56*activity.getResources().getDisplayMetrics().density));
        button.setText((name==null?"富士候选相机":name)+(bonded?"\n已配对 · 点击重连":"\n点击连接"));
        button.setOnClickListener(v->{
            try{
            if(busy)return;
            Long seenAt=lastSeen.get(address);
            if(device.getBondState()!=BluetoothDevice.BOND_BONDED&&(seenAt==null||SystemClock.elapsedRealtime()-seenAt>30000)){
                if(scanner!=null)stopScan();start();status("相机候选已过期，正在重新扫描。请保持相机配对画面，选择新发现的相机。");return;
            }
            if(scanner!=null)stopScan();pair(device);
            }catch(SecurityException e){status("蓝牙权限已撤销，请重新授权后连接。");}
        });
        ((android.widget.LinearLayout)activity.findViewById(R.id.bluetooth_devices)).addView(button);
    }
    private static boolean fujiName(String name){
        if(name==null)return false;
        String upper=name.toUpperCase(Locale.ROOT);
        return upper.contains("FUJI")||upper.contains("X-E5")||upper.contains("XE5");
    }
    private final StringBuilder log=new StringBuilder();
    private String stage="准备";
    private static final class GattFailure extends Exception {
        final int code;
        GattFailure(String label, int code) { super(label+"失败，GATT="+code); this.code=code; }
    }
    private final Runnable scanTimeout=()->finishScan();
    public FujiBluetooth(MainActivity activity){this.activity=activity;context=activity;standby=false;listener=null;}
    FujiBluetooth(Context context,Listener listener){this.activity=null;this.context=context.getApplicationContext();this.listener=listener;standby=true;}
    void showPairedCameras(){
        if(activity==null||closed)return;
        ((android.widget.LinearLayout)activity.findViewById(R.id.bluetooth_devices)).removeAllViews();
        devices.clear();lastSeen.clear();
        try{
            BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter!=null)for(BluetoothDevice device:adapter.getBondedDevices())if(fujiName(device.getName()))addCamera(device,device.getName(),true);
        }catch(SecurityException ignored){}
    }
    boolean isConnected(){return connected;}
    void connectPaired(String address){
        if(!standby||closed||busy||connected)return;
        try{BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){status("等待手机蓝牙开启");return;}
            BluetoothDevice device=adapter.getRemoteDevice(address);
            if(device.getBondState()!=BluetoothDevice.BOND_BONDED){status("相机尚未配对，请先在相机页完成配对");return;}pair(device);
        }catch(SecurityException|IllegalArgumentException e){status("后台连接未启动：请检查蓝牙权限和已配对相机");}
    }
    private void status(String s){stage=s; synchronized(log){log.append(s).append('\n');if(log.length()>32768)log.delete(0,log.length()-32768);} ui.post(()->{if(closed)return;Frontend.print(s);if(listener!=null)listener.status(s);if(activity!=null&&!activity.isDestroyed())((android.widget.TextView)activity.findViewById(R.id.bluetooth_status)).setText(s);});}
    private synchronized void complete(int code,byte[] data){CountDownLatch l=pending;if(l==null || l.getCount()==0)return;result=code;value=data==null?null:data.clone();l.countDown();}
    private synchronized void begin(GattOperation operation){pending=new CountDownLatch(1);pendingOperation=operation;result=-1;value=null;}
    private synchronized void completeOperation(BluetoothGatt source, GattOperation operation, int code, byte[] data) {
        if(source==gatt && pendingOperation!=null && pendingOperation.matches(operation))complete(code,data);
    }
    private static GattOperation characteristicOperation(GattOperation.Kind kind,BluetoothGattCharacteristic c) {
        return new GattOperation(kind,c.getService().getUuid().toString(),c.getUuid().toString(),null);
    }
    private static GattOperation descriptorOperation(BluetoothGattDescriptor d) {
        BluetoothGattCharacteristic c=d.getCharacteristic();
        return new GattOperation(GattOperation.Kind.DESCRIPTOR,c.getService().getUuid().toString(),c.getUuid().toString(),d.getUuid().toString());
    }
    private byte[] await(String label) throws Exception {
        CountDownLatch operation=pending;
        if(!operation.await(20,TimeUnit.SECONDS)){
            // Same-characteristic late callbacks cannot be distinguished on Android;
            // abandon the connection before another request can begin.
            disconnect();
            throw new TimeoutException(label+"超时，已释放蓝牙会话");
        }
        if(result!=BluetoothGatt.GATT_SUCCESS)throw new GattFailure(label,result);
        return value;
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int code,int state){
            synchronized(FujiBluetooth.this){
                if(g!=gatt)return;
                connected=state==BluetoothProfile.STATE_CONNECTED && code==BluetoothGatt.GATT_SUCCESS;
                if(state==BluetoothProfile.STATE_CONNECTED)completeOperation(g,GattOperation.simple(GattOperation.Kind.CONNECT),code,null);
                else if(state==BluetoothProfile.STATE_DISCONNECTED){disconnectReason=code;complete(code==0?-2:code,null);if(!closed){if(!standby)ConnectionState.shared.bluetoothLost();status("蓝牙连接已断开（"+code+"）");}}
            }
        }
        public void onServicesDiscovered(BluetoothGatt g,int code){completeOperation(g,GattOperation.simple(GattOperation.Kind.SERVICES),code,null);}
        public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int code){completeOperation(g,characteristicOperation(GattOperation.Kind.READ,c),code,c.getValue());}
        public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] data,int code){completeOperation(g,characteristicOperation(GattOperation.Kind.READ,c),code,data);}
        public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int code){completeOperation(g,characteristicOperation(GattOperation.Kind.WRITE,c),code,null);}
        public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code){completeOperation(g,descriptorOperation(d),code,null);}
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){notice(g,c,c.getValue());}
        public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] data){notice(g,c,data);}
        private void notice(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] data){
            if(g!=gatt||closed)return;String id=c.getUuid().toString();
            if(id.equals("f9150137-5d40-4801-a8dc-f7fc5b01da50")){timeRequested=true;try{worker.execute(()->{if(!closed&&g==gatt)replyTimeRequest();});}catch(java.util.concurrent.RejectedExecutionException ignored){}}
            // Never record Wi-Fi credentials, network names or location payloads.
            if(id.equals(SSID)||id.equals(PASSWORD)||id.equals("ad06c7b7-f41a-46f4-a29a-712055319122")||id.equals("c95d91ae-b247-4d6d-8661-7dd5d6a0f85b"))return;
            StringBuilder hex=new StringBuilder();if(data!=null)for(int i=0;i<data.length&&i<16;i++)hex.append(String.format(Locale.ROOT,"%02x",data[i]&255));
            ui.post(()->{if(!closed)Frontend.print("相机蓝牙通知："+id+"，长度="+(data==null?0:data.length)+"，状态="+hex);});
        }
    };
    private boolean standbyHandoffPending;
    private boolean pauseStandbyFor(Runnable action){
        if(!BackgroundCamera.running)return false;
        if(standbyHandoffPending)return true;
        standbyHandoffPending=true;BackgroundCamera.pauseForForeground(context);
        status("正在暂停后台蓝牙，切换到图库连接…");
        final long deadline=SystemClock.elapsedRealtime()+5000;
        ui.postDelayed(new Runnable(){public void run(){
            if(closed){standbyHandoffPending=false;return;}
            if(!BackgroundCamera.running){standbyHandoffPending=false;action.run();return;}
            if(SystemClock.elapsedRealtime()>=deadline){standbyHandoffPending=false;status("后台蓝牙尚未释放，请稍后重试");return;}
            ui.postDelayed(this,100);
        }},100);return true;
    }
    public boolean isBusy() { return busy; }
    public void start(){
        if(closed||!MainActivity.beginUserConnection())return;
        Backend.suspendLanDiscovery();
        if(pauseStandbyFor(this::start))return;
        if(MainActivity.blockConnect||!Backend.cGetKillSwitch()||(MainActivity.wifi!=null&&MainActivity.wifi.isSelecting())){status("相机已连接或连接正在进行，请先正常断开再重连。");return;}
        if(DownloadTransfer.current!=null&&DownloadTransfer.current.running){status("照片正在下载，请先停止剩余任务再切换连接。");return;}
        if(scanner!=null){finishScan();return;}
        if(busy){Toast.makeText(activity,"连接正在进行，请稍候",Toast.LENGTH_SHORT).show();return;}
        ConnectionState.shared.begin(ConnectionState.Mode.BLUETOOTH);
        List<String> permissions=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){permissions.add(Manifest.permission.BLUETOOTH_SCAN);permissions.add(Manifest.permission.BLUETOOTH_CONNECT);}
        if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        else { permissions.add(Manifest.permission.ACCESS_FINE_LOCATION); permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION); }
        List<String> missing=new ArrayList<>();
        for(String p:permissions)if(ContextCompat.checkSelfPermission(context,p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(!missing.isEmpty()){ConnectionState.shared.error(ConnectionState.Step.BLUETOOTH,"请允许附近设备权限，然后再次连接");ActivityCompat.requestPermissions(activity,missing.toArray(new String[0]),91);status("请允许附近设备权限，然后再次点击蓝牙连接。");return;}
        BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){ConnectionState.shared.error(ConnectionState.Step.BLUETOOTH,"请先开启手机蓝牙");status("请先开启手机蓝牙。");return;}
        if(!standby){MySettings.directAddress=null;MySettings.photoCameraIdentity=null;clearWifiRetry();}
        disconnect();closed=false;devices.clear();lastSeen.clear();seen.clear();scanSeconds=0;
        ((android.widget.LinearLayout)activity.findViewById(R.id.bluetooth_devices)).removeAllViews();
        for(BluetoothDevice device:adapter.getBondedDevices())if(fujiName(device.getName()))addCamera(device,device.getName(),true);
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null){ConnectionState.shared.error(ConnectionState.Step.BLUETOOTH,"系统未提供蓝牙扫描器，请关闭再开启蓝牙");status("系统未提供蓝牙扫描器，请关闭再开启手机蓝牙。");return;}
        status("正在扫描：请在 X-E5 的蓝牙/智能手机设置中开启配对注册。");
        try{
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),scan);
            scanStatus();ui.postDelayed(scanTick,1000);ui.postDelayed(scanTimeout,15000);
        }catch(SecurityException|IllegalStateException e){ConnectionState.shared.error(ConnectionState.Step.BLUETOOTH,"无法扫描相机，请检查蓝牙权限");cancelScan();status("无法开始扫描："+e.getClass().getSimpleName());}
    }
    private void scanResult(ScanResult r){
        ui.post(()->{
            if(scanner==null||closed)return;
            try{
                BluetoothDevice device=r.getDevice();seen.add(device.getAddress());
                ScanRecord record=r.getScanRecord();
                String name=record==null?null:record.getDeviceName();
                if(name==null)name=device.getName();
                List<String> services=new ArrayList<>();
                if(record!=null&&record.getServiceUuids()!=null)for(ParcelUuid id:record.getServiceUuids())services.add(id.toString());
                boolean fuji=FujiBleAdvertisement.matches(name,record==null?null:record.getManufacturerSpecificData(FujiBleAdvertisement.COMPANY_ID),services);
                if(fuji){lastSeen.put(device.getAddress(),SystemClock.elapsedRealtime());addCamera(device,name,false);}
                scanStatus();
            }catch(SecurityException e){cancelScan();status("蓝牙权限已撤销，请重新授权。");}
        });
    }
    private final ScanCallback scan=new ScanCallback(){
        public void onScanResult(int type,ScanResult r){scanResult(r);}
        public void onBatchScanResults(List<ScanResult> results){for(ScanResult r:results)scanResult(r);}
        public void onScanFailed(int code){ui.post(()->{ConnectionState.shared.error(ConnectionState.Step.BLUETOOTH,"蓝牙扫描失败，请关闭再开启蓝牙后重试");cancelScan();status("扫描失败（"+code+"），请关闭再开启蓝牙。");});}
    };
    private void stopScan(){ui.removeCallbacks(scanTimeout);ui.removeCallbacks(scanTick);if(scanner!=null){try{scanner.stopScan(scan);}catch(SecurityException ignored){}scanner=null;}}
    private void cancelScan(){stopScan();}
    private void finishScan(){
        stopScan();
        if(!busy)ConnectionState.shared.update(ConnectionState.Step.BLUETOOTH,ConnectionState.Status.WAITING,devices.isEmpty()?"未找到富士相机，请确认相机已开机并开启配对注册":"扫描完成，请点击已配对相机连接");
        status("蓝牙扫描结束：收到 "+seen.size()+" 台设备，富士候选 "+devices.size()+" 台。"+(devices.isEmpty()?"请关闭 XApp，并让相机进入配对注册后再点击蓝牙按钮重试。":"请点击上方相机连接。"));
    }
    private BluetoothGattCharacteristic chr(String service,String characteristic) throws Exception {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("蓝牙权限已撤销");
        if(!connected)throw new Exception("蓝牙已断开");
        BluetoothGattService s=gatt.getService(UUID.fromString(service));
        BluetoothGattCharacteristic c=s==null?null:s.getCharacteristic(UUID.fromString(characteristic));
        if(c==null)throw new Exception("相机缺少所需特征："+characteristic);return c;
    }
    private byte[] read(String s,String c)throws Exception{if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("蓝牙权限已撤销");BluetoothGattCharacteristic ch=chr(s,c);begin(characteristicOperation(GattOperation.Kind.READ,ch));if(!gatt.readCharacteristic(ch))throw new Exception("读取请求未启动");return await("读取特征 "+c);}
    private void write(String service,String characteristic,byte[] bytes)throws Exception {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("蓝牙权限已撤销");
        BluetoothGattCharacteristic ch=chr(service,characteristic);
        int properties=ch.getProperties();
        int type;
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE)!=0) type=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        else if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0) type=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        else throw new Exception("特征不可写："+characteristic);
        status("写入特征 "+characteristic+"，属性=0x"+Integer.toHexString(properties)+"，方式="+type);
        begin(characteristicOperation(GattOperation.Kind.WRITE,ch));
        if(Build.VERSION.SDK_INT>=33){
            int request=gatt.writeCharacteristic(ch,bytes,type);
            if(request!=BluetoothStatusCodes.SUCCESS)throw new Exception("写入请求未启动，状态="+request);
        }else{
            ch.setWriteType(type);ch.setValue(bytes);
            if(!gatt.writeCharacteristic(ch))throw new Exception("写入请求未启动");
        }
        await("写入特征 "+characteristic);
        Thread.sleep(150);
    }
    private void awaitPairedAdvertisement(BluetoothDevice device) throws Exception {
        if(Build.VERSION.SDK_INT>=31 && ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("重连需要附近设备扫描权限，请返回相机页授权");
        if(Build.VERSION.SDK_INT<31 && ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("重连扫描需要定位权限，请返回相机页授权");
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothLeScanner active=manager.getAdapter().getBluetoothLeScanner();
        if(active==null)throw new Exception("手机蓝牙未就绪");
        CountDownLatch seenTarget=new CountDownLatch(1);
        int[] error={0};
        String address=device.getAddress();
        ScanCallback probe=new ScanCallback(){
            public void onScanResult(int type,ScanResult scanResult){
                try{if(address.equals(scanResult.getDevice().getAddress()))seenTarget.countDown();}
                catch(SecurityException denied){error[0]=-1;seenTarget.countDown();}
            }
            public void onBatchScanResults(List<ScanResult> results){for(ScanResult result:results)onScanResult(0,result);}
            public void onScanFailed(int code){error[0]=code;seenTarget.countDown();}
        };
        synchronized(this){if(closed)throw new InterruptedException("已取消");reconnectScanner=active;reconnectCallback=probe;}
        status("正在等待已配对相机广播（最多 15 秒）…请打开相机，并选择本手机作为配对目标。");
        try{
            active.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),probe);
            boolean observed=seenTarget.await(15,TimeUnit.SECONDS);
            if(closed)throw new InterruptedException("已取消");
            if(error[0]!=0)throw new Exception("重连扫描失败，状态="+error[0]);
            if(!observed)throw new TimeoutException("未收到已配对相机广播，请检查相机开机和配对目标");
            status("已发现目标相机广播，正在重连…");
        }finally{
            try{active.stopScan(probe);}catch(SecurityException|IllegalStateException ignored){}
            synchronized(this){if(reconnectCallback==probe){reconnectScanner=null;reconnectCallback=null;}}
        }
    }
    private synchronized void stopReconnectScan(){
        if(reconnectScanner!=null&&reconnectCallback!=null)try{reconnectScanner.stopScan(reconnectCallback);}catch(SecurityException|IllegalStateException ignored){}
        reconnectScanner=null;reconnectCallback=null;
    }
    private void openGatt(BluetoothDevice device) throws Exception {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("蓝牙权限已撤销");
        disconnectReason=0;timeRequested=false;status("连接蓝牙设备…");
        if(!standby)ConnectionState.shared.update(ConnectionState.Step.BLUETOOTH,ConnectionState.Status.CONNECTING,"正在连接相机蓝牙");
        synchronized(this){
            if(closed)throw new InterruptedException("已取消");
            begin(GattOperation.simple(GattOperation.Kind.CONNECT));
            gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
        }
        if(gatt==null)throw new Exception("系统未创建 GATT 连接");
        await("蓝牙连接");Thread.sleep(600);
        if(!standby)ConnectionState.shared.update(ConnectionState.Step.BLUETOOTH,ConnectionState.Status.CONNECTING,"蓝牙已连接，正在发现服务并验证配对");
        status("发现相机服务…");begin(GattOperation.simple(GattOperation.Kind.SERVICES));
        if(!gatt.discoverServices())throw new Exception("服务发现未启动");
        await("服务发现");Thread.sleep(300);
    }
    private void openGattWithRetry(BluetoothDevice device)throws Exception{
        for(int attempt=0;attempt<2;attempt++){
            try{openGatt(device);return;}
            catch(Exception failure){
                boolean retry=failure instanceof TimeoutException||(failure instanceof GattFailure&&(((GattFailure)failure).code==62||((GattFailure)failure).code==133||((GattFailure)failure).code==-2));
                disconnect();
                if(attempt!=0||!retry||closed)throw failure;
                status("蓝牙初始连接未完成，释放连接后重试一次…");
                if(!standby)ConnectionState.shared.update(ConnectionState.Step.BLUETOOTH,ConnectionState.Status.RETRYING,"蓝牙连接未完成，正在自动重试");
                Thread.sleep(1500);
                if(closed)throw new Exception("已取消");
            }
        }
    }
    private void secureHandshake() throws Exception {
        status("进行富士安全握手…");
        byte[] ack=read(PAIR,STATUS);
        if(ack==null||ack.length!=4)throw new Exception("安全状态长度不符合协议");
        status("已读取安全状态，末字节=0x"+Integer.toHexString(ack[3]&255));
        ack[3]=0x20;
        Thread.sleep(300);
        write(PAIR,STATUS,ack);
    }
    private void subscribe(String s,String c)throws Exception{
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("蓝牙权限已撤销");
        BluetoothGattCharacteristic ch=chr(s,c);
        BluetoothGattDescriptor d=ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if(d==null)throw new Exception("通知描述符缺失");
        if(!gatt.setCharacteristicNotification(ch,true))throw new Exception("无法开启通知");
        byte[] enable=(ch.getProperties()&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        begin(descriptorOperation(d));d.setValue(enable);if(!gatt.writeDescriptor(d))throw new Exception("通知订阅未启动");await("订阅通知 "+c);
    }
    private static String text(byte[] bytes){if(bytes==null)return "";int end=0;while(end<bytes.length&&bytes[end]!=0)end++;return new String(bytes,0,end,StandardCharsets.UTF_8);}
    private void pair(BluetoothDevice device){
        if(closed||(!standby&&!MainActivity.beginUserConnection()))return;
        if(!standby&&pauseStandbyFor(()->pair(device)))return;
        if(busy||MainActivity.blockConnect||!Backend.cGetKillSwitch()||(MainActivity.wifi!=null&&MainActivity.wifi.isSelecting())){status("相机已连接或连接正在进行，请先正常断开再重连。");return;}
        if(!standby)ConnectionState.shared.begin(ConnectionState.Mode.BLUETOOTH);
        disconnect();
        if(RemoteSession.isBusy()){status("请先退出监看并等待相机模式恢复");return;}
        if(DownloadTransfer.current!=null&&DownloadTransfer.current.running){status("照片正在下载，暂不能切换相机。");return;}
        if(!standby){MySettings.directAddress=null;MySettings.photoCameraIdentity=null;clearWifiRetry();}
        Backend.suspendLanDiscovery();
        busy=true;worker.execute(()->{
            try{
                if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("蓝牙权限已撤销");
                // Foreground is explicitly requested: a missed advertisement must not block GATT.
                // Standby still waits for advertising to avoid waking the camera repeatedly.
                if(standby&&device.getBondState()==BluetoothDevice.BOND_BONDED)awaitPairedAdvertisement(device);
                openGattWithRetry(device);
                status("系统绑定状态="+device.getBondState());
                if(gatt.getService(UUID.fromString(PAIR))==null)throw new Exception("此版只支持安全配对服务，未检测到新版服务");
                boolean newlyBonded = device.getBondState()!=BluetoothDevice.BOND_BONDED;
                if(standby&&newlyBonded)throw new Exception("相机配对已失效，请回到相机页重新注册");
                if(newlyBonded){
                    status("等待系统配对：请在手机通知/弹窗和相机上确认相同代码；相机应保持配对注册画面。");
                    if(device.getBondState()==BluetoothDevice.BOND_NONE&&!device.createBond())throw new Exception("系统配对未启动");
                    long deadline=SystemClock.elapsedRealtime()+60000;
                    boolean bondingSeen=device.getBondState()==BluetoothDevice.BOND_BONDING;
                    while(device.getBondState()!=BluetoothDevice.BOND_BONDED&&SystemClock.elapsedRealtime()<deadline){
                        if(closed)throw new Exception("已取消");
                        int state=device.getBondState();
                        if(state==BluetoothDevice.BOND_BONDING)bondingSeen=true;
                        if(bondingSeen&&state==BluetoothDevice.BOND_NONE)throw new Exception("系统配对失败或被取消。请重新进入相机配对注册，确认手机和相机两端的代码；不要点击旧的相机候选反复重连。");
                        Thread.sleep(200);
                    }
                    if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new Exception("系统配对未完成，请重新进入相机配对注册");
                }
                if(newlyBonded){
                    status("系统配对完成，重新建立蓝牙会话…");disconnect();Thread.sleep(1200);openGattWithRetry(device);
                }
                try { secureHandshake(); }
                catch(Exception failure){
                    if(!(failure instanceof TimeoutException)&&!(failure instanceof GattFailure&&(((GattFailure)failure).code==133||((GattFailure)failure).code==-2)))throw failure;
                    status("安全握手遇到连接错误，重新连接后重试一次…");
                    disconnect();Thread.sleep(1200);openGattWithRetry(device);secureHandshake();
                }
                write(PAIR,NAME,"Fudge-XE5".getBytes(StandardCharsets.UTF_8));
                MySettings.setSetting(context,"background_camera",device.getAddress());
                if(!standby)MySettings.photoCameraIdentity=PhotoReceipts.hash(device.getAddress());
                String[][] subscriptions={{CONF,"a68e3f66-0fcc-4395-8d4c-aa980b5877fa"},{CONF,"bd17ba04-b76b-4892-a545-b73ba1f74dae"},{CONF,"f9150137-5d40-4801-a8dc-f7fc5b01da50"},{CONF,"ad06c7b7-f41a-46f4-a29a-712055319122"},{"804daa8e-ffeb-4ab3-8e75-6edd7303208d","7170fd5a-56d9-4c19-b043-7a7047d8e1a0"},{NET,SSID},{NET,"75823784-fbb7-4b71-abae-cd9a34072e3c"},{CONF,"e6692c5c-b7cd-44f4-95fc-eda07ce32560"},{NET,"aab609c4-94dd-4d89-bc60-665d5090b828"},{NET,"2a125640-706d-4dd1-b420-c0f4ab93c361"},{NET,"82a9f452-c5ce-4ef5-8203-3fc9a47f8171"},{NET,"deef7187-3f43-4364-9e22-11a8c8a15951"},{NET,"c95d91ae-b247-4d6d-8661-7dd5d6a0f85b"}};
                status("订阅相机状态…");for(String[] item:subscriptions){BluetoothGattService s=gatt.getService(UUID.fromString(item[0]));if(s!=null&&s.getCharacteristic(UUID.fromString(item[1]))!=null)subscribe(item[0],item[1]);}
                replyTimeRequest();
                if(standby){status("后台蓝牙已连接 · 等待相机状态通知");return;}
                ConnectionState.shared.update(ConnectionState.Step.BLUETOOTH,ConnectionState.Status.READY,"蓝牙连接成功，配对验证已完成");
                ConnectionState.shared.update(ConnectionState.Step.WIFI,ConnectionState.Status.CONNECTING,"蓝牙已连接，正在开启相机 Wi-Fi");
                status("读取相机 Wi-Fi 名称…");String ssid=text(read(NET,SSID));if(ssid.isEmpty())throw new Exception("相机返回空 Wi-Fi 名称");
                status("请求开启相机 Wi-Fi…");write("6514eb81-4e8f-458d-aa2a-e691336cdfac","600655e6-3637-42f1-8fb2-44efc5c63b13",new byte[]{4,0});
                Thread.sleep(1000);String password=text(read(NET,PASSWORD));
                wifiSsid=ssid;wifiSecret=password;
                status("蓝牙握手完成，等待系统连接相机 Wi-Fi…");
                ui.post(()->{if(!closed){
                    try { activity.findViewById(R.id.retry_camera_wifi).setVisibility(android.view.View.VISIBLE);Backend.cSetXAppHandoff(true); MainActivity.wifi.connectToCameraAccessPoint(activity,ssid,password); }
                    catch (SecurityException | IllegalArgumentException | IllegalStateException e) { Backend.cSetXAppHandoff(false);ConnectionState.shared.error(ConnectionState.Step.WIFI,"无法请求相机 Wi-Fi，请检查权限并重新连接"); status("Wi-Fi 请求失败："+e.getClass().getSimpleName()); }
                }});
            }catch(Exception e){
                if(!standby&&!closed)ConnectionState.shared.error(ConnectionState.shared.snapshot().steps[1]==ConnectionState.Status.CONNECTING?ConnectionState.Step.WIFI:ConnectionState.Step.BLUETOOTH,"连接未完成："+(e.getMessage()==null?"请重新连接":e.getMessage()));
                boolean unbonded=false;
                try{unbonded=device.getBondState()!=BluetoothDevice.BOND_BONDED;}catch(SecurityException ignored){}
                if(activity!=null&&unbonded)ui.post(()->{if(!closed){devices.clear();lastSeen.clear();((android.widget.LinearLayout)activity.findViewById(R.id.bluetooth_devices)).removeAllViews();}});
                String failure=stage+"："+e.getMessage();if(disconnectReason==19)failure+="\n相机主动结束蓝牙连接（原因 0x13）。请检查相机的配对目标是否为本手机，并先退出旧的无线传图会话；此原因不能单独证明需要重新配对。";final String detail=failure;status("连接未完成："+failure);ui.post(()->{if(closed||activity==null)return;new AlertDialog.Builder(activity).setTitle("蓝牙连接诊断").setMessage(detail+"\n\n若安全握手再次失败，请确认相机选择的配对设备是本手机，必要时在相机与手机设置中删除对应的旧配对后重新注册。请复制诊断发送给开发者。").setPositiveButton("复制诊断",(d,w)->{String text;synchronized(log){text=log.toString();}((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Fudge 蓝牙诊断",text));}).setNegativeButton("关闭",null).show();});disconnect();}
            finally{busy=false;}
        });
    }
    // libfuji bluetooth.c responds to NOT1 with packed local date/time, without GPS.
    static byte[] localTimePayload(java.util.Calendar now){
        int year=now.get(java.util.Calendar.YEAR);
        return new byte[]{(byte)year,(byte)(year>>>8),(byte)(now.get(java.util.Calendar.MONTH)+1),(byte)now.get(java.util.Calendar.DAY_OF_MONTH),(byte)now.get(java.util.Calendar.HOUR_OF_DAY),(byte)now.get(java.util.Calendar.MINUTE),(byte)now.get(java.util.Calendar.SECOND)};
    }
    private void replyTimeRequest(){
        if(!timeRequested||closed||!connected)return;
        timeRequested=false;
        try{BluetoothGattService service=gatt.getService(UUID.fromString("e872b11f-d526-4ae1-9bb4-89a99d48fa59"));
            if(service==null||service.getCharacteristic(UUID.fromString("b9bfd37f-ccad-4d36-a1ee-018e792b3edf"))==null){status("相机请求时间同步，但未提供时间特征");return;}
            write("e872b11f-d526-4ae1-9bb4-89a99d48fa59","b9bfd37f-ccad-4d36-a1ee-018e792b3edf",localTimePayload(java.util.Calendar.getInstance()));
            status("已响应相机时间同步请求");
        }catch(Exception e){status("时间同步未完成："+e.getMessage());}
    }
    void clearWifiRetry(){wifiSsid=null;wifiSecret=null;if(activity!=null)ui.post(()->{if(!activity.isDestroyed())activity.findViewById(R.id.retry_camera_wifi).setVisibility(android.view.View.GONE);});}
    void showWifiRetry(){
        if(activity==null||closed||busy||MainActivity.blockConnect||!Backend.cGetKillSwitch()||BackgroundCamera.running){status("连接正在进行或相机已连接，请稍候");return;}
        final String ssid=wifiSsid,secret=wifiSecret;
        if(ssid==null||secret==null){status("请先完成蓝牙握手，以读取相机热点信息");return;}
        new AlertDialog.Builder(activity).setTitle("重试相机 Wi-Fi")
            .setMessage("请让相机保持 Wi-Fi 等待连接画面。此操作只重试热点连接，不重新配对蓝牙。若系统 Wi-Fi 列表能看到相机热点，可选择“连接可见热点”。")
            .setPositiveButton("重试连接",(d,w)->retryWifi(ssid,secret,false))
            .setNeutralButton("连接可见热点",(d,w)->retryWifi(ssid,secret,true))
            .setNegativeButton("取消",null).show();
    }
    private void retryWifi(String ssid,String secret,boolean visible){
        if(closed||busy||MainActivity.blockConnect||!Backend.cGetKillSwitch()||BackgroundCamera.running)return;
        if(!MainActivity.beginUserConnection())return;
        try{Backend.cSetXAppHandoff(true);int rc=visible?MainActivity.wifi.connectToVisibleCameraAccessPoint(activity,ssid,secret):MainActivity.wifi.connectToCameraAccessPoint(activity,ssid,secret);
            if(rc!=0){Backend.cSetXAppHandoff(false);status("此系统请在 Wi-Fi 设置连接相机热点后使用直连");return;}
            status("正在重新请求相机 Wi-Fi，请保持相机等待连接画面…");
        }catch(SecurityException|IllegalArgumentException|IllegalStateException e){Backend.cSetXAppHandoff(false);status("Wi-Fi 重试未启动："+e.getClass().getSimpleName());}
    }
    public void recordNetworkStatus(String message) { status(message); }
    public void showDiagnostics() {
        String text; synchronized(log){text=log.toString();}
        final String diagnostic=(text.isEmpty()?"尚未开始蓝牙连接。":text)+"\n——应用与 PTP 诊断——\n"+Frontend.getConnectionDiagnostic();
        new AlertDialog.Builder(activity).setTitle("连接诊断")
            .setMessage(diagnostic+"\nWi-Fi/图库阶段的错误请同时记录首页提示。")
            .setPositiveButton("复制",(d,w)->((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Fudge 连接诊断",diagnostic)))
            .setNegativeButton("关闭",null).show();
    }
    boolean releaseForStandby(){if(busy)return false;cancelScan();disconnect();clearWifiRetry();return true;}
    public synchronized void disconnect(){
        BluetoothGatt old=gatt;gatt=null;connected=false;complete(-2,null);pendingOperation=null;
        if(old!=null){
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            try { old.disconnect(); old.close(); } catch(SecurityException ignored) {}
        }
    }
    public void close(){clearWifiRetry();closed=true;cancelScan();stopReconnectScan();complete(-3,null);disconnect();worker.shutdownNow();}
}
