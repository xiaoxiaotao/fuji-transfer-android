package dev.danielc.fujiapp;
import java.util.*;
/** Byte-weighted processing progress; network bytes never include skipped files. */
final class TransferMeter {
 private final Map<Integer,Long> sizes=new HashMap<>();
 private final Set<Integer> finished=new HashSet<>();
 private int active,percent,totalCount;
 private long network,lastBytes,lastTime;
 private double rate;
 TransferMeter(int count){totalCount=count;}
 synchronized void expect(int handle,long size){if(size>0)sizes.put(handle,size);}
 synchronized void start(int handle){active=handle;percent=0;}
 synchronized void progress(int value,long now){int next=Math.max(percent,Math.min(100,Math.max(0,value)));long size=sizes.containsKey(active)?sizes.get(active):0;network+=size*next/100-size*percent/100;percent=next;if(lastTime==0){lastTime=now;lastBytes=network;}else if(now-lastTime>=500){double sample=(network-lastBytes)*1000.0/(now-lastTime);rate=rate==0?sample:rate*0.7+sample*0.3;lastTime=now;lastBytes=network;}}
 synchronized void finish(int handle,boolean downloaded){if(downloaded&&handle==active){long size=sizes.containsKey(active)?sizes.get(active):0;network+=size-size*percent/100;}finished.add(handle);percent=0;active=0;}
 synchronized long total(){long total=0;for(long size:sizes.values())total+=size;return total;}
 synchronized long processed(){long done=0;for(int id:finished)done+=sizes.containsKey(id)?sizes.get(id):0;if(!finished.contains(active))done+=(sizes.containsKey(active)?sizes.get(active):0)*percent/100;return done;}
 synchronized boolean completeSizes(){return sizes.size()==totalCount;}
 synchronized int progress(){return completeSizes()&&total()>0?(int)Math.min(100,processed()*100/total()):(finished.size()*100+percent)/Math.max(1,totalCount);}
 synchronized long network(){return network;}
 synchronized double rate(){return rate;}
 synchronized String detail(){String bytes=String.format(java.util.Locale.CHINA,"已传约 %.1f MB",network/1048576.0);if(rate>0)bytes+=String.format(java.util.Locale.CHINA," · 约 %.1f MB/s",rate/1048576.0);if(completeSizes()&&rate>0){long seconds=(long)Math.ceil(Math.max(0,total()-processed())/rate);bytes+=" · 剩余约 "+(seconds>=60?seconds/60+" 分":seconds+" 秒");}else if(!completeSizes())bytes+=" · 总大小统计中";return bytes;}
}
