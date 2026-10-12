package dev.danielc.fujiapp;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** User-authored records, not camera writes or inferred EXIF settings. Worker-only disk access. */
final class FujiRecipes {
 static final String[] KEYS={"title","camera","slot","simulation","whiteBalance","wbShift","dynamicRange","highlight","shadow","color","sharpness","noiseReduction","grain","colorChrome","colorChromeBlue","clarity","exposure","notes","source"};
 static final String[] LABELS={"配方名称","记录机型","C1–C7 槽位","胶片模拟","白平衡 / 色温","白平衡偏移（R / B）","动态范围","高光","阴影","色彩","锐度","降噪","颗粒效果","Color Chrome","Color Chrome FX Blue","清晰度","曝光补偿参考","使用场景与笔记","参考来源链接或作者"};
 private final File folder;
 FujiRecipes(Context c){folder=new File(c.getFilesDir(),"fuji-recipes");}
 private File file(String id){if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid recipe ID");return new File(folder,id+".json");}
 void save(JSONObject r)throws IOException,JSONException{synchronized(FujiRecipes.class){if(r.optString("title").trim().isEmpty())throw new IllegalArgumentException("请填写配方名称");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建配方目录");r.put("version",1);r.put("updated",System.currentTimeMillis());AtomicFile f=new AtomicFile(file(r.getString("id")));FileOutputStream out=null;try{out=f.startWrite();out.write(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);}catch(IOException e){if(out!=null)f.failWrite(out);throw e;}}}
 List<JSONObject> list(){synchronized(FujiRecipes.class){List<JSONObject> result=new ArrayList<>();Set<File> paths=new HashSet<>();File[] files=folder.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))paths.add(f);else if(f.getName().endsWith(".json.bak"))paths.add(new File(folder,f.getName().substring(0,f.getName().length()-4)));for(File f:paths)try{JSONObject r=new JSONObject(new String(new AtomicFile(f).readFully(),java.nio.charset.StandardCharsets.UTF_8));if(r.optInt("version")==1&&r.has("id")&&r.has("title"))result.add(r);}catch(Exception ignored){}Collections.sort(result,(a,b)->{int favorite=Boolean.compare(b.optBoolean("favorite"),a.optBoolean("favorite"));return favorite!=0?favorite:Long.compare(b.optLong("updated"),a.optLong("updated"));});return result;}}
 void delete(String id){synchronized(FujiRecipes.class){new AtomicFile(file(id)).delete();}}
 static boolean matches(JSONObject r,String search,boolean favorites){if(favorites&&!r.optBoolean("favorite"))return false;StringBuilder text=new StringBuilder();for(String key:KEYS)text.append(r.optString(key)).append(' ');return text.toString().toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));}
 static String note(JSONObject r){String title=r.optString("title"),film=r.optString("simulation");String note=film.isEmpty()?title:title+" / "+film;return note.length()>48?note.substring(0,48):note;}
 static String shareText(JSONObject r){StringBuilder s=new StringBuilder("我的富士配方 · 手动记录\n\n");for(int i=0;i<KEYS.length;i++){String v=r.optString(KEYS[i]).trim();if(!v.isEmpty())s.append(LABELS[i]).append("：").append(v).append('\n');}return s.toString();}
}
