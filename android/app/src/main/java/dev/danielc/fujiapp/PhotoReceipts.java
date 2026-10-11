package dev.danielc.fujiapp;
import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Access only from a worker: receipts are valid only while published files exist. */
final class PhotoReceipts {
 static String hash(String value){return PhotoIdentity.hash(value);}
 static String key(String camera,int handle,JSONObject info){return PhotoIdentity.key(camera,info.optString("filename"),info.optString("dateCreated"),info.optLong("compressedSize"),info.optInt("format_int"));}
 static boolean exists(Context c,String key){if(key==null)return false;String raw=c.getSharedPreferences("photo_receipts",0).getString(key,null);if(raw==null)return false;try{JSONObject record=new JSONObject(raw);String target=record.getString("target");long size=record.getLong("size");if(target.startsWith("content:")){Uri uri=Uri.parse(target);try(android.database.Cursor cursor=c.getContentResolver().query(uri,new String[]{android.provider.MediaStore.MediaColumns.SIZE},null,null,null)){if(cursor==null||!cursor.moveToFirst()||cursor.getLong(0)!=size)return false;}try(android.content.res.AssetFileDescriptor fd=c.getContentResolver().openAssetFileDescriptor(uri,"r")){return fd!=null;}}File file=new File(target);return file.isFile()&&file.canRead()&&file.length()==size;}catch(Exception e){return false;}}
 static String target(Context c,String key){if(key==null)return null;try{String raw=c.getSharedPreferences("photo_receipts",0).getString(key,null);return raw==null?null:new JSONObject(raw).optString("target",null);}catch(Exception e){return null;}}
 static void saved(Context c,String key,String target,long size){if(key==null)return;try{JSONObject record=new JSONObject();record.put("target",target);record.put("size",size);if(!c.getSharedPreferences("photo_receipts",0).edit().putString(key,record.toString()).commit())Frontend.print("文件已保存，但下载标记未能写入");}catch(Exception e){Frontend.print("文件已保存，但下载标记未能写入");}}
}
