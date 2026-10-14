package dev.danielc.fujiapp;

import java.io.*;
import java.util.*;

/** Bounded JPEG/Exif reader. Fuji offsets are relative to the little-endian MakerNote. */
final class FujiMakerNote {
    final Map<String,String> settings = new LinkedHashMap<>();
    final List<String> unknown = new ArrayList<>();
    boolean present;
    String lens="";private long[] wbRaw;
    void applyCameraScale(String camera){if(wbRaw==null)return;long red=wbRaw[0],blue=wbRaw[1];if("X-E5".equals(camera)&&red%20==0&&blue%20==0&&Math.abs(red)<=180&&Math.abs(blue)<=180)settings.put("wbShift","R "+signed(red/20)+" / B "+signed(blue/20)+"（相机档位；原始 "+red+" / "+blue+"）");}
    String film(){return settings.containsKey("simulation")?settings.get("simulation"):"";}
    static FujiMakerNote read(InputStream source) throws IOException {
        if(source==null)return new FujiMakerNote();
        DataInputStream in=new DataInputStream(new BufferedInputStream(source));
        try {
            if(in.readUnsignedShort()!=0xffd8)return new FujiMakerNote();
            int consumed=2;
            while(consumed<2*1024*1024){
                if(in.readUnsignedByte()!=255)return new FujiMakerNote();
                int marker;do{marker=in.readUnsignedByte();consumed++;}while(marker==255&&consumed<2*1024*1024);
                consumed++;
                if(marker==0xda||marker==0xd9)return new FujiMakerNote();
                if(marker==0x01||(marker>=0xd0&&marker<=0xd7))continue;
                int length=in.readUnsignedShort()-2;consumed+=2;
                if(length<0||consumed+length>2*1024*1024)return new FujiMakerNote();
                byte[] segment=new byte[length];in.readFully(segment);consumed+=length;
                if(marker==0xe1&&length>=14&&segment[0]=='E'&&segment[1]=='x'&&segment[2]=='i'&&segment[3]=='f'&&segment[4]==0&&segment[5]==0){
                    FujiMakerNote result=parseExif(segment);if(result.present||!result.lens.isEmpty())return result;
                }
            }
        } catch(EOFException truncated){return new FujiMakerNote();}
        return new FujiMakerNote();
    }
    static FujiMakerNote parseExif(byte[] bytes){
        FujiMakerNote out=new FujiMakerNote();
        try{
            Reader t=new Reader(bytes,6,bytes.length-6,bytes.length>7&&bytes[6]=='I'&&bytes[7]=='I');
            if(!((bytes[6]=='I'&&bytes[7]=='I')||(bytes[6]=='M'&&bytes[7]=='M'))||t.u16(2)!=42)return out;
            Entry exif=t.entry(t.u32(4),0x8769);
            if(exif==null||exif.type!=4||exif.count!=1)return out;
            Entry lensEntry=t.entry(exif.number(0),0xa434);
            if(lensEntry!=null&&lensEntry.type==2&&lensEntry.count>0&&lensEntry.count<=256){int pos=lensEntry.position();String model=new String(bytes,t.base+pos,(int)lensEntry.count,java.nio.charset.StandardCharsets.US_ASCII);out.lens=model.replace("\u0000","").trim();}
            Entry maker=t.entry(exif.number(0),0x927c);
            if(maker==null||maker.type!=7||maker.count<12)return out;
            int start=maker.position();t.check(start,maker.count);
            int absolute=t.base+start;byte[] signature="FUJIFILM".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            for(int i=0;i<8;i++)if(bytes[absolute+i]!=signature[i])return out;
            Reader f=new Reader(bytes,absolute,(int)maker.count,true);
            long dir=f.u32(8);int count=f.u16(dir);if(count>512)return out;f.check(dir+2,12L*count);
            Map<Integer,long[]> tags=new HashMap<>();
            for(int i=0;i<count;i++){
                long pos=dir+2+12L*i;int tag=f.u16(pos);Entry e=new Entry(f,pos);
                if((e.type==3||e.type==4||e.type==9)&&e.count>=1&&e.count<=2){long[] values=new long[(int)e.count];for(int j=0;j<values.length;j++)values[j]=e.number(j);tags.put(tag,values);}
            }
            out.present=true;out.decode(tags);
        }catch(IndexOutOfBoundsException|IllegalArgumentException invalid){return new FujiMakerNote();}
        return out;
    }
    private void decode(Map<Integer,long[]> t){
        enumValue(t,0x1401,"simulation",new long[]{0,0x100,0x110,0x120,0x130,0x200,0x300,0x400,0x500,0x501,0x600,0x700,0x800,0x900,0xa00,0xb00},new String[]{"Provia","F1 Studio Portrait","F1a Studio Portrait","Astia","F1c Studio Portrait","Velvia","F3 Studio Portrait Ex","F4 Velvia","Pro Neg. Std","Pro Neg. Hi","Classic Chrome","Eterna","Classic Negative","Eterna Bleach Bypass","Nostalgic Negative","Reala ACE"});
        long sat=value(t,0x1003);String mono=null;
        if(sat>=0x300&&sat<=0x303)mono=new String[]{"Monochrome","Monochrome + R","Monochrome + Ye","Monochrome + G"}[(int)sat-0x300];
        if(sat==0x310)mono="Sepia";
        if(sat>=0x500&&sat<=0x503)mono=new String[]{"Acros","Acros + R","Acros + Ye","Acros + G"}[(int)sat-0x500];
        if(mono!=null)settings.put("simulation",mono);
        else enumValue(t,0x1003,"color",new long[]{0,0x80,0x100,0xc0,0xe0,0x180,0x200,0x400,0x4c0,0x4e0,0x8000},new String[]{"0","+1","+2","+3","+4","-1","Low（旧机型）","-2","-3","-4","由胶片模拟决定"});
        enumValue(t,0x1002,"whiteBalance",new long[]{0,1,2,0x100,0x200,0x300,0x301,0x302,0x303,0x304,0x400,0x500,0x600,0xf00,0xf01,0xf02,0xf03,0xf04,0xff0},new String[]{"自动","自动 · 白优先","自动 · 氛围优先","日光","阴天","日光荧光灯","昼白荧光灯","白色荧光灯","暖白荧光灯","暖色荧光灯","白炽灯","闪光灯","水下","自定义 1","自定义 2","自定义 3","自定义 4","自定义 5","色温"});
        if(value(t,0x1002)==0xff0&&t.containsKey(0x1005))settings.put("whiteBalance","色温 "+value(t,0x1005)+" K");
        // Keep raw units: older/newer models use different camera-step scales.
        if(t.containsKey(0x100a)&&t.get(0x100a).length==2){long[] wb=t.get(0x100a);wbRaw=wb.clone();settings.put("wbShift","R "+signed(wb[0])+" / B "+signed(wb[1])+"（MakerNote 原始单位）");}
        enumValue(t,0x1001,"sharpness",new long[]{0,1,2,3,4,5,6,0x82,0x84,0x8000},new String[]{"-4","-3","-2","0","+2","+3","+4","-1","+1","由胶片模拟决定"});
        enumValue(t,0x100e,"noiseReduction",new long[]{0,0x100,0x180,0x1c0,0x1e0,0x200,0x280,0x2c0,0x2e0},new String[]{"0","+2","+1","+3","+4","-2","-1","-3","-4"});
        for(int tag:new int[]{0x1040,0x1041})if(t.containsKey(tag))settings.put(tag==0x1040?"shadow":"highlight",decimal(-value(t,tag)/16.0));
        if(t.containsKey(0x100f))settings.put("clarity",decimal(value(t,0x100f)/1000.0));
        enumValue(t,0x1047,"grain",new long[]{0,32,64},new String[]{"关闭","弱","强"});
        if(settings.containsKey("grain")&&!settings.get("grain").equals("关闭")){long size=value(t,0x104c);if(size==16||size==32)settings.put("grain",settings.get("grain")+" / "+(size==16?"小":"大"));}
        enumValue(t,0x1048,"colorChrome",new long[]{0,32,64},new String[]{"关闭","弱","强"});
        enumValue(t,0x104e,"colorChromeBlue",new long[]{0,32,64},new String[]{"关闭","弱","强"});
        if(t.containsKey(0x1403))settings.put("dynamicRange","DR "+value(t,0x1403)+"%");
        else enumValue(t,0x1402,"dynamicRange",new long[]{0,1,0x100,0x200,0x201,0x8000},new String[]{"自动","手动（比例未记录）","100%","230%","400%","由胶片模拟决定"});
        if(!settings.containsKey("dynamicRange"))enumValue(t,0x1400,"dynamicRange",new long[]{1,3},new String[]{"标准（MakerNote）","宽动态范围（比例未记录）"});
    }
    private void enumValue(Map<Integer,long[]> t,int tag,String key,long[] values,String[] names){if(!t.containsKey(tag))return;long raw=value(t,tag);for(int i=0;i<values.length;i++)if(raw==values[i]){settings.put(key,names[i]);return;}unknown.add(String.format(Locale.ROOT,"0x%04X = %d（暂未支持）",tag,raw));}
    private static long value(Map<Integer,long[]> t,int tag){return t.containsKey(tag)?t.get(tag)[0]:Long.MIN_VALUE;}
    private static String signed(long n){return n>0?"+"+n:Long.toString(n);}
    private static String decimal(double n){String s=java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();return n>0?"+"+s:s;}
    private static final class Reader {
        final byte[] data;final int base,length;final boolean little;
        Reader(byte[] b,int base,int len,boolean le){if(base<0||len<0||(long)base+len>b.length)throw new IllegalArgumentException();data=b;this.base=base;length=len;little=le;}
        void check(long p,long n){if(p<0||n<0||p>length||n>length-p)throw new IndexOutOfBoundsException();}
        int u16(long p){check(p,2);int a=data[base+(int)p]&255,b=data[base+(int)p+1]&255;return little?a|(b<<8):(a<<8)|b;}
        long u32(long p){check(p,4);return little?(long)u16(p)|((long)u16(p+2)<<16):((long)u16(p)<<16)|u16(p+2);}
        Entry entry(long p,int tag){int count=u16(p);if(count>512)throw new IllegalArgumentException();check(p+2,12L*count);for(int i=0;i<count;i++){long at=p+2+12L*i;if(u16(at)==tag)return new Entry(this,at);}return null;}
    }
    private static final class Entry {
        final Reader r;final long at,count;final int type;
        Entry(Reader r,long at){this.r=r;this.at=at;type=r.u16(at+2);count=r.u32(at+4);}
        int size(){switch(type){case 3:return 2;case 4:case 9:return 4;case 2:case 7:return 1;default:throw new IllegalArgumentException();}}
        int position(){long bytes=count*size(),p=bytes<=4?at+8:r.u32(at+8);r.check(p,bytes);return (int)p;}
        long number(int index){if(index<0||index>=count)throw new IllegalArgumentException();long p=position()+(long)index*size();return type==3?r.u16(p):type==9?(int)r.u32(p):r.u32(p);}
    }
}
