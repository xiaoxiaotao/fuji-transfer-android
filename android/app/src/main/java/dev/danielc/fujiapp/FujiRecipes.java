package dev.danielc.fujiapp;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** Personal records with explicit original-metadata provenance. Worker-only disk access. */
final class FujiRecipes {
 static final String[] KEYS={"title","camera","slot","simulation","whiteBalance","wbShift","dynamicRange","highlight","shadow","color","sharpness","noiseReduction","grain","colorChrome","colorChromeBlue","clarity","exposure","notes","source","lens"};
 static final String[] LABELS={"配方名称","记录机型","C1–C7 槽位","胶片模拟","白平衡 / 色温","白平衡偏移（R / B）","动态范围","高光","阴影","色彩","锐度","降噪","颗粒效果","Color Chrome","Color Chrome FX Blue","清晰度","曝光补偿参考","使用场景与笔记","参考来源链接或作者","镜头型号"};
 private final File folder;
 FujiRecipes(Context c){folder=new File(c.getFilesDir(),"fuji-recipes");}
 private File file(String id){if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid recipe ID");return new File(folder,id+".json");}
 void save(JSONObject r)throws IOException,JSONException{synchronized(FujiRecipes.class){if(r.optString("title").trim().isEmpty())throw new IllegalArgumentException("请填写配方名称");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建配方目录");r.put("version",1);r.put("updated",System.currentTimeMillis());AtomicFile f=new AtomicFile(file(r.getString("id")));FileOutputStream out=null;try{out=f.startWrite();out.write(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);}catch(IOException e){if(out!=null)f.failWrite(out);throw e;}}}
 List<JSONObject> list(){synchronized(FujiRecipes.class){List<JSONObject> result=new ArrayList<>();Set<File> paths=new HashSet<>();File[] files=folder.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))paths.add(f);else if(f.getName().endsWith(".json.bak"))paths.add(new File(folder,f.getName().substring(0,f.getName().length()-4)));for(File f:paths)try{JSONObject r=new JSONObject(new String(new AtomicFile(f).readFully(),java.nio.charset.StandardCharsets.UTF_8));if(r.optInt("version")==1&&r.has("id")&&r.has("title"))result.add(r);}catch(Exception ignored){}Collections.sort(result,(a,b)->{int favorite=Boolean.compare(b.optBoolean("favorite"),a.optBoolean("favorite"));return favorite!=0?favorite:Long.compare(b.optLong("updated"),a.optLong("updated"));});return result;}}
 void delete(String id){synchronized(FujiRecipes.class){new AtomicFile(file(id)).delete();}}
 static boolean matches(JSONObject r,String search,boolean favorites){if(favorites&&!r.optBoolean("favorite"))return false;StringBuilder text=new StringBuilder();for(String key:KEYS)text.append(r.optString(key)).append(' ');return text.toString().toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));}
 static String note(JSONObject r){String title=r.optString("title"),film=r.optString("simulation");String note=film.isEmpty()?title:title+" / "+film;return note.length()>48?note.substring(0,48):note;}
 static JSONObject fromPhoto(PhotoDetails details,String name)throws JSONException{JSONObject r=new JSONObject().put("id",UUID.randomUUID().toString()).put("title",details.fuji.film()+" · 原图记录").put("camera",details.camera).put("source","原图："+name).put("origin","maker_note");JSONObject detected=new JSONObject();if(!details.lens.isEmpty()){r.put("lens",details.lens);detected.put("lens",details.lens);}if(!details.camera.isEmpty())detected.put("camera",details.camera);for(Map.Entry<String,String> entry:details.fuji.settings.entrySet()){r.put(entry.getKey(),entry.getValue());detected.put(entry.getKey(),entry.getValue());}r.put("detected",detected);r.put("notes","拍摄参数读取自富士 MakerNote；配方名称、槽位和曝光参考需自行补充。X-E5 的有效白平衡偏移显示相机档位并保留原值；其他机型保留原始单位。");return r;}
 static String shareText(JSONObject r){JSONObject detected=r.optJSONObject("detected");StringBuilder s=new StringBuilder(detected==null?"我的富士配方 · 手动记录\n\n":"我的富士配方 · 原图识别 + 个人记录\n\n");for(int i=0;i<KEYS.length;i++){String v=r.optString(KEYS[i]).trim();if(!v.isEmpty())s.append(LABELS[i]).append("：").append(v).append(detected==null?"":v.equals(detected.optString(KEYS[i],""))?" [原图识别]":" [个人记录]").append('\n');}return s.toString();}
}
