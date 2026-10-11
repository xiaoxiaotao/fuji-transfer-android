package dev.danielc.fujiapp;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.content.SharedPreferences;
import android.widget.TextView;

public class MySettings extends AppCompatActivity {
    final static String defaultIPAddress = "192.168.0.1";
    final static String defaultPassword = "";
    final static String defaultClientName = "Fudge";

    public static String getWPA2Password() {
        return getSetting(MainActivity.instance, "wpa_password", defaultPassword);
    }

    public static String getClientName() {
        return getSetting(MainActivity.instance, "client_name", defaultClientName);
    }

    static volatile String directAddress;
    static volatile String photoCameraIdentity;
    public static String getIPAddress() {
        if (directAddress != null) return directAddress;
        return getSetting(MainActivity.instance, "ip_address", defaultIPAddress);
    }

    static void setSetting(Context ctx, String key, String value) {
        SharedPreferences prefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        prefs.edit().putString(key, value).apply();
    }
    static void setSetting(Context ctx, String key, int value) {
        SharedPreferences prefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        prefs.edit().putInt(key, value).apply();
    }
    static String getSetting(Context ctx, String key, String defaultValue) {
        SharedPreferences prefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        return prefs.getString(key, defaultValue);
    }
    static int getSetting(Context ctx, String key, int defaultValue) {
        SharedPreferences prefs = ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE);
        return prefs.getInt(key, defaultValue);
    }

    void setupTextView(TextView tv, String key, String defaultValue) {
        tv.setText(MySettings.getSetting(MySettings.this, key, defaultValue));
        tv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                MySettings.setSetting(MySettings.this, key, tv.getText().toString());
            }
            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.ui_toolbar));
        MainActivity.applyInsets(getWindow(),findViewById(R.id.settings_root));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        WorkbenchNav.attach(this,WorkbenchNav.SETTINGS);
        settingSwitch(R.id.setting_skip,"skip_existing",1);settingSwitch(R.id.setting_private,"save_private",0);settingSwitch(R.id.setting_details,"gallery_details",1);
        findViewById(R.id.settings_help).setOnClickListener(v->startActivity(new android.content.Intent(this,Help.class)));
        findViewById(R.id.settings_clear_cache).setOnClickListener(v->{v.setEnabled(false);new Thread(()->{long bytes=PhotoDiskCache.clearThumbnails(this);runOnUiThread(()->{if(isDestroyed())return;v.setEnabled(true);android.widget.Toast.makeText(this,String.format(java.util.Locale.CHINA,"已清理 %.1f MB 预览缓存",bytes/1048576.0),android.widget.Toast.LENGTH_LONG).show();});},"clear-preview-cache").start();});
        findViewById(R.id.settings_diagnostic).setOnClickListener(v->{String diagnostic=Frontend.getConnectionDiagnostic();new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("连接与传输诊断").setMessage(diagnostic.isEmpty()?"尚无诊断记录":diagnostic).setPositiveButton("复制",(d,w)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Fudge 诊断",diagnostic))).setNegativeButton("关闭",null).show();});
        android.widget.Button standby=new android.widget.Button(this,null,0,R.style.UiTextButton);standby.setText("后台蓝牙连接 · 实验");
        ((android.view.ViewGroup)findViewById(R.id.settings_help).getParent()).addView(standby);
        standby.setOnClickListener(v->new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("后台蓝牙连接").setMessage("保持与已配对相机的蓝牙连接，关闭界面后仍在通知栏显示状态。\n\n当前用于验证拍摄通知，自动下载尚未开放。手动进入图库时会自动交接连接；退出图库后恢复后台待机。").setPositiveButton("开启",(d,w)->BackgroundCamera.start(this)).setNeutralButton("停止",(d,w)->BackgroundCamera.stop(this)).setNegativeButton("关闭",null).show());
        actionBar.setTitle(R.string.settings);
        setupTextView(findViewById(R.id.ip_address_text), "ip_address", defaultIPAddress);
        setupTextView(findViewById(R.id.wpa_password), "wpa_password", defaultPassword);
        setupTextView(findViewById(R.id.client_name), "client_name", defaultClientName);
        findViewById(R.id.start_discovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Backend.startLanDiscovery(MainActivity.instance);
            }
        });
    }

    private void settingSwitch(int id,String key,int fallback){androidx.appcompat.widget.SwitchCompat toggle=findViewById(id);toggle.setChecked(getSetting(this,key,fallback)==1);toggle.setOnCheckedChangeListener((button,checked)->setSetting(this,key,checked?1:0));}
    @Override public void onBackPressed(){WorkbenchNav.open(this,WorkbenchNav.CAMERA);}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
