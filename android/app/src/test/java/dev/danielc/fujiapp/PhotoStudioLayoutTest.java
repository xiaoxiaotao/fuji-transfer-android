package dev.danielc.fujiapp;
import android.content.Intent;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w320dp-h640dp-xhdpi")
public class PhotoStudioLayoutTest {
 private void verify(int height){
  Intent intent=new Intent().putExtra("target","file:///unavailable-test-photo.jpg").putExtra("name","test.jpg");
  ActivityController<PhotoStudio> controller=Robolectric.buildActivity(PhotoStudio.class,intent).create();PhotoStudio activity=controller.get();
  View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,640,height);forceLayout(root);root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,640,height);
  View export=find(root,"↓ 导出图片"),preview=findDescription(root,"照片预览，支持双指缩放");assertNotNull(export);assertNotNull(preview);assertTrue("Preview height after resize: "+preview.getHeight(),preview.getHeight()>=200);
  Rect action=new Rect();export.getDrawingRect(action);((ViewGroup)root).offsetDescendantRectToMyCoords(export,action);assertTrue(action.top>=0);assertTrue(action.right<=640);assertTrue(action.bottom<=height);
  for(String tab:new String[]{"模板","布局","参数","主题","阴影","边距"}){View target=findDescription(root,tab);assertNotNull(target);Rect bounds=new Rect();target.getDrawingRect(bounds);((ViewGroup)root).offsetDescendantRectToMyCoords(target,bounds);assertTrue(bounds.width()>=96);assertTrue(bounds.bottom<=height);assertTrue(bounds.left>=0);}
  controller.destroy();
 }
 private void forceLayout(View view){view.forceLayout();if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)forceLayout(((ViewGroup)view).getChildAt(i));}
 private View find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
 private View findDescription(View view,String text){if(text.equals(view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
 @Test public void compactEditorKeepsExportAndSixControlsInsideViewport(){verify(1176);}
 @Test public void keyboardResizeKeepsThePreviewAndControlsUsable(){verify(820);}
}
