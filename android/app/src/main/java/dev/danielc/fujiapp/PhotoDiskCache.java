package dev.danielc.fujiapp;
import android.content.Context;
import java.io.*;
import java.util.*;
/** App-private thumbnail/original caches, scoped by actual photo identity. */
final class PhotoDiskCache {
 private static File dir(Context c){File d=new File(c.getCacheDir(),"camera-images");d.mkdirs();return d;}
 static synchronized File file(Context c,String key,String suffix){if(key==null)return null;return new File(dir(c),PhotoIdentity.hash(key)+suffix);}
 static synchronized byte[] thumb(Context c,String key){File f=file(c,key,".thumb");if(f==null||!f.isFile()||f.length()>4*1024*1024)return null;try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}f.setLastModified(System.currentTimeMillis());return out.toByteArray();}catch(IOException e){return null;}}
 static synchronized void thumb(Context c,String key,byte[] bytes){if(key==null||bytes.length==0||bytes.length>4*1024*1024)return;File file=file(c,key,".thumb"),part=new File(file.getPath()+".part");try{try(OutputStream out=new FileOutputStream(part)){out.write(bytes);}if(!part.renameTo(file))part.delete();trim(c,file);}catch(IOException e){part.delete();}}
 static synchronized void trim(Context c,File keep){File[] files=dir(c).listFiles();if(files==null)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long bytes=0;for(File f:files){if(f.getName().endsWith(".part")){if(System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();}else bytes+=f.length();}for(File f:files){if(bytes<=256L*1024*1024)break;if(f.equals(keep)||f.getName().endsWith(".part"))continue;long size=f.length();if(f.delete())bytes-=size;}}
 static synchronized long clearThumbnails(Context c){long removed=0;File[] files=dir(c).listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".thumb")){long size=f.length();if(f.delete())removed+=size;}return removed;}
}
