package dev.danielc.fujiapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import dev.danielc.common.CameraNetworkWatch;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class DownloadRecoveryTest {
 private Context context;private DownloadTasks store;
 private DownloadTasks open()throws Exception{java.lang.reflect.Constructor<DownloadTasks> constructor=DownloadTasks.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);return constructor.newInstance(context);}
 @Before public void setup()throws Exception{context=RuntimeEnvironment.getApplication();context.deleteDatabase("photo-downloads.db");store=open();}
 @After public void close(){store.close();}
 private DownloadTask pending(String camera,String key,int handle){DownloadTask task=store.create("old-session",new int[]{handle}).get(0);task.camera=camera;task.photoKey=key;task.name="DSCF0001.JPG";task.stop();store.save(task);return task;}
 @Test public void reconnectUsesMatchedCurrentHandleAndCompletedAttemptSuppressesOldFailure(){pending("camera-a","photo-a",7);List<DownloadTask> pending=store.recoverable("camera-a","new-session");assertEquals(1,pending.size());List<DownloadTask> restored=store.restore("new-session",pending,Collections.singletonMap("photo-a",91));assertEquals(91,restored.get(0).handle);assertEquals("photo-a",restored.get(0).photoKey);restored.get(0).done();store.save(restored.get(0));assertTrue(store.recoverable("camera-a","third-session").isEmpty());}
 @Test public void differentCameraAndUnknownIdentityCannotResume(){pending("camera-a","photo-a",1);pending("camera-a",null,2);assertTrue(store.recoverable("camera-b","next").isEmpty());assertNull(store.recoverable(null,"next").stream().findFirst().orElse(null));assertEquals(1,store.recoverable("camera-a","next").size());}
 @Test public void absentAndAmbiguousPhotosRemainPendingWithoutReusingOldHandle(){pending("camera-a","photo-a",7);DownloadRecovery matcher=new DownloadRecovery();matcher.observe("photo-a",30);matcher.observe("photo-a",40);matcher.observe(null,7);List<DownloadTask> saved=store.restore("next",store.recoverable("camera-a","next"),matcher.matches());assertTrue(saved.isEmpty());assertEquals(1,store.recoverable("camera-a","later").size());}
 @Test public void currentSessionPendingIsNotDuplicatedByRecovery(){pending("camera-a","photo-a",7);DownloadTask task=store.restore("next",store.recoverable("camera-a","next"),Collections.singletonMap("photo-a",91)).get(0);task.stop();store.save(task);assertTrue(store.recoverable("camera-a","next").isEmpty());assertEquals(1,store.recoverable("camera-a","third").size());assertEquals(1,store.retryable("next",DownloadTask.State.STOPPED).size());}
 @Test public void migrationRetainsLegacyHistoryButDoesNotGuessItsPhotoIdentity()throws Exception{store.close();context.deleteDatabase("photo-downloads.db");try(SQLiteDatabase db=context.openOrCreateDatabase("photo-downloads.db",0,null)){db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT,session TEXT NOT NULL,handle INTEGER NOT NULL,name TEXT NOT NULL,state TEXT NOT NULL,progress INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");db.execSQL("INSERT INTO tasks(session,handle,name,state,created) VALUES('old',1,'DSCF0001.JPG','RUNNING',1)");db.setVersion(1);}store=open();assertEquals(1,store.page(Long.MAX_VALUE,10).size());assertEquals(DownloadTask.State.STOPPED,store.page(Long.MAX_VALUE,10).get(0).task.state());assertTrue(store.recoverable("camera-a","new").isEmpty());assertEquals(2,store.getReadableDatabase().getVersion());}
 @Test public void processRestartKeepsPreparedWaitingPhotosRecoverable()throws Exception{DownloadTask task=store.create("old-session",new int[]{1}).get(0);task.camera="camera-a";task.photoKey="photo-a";store.save(task);store.close();store=open();assertEquals(1,store.recoverable("camera-a","next").size());assertEquals(DownloadTask.State.STOPPED,store.recoverable("camera-a","next").get(0).state());}
 @Test public void cameraNetworkLossIgnoresOtherNetworksAndDuplicateCallbacks(){CameraNetworkWatch watch=new CameraNetworkWatch();watch.track(100);assertFalse(watch.lost(200));assertFalse(watch.isLost());assertTrue(watch.lost(100));assertFalse(watch.lost(100));assertTrue(watch.isLost());watch.track(300);assertFalse(watch.lost(100));assertTrue(watch.lost(300));watch.clear();assertFalse(watch.lost(300));assertFalse(watch.isLost());}
}
