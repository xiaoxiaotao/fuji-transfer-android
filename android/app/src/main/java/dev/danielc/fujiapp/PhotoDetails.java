package dev.danielc.fujiapp;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads only the downloaded original. Missing fields are never inferred from the preview. */
final class PhotoDetails {
    FujiMakerNote fuji = new FujiMakerNote();
    final List<String> lines = new ArrayList<>();
    String camera = "", exposure = "", lens="", takenAt="", aperture="", shutter="", iso="", focalLength="";
    static PhotoDetails read(Context context, Uri uri, String name) throws IOException {
        PhotoDetails result = new PhotoDetails();
        result.lines.add("文件：" + name);
        if (Build.VERSION.SDK_INT < 24) {
            result.lines.add("此系统版本暂不支持读取拍摄参数");
            return result;
        }
        try (android.os.ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (fd == null) throw new IOException("无法打开照片");
            android.media.ExifInterface exif = new android.media.ExifInterface(fd.getFileDescriptor());
            result.camera = value(exif, "Model");
            String lens = value(exif, "LensModel"), time = value(exif, "DateTimeOriginal");
            result.takenAt=time;
            String aperture = value(exif, "FNumber"), shutter = value(exif, "ExposureTime");
            shutter = shutter(shutter);result.lens=lens;result.aperture=aperture.isEmpty()?"":"f/"+aperture;result.shutter=shutter.isEmpty()?"":shutter+" s";
            String iso = value(exif, "PhotographicSensitivity");
            if (iso.isEmpty()) iso = value(exif, "ISOSpeedRatings");result.iso=iso;
            add(result, "相机", result.camera); add(result, "镜头", lens); add(result, "拍摄时间", time);
            add(result, "光圈", aperture.isEmpty() ? "" : "f/" + aperture);
            add(result, "快门", shutter.isEmpty() ? "" : shutter + " s"); add(result, "ISO", iso);
            result.focalLength=focalLength(value(exif,"FocalLength"));add(result, "焦距", result.focalLength);
            add(result, "尺寸", value(exif, "ImageWidth") + " × " + value(exif, "ImageLength"));
            result.exposure = (aperture.isEmpty() ? "" : "f/" + aperture + "  ")
                + (shutter.isEmpty() ? "" : shutter + "s  ") + (iso.isEmpty() ? "" : "ISO " + iso);
        }
        try (java.io.InputStream stream = context.getContentResolver().openInputStream(uri)) {
            result.fuji = FujiMakerNote.read(stream);
        } catch (IOException error) {
            android.util.Log.w("fuji-metadata", "Cannot read MakerNote", error);
        }
        result.fuji.applyCameraScale(result.camera);
        if(result.lens.isEmpty()&&!result.fuji.lens.isEmpty()){result.lens=result.fuji.lens;add(result,"镜头",result.lens);}
        result.lines.add(result.fuji.film().isEmpty() ? "胶片模拟：无法识别（未保留富士参数或数值暂未支持）" : "胶片模拟（原图识别）：" + result.fuji.film());
        for (int i=0;i<FujiRecipes.KEYS.length;i++) {
            String setting=result.fuji.settings.get(FujiRecipes.KEYS[i]);
            if (setting!=null && !FujiRecipes.KEYS[i].equals("simulation")) result.lines.add(FujiRecipes.LABELS[i]+"（原图）："+setting);
        }
        for(String unknown:result.fuji.unknown) result.lines.add("未识别参数："+unknown);
        result.lines.add("原图参数不包含自定义配方名称或 C1–C7 槽位；无法据此确认某个网络配方。");
        android.util.Log.i("fuji-metadata", "MakerNote present="+result.fuji.present+", film="+result.fuji.film()+", fields="+result.fuji.settings.size());
        return result;
    }
    static String focalLength(String raw){if(raw.isEmpty())return "";try{double value;if(raw.contains("/")){String[] parts=raw.split("/");value=Double.parseDouble(parts[0])/Double.parseDouble(parts[1]);}else value=Double.parseDouble(raw);if(!Double.isFinite(value)||value<=0)return "";return String.format(java.util.Locale.ROOT,"%.1f",value).replaceAll("\\.0$", "")+" mm";}catch(RuntimeException error){return "";}}
    static String shutter(String raw) {
        try {
            double seconds;
            if(raw.contains("/")){String[] pieces=raw.split("/");seconds=Double.parseDouble(pieces[0])/Double.parseDouble(pieces[1]);}
            else seconds=Double.parseDouble(raw);
            if(!Double.isFinite(seconds)||seconds<=0)return raw;
            if(seconds<0.5)return "1/"+Math.round(1/seconds);
            return String.format(java.util.Locale.ROOT,"%.2f",seconds).replaceAll("0+$", "").replaceAll("\\.$", "");
        }catch(RuntimeException e){return raw;}
    }
    private static String value(android.media.ExifInterface exif, String tag) {
        String text = exif.getAttribute(tag); return text == null ? "" : text.trim();
    }
    private static void add(PhotoDetails result, String label, String text) {
        if (!text.isEmpty() && !text.equals(" × ")) result.lines.add(label + "：" + text);
    }
    String text() { return android.text.TextUtils.join("\n", lines); }
}
