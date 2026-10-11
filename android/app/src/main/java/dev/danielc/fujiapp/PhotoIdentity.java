package dev.danielc.fujiapp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
final class PhotoIdentity {
 static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 static String key(String camera,String name,String date,long size,int format){if(camera==null||date.isEmpty()||size<=0||name.isEmpty())return null;return hash(camera+"|"+name.length()+":"+name+"|"+date+"|"+size+"|"+format);}
}
