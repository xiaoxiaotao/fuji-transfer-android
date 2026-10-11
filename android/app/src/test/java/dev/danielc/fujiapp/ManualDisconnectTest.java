package dev.danielc.fujiapp;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import dev.danielc.common.WiFiComm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ManualDisconnectTest {
    @Test public void reconnectWaitsForCurrentPhotoAndPendingDisconnect(){
        DownloadTransfer.Run previous=DownloadTransfer.current;
        DownloadTransfer.Run transfer=new DownloadTransfer.Run("session","camera",new int[]{1},null,false,java.util.Collections.emptyMap(),false);
        DownloadTransfer.current=transfer;
        MainActivity.manualDisconnected=true;MainActivity.disconnecting=false;
        transfer.afterStop=()->{};
        transfer.stop=true;
        try{
            assertFalse(MainActivity.beginUserConnection());
            assertTrue(MainActivity.manualDisconnected);
            // Once saving finishes, teardown still has to finish before reconnecting.
            transfer.running=false;MainActivity.disconnecting=true;
            assertFalse(MainActivity.beginUserConnection());
            MainActivity.disconnecting=false;transfer.afterStop=null;
            assertTrue(MainActivity.beginUserConnection());
            assertFalse(MainActivity.manualDisconnected);
        }finally{DownloadTransfer.current=previous;MainActivity.disconnecting=false;MainActivity.manualDisconnected=false;}
    }
    @Test public void explicitReconnectClearsManualStopWithoutEnablingBackground(){
        Context context=RuntimeEnvironment.getApplication();
        MySettings.setSetting(context,"background_enabled",0);
        MainActivity.manualDisconnected=true;MainActivity.disconnecting=false;
        assertTrue(MainActivity.beginUserConnection());
        assertFalse(MainActivity.manualDisconnected);
        assertEquals(0,MySettings.getSetting(context,"background_enabled",-1));
    }
    @Test public void reconnectCannotInterruptSessionTeardown(){
        MainActivity.manualDisconnected=true;MainActivity.disconnecting=true;
        try{assertFalse(MainActivity.beginUserConnection());assertTrue(MainActivity.manualDisconnected);}
        finally{MainActivity.disconnecting=false;MainActivity.manualDisconnected=false;}
    }
    @Test public void explicitStopDisablesResumeAndStopsService(){
        Context context=RuntimeEnvironment.getApplication();
        MySettings.setSetting(context,"background_enabled",1);
        BackgroundCamera.stop(context);
        assertEquals(0,MySettings.getSetting(context,"background_enabled",-1));
        Intent stopped=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStoppedService();
        assertNotNull(stopped);
        assertEquals(BackgroundCamera.class.getName(),stopped.getComponent().getClassName());
    }
    @Test public void stoppedServiceCannotRestartFromQueuedStart(){
        Context context=RuntimeEnvironment.getApplication();
        MySettings.setSetting(context,"background_enabled",0);
        org.robolectric.android.controller.ServiceController<BackgroundCamera> controller=Robolectric.buildService(BackgroundCamera.class).create();
        try{
            assertEquals(android.app.Service.START_NOT_STICKY,controller.get().onStartCommand(new Intent(),0,1));
            assertFalse(BackgroundCamera.running);
        }finally{controller.destroy();}
    }
    @Test public void releasingHotspotInvalidatesOldRetryToken()throws Exception{
        WiFiComm wifi=new WiFiComm();
        wifi.setConnectivityManager((ConnectivityManager)RuntimeEnvironment.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE));
        java.lang.reflect.Field token=WiFiComm.class.getDeclaredField("lastCallback");token.setAccessible(true);
        token.set(wifi,new ConnectivityManager.NetworkCallback());
        java.lang.reflect.Field selecting=WiFiComm.class.getDeclaredField("selecting");selecting.setAccessible(true);selecting.set(wifi,true);
        Object old=wifi.selectionToken();
        wifi.releaseCameraNetwork();
        assertNotSame(old,wifi.selectionToken());assertNull(wifi.selectionToken());assertFalse(wifi.isSelecting());assertFalse(wifi.selectedNetworkReady());
        wifi.releaseCameraNetwork(); // Repeated disconnect is harmless.
    }
}
