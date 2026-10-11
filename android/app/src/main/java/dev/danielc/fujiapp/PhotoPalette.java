package dev.danielc.fujiapp;
import android.graphics.Bitmap;
import android.graphics.Color;
/** Representative, separated colors sampled from the whole photograph. */
final class PhotoPalette {
 static int[] colors(Bitmap photo){int[] counts=new int[512],red=new int[512],green=new int[512],blue=new int[512];
  for(int y=0;y<photo.getHeight();y+=Math.max(1,photo.getHeight()/64))for(int x=0;x<photo.getWidth();x+=Math.max(1,photo.getWidth()/64)){int pixel=photo.getPixel(x,y),r=Color.red(pixel),g=Color.green(pixel),b=Color.blue(pixel),bin=(r/32)*64+(g/32)*8+b/32;counts[bin]++;red[bin]+=r;green[bin]+=g;blue[bin]+=b;}
  int[] result=new int[5];boolean[] used=new boolean[512];
  for(int slot=0;slot<5;slot++){int best=-1;double bestScore=-1;
   for(int bin=0;bin<512;bin++){if(counts[bin]==0||used[bin])continue;int r=red[bin]/counts[bin],g=green[bin]/counts[bin],b=blue[bin]/counts[bin];double distance=slot==0?1:Double.MAX_VALUE;for(int n=0;n<slot;n++){int dr=r-Color.red(result[n]),dg=g-Color.green(result[n]),db=b-Color.blue(result[n]);distance=Math.min(distance,dr*dr+dg*dg+db*db);}double chroma=(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))/255.0,score=counts[bin]*(.35+chroma)*distance;if(score>bestScore){best=bin;bestScore=score;}}
   if(best<0){result[slot]=result[Math.max(0,slot-1)];continue;}used[best]=true;result[slot]=Color.rgb(red[best]/counts[best],green[best]/counts[best],blue[best]/counts[best]);
  }return result;
 }
}
