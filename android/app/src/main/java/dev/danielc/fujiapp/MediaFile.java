package dev.danielc.fujiapp;
import java.util.Locale;
/** Stable file classification shared by the offline grid, filters, and sharing. */
final class MediaFile {
 static int kind(String name){String n=name.toLowerCase(Locale.ROOT);return n.endsWith(".mov")||n.endsWith(".mp4")?3:n.endsWith(".raf")?2:1;}
 static boolean supported(String name){String n=name.toLowerCase(Locale.ROOT);return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".heif")||n.endsWith(".heic")||n.endsWith(".raf")||n.endsWith(".mp4")||n.endsWith(".mov");}
 static String mime(String name){String n=name.toLowerCase(Locale.ROOT);return n.endsWith(".png")?"image/png":n.endsWith(".webp")?"image/webp":n.endsWith(".gif")?"image/gif":n.endsWith(".bmp")?"image/bmp":n.endsWith(".avif")?"image/avif":n.endsWith(".mov")?"video/quicktime":n.endsWith(".mp4")?"video/mp4":n.endsWith(".heif")||n.endsWith(".heic")?"image/heif":n.endsWith(".raf")?"application/octet-stream":"image/jpeg";}
 static boolean matches(String name,int kind,String search){return (kind==0||kind(name)==kind)&&name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));}
}
