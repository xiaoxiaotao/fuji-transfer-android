package dev.danielc.fujiapp;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import java.io.*;
import java.util.Locale;

final class PhotoStorage {
    static File stagingRoot(Context context,boolean privateSave){
        File root=Build.VERSION.SDK_INT>=29||privateSave?context.getExternalFilesDir(Environment.DIRECTORY_PICTURES):Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return root==null?new File(context.getFilesDir(),"Pictures"):root;
    }
    static void validateDownload(File file,long expected)throws IOException{
        if(!file.isFile()||file.length()==0)throw new IOException("下载文件为空，请重试");
        if(expected>0&&file.length()!=expected)throw new IOException("文件未完整下载：应为 "+expected+" 字节，实际 "+file.length()+" 字节，请重试");
    }
    private static void remember(Context c,String target,String name,long size){try{SavedLibrary.get(c).record(target,name,size,System.currentTimeMillis());}catch(RuntimeException e){Frontend.print("文件已保存，照片库索引稍后恢复");}}
    static String publish(Context context,String path)throws IOException{return publish(context,path,MySettings.getSetting(context,"save_private",0)==1);}
    static String publish(Context context,String path,boolean privateSave)throws IOException {
        File source = new File(path);
        if (!source.isFile() || source.length() == 0) throw new IOException("下载文件为空");
        if(privateSave){remember(context,source.getAbsolutePath(),source.getName(),source.length());return source.getAbsolutePath();}
        if (Build.VERSION.SDK_INT < 29) {
            MediaScannerConnection.scanFile(context, new String[]{path}, null, null);
            remember(context,source.getAbsolutePath(),source.getName(),source.length());return source.getAbsolutePath();
        }
        String name=source.getName(), lower=name.toLowerCase(Locale.ROOT);
        boolean video=lower.endsWith(".mov")||lower.endsWith(".mp4");
        boolean photo=MediaFile.kind(name)==1;
        Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:photo?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,MediaFile.mime(name));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?Environment.DIRECTORY_MOVIES:photo?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_DOWNLOADS)+"/Fudge");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri destination=context.getContentResolver().insert(collection,values);
        if(destination==null)throw new IOException("系统未创建保存位置");
        try {
            try(InputStream input=new FileInputStream(source);OutputStream output=context.getContentResolver().openOutputStream(destination)) {
                if(output==null)throw new IOException("无法打开保存位置");
                byte[] buffer=new byte[65536];int read;
                while((read=input.read(buffer))!=-1)output.write(buffer,0,read);
            }
            ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(context.getContentResolver().update(destination,ready,null,null)!=1)throw new IOException("无法完成保存");
        }catch(IOException|RuntimeException e){try{context.getContentResolver().delete(destination,null,null);}catch(RuntimeException cleanup){e.addSuppressed(cleanup);}throw e;}
        remember(context,destination.toString(),name,source.length());return destination.toString();
    }
}
