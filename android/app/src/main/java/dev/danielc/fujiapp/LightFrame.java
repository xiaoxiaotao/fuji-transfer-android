package dev.danielc.fujiapp;
import android.graphics.*;
/** Software rendering shared by previews and exports, including Android 6 devices. */
final class LightFrame {
 static Bitmap render(Bitmap photo,PhotoDetails details,PhotoFrame.Options o){
  int w=photo.getWidth(),h=photo.getHeight(),m=Math.max(1,w*(o.spacing+4)/100);float u=w/1000f;
  boolean sidebar=o.style==9,palette=o.style==10;
  int side=sidebar?Math.round(w*.49f):0,foot=sidebar?0:Math.round(w*(palette?.30f:.20f));
  int width=w+2*m+side,height=Math.max(h,sidebar?Math.round(w*.90f):h)+2*m+foot;
  if((long)width*height>28000000L)throw new IllegalArgumentException("成品尺寸过大，请降低导出画质");
  Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);out.eraseColor(PhotoFrame.PAPERS[o.paper]);
  Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  Bitmap bg=blur(photo,Math.max(1,o.blur));try{float scale=Math.max((float)width/bg.getWidth(),(float)height/bg.getHeight());float bw=bg.getWidth()*scale,bh=bg.getHeight()*scale;c.drawBitmap(bg,null,new RectF((width-bw)/2,(height-bh)/2,(width+bw)/2,(height+bh)/2),p);}finally{bg.recycle();}
  c.drawColor(sidebar?0x66303b40:0x44303b40);
  float x=m+(o.sidebarRight?0:side),y=sidebar?(height-h)/2f:m,r=Math.max(0,o.corners*u);
  RectF card=new RectF(x,y,x+w,y+h+foot);
  p.setColor(palette?0xfff6f2e9:PhotoFrame.PAPERS[o.paper]);p.setShadowLayer(Math.max(0,o.shadow*u),0,o.shadow*u*.45f,0x70000000);c.drawRoundRect(card,r,r,p);p.clearShadowLayer();
  c.save();Path clip=new Path();clip.addRoundRect(card,r,r,Path.Direction.CW);c.clipPath(clip);c.drawBitmap(photo,null,new RectF(x,y,x+w,y+h),p);c.restore();
  String camera=details.camera.isEmpty()?"FUJI GALLERY":details.camera;
  String brand=details.camera.isEmpty()?"FUJI GALLERY":camera.toUpperCase(java.util.Locale.ROOT).contains("FUJI")||camera.startsWith("X-")?"FUJIFILM":"PHOTO GALLERY";
  if(sidebar){
   float left=o.sidebarRight?m+w+m*.65f:m*.85f,available=side-m*.7f,base=height*.27f;
   text(c,p,brand,left,base,available,43*u,Color.WHITE,true);
   text(c,p,o.title.trim().isEmpty()?camera:o.title,left,base+65*u,available,25*u,0xffe6ece9,false);
   if(o.parameters){String[] labels={"APERTURE","SHUTTER","ISO"},values={details.aperture,details.shutter,details.iso};
    for(int i=0;i<3;i++){float yy=base+(155+i*105)*u;text(c,p,labels[i],left,yy,available,15*u,0xffc4d2d0,false);text(c,p,values[i].isEmpty()?"—":values[i],left,yy+42*u,available,31*u,Color.WHITE,false);}}
   text(c,p,o.author,left,height-m,available,20*u,0xffd3dfdd,false);
  }else{
   int paper=palette?0xfff6f2e9:PhotoFrame.PAPERS[o.paper];boolean dark=Color.red(paper)+Color.green(paper)+Color.blue(paper)<300;int ink=dark?0xfff0f2e9:0xff252c28,muted=dark?0xffb3bfb7:0xff747c74;
   float left=x+45*u,yy=y+h;
   text(c,p,brand,left,yy+65*u,w*.43f,31*u,ink,true);
   text(c,p,o.title.trim().isEmpty()?camera:o.title,left,yy+105*u,w*.43f,20*u,muted,false);
   p.setColor(dark?0xff657068:0xffd6d9d2);p.setStrokeWidth(Math.max(1,u));c.drawLine(x+w*.49f,yy+40*u,x+w*.49f,yy+125*u,p);
   text(c,p,o.parameters?details.exposure:o.author,x+w*.54f,yy+70*u,w*.41f,23*u,ink,false);
   text(c,p,o.parameters?details.lens:o.author,x+w*.54f,yy+107*u,w*.41f,17*u,muted,false);
   if(palette){int[] colors=PhotoPalette.colors(photo);for(int i=0;i<5;i++){p.setColor(colors[i]);float xx=left+i*w*.17f;c.drawRoundRect(xx,yy+160*u,xx+w*.135f,yy+216*u,6*u,6*u,p);}text(c,p,"COLORS OF THIS MOMENT",left,yy+263*u,w*.70f,13*u,muted,false);}
   else text(c,p,o.author,left,yy+165*u,w*.9f,14*u,muted,false);
  }
  return out;
 }
 private static void text(Canvas c,Paint p,String value,float x,float y,float max,float size,int color,boolean bold){p.setColor(color);p.setTextSize(Math.max(1,size));p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextAlign(Paint.Align.LEFT);c.drawText(android.text.TextUtils.ellipsize(value,new android.text.TextPaint(p),Math.max(1,max),android.text.TextUtils.TruncateAt.END).toString(),x,y,p);}
 private static Bitmap blur(Bitmap source,int amount){
  float scale=96f/Math.max(source.getWidth(),source.getHeight());Bitmap tiny=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);
  Bitmap result=tiny.copy(Bitmap.Config.ARGB_8888,true);if(tiny!=source)tiny.recycle();int w=result.getWidth(),h=result.getHeight();int[] pixels=new int[w*h],temp=new int[w*h];result.getPixels(pixels,0,w,0,0,w,h);
  int radius=Math.max(1,amount);for(int pass=0;pass<3;pass++){for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0,n=0;for(int dx=-radius;dx<=radius;dx++){int v=pixels[y*w+Math.max(0,Math.min(w-1,x+dx))];red+=Color.red(v);green+=Color.green(v);blue+=Color.blue(v);n++;}temp[y*w+x]=Color.rgb(red/n,green/n,blue/n);}for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0,n=0;for(int dy=-radius;dy<=radius;dy++){int v=temp[Math.max(0,Math.min(h-1,y+dy))*w+x];red+=Color.red(v);green+=Color.green(v);blue+=Color.blue(v);n++;}pixels[y*w+x]=Color.rgb(red/n,green/n,blue/n);}}
  result.setPixels(pixels,0,w,0,0,w,h);return result;
 }
}
