package dev.danielc.fujiapp;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small semantic vector badges, shared by recipe details and editing. */
final class RecipeDesign {
 static final int GREEN=0xff21b68d,BG=0xff101415,SURFACE=0xff1d2425;
 static String group(String key){if(key.equals("title")||key.equals("camera")||key.equals("lens")||key.equals("slot")||key.equals("simulation"))return "机身与胶片模拟";if(key.equals("whiteBalance")||key.equals("wbShift")||key.equals("color")||key.startsWith("colorChrome"))return "色彩与白平衡";if(key.equals("dynamicRange")||key.equals("highlight")||key.equals("shadow")||key.equals("sharpness")||key.equals("noiseReduction")||key.equals("grain")||key.equals("clarity"))return "影调与质感";return "拍摄笔记";}
 static final String[] GROUPS={"机身与胶片模拟","色彩与白平衡","影调与质感","拍摄笔记"};
 static String[] choices(String key){switch(key){case "slot":return new String[]{"C1","C2","C3","C4","C5","C6","C7"};case "simulation":return new String[]{"Provia","Velvia","Astia","Classic Chrome","Classic Negative","Reala ACE","Nostalgic Negative","Pro Neg. Std","Pro Neg. Hi","Eterna","Eterna Bleach Bypass","Acros","Acros + R","Acros + Ye","Acros + G","Monochrome","Sepia"};case "whiteBalance":return new String[]{"自动","自动 · 白优先","自动 · 氛围优先","日光","阴天","白炽灯","荧光灯","自定义"};case "dynamicRange":return new String[]{"自动","DR 100%","DR 200%","DR 400%"};case "grain":return new String[]{"关闭","弱 / 小","弱 / 大","强 / 小","强 / 大"};case "colorChrome":case "colorChromeBlue":return new String[]{"关闭","弱","强"};case "highlight":case "shadow":return new String[]{"-2","-1.5","-1","-0.5","0","+0.5","+1","+1.5","+2","+2.5","+3","+3.5","+4"};case "sharpness":case "color":case "noiseReduction":return new String[]{"-4","-3","-2","-1","0","+1","+2","+3","+4"};case "clarity":return new String[]{"-5","-4","-3","-2","-1","0","+1","+2","+3","+4","+5"};default:return null;}}
 static View icon(Context c,String key,String value){return new Badge(c,key,value);}
 static void update(View icon,String value){if(icon instanceof Badge){((Badge)icon).value=value;icon.invalidate();}}
 private static final class Badge extends View {
  private final String key;private String value;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  Badge(Context c,String key,String value){super(c);this.key=key;this.value=value;setContentDescription(iconName(key));}
  private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);Canvas c=canvas;c.save();c.scale(getWidth()/32f,getHeight()/32f);p.setColor(0xffc6d3ce);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);
   switch(key){
    case "camera":c.drawRoundRect(3,9,29,26,3,3,p);c.drawCircle(17,17,6,p);line(c,8,9,10,5);line(c,10,5,19,5);line(c,19,5,21,9);break;
    case "lens":c.drawCircle(16,16,12,p);c.drawCircle(16,16,5,p);for(int i=0;i<6;i++){c.save();c.rotate(i*60,16,16);line(c,16,4,21,13);c.restore();}break;
    case "simulation":p.setStyle(Paint.Style.FILL);int shade=value.contains("Velvia")?0xff6986aa:value.contains("Negative")||value.contains("Neg.")?0xff8d687f:value.contains("Acros")||value.contains("Monochrome")?0xff8d9693:GREEN;p.setColor(shade);c.drawRoundRect(2,5,30,27,3,3,p);p.setColor(0xff101415);for(int i=0;i<4;i++){c.drawRect(4+i*7,6,7+i*7,9,p);c.drawRect(4+i*7,23,7+i*7,26,p);}p.setColor(Color.WHITE);p.setTextSize(9);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);c.drawText("FILM",16,20,p);break;
    case "whiteBalance":case "dynamicRange":case "slot":case "noiseReduction":case "clarity":case "exposure":p.setStyle(Paint.Style.FILL);p.setColor(key.equals("wbShift")?0xff3999d4:0xffc6d3ce);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(key.equals("noiseReduction")?12:14);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));String mark=key.equals("whiteBalance")?"WB":key.equals("dynamicRange")?"DR":key.equals("wbShift")?"R/B":key.equals("slot")?"C":key.equals("noiseReduction")?"NR":key.equals("clarity")?"CL":"±";c.drawText(mark,16,21,p);break;
    case "wbShift":p.setStyle(Paint.Style.FILL);p.setTextSize(16);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setColor(0xffed666b);c.drawText("R",8,22,p);p.setColor(0xff398fce);c.drawText("B",24,22,p);break;
    case "highlight":case "shadow":c.drawCircle(16,16,9,p);p.setStyle(Paint.Style.FILL);if(key.equals("highlight")){p.setColor(0xfff1d58a);c.drawCircle(16,16,5,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,16,16);line(c,16,2,16,5);c.restore();}}else c.drawArc(7,7,25,25,90,180,true,p);break;
    case "grain":p.setStyle(Paint.Style.FILL);for(int y=0;y<3;y++)for(int x=0;x<3;x++)c.drawCircle(8+x*8,8+y*8,1.8f,p);break;
    case "color":case "colorChrome":case "colorChromeBlue":p.setStyle(Paint.Style.FILL);p.setColor(0xffee766c);c.drawCircle(12,12,7,p);p.setColor(key.equals("colorChromeBlue")?0xff398fcc:0xff52b794);c.drawCircle(20,13,7,p);p.setColor(0xffd5bb63);c.drawCircle(16,21,7,p);break;
    case "sharpness":Path triangle=new Path();triangle.moveTo(16,5);triangle.lineTo(28,26);triangle.lineTo(4,26);triangle.close();c.drawPath(triangle,p);line(c,16,12,16,23);break;
    default:c.drawRoundRect(7,4,25,28,2,2,p);line(c,11,11,21,11);line(c,11,16,21,16);line(c,11,21,18,21);
   }c.restore();
  }
 }
 static String iconName(String key){switch(key){case "camera":return "相机图标";case "lens":return "镜头光圈图标";case "simulation":return "胶片模拟图标";case "whiteBalance":return "白平衡 WB 图标";case "wbShift":return "红蓝偏移 R/B 图标";case "dynamicRange":return "动态范围 DR 图标";case "grain":return "颗粒图标";default:return "参数图标 · "+key;}}
}
