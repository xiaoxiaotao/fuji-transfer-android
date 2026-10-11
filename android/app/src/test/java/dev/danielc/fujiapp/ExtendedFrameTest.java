package dev.danielc.fujiapp;

import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtendedFrameTest {
    private Bitmap solid(int width,int height,int color){Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);return bitmap;}
    @Test public void everyTemplateBelongsToExactlyOneCategoryAndLegacyIdsStayStable(){
        assertEquals("经典参数",PhotoFrame.NAMES[0]);assertEquals("雾光卡片",PhotoFrame.NAMES[8]);assertEquals("琉璃色盘",PhotoFrame.NAMES[10]);
        Set<Integer> ids=new HashSet<>();Set<String> names=new HashSet<>();for(int c=0;c<PhotoFrame.GROUPS.length;c++)for(int id:PhotoFrame.GROUPS[c]){assertTrue(ids.add(id));assertTrue(names.add(PhotoFrame.NAMES[id]));assertEquals(c,PhotoFrame.category(id));}
        assertEquals(PhotoFrame.NAMES.length,ids.size());
    }
    @Test public void portraitAndLandscapeRemainOpaqueAcrossAllExtendedTemplates(){
        for(int[] size:new int[][]{{300,200},{200,300},{240,240}}){Bitmap source=solid(size[0],size[1],Color.RED);for(int style=11;style<PhotoFrame.NAMES.length;style++){
            PhotoFrame.Options o=new PhotoFrame.Options();o.style=style;o.paper=PhotoFrame.defaultPaper(style);o.parameters=false;
            Bitmap out=PhotoFrame.render(source,new PhotoDetails(),o);int exactRed=0;for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++){int pixel=out.getPixel(x,y);assertEquals(255,Color.alpha(pixel));if(pixel==Color.RED)exactRed++;}
            assertTrue("Photo missing or tinted: "+PhotoFrame.NAMES[style],exactRed>size[0]*size[1]/(PhotoFrame.requiredPhotos(style)>1?100:5));assertFalse(source.isRecycled());out.recycle();
        }source.recycle();}
    }
    @Test public void collageUsesEachSelectedPhotoAndFitsDifferentOrientations(){
        Bitmap a=solid(300,200,Color.RED),b=solid(200,300,Color.GREEN),c=solid(400,100,Color.BLUE);
        PhotoFrame.Options o=new PhotoFrame.Options();o.style=55;Bitmap out=PhotoFrame.render(a,new PhotoDetails(),o,Arrays.asList(b,c));
        int x=out.getWidth()/2;assertEquals(Color.RED,out.getPixel(x,110));assertEquals(Color.GREEN,out.getPixel(x,300));assertEquals(Color.BLUE,out.getPixel(x,490));
        assertFalse(a.isRecycled());assertFalse(b.isRecycled());assertFalse(c.isRecycled());a.recycle();b.recycle();c.recycle();out.recycle();
    }
    @Test public void catalogContactSheetUsesActualNativeRenderer()throws Exception{
        android.content.Context context=RuntimeEnvironment.getApplication();BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=4;
        Bitmap coast,forest;try(InputStream in=context.getAssets().open("studio/coast.png")){coast=BitmapFactory.decodeStream(in,null,decode);}try(InputStream in=context.getAssets().open("studio/forest.png")){forest=BitmapFactory.decodeStream(in,null,decode);}
        int columns=6,tw=240,th=260;Bitmap sheet=solid(columns*tw,((PhotoFrame.NAMES.length+columns-1)/columns)*th,0xff191c20);Canvas canvas=new Canvas(sheet);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        PhotoDetails d=new PhotoDetails();for(int i=0;i<PhotoFrame.NAMES.length;i++){PhotoFrame.Options o=new PhotoFrame.Options();o.style=i;o.paper=PhotoFrame.defaultPaper(i);o.parameters=false;o.title="Photography";Bitmap frame=PhotoFrame.render(i%2==0?coast:forest,d,o,Arrays.asList(forest,coast,forest,coast,forest));float scale=Math.min(214f/frame.getWidth(),208f/frame.getHeight());float w=frame.getWidth()*scale,h=frame.getHeight()*scale;float cx=(i%columns)*tw+tw/2f,cy=(i/columns)*th+112;
            canvas.drawBitmap(frame,null,new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2),p);p.setColor(Color.WHITE);p.setTextSize(17);p.setTextAlign(Paint.Align.CENTER);canvas.drawText(String.format(Locale.ROOT,"%02d  %s",i+1,PhotoFrame.NAMES[i]),cx,(i/columns)*th+241,p);frame.recycle();}
        File folder=new File("build/reports/frame-catalog");assertTrue(folder.isDirectory()||folder.mkdirs());try(OutputStream out=new FileOutputStream(new File(folder,"FujiGallery-zh58-catalog.png"))){assertTrue(sheet.compress(Bitmap.CompressFormat.PNG,100,out));}sheet.recycle();coast.recycle();forest.recycle();
    }
}
