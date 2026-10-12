package dev.danielc.fujiapp;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FrameHomeTest {
 @Test public void pickerAndCameraReceiptsForTheSamePhotoHaveOneIdentity(){
  String original=SavedLibrary.sourceKey(android.net.Uri.parse("content://media/external/images/media/42"));
  assertEquals(original,SavedLibrary.sourceKey(android.net.Uri.parse("content://com.android.providers.media.documents/document/image%3A42")));
  assertEquals(original,SavedLibrary.sourceKey(android.net.Uri.parse("content://media/external_primary/images/media/42")));
  assertNotEquals(original,SavedLibrary.sourceKey(android.net.Uri.parse("content://com.android.providers.media.documents/document/image%3A43")));
  assertNotEquals(original,SavedLibrary.sourceKey(android.net.Uri.parse("file:///Pictures/42.jpg")));
  assertEquals("image/png",MediaFile.mime("SCREENSHOT.PNG"));
 }
 @Test public void launcherOpensCreationAndUsbStillOpensCamera(){
  android.content.Context context=RuntimeEnvironment.getApplication();
  Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.getPackageName());
  List<ResolveInfo> entries=context.getPackageManager().queryIntentActivities(launch,0);
  assertEquals(1,entries.size());assertEquals(FrameHome.class.getName(),entries.get(0).activityInfo.name);
  Intent usb=new Intent("android.hardware.usb.action.USB_DEVICE_ATTACHED").setPackage(context.getPackageName());
  assertEquals(MainActivity.class.getName(),context.getPackageManager().queryIntentActivities(usb,0).get(0).activityInfo.name);
 }
 @Test public void primaryActionUsesSystemPhotoPickerWithoutCameraSession(){
  ActivityController<FrameHome> controller=Robolectric.buildActivity(FrameHome.class).create();FrameHome home=controller.get();
  home.findViewById(R.id.creation_pick).performClick();Intent intent=Shadows.shadowOf(home).getNextStartedActivityForResult().intent;
  assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.getAction());assertEquals("image/*",intent.getType());assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
  assertTrue((intent.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0);
  Shadows.shadowOf(home).getNextStartedActivity();home.onActivityResult(501,Activity.RESULT_CANCELED,null);assertNull(Shadows.shadowOf(home).getNextStartedActivity());controller.destroy();
 }
 @Test @Config(qualifiers="w320dp-h640dp-xhdpi") public void compactHomeKeepsNavigationAndPrimaryActionAvailable(){
  ActivityController<FrameHome> controller=Robolectric.buildActivity(FrameHome.class).create();FrameHome home=controller.get();View root=home.findViewById(R.id.creation_root);
  root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1176,View.MeasureSpec.EXACTLY));root.layout(0,0,640,1176);
  LinearLayout nav=root.findViewById(R.id.workbench_nav);assertEquals(4,nav.getChildCount());assertTrue(nav.getBottom()<=root.getHeight());assertTrue(nav.getChildAt(0).isSelected());
  assertEquals("首页，当前页面",nav.getChildAt(0).getContentDescription());assertEquals("相机",nav.getChildAt(2).getContentDescription());assertTrue(root.findViewById(R.id.creation_scroll).getHeight()>600);
  assertTrue(root.findViewById(R.id.creation_pick).isEnabled());controller.destroy();
 }
 @Test public void selectedTemplateIsPassedToEditorAndReopeningImportDoesNotRepeat(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();PhotoStudio.open(activity,android.net.Uri.parse("content://photos/1"),"sample.jpg",10);
  Intent editor=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(10,editor.getIntExtra("style",-1));assertEquals("content://photos/1",editor.getStringExtra("target"));
  Intent importing=new Intent().putExtra("pick_photo",true);ActivityController<FrameHome> controller=Robolectric.buildActivity(FrameHome.class,importing).create();FrameHome home=controller.get();
  assertNotNull(Shadows.shadowOf(home).getNextStartedActivityForResult());assertFalse(home.getIntent().hasExtra("pick_photo"));controller.destroy();activity.finish();
 }
 @Test public void recipeLaunchKeepsNoteAndStyleDuringPickerAndClearsOnCancel(){
  Intent intent=new Intent().putExtra("pick_photo",true).putExtra("frame_style",70).putExtra("recipe_note","My look / Classic Chrome");ActivityController<FrameHome> controller=Robolectric.buildActivity(FrameHome.class,intent).create();FrameHome home=controller.get();assertNotNull(Shadows.shadowOf(home).getNextStartedActivityForResult());Bundle state=new Bundle();home.onSaveInstanceState(state);assertEquals(70,state.getInt("pending_style"));assertEquals("My look / Classic Chrome",state.getString("pending_recipe_note"));assertFalse(home.getIntent().hasExtra("pick_photo"));home.onActivityResult(501,Activity.RESULT_CANCELED,null);Bundle cancelled=new Bundle();home.onSaveInstanceState(cancelled);assertEquals("",cancelled.getString("pending_recipe_note"));controller.destroy();
 }
}
