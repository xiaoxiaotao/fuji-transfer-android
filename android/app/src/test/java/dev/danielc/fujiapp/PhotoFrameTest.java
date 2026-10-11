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
 @Test public void allTemplatesKeepOriginalAndAddSpace(){
  Bitmap source=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
  PhotoDetails d=new PhotoDetails();d.camera="X-E5";d.exposure="f/2 ISO 125";
  for(int style=0;style<PhotoFrame.NAMES.length;style++){PhotoFrame.Options o=new PhotoFrame.Options();o.style=style;Bitmap framed=PhotoFrame.render(source,d,o);assertTrue(framed.getWidth()>source.getWidth());assertTrue(framed.getHeight()>source.getHeight());if(style<8)assertEquals(style==5||style==7?Color.rgb(19,21,20):PhotoFrame.PAPERS[o.paper],framed.getPixel(0,0));assertEquals(Color.RED,source.getPixel(0,0));assertFalse(source.isRecycled());framed.recycle();}source.recycle();
 }
 @Test public void previewAndExportHaveMatchingAspectRatios(){
  PhotoDetails d=new PhotoDetails();Bitmap preview=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888),full=Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888);
  for(int style=0;style<PhotoFrame.NAMES.length;style++){PhotoFrame.Options o=new PhotoFrame.Options();o.style=style;Bitmap a=PhotoFrame.render(preview,d,o),b=PhotoFrame.render(full,d,o);assertEquals((double)a.getWidth()/a.getHeight(),(double)b.getWidth()/b.getHeight(),0.005);a.recycle();b.recycle();}preview.recycle();full.recycle();
 }
 @Test public void shutterIsReadableWithoutInventingMissingValues(){assertEquals("1/60",PhotoDetails.shutter("0.0166667"));assertEquals("1/125",PhotoDetails.shutter("1/125"));assertEquals("2.5",PhotoDetails.shutter("2.5"));assertEquals("",PhotoDetails.shutter(""));}
 @Test public void exportOptionsAreSnapshot(){PhotoFrame.Options o=new PhotoFrame.Options();o.title="Original";PhotoFrame.Options copy=o.copy();o.title="Changed";o.style=7;assertEquals("Original",copy.title);assertEquals(8,copy.style);}
}
