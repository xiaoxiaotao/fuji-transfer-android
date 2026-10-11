package dev.danielc.fujiapp;
import android.graphics.*;

/** Original information cards: EXIF stays separate from the photographer's manual note. */
final class FujiPhotoCard {
 static final int[] STYLES={68,69,70};
 static Bitmap render(Bitmap photo,PhotoDetails d,PhotoFrame.Options o){
  int w=photo.getWidth(),h=photo.getHeight();float u=w/1000f;int m=Math.max(1,Math.round(w*o.spacing/100f));
  int top=m+Math.round(w*.10f),footer=m+Math.round(w*.40f),width=w+2*m,height=h+top+footer;
  if((long)width*height>28000000L)throw new IllegalArgumentException("成品尺寸过大，请降低导出画质");
  int paper=o.style==69?0xff192827:PhotoFrame.PAPERS[o.paper];boolean dark=Color.red(paper)+Color.green(paper)+Color.blue(paper)<330;
  int ink=dark?0xfff3f1e8:0xff263735,muted=dark?0xffa2b7b0:0xff78877f,accent=dark?0xffd7e89d:0xff4b7467;
  Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);out.eraseColor(paper);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  text(c,p,o.style==70?"PHOTO / RECIPE NOTE":"PHOTOGRAPHIC RECORD",m,top-38*u,w*.75f,20,u,accent,true);
  text(c,p,"01",width-m-60*u,top-38*u,60*u,20,u,muted,false);
  p.setAlpha(255);c.drawBitmap(photo,m,top,p);float y=top+h;
  text(c,p,o.title.trim().isEmpty()?(d.camera.isEmpty()?"MY PHOTO JOURNAL":d.camera):o.title.trim(),m,y+73*u,w,42,u,ink,true);
  p.setColor(accent);p.setStrokeWidth(Math.max(1,u));c.drawLine(m,y+97*u,width-m,y+97*u,p);
  String cameraLens=d.lens.isEmpty()?d.camera:d.camera.isEmpty()?d.lens:d.camera+"  /  "+d.lens;
  if(o.parameters){text(c,p,cameraLens,m,y+135*u,w,19,u,muted,false);if(o.style==68){String[] labels={"APERTURE","SHUTTER","ISO"},values={d.aperture,d.shutter,d.iso};for(int i=0;i<3;i++){float x=m+w*i/3f;text(c,p,labels[i],x,y+165*u,w/3f-12*u,11,u,muted,true);text(c,p,values[i],x,y+195*u,w/3f-12*u,25,u,ink,false);}}else text(c,p,d.exposure,m,y+183*u,w,27,u,ink,false);text(c,p,join(d.focalLength,d.takenAt),m,y+223*u,w,17,u,muted,false);}
  if(o.style==70){p.setColor(dark?0xff2d4038:0xffe3e9dd);c.drawRoundRect(m,y+248*u,width-m,y+330*u,12*u,12*u,p);}
  if(!o.filmNote.trim().isEmpty()){text(c,p,"MY NOTE / 手动备注",m,y+275*u,w,13,u,accent,true);text(c,p,o.filmNote.trim(),m,y+313*u,w,27,u,ink,false);}
  text(c,p,o.author.trim(),m,y+365*u,w*.70f,19,u,muted,false);text(c,p,"FUJI GALLERY",m+w*.75f,y+365*u,w*.25f,14,u,accent,true);
  return out;
 }
 private static String join(String a,String b){return a.isEmpty()?b:b.isEmpty()?a:a+"  /  "+b;}
 private static void text(Canvas c,Paint p,String s,float x,float y,float width,float size,float u,int color,boolean bold){p.setColor(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(Math.max(1,size*u));p.setTextAlign(Paint.Align.LEFT);c.drawText(android.text.TextUtils.ellipsize(s,new android.text.TextPaint(p),width,android.text.TextUtils.TruncateAt.END).toString(),x,y,p);}
}
