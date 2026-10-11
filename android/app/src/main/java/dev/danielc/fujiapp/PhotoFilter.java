package dev.danielc.fujiapp;
import java.util.Locale;
final class PhotoFilter {
 static boolean matches(int format,String date,boolean onlyNew,String filename,String created,boolean saved){String name=filename.toLowerCase(Locale.ROOT);boolean match=format==0||(format==1&&(name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".heif")||name.endsWith(".heic")))||(format==2&&name.endsWith(".raf"))||(format==3&&(name.endsWith(".mov")||name.endsWith(".mp4")));return match&&(!onlyNew||!saved)&&(date.isEmpty()||created.startsWith(date));}
 static boolean validDate(String date){if(date.isEmpty())return true;if(!date.matches("\\d{8}"))return false;try{java.util.Calendar c=new java.util.GregorianCalendar();c.setLenient(false);c.clear();c.set(Integer.parseInt(date.substring(0,4)),Integer.parseInt(date.substring(4,6))-1,Integer.parseInt(date.substring(6,8)));c.getTime();return true;}catch(Exception e){return false;}}
}
