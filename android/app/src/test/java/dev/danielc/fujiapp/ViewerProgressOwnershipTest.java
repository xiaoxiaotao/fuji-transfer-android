package dev.danielc.fujiapp;

import android.os.Looper;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ViewerProgressOwnershipTest {
 @Test public void nativeCallbacksCannotOverwriteServiceDownloadCaption() throws Exception {
  // Attach the real Viewer without starting camera work from onCreate.
  Viewer viewer=Robolectric.buildActivity(Viewer.class).get();
  viewer.setTheme(R.style.Theme_FujiWifiApp_NoActionBar);viewer.setContentView(R.layout.activity_viewer);
  Field instance=Viewer.class.getDeclaredField("instance");instance.setAccessible(true);Object old=instance.get(null);
  Field busy=Viewer.class.getDeclaredField("busy");busy.setAccessible(true);((AtomicBoolean)busy.get(viewer)).set(true);
  TextView caption=viewer.findViewById(R.id.fileInfo);String service="保存中 · 25% · 已传约 8.0 MB";caption.setText(service);
  try {instance.set(null,new WeakReference<>(viewer));for(int i=0;i<100;i++){Viewer.notifyDownloadProgress(i);Viewer.notifyDownloadSpeed(i*1024);}Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(service,caption.getText().toString());}
  finally {instance.set(null,old);}
 }
}
