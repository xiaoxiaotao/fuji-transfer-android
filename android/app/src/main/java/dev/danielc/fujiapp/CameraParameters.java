package dev.danielc.fujiapp;
import java.util.Locale;
final class CameraParameters {
 static final String[] LABELS={"光圈","快门","ISO","白平衡","曝光补偿","拍摄模式","对焦模式","相机电量"};
 final long[] values;final long readAt;final boolean fuji;final int stride;
 CameraParameters(long[] raw,long now){this(raw,now,false);}
 CameraParameters(long[] raw,long now,boolean fuji){this.fuji=fuji;if(raw==null||(raw.length!=32&&raw.length!=48&&raw.length!=56))throw new IllegalArgumentException("Incomplete parameter snapshot");stride=raw.length/8;values=raw.clone();readAt=now;}
 int status(int i){return (int)values[i*stride];}
 int response(int i){return (int)values[i*stride+1];}
 long value(int i){return values[i*stride+2];}
 boolean writable(int i){return status(i)==0&&values[i*stride+3]==1;}
 int code(int i){return stride>=6?(int)values[i*stride+4]:new int[]{0x5007,0x500d,0x500f,0x5005,0x5010,0x500e,0x500a,0x5001}[i];}
 String access(int i){return values[i*stride+3]==3?"遥控状态事件（只读）":values[i*stride+3]==2?"未查询（直接读值）":writable(i)?"相机标记可写":"相机标记只读";}
 int payloadLength(int i){return stride>=7?(int)values[i*stride+6]:-1;}
 int readCount(){int count=0;for(int i=0;i<8;i++)if(status(i)==0)count++;return count;}
 String summary(){int count=readCount();return count==0?"未读到参数 · 当前模式可能仅开放图库传图。点击卡片查看响应，或复制诊断。":"已读取 "+count+" / 8 项 · 未开放的属性显示具体原因";}
 int failure(){for(int i=0;i<8;i++){int rc=status(i);if(rc!=0&&rc!=-7&&rc!=-8&&rc!=-9)return rc;}return 0;}
 String display(int i){
  int rc=status(i);long v=value(i);
  if(rc!=0)return rc==-8&&(response(i)==0x200a||response(i)==0x2005)?"此连接不支持":rc==-7?(payloadLength(i)==0?"此模式未提供数值":"格式待适配"):rc==-9?"未完成读取":rc==-8&&response(i)==0x2019?"相机忙碌":"读取失败";
  switch(i){
   case 0:if(code(i)==0xd218)return FujiExposure.aperture(v);return v>0?String.format(Locale.CHINA,"f/%.1f",v/100.0):"未提供有效光圈";
   case 1:if(code(i)==0xd219)return FujiExposure.legacy(v);if(fuji)return FujiExposure.display(v);if(v==0xffffffffL)return "相机返回特殊值";if(v<=0)return "未提供有效快门";return v<10000?String.format(Locale.CHINA,"1/%.0f s",10000.0/v):String.format(Locale.CHINA,"%.2f s",v/10000.0);
   case 2:return (v==0xffff||fuji&&(v==-1||v==0xffffffffL))?"自动 ISO":v>0?String.valueOf(v):"未提供有效 ISO";
   case 3:switch((int)v){case 1:return "手动";case 2:return "自动";case 3:return "一键白平衡";case 4:return "日光";case 5:return "荧光灯";case 6:return "钨丝灯";case 7:return "闪光灯";case 8:return "阴天";case 9:return "阴影";default:return "模式 "+v;}
   case 4:return String.format(Locale.CHINA,"%+.1f EV",v/1000.0);
   case 5:switch((int)v){case 1:return "M · 手动";case 2:return "P · 程序自动";case 3:return "A · 光圈优先";case 4:return "S · 快门优先";default:return "模式 "+v;}
   case 6:if(fuji&&v==0x8001)return "AF-S · 单次对焦";if(fuji&&v==0x8002)return "AF-C · 连续对焦";return v==1?"手动对焦":v==2?"自动对焦":v==3?"自动微距":"模式 "+v;
   case 7:return v>=0&&v<=100?v+"%":"相机返回 "+v;
   default:return "—";
  }
 }
}
