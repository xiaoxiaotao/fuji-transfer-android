package dev.danielc.fujiapp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;

/** Stable template IDs, independent of names and category positions. */
final class FrameCollection {
    static final String STORE="frame_collection";
    private final SharedPreferences prefs;
    FrameCollection(Context context){prefs=context.getSharedPreferences(STORE,0);}
    private boolean valid(int id){return id>=0&&id<PhotoFrame.NAMES.length;}
    int[] list(boolean favorites){ArrayList<Integer> ids=new ArrayList<>();try{JSONArray values=new JSONArray(prefs.getString(favorites?"favorites":"recent","[]"));for(int i=0;i<values.length();i++){int id=values.optInt(i,-1);if(valid(id)&&!ids.contains(id)&&ids.size()<(favorites?PhotoFrame.NAMES.length:12))ids.add(id);}}catch(Exception ignored){}int[] result=new int[ids.size()];for(int i=0;i<result.length;i++)result[i]=ids.get(i);return result;}
    boolean contains(int id){for(int value:list(true))if(value==id)return true;return false;}
    boolean toggle(int id){if(!valid(id))return false;boolean adding=!contains(id);JSONArray values=new JSONArray();if(adding)values.put(id);for(int value:list(true))if(value!=id)values.put(value);prefs.edit().putString("favorites",values.toString()).apply();return adding;}
    void used(int id){if(!valid(id))return;JSONArray values=new JSONArray();values.put(id);int count=1;for(int value:list(false))if(value!=id&&count++<12)values.put(value);prefs.edit().putString("recent",values.toString()).apply();}
}
