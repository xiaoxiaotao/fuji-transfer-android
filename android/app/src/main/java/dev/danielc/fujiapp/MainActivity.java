// Copyright 2023 Daniel C - https://github.com/petabyt/fujiapp
package dev.danielc.fujiapp;

import android.Manifest;
import android.widget.Button;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import dev.danielc.fujiapp.BuildConfig;

import java.io.InputStream;

import dev.danielc.common.WiFiComm;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {
    private FujiBluetooth bluetooth;
    public static MainActivity instance;
    public Handler handler;
    static WiFiComm wifi;
    public static volatile boolean blockConnect = false;

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public static void applyInsets(Window win, View root) {
        WindowCompat.setDecorFitsSystemWindows(win, false);
        WindowCompat.getInsetsController(win, root).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(win, root).setAppearanceLightNavigationBars(false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.ui_toolbar));
        getSupportActionBar().setTitle(R.string.app_name);
        getSupportActionBar().setSubtitle(null);
        WorkbenchNav.attach(this,WorkbenchNav.CAMERA);
        setupWorkbench();
        Frontend.updateLog();
        instance = this;
        handler = new Handler(Looper.getMainLooper());

        applyInsets(getWindow(), findViewById(R.id.main_activity_view));

        findViewById(R.id.download_history).setOnClickListener(v->DownloadTaskCenter.show(this,Gallery.getInstance()));
        bluetooth = new FujiBluetooth(this);
        findViewById(R.id.retry_camera_wifi).setOnClickListener(v->bluetooth.showWifiRetry());
        findViewById(R.id.connect_bluetooth).setOnLongClickListener(v -> { if (!DebugUi.enabled(this)) return false; bluetooth.showDiagnostics(); return true; });
        findViewById(R.id.connect_bluetooth).setOnClickListener(v -> {
            if (blockConnect || !Backend.cGetKillSwitch()) return;
            if(!beginUserConnection())return;
            bluetooth.start();
        });

        findViewById(R.id.connect_wifi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (blockConnect || !Backend.cGetKillSwitch()) return;
                Frontend.clearPrint();
                if (bluetooth.isBusy()) { Frontend.print("蓝牙连接仍在进行，请稍候。"); return; }
                if(!beginUserConnection())return;
                DirectWifiDialog.show(MainActivity.this);
            }
        });

        findViewById(R.id.connect_wifi).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (blockConnect || !Backend.cGetKillSwitch()) return false;
                Intent intent = new Intent(MainActivity.this, Tester.class);
                startActivity(intent);
                return false;
            }
        });

        findViewById(R.id.feedback_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/DfRptGxGFS"));
                startActivity(browserIntent);
            }
        });

        findViewById(R.id.connect_usb).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (blockConnect || !Backend.cGetKillSwitch()) return;
                Frontend.clearPrint();
                connectUSB();
            }
        });

        findViewById(R.id.help_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, Help.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.wifi_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent in = new Intent(Settings.ACTION_WIFI_SETTINGS);
                in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(in);
            }
        });

        findViewById(R.id.discovery_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder p = new AlertDialog.Builder(MainActivity.this);
                p.setTitle(R.string.camera_discovery_title);
                p.setMessage(R.string.camera_discovery_help);
                p.setNeutralButton("OK", null);
                p.show();
            }
        });

        if(wifi==null)wifi = new WiFiComm();
        wifi.handler=handler;
        wifi.setConnectivityManager((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE));
        wifi.onCameraNetworkLost=()->{
            if(manualDisconnected||disconnecting)return;
            String reason="相机 Wi-Fi 已断开，请重新连接；未完成下载记录已保留";
            ConnectionState.shared.error(ConnectionState.Step.WIFI,reason);
            DownloadTransfer.Run run=DownloadTransfer.current;if(run!=null&&run.running){run.stop=true;run.error=reason;}
            Frontend.print(reason);
            if(!Backend.cGetKillSwitch()&&Backend.cGetTransport()==10)Backend.reportErrorNonBlock(Backend.PTP_IO_ERR,reason);
        };
        wifi.startNetworkListeners(getApplicationContext());

        wifi.onWiFiSelectCancel = new Runnable() {
            @Override
            public void run() {
                if(manualDisconnected||disconnecting||wifi.cameraNetworkLost())return;
                ConnectionState.shared.error(ConnectionState.Step.WIFI,"相机 Wi-Fi 未连接或已断开，请重新连接");
                if(BackgroundCamera.running&&Backend.cGetKillSwitch()){Frontend.print("图库 Wi-Fi 已释放，后台蓝牙继续待机");return;}
                if(!Backend.cGetKillSwitch()&&Backend.cGetTransport()==10){Gallery.stopAll();Backend.reportErrorNonBlock(Backend.PTP_IO_ERR,"相机 Wi-Fi 已断开，请重新连接");}
                if(bluetooth!=null)bluetooth.recordNetworkStatus("相机 Wi-Fi 请求未完成或热点已断开。可点击“重试 Wi-Fi 连接”，无需重新配对。");
                Frontend.print("相机 Wi-Fi 未连接或已断开。若系统取消了请求，请重新连接；若已手动连上相机热点，可使用 Wi-Fi 直连。");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.wifi_settings).setVisibility(View.VISIBLE);
                    }
                });
            }
        };
        wifi.onWiFiSelectAvailable = new Runnable() {
            @Override
            public void run() {
                if(manualDisconnected||disconnecting)return;
                ConnectionState.shared.update(ConnectionState.Step.WIFI,ConnectionState.Status.CONNECTING,"已选中相机热点，等待网络地址就绪");
                Log.d("main", "Selection successful");
                final Object selection=wifi.selectionToken();
                final long selectedHandle=wifi.selectedNetworkHandle();
                new Thread(() -> {
                    if (blockConnect || !Backend.cGetKillSwitch()) return;
                    long readyDeadline=android.os.SystemClock.elapsedRealtime()+20000;
                    int attempt=0;
                    boolean waitingShown=false;
                    while(attempt<3){
                        try { Thread.sleep(500); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        if (manualDisconnected || disconnecting || isFinishing() || isDestroyed() || blockConnect || !Backend.cGetKillSwitch()) return;
                        if(selection!=wifi.selectionToken()||selectedHandle<0||selectedHandle!=wifi.selectedNetworkHandle()){if(bluetooth!=null)bluetooth.recordNetworkStatus("相机 Wi-Fi 请求已更换或热点已断开，停止旧连接重试。");return;}
                        if(!wifi.selectedNetworkReady()||selectedHandle!=dev.danielc.common.WiFiComm.getNetworkHandle()){
                            if(android.os.SystemClock.elapsedRealtime()>=readyDeadline){ConnectionState.shared.error(ConnectionState.Step.WIFI,"相机热点未取得网络地址，请允许系统连接后重试");if(bluetooth!=null)bluetooth.recordNetworkStatus("相机 Wi-Fi 未能取得有效网络地址。请确认系统已允许连接相机热点，再重新连接。");return;}
                            if(!waitingShown&&bluetooth!=null){bluetooth.recordNetworkStatus("已选中相机 Wi-Fi，等待网络地址准备完成…");waitingShown=true;}
                            continue;
                        }
                        attempt++;
                        try { Thread.sleep(attempt==1?1000:2500); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        if(manualDisconnected||disconnecting||isDestroyed()||isFinishing()||selection!=wifi.selectionToken()||selectedHandle!=wifi.selectedNetworkHandle()||!wifi.selectedNetworkReady())return;
                        String detail = "Wi-Fi 已可用，连接相机 " + MySettings.getIPAddress() + ":55740（第 " + attempt + "/3 次）";
                        ConnectionState.shared.update(ConnectionState.Step.WIFI,ConnectionState.Status.READY,"Wi-Fi 连接成功");
                        ConnectionState.shared.update(ConnectionState.Step.SESSION,attempt==1?ConnectionState.Status.CONNECTING:ConnectionState.Status.RETRYING,attempt==1?"Wi-Fi 已连接，正在连接相机":"正在自动重试连接相机（"+attempt+"/3）");
                        if (bluetooth != null) bluetooth.recordNetworkStatus(detail);
                        int rc = tryConnect(3);
                        if(rc!=0&&!Backend.cGetKillSwitch())return;
                        if (rc == 0) {
                            if (bluetooth != null) bluetooth.recordNetworkStatus("相机 TCP 连接成功，正在进入图库。");
                            return;
                        }
                        if (bluetooth != null) bluetooth.recordNetworkStatus("相机 TCP 连接失败，错误码=" + rc + "，Wi-Fi 网络句柄=" + dev.danielc.common.WiFiComm.getNetworkHandle());
                    }
                    if (bluetooth != null) bluetooth.recordNetworkStatus("三次连接均失败。请长按蓝牙按钮复制完整诊断，并确认手机 Wi-Fi 已连接相机网络。");
                    ConnectionState.shared.error(ConnectionState.Step.SESSION,"三次连接均未成功，请确认相机仍在传图模式后重试");
                }, "camera-wifi-connect").start();
            }
        };
        // Start discovery if wifi is disconnected/reconnected
        wifi.onAvailable = new Runnable() {
            @Override
            public void run() {
                Backend.discoveryThread(MainActivity.this);
            }
        };

        if (savedInstanceState == null) {
            // Require legacy Android write permissions
            if (Build.VERSION.SDK_INT<=28&&ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }

        }

        discoveryPopup = new AlertDialog.Builder(this);

        ViewTreeObserver viewTreeObserver = this.getWindow().getDecorView().getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                MainActivity.this.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // The modern home uses a solid surface, avoiding full-screen bitmap allocation.
            }
        });

        Backend.discoveryThread(MainActivity.this);
        if(wifi.selectedNetworkHandle()>0&&Backend.cGetKillSwitch())wifi.onWiFiSelectAvailable.run();
    }

    private final Handler dashboardUi=new Handler(Looper.getMainLooper());
    private final Runnable dashboardTick=new Runnable(){public void run(){renderDashboard();dashboardUi.postDelayed(this,1000);}};
    private void setupWorkbench(){for(int i=0;i<3;i++){final int mode=i;findViewById(new int[]{R.id.connect_mode_0,R.id.connect_mode_1,R.id.connect_mode_2}[i]).setOnClickListener(v->{MySettings.setSetting(this,"connection_mode_tab",mode);renderConnectionMode(mode);});}renderConnectionMode(MySettings.getSetting(this,"connection_mode_tab",0));findViewById(R.id.advanced_connection).setOnClickListener(v->{View panel=findViewById(R.id.advanced_panel);boolean opening=panel.getVisibility()!=View.VISIBLE;panel.setVisibility(opening?View.VISIBLE:View.GONE);if(opening)Backend.startLanDiscovery(this);else Backend.suspendLanDiscovery();});findViewById(R.id.dashboard_gallery).setOnClickListener(v->WorkbenchNav.open(this,WorkbenchNav.GALLERY));findViewById(R.id.dashboard_info).setOnClickListener(v->{CameraDetails.show(this,Gallery.currentInfo());});findViewById(R.id.dashboard_disconnect).setOnClickListener(v->Gallery.disconnectFrom(this));}
    private void renderConnectionMode(int mode){int[] tabs={R.id.connect_mode_0,R.id.connect_mode_1,R.id.connect_mode_2},sections={R.id.connection_ble,R.id.connection_wifi,R.id.connection_usb};for(int i=0;i<3;i++){findViewById(sections[i]).setVisibility(i==mode?View.VISIBLE:View.GONE);Button tab=findViewById(tabs[i]);tab.setBackgroundResource(i==mode?R.drawable.light_button:R.drawable.ui_text_button);tab.setTextColor(ContextCompat.getColor(this,i==mode?R.color.ui_accent:R.color.ui_muted));tab.setSelected(i==mode);}}
    private void renderDashboard(){CameraInfo info=Gallery.currentInfo();boolean connected=!Backend.cGetKillSwitch()&&info!=null;findViewById(R.id.connected_dashboard).setVisibility(connected?View.VISIBLE:View.GONE);findViewById(R.id.connection_options).setVisibility(connected?View.GONE:View.VISIBLE);((TextView)findViewById(R.id.camera_connection_state)).setText(ConnectionState.shared.snapshot().title);if(connected){((TextView)findViewById(R.id.camera_dashboard_name)).setText(info==null?"正在读取相机…":info.title());((TextView)findViewById(R.id.camera_dashboard_detail)).setText(info==null?"等待图库初始化完成":info.count+" 个文件"+(!info.version.isEmpty()?"\n设备版本 "+info.version:"")+(!info.address.isEmpty()?"\n"+info.address:""));findViewById(R.id.dashboard_info).setEnabled(info!=null);findViewById(R.id.dashboard_gallery).setEnabled(info!=null);} }
    @Override protected void onResume(){super.onResume();dashboardUi.post(dashboardTick);
        dashboardUi.postDelayed(()->{if(isFinishing()||isDestroyed()||BackgroundCamera.running||MySettings.getSetting(this,"background_enabled",0)!=1||!BackgroundCamera.hasPermission(this)||!Backend.cGetKillSwitch()||blockConnect||(bluetooth!=null&&bluetooth.isBusy())||(wifi!=null&&wifi.isSelecting()))return;BackgroundCamera.start(this);},1000);
    }
    @Override protected void onPause(){dashboardUi.removeCallbacksAndMessages(null);super.onPause();}
    @Override public void onBackPressed(){moveTaskToBack(true);}
    private void openConnectedGallery(){handler.post(()->{if(manualDisconnected||disconnecting||ConnectionState.shared.snapshot().ended)return;Gallery.discardPreviousSession();startActivity(new Intent(this,Gallery.class));});}

    void backgroundImage() {
        try {
            ImageView fl = findViewById(R.id.background_image);
            int width = fl.getWidth();
            int height = fl.getHeight();
            InputStream is = getAssets().open("pexels-thevibrantmachine-3066867.jpg");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            int bmpWidth = bitmap.getWidth();
            int bmpHeight = bitmap.getHeight();

            float scale = Math.max((float)width / bmpWidth, (float)height / bmpHeight);

            int newWidth = Math.round(bmpWidth * scale);
            int newHeight = Math.round(bmpHeight * scale);

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

            Bitmap croppedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, width, height);
            fl.setImageBitmap(croppedBitmap);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    AlertDialog.Builder discoveryPopup;

    void onCameraRegistered(String model, String name, String ip) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("新相机已注册：" + model);
                builder.setMessage("请使用相机的“电脑自动保存”功能连接本设备。");
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.setNeutralButton("查看步骤", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        Intent intent = new Intent(MainActivity.this, Help.class);
                        intent.putExtra("section", "autosave");
                        startActivity(intent);
                    }
                });
                builder.show();
            }
        });
    }

    /// Called by backend
    boolean onReceiveCameraInfo(String model, String name, byte[] struct) {
        Semaphore sem = new Semaphore(1);
        final boolean[] connect = {true};
        handler.post(new Runnable() {
            @Override
            public void run() {
                discoveryPopup.setTitle("发现相机（" + model + ")");
                discoveryPopup.setMessage("是否连接这台相机？");
                discoveryPopup.show();
                discoveryPopup.setNeutralButton("Yes!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            sem.release();
                        } catch (Exception ignored) {}
                    }
                });
                discoveryPopup.setNegativeButton("No!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            connect[0] = false;
                            sem.release();
                        } catch (Exception ignored) {}
                    }
                });
            }
        });
        try {
            sem.acquire();
        } catch (Exception ignored) {}
        // TODO: return user accepts connection or not
        return connect[0];
    }

    // called by backend
    void onCameraWantsToConnect(String model, String name) {
        if(DownloadTransfer.current!=null&&DownloadTransfer.current.running)return;
        MySettings.photoCameraIdentity=null;
        try {
            Frontend.print("正在等待网络…");
            Thread.sleep(500); // Time for network to configure - this can probably be removed
            if (Backend.cConnectFromDiscovery() != 0) {
                Backend.discoveryThread(MainActivity.this);
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    findViewById(R.id.wifi_settings).setVisibility(View.GONE);
                }
            });
            openConnectedGallery();
        } catch (Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    findViewById(R.id.wifi_settings).setVisibility(View.VISIBLE);
                }
            });
            Frontend.print(e.getMessage());
        }
    }

    static volatile boolean manualDisconnected, disconnecting;
    static boolean beginUserConnection(){
        if(disconnecting)return false;
        // A requested disconnect first lets the current photo finish saving.
        // Do not clear the manual stop until that transfer has closed its session.
        DownloadTransfer.Run transfer=DownloadTransfer.current;
        if(transfer!=null&&transfer.running&&transfer.afterStop!=null)return false;
        manualDisconnected=false;
        return true;
    }
    static void stopAutomaticConnection(android.content.Context context) {
        manualDisconnected=true;
        DownloadTransfer.Run transfer=DownloadTransfer.current;
        ConnectionState.shared.closing(transfer!=null&&transfer.running);
        BackgroundCamera.stop(context);
        Backend.suspendLanDiscovery();
        MainActivity owner=instance;
        if(owner!=null&&owner.bluetooth!=null){
            owner.bluetooth.close();owner.bluetooth=new FujiBluetooth(owner);
            // Old candidate buttons capture the closed Bluetooth instance; replace them too.
            owner.bluetooth.showPairedCameras();
            ((TextView)owner.findViewById(R.id.bluetooth_status)).setText("已停止自动连接。需要重连时，请点击下方已配对相机。");
        }
    }
    static void closeManualSession(Runnable finished) {
        if(disconnecting)return;
        disconnecting=true;
        new Thread(()->{
            try {
                // Wait for any TCP connection attempt before sending CloseSession/goodbye.
                synchronized(MainActivity.class){Backend.reportError(0,"已手动断开，后台重连已停止");}
            } finally {
                new Handler(Looper.getMainLooper()).post(()->{
                    if(wifi!=null)wifi.releaseCameraNetwork();
                    ConnectionState.shared.disconnected();
                    disconnecting=false;
                    finished.run();
                });
            }
        },"camera-manual-disconnect").start();
    }
    static boolean releaseBluetoothForStandby(){return instance==null||instance.bluetooth==null||instance.bluetooth.releaseForStandby();}

    @Override
    protected void onDestroy() {
        dashboardUi.removeCallbacksAndMessages(null);
        if (bluetooth != null) bluetooth.close();
        if(wifi!=null&&wifi.handler==handler){wifi.onAvailable=null;wifi.onWiFiSelectAvailable=null;wifi.onWiFiSelectCancel=null;wifi.handler=null;}
        super.onDestroy();
    }

    public int tryConnect(int extraTmout) {
      synchronized(MainActivity.class){
        if(manualDisconnected||disconnecting||blockConnect||!Backend.cGetKillSwitch())return -6;
        if(DownloadTransfer.current!=null&&DownloadTransfer.current.running)return -1;
        blockConnect = true;
        Backend.suspendLanDiscovery();
        Frontend.print(getString(R.string.connecting));
        int rc;
        if(wifi!=null)wifi.trackCameraNetwork(dev.danielc.common.WiFiComm.getNetworkHandle());
        try{rc=Backend.cTryConnectWiFi(extraTmout);}finally{blockConnect=false;}
        if (rc != 0){if(wifi!=null)wifi.clearCameraNetwork();return rc;}
        if(wifi!=null&&wifi.cameraNetworkLost()){Backend.reportErrorNonBlock(Backend.PTP_IO_ERR,"连接期间相机 Wi-Fi 已断开");return Backend.PTP_IO_ERR;}
        Backend.cancelDiscoveryThread();
        Frontend.print("已建立连接");
        ConnectionState.shared.update(ConnectionState.Step.SESSION,ConnectionState.Status.CONNECTING,"网络已连接，正在验证相机会话");
        handler.post(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.wifi_settings).setVisibility(View.GONE);
            }
        });
        openConnectedGallery();
        return 0;
      }
    }

    public void connectDirectWifi() {
        if(!beginUserConnection())return;
        ConnectionState.shared.begin(ConnectionState.Mode.WIFI);
        new Thread(() -> {
            if (dev.danielc.common.WiFiComm.getNetworkHandle() < 0) {
                Frontend.print("未连接 Wi-Fi。请先在系统设置连接相机热点，再点击直连。");
                ConnectionState.shared.error(ConnectionState.Step.WIFI,"手机未连接 Wi-Fi，请先连接相机热点");
                handler.post(() -> findViewById(R.id.wifi_settings).setVisibility(View.VISIBLE));
                return;
            }
            ConnectionState.shared.update(ConnectionState.Step.WIFI,ConnectionState.Status.READY,"Wi-Fi 已连接，正在连接相机");
            int rc=tryConnect(3);
            if (rc!=0&&Backend.cGetKillSwitch()){ConnectionState.shared.error(ConnectionState.Step.SESSION,"相机未响应，请确认热点、传图模式及 IP 地址");Frontend.print("相机直连失败，错误码="+rc+"。请确认已连接相机热点、相机等待传图、IP 地址正确。");}
        },"direct-camera-wifi").start();
    }

    public void connectClick() {
        Context ctx = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String password = MySettings.getWPA2Password();
                if (password.length() == 0) password = null;
                int rc = tryConnect(0);
                if (rc != 0&&Backend.cGetKillSwitch()&&!blockConnect) {
                    Frontend.print(R.string.connection_failed);
                    if (wifi.connectToAccessPoint(ctx, password, Backend.matchAp) != 0) {
                        // TODO: This message repeats on every connect attempt, annoying
                        Frontend.print("请先在手机 Wi-Fi 设置中手动连接相机网络");
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.wifi_settings).setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private final java.util.concurrent.atomic.AtomicBoolean usbConnecting = new java.util.concurrent.atomic.AtomicBoolean();
    public void connectUSB() {
        if(!beginUserConnection())return;
        if(RemoteSession.isBusy()||!Backend.cGetKillSwitch()){Frontend.print("请先断开当前相机连接，再使用 USB");return;}
        if(bluetooth!=null&&bluetooth.isBusy()){Frontend.print("蓝牙连接仍在进行，请稍候");return;}
        if(DownloadTransfer.current!=null&&DownloadTransfer.current.running){Frontend.print("照片正在下载，请先停止剩余任务");return;}
        if(!usbConnecting.compareAndSet(false,true)){Frontend.print("正在检测 USB 或等待授权，请稍候");return;}
        MySettings.photoCameraIdentity=null;
        ConnectionState.shared.begin(ConnectionState.Mode.USB);
        Frontend.print("正在检测 USB 相机：请设置 USB 读卡器、供电关闭/通信开启，并使用数据线");
        new Thread(()->{
            try{
                int rc=Backend.cTryConnectUSB(MainActivity.this);
                if(rc!=0){ConnectionState.shared.error(ConnectionState.Step.SESSION,"USB 连接未完成："+Frontend.parseErr(rc));Frontend.print("USB 连接未完成："+Frontend.parseErr(rc)+"（错误码="+rc+"）。在设置中复制诊断可查看系统检测结果。");return;}
                Frontend.print("USB 接口已连接，正在初始化相机会话");openConnectedGallery();
            }finally{usbConnecting.set(false);}
        },"fuji-usb-connect").start();
    }

    public static void setLogText(String arg) {
        if (instance == null) return;
       instance.handler.post(new Runnable() {
            @Override
            public void run() {
                TextView tv = instance.findViewById(R.id.error_msg);
                if (tv == null) return;
                tv.setVisibility(DebugUi.enabled(tv.getContext()) ? View.VISIBLE : View.GONE);
                tv.setText(arg);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.home_files) {
            Intent intent = new Intent(MainActivity.this, FileGallery.class);
            startActivity(intent);
        } else if (item.getItemId() == R.id.home_settings) {
            Intent intent = new Intent(MainActivity.this, MySettings.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.add(Menu.NONE, R.id.home_files, Menu.NONE, "本地照片");
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menuItem.setIcon(R.drawable.baseline_folder_open_24);

        menuItem = menu.add(Menu.NONE, R.id.home_settings, Menu.NONE, "设置");
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menuItem.setIcon(R.drawable.baseline_settings_24);

        return true;
    }
}
