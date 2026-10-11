package dev.danielc.fujiapp;
/** Immutable per-connection snapshot; absent fields never inherit previous cameras. */
final class CameraInfo {
 final String name,model,manufacturer,version,serial,address;
 final int transport,count;
 final long readyAt;
 CameraInfo(String[] values,int transport,int count,long readyAt){name=value(values,0);model=value(values,1);manufacturer=value(values,2);version=value(values,3);serial=value(values,4);address=value(values,5);this.transport=transport;this.count=count;this.readyAt=readyAt;}
 static String value(String[] values,int index){return values==null||index>=values.length||values[index]==null?"":values[index].trim();}
 boolean isFuji(){return manufacturer.toUpperCase(java.util.Locale.ROOT).contains("FUJI")||model.toUpperCase(java.util.Locale.ROOT).startsWith("FUJIFILM")||model.toUpperCase(java.util.Locale.ROOT).startsWith("X-")||model.toUpperCase(java.util.Locale.ROOT).startsWith("GFX");}
 String title(){return !model.isEmpty()?model:!name.isEmpty()?name:"已连接相机";}
 static String available(String value){return value.isEmpty()?"相机未提供":value;}
 String link(){switch(transport){case 1:return "Wi-Fi · 电脑自动保存";case 2:return "Wi-Fi · 无线联机";case 3:return "Wi-Fi · 传统传图";case 10:return "Wi-Fi · XApp 传图";case 4:return "USB · 读卡模式";case 5:return "USB · 联机拍摄";case 6:return "USB · RAW 转换";case 7:return "USB · PTP";case 8:return "USB · 网络摄像头";case 9:return "USB · 视频拍摄";default:return "连接方式未提供";}}
 String elapsed(long now){long seconds=Math.max(0,now-readyAt)/1000;return seconds<60?seconds+" 秒":seconds/60+" 分 "+seconds%60+" 秒";}
}
