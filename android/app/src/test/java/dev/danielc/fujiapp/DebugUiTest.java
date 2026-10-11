package dev.danielc.fujiapp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class DebugUiTest {
    @Test public void visibilityToggleKeepsConnectionStatusAndRecording() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_FujiWifiApp_NoActionBar);
        activity.setContentView(LayoutInflater.from(activity).inflate(R.layout.activity_main,null));
        MySettings.setSetting(activity,"show_debug",0);
        DebugUi.apply(activity);
        assertEquals(View.GONE,activity.findViewById(R.id.error_msg).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.bluetooth_status).getVisibility());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.connection_status_panel).getVisibility());
        RunLog.initialize(activity);
        String marker="debug-off-record-"+System.nanoTime();
        Frontend.print(marker);
        assertTrue(Frontend.getConnectionDiagnostic().contains(marker));
        File log=new File(activity.getNoBackupFilesDir(),"run-logs/current.log");
        long deadline=System.currentTimeMillis()+3000;
        while(System.currentTimeMillis()<deadline&&(!log.exists()||!new String(Files.readAllBytes(log.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains(marker)))Thread.sleep(10);
        assertTrue(new String(Files.readAllBytes(log.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains(marker));
        MySettings.setSetting(activity,"show_debug",1);DebugUi.apply(activity);
        assertEquals(View.VISIBLE,activity.findViewById(R.id.error_msg).getVisibility());
        MySettings.setSetting(activity,"show_debug",0);DebugUi.apply(activity);
        assertEquals(View.GONE,activity.findViewById(R.id.error_msg).getVisibility());
    }
    @Test public void operationalHistoryRotatesAndSurvivesInitialization() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        RunLog.initialize(activity);
        String message=new String(new char[600000]).replace('\0','x');
        RunLog.record(message);RunLog.record(message);
        String marker="after-rotation-"+System.nanoTime();RunLog.record(marker);RunLog.initialize(activity);
        File directory=new File(activity.getNoBackupFilesDir(),"run-logs");
        File current=new File(directory,"current.log"),previous=new File(directory,"previous.log");
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline&&(!current.exists()||!new String(Files.readAllBytes(current.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains(marker)))Thread.sleep(10);
        assertTrue(previous.exists());assertTrue(previous.length()<=1048576);assertTrue(current.length()<=1048576);
        assertTrue(new String(Files.readAllBytes(current.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains(marker));
    }
}
