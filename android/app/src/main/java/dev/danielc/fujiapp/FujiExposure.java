package dev.danielc.fujiapp;
import java.util.Locale;
/** Fuji ExposureTime (0x500d) wire values, independently decoded here.
 * Protocol reference: gphoto/libgphoto2 camlibs/ptp2/config.c, Fuji_New_ShutterSpeed.
 * Unknown values remain visible as raw codes; never apply standard PTP units to Fuji.
 */
final class FujiExposure {
 private static final long[] CODES={64000180,64000150,64000120,64000090,64000060,64000030,64000000,50796833,40317473,32000000,25398416,20158736,16000000,12699208,10079368,8000000,6349604,5039684,4000000,3174802,2519842,2000000,1587401,1259921,1000000,793700,629960,500000,396850,314980,250000,198425,157490,125000,99212,78745,62500,49606,39372,31250,24803,19686,15625,12401,9843,7812,6200,4921,3906,3100,2460,1953,1550,1230,976,775,615,488,387,307,244,193,153,122,96,76,61,48,38,30};
 private static final String[] LABELS={"60 分钟","30 分钟","15 分钟","8 分钟","4 分钟","2 分钟","60 s","50 s","40 s","30 s","25 s","20 s","15 s","13 s","10 s","8 s","6 s","5 s","4 s","3 s","2.5 s","2 s","1.6 s","1.3 s","1 s","0.8 s","0.6 s","1/2 s","0.4 s","1/3 s","1/4 s","1/5 s","1/6 s","1/8 s","1/10 s","1/13 s","1/15 s","1/20 s","1/25 s","1/30 s","1/40 s","1/50 s","1/60 s","1/80 s","1/100 s","1/125 s","1/160 s","1/200 s","1/250 s","1/320 s","1/400 s","1/500 s","1/640 s","1/800 s","1/1000 s","1/1250 s","1/1600 s","1/2000 s","1/2500 s","1/3200 s","1/4000 s","1/5000 s","1/6400 s","1/8000 s","1/10000 s","1/13000 s","1/16000 s","1/20000 s","1/25000 s","1/32000 s"};
 static String display(long value){
  if(value==0xffffffffL||value==-1)return "B · 长曝光";
  if(value==0)return "未提供有效快门";
  for(int i=0;i<CODES.length;i++)if(value==CODES[i])return LABELS[i];
  return String.format(Locale.ROOT,"未适配 · 0x%X",value);
 }
 static String aperture(long value){String[] names={"1.8","2","2.2","2.5","2.8","3.2","3.5","4","4.5","5","5.6","6.3","7.1","8","9","10","11","13","14","16","18","20","22","25","29","32","36"};if(value>=10&&value<=62&&value%2==0)return "f/"+names[(int)(value-10)/2];return String.format(Locale.ROOT,"未适配 · 0x%X",value);}
 static String legacy(long value){
  if(value==-31)return "B · 长曝光";
  if(value>=-30&&value<=0&&value%2==0){String[] times={"30","25","20","15","13","10","8","6","5","4","3","2.5","2","1.6","1.3","1"};return times[(int)(value+30)/2]+" s";}
  if(value>=2&&value<=78&&value%2==0){String[] denominators={"1.3","1.6","2","2.5","3","4","5","6","8","10","13","15","20","25","30","40","50","60","80","100","125","160","200","250","320","400","500","640","800","1000","1250","1600","2000","2500","3200","4000","5000","6400","8000"};return "1/"+denominators[(int)value/2-1]+" s";}
  return String.format(Locale.ROOT,"未适配 · 0x%X",value);
 }
 private FujiExposure(){}
}
