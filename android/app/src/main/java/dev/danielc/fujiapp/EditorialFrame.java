package dev.danielc.fujiapp;

import android.graphics.*;

/** Lightweight original editorial layouts, drawn at output resolution without external assets. */
final class EditorialFrame {
    static Bitmap render(Bitmap photo,PhotoDetails d,PhotoFrame.Options o){
        int w=photo.getWidth(),h=photo.getHeight(),s=o.style;
        float u=w/1000f;
        int m=Math.max(1,Math.round(w*(.08f+o.spacing/100f))),top=m,foot=Math.round(w*.25f)+m;
        if(s==66)top+=Math.round(w*.22f);
        int width=w+2*m,height=h+top+foot;
        if((long)width*height>28000000L)throw new IllegalArgumentException("成品尺寸过大，请降低导出画质");
        int paper=s==62?0xff173854:s==65?0xff151329:s==59?0xffd9cebb:s==63?0xfff5f0df:s==61?0xffece8de:PhotoFrame.PAPERS[o.paper];
        boolean dark=Color.red(paper)+Color.green(paper)+Color.blue(paper)<330;
        int ink=dark?0xfff2eee6:0xff303534,muted=dark?0xffa5bbc9:0xff858479;
        Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);out.eraseColor(paper);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float x=m,y=top,b=y+h;
        if(s==62||s==63){
            p.setStrokeWidth(Math.max(.5f,u));p.setColor(s==62?0x357bb3d1:0x407f99a6);
            float step=Math.max(1,40*u);
            for(float yy=20*u;yy<height;yy+=step)c.drawLine(0,yy,width,yy,p);
            if(s==62)for(float xx=20*u;xx<width;xx+=step)c.drawLine(xx,0,xx,height,p);
            else {p.setColor(0x70cf7b79);c.drawLine(m*.65f,0,m*.65f,height,p);}
        }
        if(s==64){float size=Math.max(1,45*u);for(int row=0;row<Math.ceil(height/size);row++)for(int col=0;col<Math.ceil(width/size);col++)if((row+col)%2==0){p.setColor(dark?0xff354139:0xffdedbcf);c.drawRect(col*size,row*size,(col+1)*size,(row+1)*size,p);}}
        if(s==58){
            p.setColor(dark?0xffd5d1c6:Color.WHITE);Path edge=new Path();
            edge.moveTo(x-22*u,y-18*u);
            for(int i=0;i<=50;i++)edge.lineTo(x+w*i/50f,y-(14+(i*17%13))*u);
            for(int i=0;i<=40;i++)edge.lineTo(x+w+(14+(i*11%15))*u,y+h*i/40f);
            for(int i=50;i>=0;i--)edge.lineTo(x+w*i/50f,b+(14+(i*19%13))*u);
            for(int i=40;i>=0;i--)edge.lineTo(x-(14+(i*7%15))*u,y+h*i/40f);
            edge.close();p.setShadowLayer(10*u,0,5*u,0x38000000);c.drawPath(edge,p);p.clearShadowLayer();
        }
        if(s==59||s==63){p.setColor(0xfffdfaf1);p.setShadowLayer(12*u,2*u,6*u,0x38000000);c.drawRect(x-20*u,y-20*u,x+w+20*u,b+95*u,p);p.clearShadowLayer();}
        if(s==61||s==65){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,2*u));p.setColor(s==61?0xffaa8a50:0xffc780fa);c.drawRect(x-25*u,y-25*u,x+w+25*u,b+25*u,p);p.setStrokeWidth(Math.max(1,u));p.setColor(s==61?0xffcabb99:0xff5edee8);c.drawRect(x-35*u,y-35*u,x+w+35*u,b+35*u,p);p.setStyle(Paint.Style.FILL);}
        p.setAlpha(255);c.drawBitmap(photo,x,y,p);
        if(s==59){
            for(int i=0;i<2;i++){float cx=i==0?x+85*u:x+w-85*u,cy=i==0?y-12*u:b+12*u;c.save();c.rotate(i==0?-13:11,cx,cy);p.setColor(0xbdd6b88b);c.drawRect(cx-100*u,cy-26*u,cx+100*u,cy+26*u,p);p.setColor(0x40866b42);p.setStrokeWidth(Math.max(1,u));for(int j=-4;j<=4;j++)c.drawLine(cx+j*22*u,cy-26*u,cx+j*22*u,cy+26*u,p);c.restore();}
        }
        if(s==60){
            p.setColor(ink);float k=42*u;
            for(int i=0;i<4;i++){float cx=i%2==0?x:x+w,cy=i<2?y:b,dx=i%2==0?1:-1,dy=i<2?1:-1;Path triangle=new Path();triangle.moveTo(cx-8*u*dx,cy-8*u*dy);triangle.lineTo(cx+k*dx,cy-8*u*dy);triangle.lineTo(cx-8*u*dx,cy+k*dy);triangle.close();c.drawPath(triangle,p);}
        }
        String title=o.title.trim().isEmpty()?defaultTitle(s):o.title.trim();
        String caption=o.parameters?d.exposure:o.author.trim();
        if(s==66){text(c,p,"STILL / LIFE",x,top-98*u,w,78,u,ink,true);text(c,p,"PHOTOGRAPHY EXHIBITION  /  VOL. 01",x,top-45*u,w,17,u,muted,false);}
        if(s==62){p.setColor(0xff8bbbd5);p.setStrokeWidth(Math.max(1,u));c.drawLine(x,y-42*u,x+w,y-42*u,p);c.drawLine(x-42*u,y,x-42*u,b,p);for(float xx:new float[]{x,x+w})c.drawLine(xx,y-55*u,xx,y-29*u,p);text(c,p,"IMAGE STUDY  /  01",x,b+75*u,w,18,u,muted,false);}
        if(s==67){text(c,p,"No. 001",x,b+90*u,w*.26f,42,u,ink,true);p.setColor(muted);p.setStrokeWidth(Math.max(1,u));c.drawLine(x+w*.29f,b+40*u,x+w*.29f,b+160*u,p);text(c,p,title,x+w*.34f,b+84*u,w*.66f,30,u,ink,true);text(c,p,caption,x+w*.34f,b+137*u,w*.66f,17,u,muted,false);}
        else {text(c,p,title,x,b+(s==62?123:120)*u,w,32,u,ink,s==66||s==65);text(c,p,caption,x,b+175*u,w,18,u,muted,false);}
        return out;
    }
    private static String defaultTitle(int s){switch(s){case 58:return "A FRAGMENT OF TIME";case 59:return "Collected little moments.";case 60:return "MEMORIES / PHOTO ALBUM";case 61:return "THE GOLDEN COLLECTION";case 62:return "LIGHT / SPACE / STRUCTURE";case 63:return "Notes from today.";case 64:return "PLAYFUL MOMENTS";case 65:return "AFTER DARK";case 66:return "A MOMENT, ON DISPLAY";default:return "ARCHIVE / A STILL MOMENT";}}
    private static void text(Canvas c,Paint p,String s,float x,float y,float width,float size,float u,int ink,boolean bold){p.setColor(ink);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(Math.max(1,size*u));p.setTextAlign(Paint.Align.LEFT);c.drawText(android.text.TextUtils.ellipsize(s,new android.text.TextPaint(p),width,android.text.TextUtils.TruncateAt.END).toString(),x,y,p);}
}
