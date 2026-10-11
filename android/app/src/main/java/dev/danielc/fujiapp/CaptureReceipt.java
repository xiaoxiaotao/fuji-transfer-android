package dev.danielc.fujiapp;
import java.util.HashSet;
import java.util.Set;
/** Compares complete camera indexes, not filtered views or file counts alone. */
final class CaptureReceipt {
 private final Set<Integer> before=new HashSet<>();
 private final boolean known;
 CaptureReceipt(int[] handles){known=handles!=null;if(known)for(int h:handles)before.add(h);}
 int added(int[] after){if(!known||after==null)return -1;Set<Integer> fresh=new HashSet<>();for(int h:after)if(!before.contains(h))fresh.add(h);return fresh.size();}
 String summary(int accepted,int[] after){if(accepted==0)return "图库已刷新。";int count=added(after);return count>0?"快门请求已接受 "+accepted+" 次 · 图库新增 "+count+" 个文件\nRAW + JPEG 可能对应同一次拍摄。":count==0?"快门请求已接受 "+accepted+" 次，但尚未发现新增文件。请在相机上核对，勿因未确认而重复拍摄。":"快门请求已接受 "+accepted+" 次，照片保存结果未能核验。请在相机上核对。";}
}
