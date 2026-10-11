package dev.danielc.fujiapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Direct hotspot entry; no Bluetooth activation and no password persistence. */
final class DirectWifiDialog {
    static void show(MainActivity activity) {
        if(DownloadTransfer.current!=null&&DownloadTransfer.current.running){android.widget.Toast.makeText(activity,"照片正在下载，请先停止剩余任务",android.widget.Toast.LENGTH_LONG).show();return;}
        int padding=(int)(20*activity.getResources().getDisplayMetrics().density);
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(padding,padding,padding,padding);
        TextView guide=new TextView(activity);
        guide.setText("先在相机开启“无线通信/图像传输”，让相机停留在等待连接画面。手机若提示无法上网，请保持连接。\nX-E5 1.12 请使用首页蓝牙入口启动 Wi-Fi。");form.addView(guide);
        EditText ssid=field(activity,form,"相机 Wi-Fi 名称（系统连接时填写）",MySettings.getSetting(activity,"direct_ssid",""));
        EditText password=field(activity,form,"Wi-Fi 密码（无密码则留空，不会保存）","");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText ip=field(activity,form,"相机 IP 地址",MySettings.getSetting(activity,"direct_ip",MySettings.defaultIPAddress));
        ip.setInputType(InputType.TYPE_CLASS_PHONE);
        TextView modeLabel=new TextView(activity);modeLabel.setText("相机连接方式");form.addView(modeLabel);
        Spinner mode=new Spinner(activity);
        mode.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"自动识别（推荐）","旧款无线通信","新版 XApp 连接"}));form.addView(mode);
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("相机 Wi-Fi 直连").setView(scroll)
            .setPositiveButton("连接当前 Wi-Fi",null).setNeutralButton("选择相机 Wi-Fi",null).setNegativeButton("打开 Wi-Fi 设置",(d,w)->activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS))).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(!configure(activity,ip,mode))return;
                dialog.dismiss();activity.connectDirectWifi();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                String name=ssid.getText().toString().trim(),secret=password.getText().toString();
                if(name.isEmpty()){ssid.setError("请填写相机显示的 Wi-Fi 名称，或先在系统设置连接");return;}
                if(!secret.isEmpty()&&(secret.length()<8||secret.length()>63)){password.setError("WPA2 密码应为 8–63 个字符");return;}
                if(Build.VERSION.SDK_INT<29){activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));return;}
                String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
                if(ContextCompat.checkSelfPermission(activity,permission)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(activity,new String[]{permission},92);guide.setText("请允许附近 Wi-Fi 或定位权限，然后再次点击“选择相机 Wi-Fi”。");return;}
                if(!configure(activity,ip,mode))return;
                MySettings.setSetting(activity,"direct_ssid",name);
                ConnectionState.shared.begin(ConnectionState.Mode.WIFI);
                try { int rc=MainActivity.wifi.connectToVisibleCameraAccessPoint(activity,name,secret);if(rc!=0){ConnectionState.shared.error(ConnectionState.Step.WIFI,"请先在系统设置连接相机热点，再选择直连");guide.setText("此系统请先在 Wi-Fi 设置连接相机，再选择“连接当前 Wi-Fi”。");return;} }
                catch(SecurityException|IllegalArgumentException|IllegalStateException e){ConnectionState.shared.error(ConnectionState.Step.WIFI,"无法请求 Wi-Fi，请检查权限或在系统设置连接");guide.setText("无法请求 Wi-Fi："+e.getMessage()+"。可在系统设置连接后重试。");return;}
                Frontend.print("等待系统连接相机热点…");dialog.dismiss();
            });
        });dialog.show();
    }
    private static boolean configure(MainActivity activity,EditText ip,Spinner mode) {
        String address=ip.getText().toString().trim();
        if(!DirectWifiConfig.validAddress(address)){ip.setError("请输入有效 IPv4 地址，例如 192.168.0.1");return false;}
        MySettings.setSetting(activity,"direct_ip",address);
        MySettings.directAddress=address;MySettings.photoCameraIdentity=null;
        Backend.cSetWiFiProtocol(mode.getSelectedItemPosition());
        return true;
    }
    private static EditText field(MainActivity activity,LinearLayout form,String hint,String value) {
        EditText edit=new EditText(activity);edit.setHint(hint);edit.setText(value);edit.setSingleLine(true);form.addView(edit);return edit;
    }
}
