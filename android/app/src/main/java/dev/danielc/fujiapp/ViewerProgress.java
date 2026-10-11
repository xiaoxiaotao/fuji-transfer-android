package dev.danielc.fujiapp;

/** One snapshot for the direct full-resolution read; service downloads own their separate caption. */
final class ViewerProgress {
    private int percent,kibps;
    synchronized void reset(){percent=0;kibps=0;}
    synchronized void progress(int value){percent=Math.max(percent,Math.max(0,Math.min(100,value)));}
    synchronized void speed(int value){kibps=Math.max(0,value);}
    synchronized String caption(){return "正在读取原文件 · "+percent+"%"+(kibps>0?String.format(java.util.Locale.CHINA," · %.1f MB/s",kibps/1024.0):"");}
}
