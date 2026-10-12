package dev.danielc.fujiapp;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** Small versioned records; called on a worker. Source photos are never modified. */
final class FrameDrafts {
 static final String UPDATES="frame_draft_updates";
 private final File folder;private final Context context;
 FrameDrafts(Context c){context=c.getApplicationContext();folder=new File(c.getFilesDir(),"frame-drafts");}
 static JSONObject snapshot(String id,String source,String name,PhotoFrame.Options o,List<android.net.Uri> extras,int quality)throws JSONException{
  JSONObject r=new JSONObject();r.put("version",1);r.put("id",id);r.put("source",source);r.put("name",name);r.put("updated",System.currentTimeMillis());r.put("style",o.style);r.put("paper",o.paper);r.put("spacing",o.spacing);r.put("blur",o.blur);r.put("corners",o.corners);r.put("shadow",o.shadow);r.put("parameters",o.parameters);r.put("sidebar",o.sidebarRight);r.put("title",o.title);r.put("author",o.author);r.put("film",o.filmNote);r.put("quality",quality);JSONArray a=new JSONArray();for(android.net.Uri uri:extras)a.put(uri.toString());r.put("extras",a);return r;
 }
 static void restore(JSONObject r,PhotoFrame.Options o){o.style=bound(r.optInt("style",8),0,PhotoFrame.NAMES.length-1);o.paper=bound(r.optInt("paper",0),0,4);o.spacing=bound(r.optInt("spacing",6),3,15);o.blur=bound(r.optInt("blur",4),1,8);o.corners=bound(r.optInt("corners",18),0,36);o.shadow=bound(r.optInt("shadow",18),0,32);o.parameters=r.optBoolean("parameters",true);o.sidebarRight=r.optBoolean("sidebar",false);o.title=r.optString("title");o.author=r.optString("author");o.filmNote=r.optString("film");}
 private static int bound(int n,int min,int max){return Math.max(min,Math.min(max,n));}
 private File file(String id){if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid draft ID");return new File(folder,id+".json");}
 synchronized void save(JSONObject r)throws IOException,JSONException{if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create draft folder");AtomicFile f=new AtomicFile(file(r.getString("id")));FileOutputStream out=null;try{out=f.startWrite();out.write(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);context.getSharedPreferences(UPDATES,0).edit().putLong("changed",System.nanoTime()).apply();}catch(IOException e){if(out!=null)f.failWrite(out);throw e;}}
 List<JSONObject> list(){List<JSONObject> result=new ArrayList<>();File[] files=folder.listFiles((dir,name)->name.endsWith(".json")||name.endsWith(".json.bak"));Set<File> records=new LinkedHashSet<>();if(files!=null)for(File file:files)records.add(file.getName().endsWith(".bak")?new File(folder,file.getName().substring(0,file.getName().length()-4)):file);for(File file:records)try{JSONObject r=new JSONObject(new String(new AtomicFile(file).readFully(),java.nio.charset.StandardCharsets.UTF_8));if(r.optInt("version")==1&&r.has("source")&&r.has("id"))result.add(r);}catch(Exception ignored){}Collections.sort(result,(a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));return result;}
 void delete(String id){new AtomicFile(file(id)).delete();}
 static void open(Context c,JSONObject r){c.startActivity(new android.content.Intent(c,PhotoStudio.class).putExtra("target",r.optString("source")).putExtra("name",r.optString("name","照片")).putExtra("draft",r.toString()));}
}
