package dev.danielc.fujiapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Small shared samples, generated only for recycled cells requested by the UI. Cached bitmaps belong to this cache. */
final class SampleFrameThumbnails {
    private static final LruCache<Integer,Bitmap> CACHE=new LruCache<Integer,Bitmap>(6*1024*1024){@Override protected int sizeOf(Integer key,Bitmap image){return image.getAllocationByteCount();}};
    private static final Map<Integer,List<WeakReference<ImageView>>> WAITING=new HashMap<>();
    private static final List<Bitmap> PHOTOS=new ArrayList<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(job->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);job.run();},"frame-samples"));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    static void bind(Context context,ImageView view,int style){
        view.setTag(style);Bitmap cached=CACHE.get(style);if(cached!=null){view.setImageBitmap(cached);return;}view.setImageDrawable(null);
        synchronized(WAITING){List<WeakReference<ImageView>> requests=WAITING.get(style);if(requests!=null){requests.add(new WeakReference<>(view));return;}requests=new ArrayList<>();requests.add(new WeakReference<>(view));WAITING.put(style,requests);}
        Context app=context.getApplicationContext();WORKER.execute(()->{Bitmap image=null;try{
            if(PHOTOS.isEmpty())for(String asset:new String[]{"studio/forest.png","studio/coast.png","pexels-thevibrantmachine-3066867.jpg"})try(InputStream in=app.getAssets().open(asset)){BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=8;Bitmap raw=BitmapFactory.decodeStream(in,null,decode);if(raw==null)continue;Bitmap small=bounded(raw,320);if(small!=raw)raw.recycle();PHOTOS.add(small);}
            if(!PHOTOS.isEmpty()){PhotoFrame.Options preset=new PhotoFrame.Options();preset.style=style;preset.paper=PhotoFrame.defaultPaper(style);preset.parameters=false;preset.title="Photography";List<Bitmap> extras=new ArrayList<>();for(int i=1;i<6;i++)extras.add(PHOTOS.get((style+i)%PHOTOS.size()));Bitmap rendered=PhotoFrame.render(PHOTOS.get(style%PHOTOS.size()),new PhotoDetails(),preset,extras);image=bounded(rendered,360);if(image!=rendered)rendered.recycle();image.prepareToDraw();CACHE.put(style,image);}
        }catch(Exception|OutOfMemoryError error){android.util.Log.w("frame-samples","Sample unavailable",error);}final Bitmap result=image;
        MAIN.post(()->{List<WeakReference<ImageView>> requests;synchronized(WAITING){requests=WAITING.remove(style);}if(requests!=null&&result!=null)for(WeakReference<ImageView> reference:requests){ImageView target=reference.get();if(target!=null&&Integer.valueOf(style).equals(target.getTag()))target.setImageBitmap(result);}});
        });
    }
    private static Bitmap bounded(Bitmap image,int edge){float scale=Math.min(1,edge/(float)Math.max(image.getWidth(),image.getHeight()));return scale==1?image:Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*scale)),Math.max(1,Math.round(image.getHeight()*scale)),true);}
}
