package dev.danielc.fujiapp;
import java.util.*;
/** Immutable rows keep section headers separate from camera handles. */
final class PhotoGroups {
 static final class Row {final String date;final int handle;final boolean header;final int[] members;
  Row(String date,int handle){this.date=date;this.handle=handle;header=false;members=new int[0];}
  Row(String date,int[] members){this.date=date;this.members=members.clone();header=true;handle=0;}
  long id(){return header?0x100000000L+(date.equals("日期待确认")?0:Long.parseLong(date.replace("/",""))):Integer.toUnsignedLong(handle);}
 }
 static String day(String raw){if(raw==null||raw.length()<8||!PhotoFilter.validDate(raw.substring(0,8)))return "日期待确认";return raw.substring(0,4)+"/"+raw.substring(4,6)+"/"+raw.substring(6,8);}
 static List<Row> build(int[] handles,Map<Integer,String> dates,boolean grouped){
  List<Row> rows=new ArrayList<>();if(!grouped){for(int handle:handles)rows.add(new Row("",handle));return rows;}
  Map<String,List<Integer>> groups=new TreeMap<>(Collections.reverseOrder());
  Map<String,String> normalized=new HashMap<>();
  for(int handle:handles){String raw=dates.get(handle),key=raw==null?"":raw.substring(0,Math.min(8,raw.length()));String date=normalized.get(key);if(date==null){date=day(raw);normalized.put(key,date);}List<Integer> members=groups.get(date);if(members==null){members=new ArrayList<>();groups.put(date,members);}members.add(handle);}
  List<Integer> unknown=groups.get("日期待确认");
  List<String> keys=new ArrayList<>(groups.keySet());keys.remove("日期待确认");if(unknown!=null)keys.add("日期待确认");
  for(String day:keys){List<Integer> values=groups.get(day);int[] members=new int[values.size()];for(int i=0;i<members.length;i++)members[i]=values.get(i);rows.add(new Row(day,members));for(int handle:members)rows.add(new Row(day,handle));}return rows;
 }
}
