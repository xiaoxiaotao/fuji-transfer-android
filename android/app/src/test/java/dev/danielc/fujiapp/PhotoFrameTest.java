package dev.danielc.fujiapp;
import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PhotoFrameTest {
 @Test public void sharedFrameUsesNarrowFileProviderPath()throws Exception{
  android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
  java.io.File folder=new java.io.File(c.getCacheDir(),"frames");folder.mkdirs();java.io.File file=new java.io.File(folder,"test.jpg");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(1);}
  android.net.Uri uri=LocalMedia.shareUri(c,android.net.Uri.fromFile(file));assertEquals("content",uri.getScheme());assertEquals(c.getPackageName()+".files",uri.getAuthority());assertTrue(uri.getPath().startsWith("/frame_shares/"));file.delete();
 }
 @Test public void stylesKeepOriginalPixelsAndAddDistinctBorders(){
  Bitmap source=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
  PhotoDetails d=new PhotoDetails();d.camera="X-E5";d.exposure="f/2 ISO 125";
  for(int style=0;style<3;style++){Bitmap framed=PhotoFrame.render(source,d,style);assertEquals(168,framed.getWidth());assertEquals(style==2?128:244,framed.getHeight());assertEquals(style==1?Color.rgb(22,24,23):Color.WHITE,framed.getPixel(0,0));assertEquals(Color.RED,source.getPixel(0,0));assertFalse(source.isRecycled());framed.recycle();}source.recycle();
 }
}
