package dev.danielc.fujiapp;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import org.json.JSONObject;
import java.io.File;
import java.util.*;
/** App-owned files only. Durable URI index includes both MediaStore and legacy private files. */
final class SavedLibrary extends SQLiteOpenHelper {
 static final String CREATE_SQL="CREATE TABLE photos (_id INTEGER PRIMARY KEY AUTOINCREMENT, target TEXT NOT NULL UNIQUE, name TEXT NOT NULL, size INTEGER NOT NULL, saved INTEGER NOT NULL)";
 private static SavedLibrary instance;
 private static final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
 static long revision(){return revision.get();}
 static synchronized SavedLibrary get(Context c){if(instance==null)instance=new SavedLibrary(c.getApplicationContext());return instance;}
 private final Context context;private boolean imported;
 private SavedLibrary(Context c){super(c,"saved-library.db",null,1);context=c;setWriteAheadLoggingEnabled(true);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL(CREATE_SQL);}
 @Override public void onUpgrade(SQLiteDatabase db,int before,int after){throw new IllegalStateException("Unsupported library migration");}
 static final class Photo {final long id,size,saved;final String name,target;Photo(long id,String target,String name,long size,long saved){this.id=id;this.target=target;this.name=name;this.size=size;this.saved=saved;}Uri uri(){return target.startsWith("content:")||target.startsWith("file:")?Uri.parse(target):Uri.fromFile(new File(target));}int kind(){return MediaFile.kind(name);}String mime(){return MediaFile.mime(name);}}
 void record(String target,String name,long size,long saved){if(target==null||target.isEmpty())return;ContentValues values=new ContentValues();values.put("target",target);values.put("name",name);values.put("size",size);values.put("saved",saved);if(getWritableDatabase().insertWithOnConflict("photos",null,values,SQLiteDatabase.CONFLICT_IGNORE)!=-1)revision.incrementAndGet();}
 // Import previous successful receipts. This also recovers a publish whose index write failed.
 synchronized void importPrevious(){if(imported)return;for(Object value:context.getSharedPreferences("photo_receipts",0).getAll().values()){if(!(value instanceof String))continue;try{JSONObject row=new JSONObject((String)value);String target=row.getString("target"),name;long saved=0;Uri uri=target.startsWith("content:")?Uri.parse(target):null;if(uri!=null){try(Cursor c=context.getContentResolver().query(uri,new String[]{android.provider.MediaStore.MediaColumns.DISPLAY_NAME,android.provider.MediaStore.MediaColumns.DATE_ADDED},null,null,null)){if(c==null||!c.moveToFirst())continue;name=c.getString(0);saved=c.getLong(1)*1000;}}else{File f=new File(target);if(!f.isFile())continue;name=f.getName();saved=f.lastModified();}record(target,name,row.optLong("size"),saved);}catch(Exception ignored){}}
  File root=android.os.Build.VERSION.SDK_INT>=29?context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES):android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES);if(root==null)root=new File(context.getFilesDir(),"Pictures");File[] files=new File(root,"fudge").listFiles();if(files!=null)for(File f:files)if(f.isFile()&&f.length()>0&&MediaFile.supported(f.getName()))record(f.getAbsolutePath(),f.getName(),f.length(),f.lastModified());
  imported=true;
 }
 List<Photo> list(){List<Photo> photos=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM photos ORDER BY saved DESC, _id DESC",null)){while(c.moveToNext())photos.add(new Photo(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4)));}return photos;}
 // A document picker and MediaStore can point to the same physical photo.
 static String sourceKey(Uri uri){
  if("com.android.providers.media.documents".equals(uri.getAuthority()))try{String id=android.provider.DocumentsContract.getDocumentId(uri);if(id.startsWith("image:"))return "media-image:"+id.substring(6);}catch(IllegalArgumentException ignored){}
  if("media".equals(uri.getAuthority())){java.util.List<String> path=uri.getPathSegments();if(path.size()==4&&(path.get(0).equals("external")||path.get(0).equals("external_primary"))&&path.get(1).equals("images")&&path.get(2).equals("media"))return "media-image:"+path.get(3);}
  return uri.toString();
 }
 boolean exists(Photo photo){try{if("file".equals(photo.uri().getScheme()))return new File(photo.uri().getPath()).isFile();try(Cursor c=context.getContentResolver().query(photo.uri(),new String[]{android.provider.MediaStore.MediaColumns.SIZE},null,null,null)){return c!=null&&c.moveToFirst();}}catch(Exception e){return false;}}
}
