package dev.danielc.fujiapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ConnectionStatusTest {
    private ConnectionState ready() {
        ConnectionState state=new ConnectionState();state.begin(ConnectionState.Mode.BLUETOOTH);
        for(ConnectionState.Step step:ConnectionState.Step.values())state.update(step,ConnectionState.Status.READY,"图库已就绪 · 234 个文件");
        return state;
    }
    @Test public void tcpConnectionDoesNotClaimGalleryIsReady() {
        ConnectionState state=new ConnectionState();state.begin(ConnectionState.Mode.BLUETOOTH);
        state.update(ConnectionState.Step.BLUETOOTH,ConnectionState.Status.READY,"蓝牙连接成功");
        state.update(ConnectionState.Step.WIFI,ConnectionState.Status.READY,"Wi-Fi 连接成功");
        state.update(ConnectionState.Step.SESSION,ConnectionState.Status.CONNECTING,"确认相机响应");
        assertFalse(state.snapshot().title.contains("图库可用"));
        assertEquals(ConnectionState.Status.WAITING,state.snapshot().steps[3]);
    }
    @Test public void bluetoothDisconnectionDoesNotMisreportWifiSessionLoss() {
        ConnectionState state=ready();state.bluetoothLost();
        assertEquals(ConnectionState.Status.DISCONNECTED,state.snapshot().steps[0]);
        assertEquals("相机已连接 · 图库可用",state.snapshot().title);
        assertTrue(state.snapshot().detail.contains("Wi-Fi 传图连接仍有效"));
    }
    @Test public void lateSuccessCannotOverwriteDisconnectOrError() {
        ConnectionState state=ready();state.closing(true);
        state.update(ConnectionState.Step.GALLERY,ConnectionState.Status.READY,"迟到成功");
        assertEquals("正在断开连接",state.snapshot().title);
        assertTrue(state.snapshot().detail.contains("保存完成"));
        state.disconnected();state.update(ConnectionState.Step.SESSION,ConnectionState.Status.READY,"迟到成功");
        assertEquals("连接已断开",state.snapshot().title);
        state.begin(ConnectionState.Mode.WIFI);state.error(ConnectionState.Step.SESSION,"相机没有响应");
        state.update(ConnectionState.Step.GALLERY,ConnectionState.Status.READY,"迟到成功");
        assertEquals("连接错误",state.snapshot().title);
        assertEquals("相机没有响应",state.snapshot().detail);
    }
    @Test public void directWifiAndUsbDoNotShowUnnecessaryStepsAsFailures() {
        ConnectionState state=new ConnectionState();state.begin(ConnectionState.Mode.WIFI);
        assertEquals("— 无需蓝牙",state.snapshot().label(ConnectionState.Step.BLUETOOTH));
        state.begin(ConnectionState.Mode.USB);
        assertEquals(ConnectionState.Status.SKIPPED,state.snapshot().steps[0]);
        assertEquals(ConnectionState.Status.SKIPPED,state.snapshot().steps[1]);
    }
    @Test public void newAttemptClearsOldFailureAndSnapshotIsIndependent() {
        ConnectionState state=ready();ConnectionState.Snapshot prior=state.snapshot();
        state.error(ConnectionState.Step.WIFI,"热点断开");
        assertEquals(ConnectionState.Status.READY,prior.steps[1]);
        state.begin(ConnectionState.Mode.BLUETOOTH);
        assertEquals("正在连接蓝牙",state.snapshot().title);
        assertEquals(ConnectionState.Status.WAITING,state.snapshot().steps[3]);
    }
    @Test public void protocolRecoveryAppearsAsProgressInsteadOfTerminalError() {
        ConnectionState state=new ConnectionState();state.begin(ConnectionState.Mode.WIFI);
        state.update(ConnectionState.Step.SESSION,ConnectionState.Status.RETRYING,"正在自动恢复，请稍候");
        assertEquals("正在恢复相机会话",state.snapshot().title);
        assertFalse(state.snapshot().ended);
        state.update(ConnectionState.Step.SESSION,ConnectionState.Status.READY,"会话成功");
        state.update(ConnectionState.Step.GALLERY,ConnectionState.Status.READY,"图库就绪");
        assertTrue(state.snapshot().title.contains("图库可用"));
    }
    @Test public void statusCardsRenderOnBothScreensAndKeepGalleryVisible() throws Exception {
        org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();
        Activity activity=controller.get();activity.setTheme(R.style.Theme_FujiWifiApp_NoActionBar);
        try {
            ConnectionState state=new ConnectionState();state.begin(ConnectionState.Mode.BLUETOOTH);
            render(activity,R.layout.activity_main,state,"status-connecting");
            state.error(ConnectionState.Step.BLUETOOTH,"蓝牙连接未完成，请确认相机已开机后重新连接");
            render(activity,R.layout.activity_main,state,"status-error");
            state.disconnected();render(activity,R.layout.activity_main,state,"status-disconnected");
            View gallery=render(activity,R.layout.gallery,ready(),"status-gallery");
            assertTrue("Status card must leave room to browse photos",gallery.findViewById(R.id.fileView).getHeight()>activity.getResources().getDisplayMetrics().density*220);
        }finally{controller.pause().stop().destroy();}
    }
    private View render(Activity activity,int layout,ConnectionState state,String name)throws Exception {
        View root=activity.getLayoutInflater().inflate(layout,null);activity.setContentView(root);
        ConnectionStatusView status=root.findViewById(R.id.connection_status_panel);status.render(state.snapshot());
        int width=786,height=1600;
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
        checkText(status);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        File output=new File("build/ui-preview/"+name+".png");output.getParentFile().mkdirs();
        try(FileOutputStream stream=new FileOutputStream(output)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
        bitmap.recycle();return root;
    }
    private void checkText(android.view.ViewGroup group) {
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child.getVisibility()!=View.VISIBLE)continue;
            if(child instanceof TextView){TextView text=(TextView)child;assertEquals("Connection state text must remain fully readable",0,text.getLayout().getEllipsisCount(text.getLineCount()-1));}
            else if(child instanceof android.view.ViewGroup)checkText((android.view.ViewGroup)child);
        }
    }
}
