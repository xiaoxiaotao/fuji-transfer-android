package dev.danielc.fujiapp;
import android.graphics.*;

/** Original photograph is never cropped or overwritten. All typography scales with output. */
final class PhotoFrame {
    static final String[] NAMES={"经典参数","夜色参数","纯净留白","画廊藏品","即刻胶片","35mm 胶卷","旅途杂志","宽银幕"};
    static final int[] PAPERS={Color.rgb(248,246,239),Color.WHITE,Color.rgb(24,28,26),Color.rgb(225,230,220),Color.rgb(233,221,207)};
    static final class Options {
        int style=3, paper=0, spacing=6; boolean parameters=true;
        String title="", author="";
        Options copy(){Options o=new Options();o.style=style;o.paper=paper;o.spacing=spacing;o.parameters=parameters;o.title=title;o.author=author;return o;}
    }
    static Bitmap render(Bitmap photo,PhotoDetails details,int style){Options o=new Options();o.style=style;o.paper=style==1?2:1;o.spacing=4;return render(photo,details,o);}
    static Bitmap render(Bitmap photo,PhotoDetails details,Options o){
        int w=photo.getWidth(),h=photo.getHeight(),m=Math.max(1,w*o.spacing/100);
        boolean plain=o.style==2,film=o.style==5,magazine=o.style==6,cinema=o.style==7;
        int top=film?m*2:magazine?Math.max(m,w/7):m;
        int footer=plain?m:cinema?Math.max(m,w/7):Math.max(1,w*(o.style==4?24:o.style==3?21:14)/100);
        if(o.style<=1)footer=Math.max(1,w/8);
        int width=w+m*2,height=h+top+footer;
        Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int bg=film||cinema?Color.rgb(19,21,20):PAPERS[o.paper];out.eraseColor(bg);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        int ink=(Color.red(bg)+Color.green(bg)+Color.blue(bg))<300?Color.rgb(242,240,232):Color.rgb(36,44,38);
        int muted=(Color.red(bg)+Color.green(bg)+Color.blue(bg))<300?Color.rgb(167,174,165):Color.rgb(114,119,108);
        float unit=w/1000f;
        if(o.style==3||o.style==4){p.setColor((Color.red(bg)+Color.green(bg)+Color.blue(bg))<300?Color.rgb(7,9,8):Color.rgb(204,201,190));c.drawRect(m-2,top-2,m+w+3,top+h+4,p);}
        c.drawBitmap(photo,m,top,p);
        if(plain)return out;
        String camera=details.camera.isEmpty()?"FUJI GALLERY":details.camera;
        String title=o.title.trim().isEmpty()?(o.style==6?"A MOMENT IN TIME":o.style==4?"Little moments, forever.":camera):o.title.trim();
        float y=top+h;
        if(film){
            p.setColor(Color.rgb(224,167,70));p.setTypeface(Typeface.create("monospace",Typeface.NORMAL));p.setTextSize(Math.max(1,18*unit));
            line(c,"FUJI GALLERY   /   35 MM",m,top*0.52f,w,p);
            for(int x=m;x<width-m;x+=Math.max(28,w/14)){p.setColor(Color.rgb(211,205,180));c.drawRoundRect(x,height-m*0.6f,x+Math.max(1,w/40f),height-m*0.2f,3,3,p);}
            p.setColor(Color.rgb(224,167,70));line(c,"01  —  "+title,m,y+footer*0.35f,w,p);
            if(o.parameters){p.setTextSize(Math.max(1,23*unit));line(c,details.exposure,m,y+footer*0.58f,w,p);}
        }else if(magazine){
            p.setColor(ink);p.setTypeface(Typeface.create("serif",Typeface.BOLD));p.setTextSize(Math.max(1,70*unit));line(c,"MOMENTS",m,top*0.56f,w*0.75f,p);
            p.setTypeface(Typeface.create("monospace",Typeface.NORMAL));p.setTextSize(Math.max(1,13*unit));p.setColor(muted);line(c,"PHOTO JOURNAL  /  VOL. 01",m,top*0.82f,w,p);
            p.setColor(ink);p.setTextSize(Math.max(1,30*unit));p.setTypeface(Typeface.create("serif",Typeface.NORMAL));line(c,title,m,y+footer*0.38f,w,p);
            if(o.parameters){p.setTextSize(Math.max(1,23*unit));p.setTypeface(Typeface.DEFAULT);line(c,camera+"   /   "+details.exposure,m,y+footer*0.68f,w,p);}
        }else if(o.style==3){
            p.setColor(muted);p.setTextSize(Math.max(1,13*unit));p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));line(c,"THE PHOTO COLLECTION",m,y+footer*0.24f,w,p);
            p.setColor(ink);p.setTypeface(Typeface.create("serif",Typeface.NORMAL));p.setTextSize(Math.max(1,40*unit));line(c,title,m,y+footer*0.52f,w,p);
            p.setColor(muted);p.setStrokeWidth(Math.max(1,unit));c.drawLine(m,y+footer*0.64f,width-m,y+footer*0.64f,p);
            p.setTextSize(Math.max(1,23*unit));p.setTypeface(Typeface.DEFAULT);line(c,o.parameters?details.exposure:"",m,y+footer*0.82f,w*0.72f,p);
            p.setTextAlign(Paint.Align.RIGHT);lineRight(c,o.author,width-m,y+footer*0.82f,w*0.25f,p);
        }else if(o.style==4){
            p.setColor(ink);p.setTypeface(Typeface.create("casual",Typeface.NORMAL));p.setTextSize(Math.max(1,38*unit));center(c,title,width/2f,y+footer*0.46f,w,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(Math.max(1,23*unit));p.setColor(muted);center(c,o.parameters?details.exposure:o.author,width/2f,y+footer*0.73f,w,p);
        }else{
            p.setColor(ink);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(Math.max(1,27*unit));line(c,title,m,y+footer*0.40f,w,p);
            p.setColor(muted);p.setTypeface(Typeface.create("monospace",Typeface.NORMAL));p.setTextSize(Math.max(1,21*unit));line(c,o.parameters?details.exposure:o.author,m,y+footer*0.70f,w,p);
        }
        return out;
    }
    private static String fit(String s,float width,Paint p){return android.text.TextUtils.ellipsize(s,new android.text.TextPaint(p),width,android.text.TextUtils.TruncateAt.END).toString();}
    private static void line(Canvas c,String s,float x,float y,float width,Paint p){p.setTextAlign(Paint.Align.LEFT);c.drawText(fit(s,width,p),x,y,p);}
    private static void lineRight(Canvas c,String s,float x,float y,float width,Paint p){c.drawText(fit(s,width,p),x,y,p);}
    private static void center(Canvas c,String s,float x,float y,float width,Paint p){p.setTextAlign(Paint.Align.CENTER);c.drawText(fit(s,width,p),x,y,p);p.setTextAlign(Paint.Align.LEFT);}
}
