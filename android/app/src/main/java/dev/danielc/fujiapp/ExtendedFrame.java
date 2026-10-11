package dev.danielc.fujiapp;

import android.graphics.*;
import java.util.List;
import java.util.Locale;

/** Native, original frame layouts. Photos are fitted, never cropped; preview and export share this renderer. */
final class ExtendedFrame {
    private final Canvas canvas;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final float u;
    private final int ink, muted;
    private ExtendedFrame(Bitmap out,float unit,int paper){canvas=new Canvas(out);u=unit;boolean dark=Color.red(paper)+Color.green(paper)+Color.blue(paper)<330;ink=dark?0xfff1efe8:0xff272d30;muted=dark?0xffb8c1c5:0xff747d83;}

    static Bitmap render(Bitmap photo,PhotoDetails d,PhotoFrame.Options o,List<Bitmap> extras){
        if(o.style>=68)return FujiPhotoCard.render(photo,d,o);
        if(o.style>=58)return EditorialFrame.render(photo,d,o);
        int style=o.style,w=photo.getWidth(),h=photo.getHeight();float u=w/1000f;
        int m=Math.max(1,Math.round(w*o.spacing/100f)),top=m,foot=Math.round(w*.20f),side=0;
        boolean blur=style==36||style==37||style==38||style==53;
        boolean instant=style==23||style==24||style==25||style==38;
        boolean cinema=style==29||style==30||style==31;
        boolean simple=style==19||style==20||style==21||style==22||style==41||style>=46&&style<=49;
        boolean sideLayout=style==13||style==54;
        int paper=PhotoFrame.PAPERS[o.paper];
        if(style==26||style==27||style==28||cinema)paper=0xff191b20;
        if(style==25)paper=0xff70777d;
        if(style==43)paper=0xffe9cdb6;
        if(style==45)paper=0xff173442;
        if(simple)foot=m;
        if(style==41){top=m+Math.round(w*.06f);foot=m+Math.round(w*.08f);}
        if(style==20||style==16){m+=Math.round(w*.08f);top=m;foot=m+Math.round(w*.13f);}
        if(instant){m+=Math.round(w*.015f);top=m;foot=Math.round(w*.29f);}
        if(cinema){top=Math.round(w*.11f)+m;foot=Math.round(w*.22f)+m;}
        if(style==35||style==34){top=Math.round(w*.18f)+m;foot=Math.round(w*.17f);}
        if(style==26||style==27){top=Math.round(w*.085f)+m;foot=Math.round(w*.13f)+m;}
        if(style==13||style==54){side=Math.round(w*.38f);foot=m;}
        if(style==39||style==40)foot=Math.round(w*.24f);
        int width=w+2*m+side,height=h+top+foot;
        if(sideLayout)height=Math.max(h,Math.round(w*.58f))+2*m;
        if(style==21||style==41){int edge=Math.max(width,height);width=edge;height=edge;}
        if(style==22){width=Math.max(width,Math.round((h+2*m)*1.9f));height=h+2*m;}
        int count=PhotoFrame.requiredPhotos(style);
        if(count>1){width=w+2*m;int cellH=Math.max(1,Math.round(w*(style==57?.42f:.62f)));height=(style==57?3:count)*cellH+2*m+Math.round(w*.14f);}
        if((long)width*height>28000000L)throw new IllegalArgumentException("成品尺寸过大，请降低导出画质");
        Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);out.eraseColor(paper);
        ExtendedFrame r=new ExtendedFrame(out,u,paper);Canvas c=r.canvas;Paint p=r.paint;
        if(blur){Bitmap bg=LightFrame.blur(photo,o.blur);try{float scale=Math.max((float)width/bg.getWidth(),(float)height/bg.getHeight());float bw=bg.getWidth()*scale,bh=bg.getHeight()*scale;c.drawBitmap(bg,null,new RectF((width-bw)/2,(height-bh)/2,(width+bw)/2,(height+bh)/2),p);c.drawColor(0x55323a41);}finally{bg.recycle();}}
        if(style==43){p.setShader(new LinearGradient(0,0,width,height,new int[]{0xfff0ded0,0xffcaa8b2,0xff7d92a9},null,Shader.TileMode.CLAMP));c.drawRect(0,0,width,height,p);p.setShader(null);}
        if(style==25||style==42){for(int i=0;i<280;i++){float x=((i*83)%997)/997f*width,y=((i*137)%991)/991f*height;p.setColor(style==25?0x257fffff:0x187f6749);c.drawCircle(x,y,Math.max(.4f,u*.7f),p);}}
        String camera=d.camera.isEmpty()?"PHOTO COLLECTION":d.camera;
        String title=o.title.trim().isEmpty()?defaultTitle(style,camera):o.title.trim();
        String params=o.parameters?d.exposure:"",author=o.author.trim();
        if(count>1){r.collage(photo,extras,style,count,m,w,title,params,author);return out;}
        boolean sideRight=style==54||o.sidebarRight;
        float x=m+(sideLayout&&!sideRight?side:0),y=top;
        if(style==21||style==41||style==22){x=(width-w)/2f;y=(height-h)/2f;}
        if(sideLayout)y=(height-h)/2f;
        if(style==17||style==36||style==38||style==50){p.setColor(style==36?0xcce9efef:paper);p.setShadowLayer(o.shadow*u,0,8*u,0x65000000);c.drawRoundRect(x-12*u,y-12*u,x+w+12*u,y+h+foot-m,o.corners*u,o.corners*u,p);p.clearShadowLayer();}
        if(style==18){r.rect(x-26*u,y-26*u,x+w+26*u,y+h+26*u,0xffaaa69e);r.rect(x-13*u,y-13*u,x+w+13*u,y+h+13*u,0xffe4e0d6);}
        if(style==19)r.rect(x-3*u,y-3*u,x+w+3*u,y+h+3*u,r.ink);
        if(style==16||style==44)r.stroke(x-8*u,y-8*u,x+w+8*u,y+h+8*u,0xffaaa69d,1.5f);
        if(style==27){r.rect(x-12*u,y-12*u,x+w+12*u,y+h+12*u,0xffdcdbd2);r.stroke(x-18*u,y-18*u,x+w+18*u,y+h+18*u,0xff898b86,3);}
        // Fit the original directly: no clipping or corner crop removes image pixels.
        r.photo(photo,new RectF(x,y,x+w,y+h));
        float base=y+h,full=width-2*m;
        switch(style){
            case 11:
                r.text(title,m,base+62*u,full*.46f,29,r.ink,true,false);
                r.rule(m+full*.50f,base+30*u,m+full*.50f,base+135*u,r.muted,1);
                r.text(params,m+full*.55f,base+62*u,full*.45f,23,r.ink,false,false);
                r.text(author,m,base+112*u,full*.46f,18,r.muted,false,false);
                r.text(d.lens,m+full*.55f,base+112*u,full*.45f,17,r.muted,false,false);break;
            case 12:case 51:case 52:
                if(style!=52)r.text(title,width/2f,base+74*u,full,style==51?32:35,r.ink,true,true);
                r.text(params.isEmpty()?author:params,width/2f,base+(style==52?95:126)*u,full,21,r.muted,false,true);break;
            case 13:case 54:{
                float sx=sideRight?m+w+32*u:m,space=side-42*u,sideY=m;
                r.text("PHOTOGRAPHY",sx,sideY+75*u,space,17,r.muted,false,false);
                r.text(title,sx,sideY+135*u,space,35,r.ink,true,false);
                String[] labels={"APERTURE","SHUTTER","ISO"},values={d.aperture,d.shutter,d.iso};
                if(o.parameters)for(int i=0;i<3;i++){float yy=sideY+(220+i*90)*u;r.text(labels[i],sx,yy,space,13,r.muted,false,false);r.text(values[i],sx,yy+32*u,space,24,r.ink,false,false);}
                r.text(author,sx,height-m-24*u,space,19,r.muted,false,false);break;}
            case 14:case 45:
                r.rule(m,base+35*u,width-m,base+35*u,r.muted,1);
                r.text(style==45?"IMAGE / TECHNICAL NOTES":"PHOTOGRAPHIC ARCHIVE",m,base+68*u,full,15,r.muted,false,false);
                r.text(title,m,base+110*u,full*.65f,28,r.ink,true,false);
                r.text(params,m,base+154*u,full*.85f,20,r.muted,false,false);break;
            case 15:
                r.text(title,m,base+65*u,full*.54f,32,r.ink,false,false);
                r.rule(m+full*.6f,base+25*u,m+full*.6f,base+155*u,r.muted,1);
                r.text("POSTCARD",m+full*.65f,base+63*u,full*.35f,17,r.muted,true,false);
                r.text(author,m+full*.65f,base+110*u,full*.35f,20,r.ink,false,false);
                r.text(params,m,base+122*u,full*.54f,17,r.muted,false,false);break;
            case 16:case 17:case 18:case 44:
                r.text(title,width/2f,base+85*u,full,31,r.ink,false,true);
                r.text(author.isEmpty()?params:author,width/2f,base+129*u,full,17,r.muted,false,true);break;
            case 23:case 24:case 25:case 38:
                r.text(title,width/2f,base+124*u,full,38,r.ink,false,true);
                r.text(author.isEmpty()?params:author,width/2f,base+194*u,full,19,r.muted,false,true);break;
            case 26:
                for(float xx=m;xx<width-m;xx+=62*u){r.round(xx,15*u,xx+30*u,top-22*u,5*u,0xffbdbbad);r.round(xx,height-45*u,xx+30*u,height-15*u,5*u,0xffbdbbad);}
                r.text("35 MM  /  FRAME 01",m,top-35*u,full,17,0xffe0a952,false,false);
                r.text(title,m,base+63*u,full*.60f,24,0xffe0a952,false,false);
                r.text(params,m,base+96*u,full,16,r.muted,false,false);break;
            case 27:case 28:
                r.text("01  ▸  "+title,m,style==27?top-35*u:base+73*u,full,22,0xffdbb667,false,false);
                r.text(params,m,base+118*u,full,17,r.muted,false,false);break;
            case 29:case 30:case 31:
                r.text(title,width/2f,base+88*u,full,style==31?32:35,0xffefdd89,false,true);
                r.text(style==30?author:params,width/2f,base+144*u,full,21,0xffe2e4e7,false,true);
                if(style==31)r.text("A STILL FROM LIFE",width/2f,top*.62f,full,15,0xffbdc3c8,false,true);break;
            case 32:case 33:
                r.ticket(title,params,author,m,base,full,style);break;
            case 34:case 35:
                r.text(style==35?"MOMENTS":"FIELD NOTES",m,top*.58f,full,style==35?82:58,r.ink,true,false);
                r.text(style==35?"THE PHOTOGRAPHY ISSUE":"A PHOTOGRAPHIC DIARY",m,top*.82f,full,15,r.muted,false,false);
                r.text(title,m,base+61*u,full,34,r.ink,false,false);
                r.text(author.isEmpty()?params:author,m,base+115*u,full,18,r.muted,false,false);break;
            case 36:case 37:case 53:
                r.text(title,width/2f,base+74*u,full,32,blur?Color.WHITE:r.ink,true,true);
                r.text(style==53?d.takenAt:(params.isEmpty()?author:params),width/2f,base+121*u,full,18,blur?0xffe5e9eb:r.muted,false,true);break;
            case 39:case 40:{
                int[] colors=PhotoPalette.colors(photo);
                for(int i=0;i<colors.length;i++){float cx=m+full*(i+.5f)/colors.length;if(style==39){p.setColor(colors[i]);c.drawCircle(cx,base+63*u,23*u,p);}else r.rect(m+full*i/colors.length,base+28*u,m+full*(i+1)/colors.length,base+85*u,colors[i]);
                    r.text(String.format(Locale.ROOT,"#%06X",colors[i]&0xffffff),cx,base+115*u,full/colors.length,12,r.muted,false,true);}
                r.text(title,width/2f,base+174*u,full,22,r.ink,false,true);break;}
            case 41:
                p.setColor(r.ink);c.drawCircle(width/2f,height-38*u,20*u,p);
                r.text(author.isEmpty()?"F":author.substring(0,1).toUpperCase(Locale.ROOT),width/2f,height-31*u,32*u,20,paper,true,true);break;
            case 42:
                for(float xx=0;xx<width;xx+=30*u){p.setColor(0xff191c20);c.drawCircle(xx,0,7*u,p);c.drawCircle(xx,height,7*u,p);}for(float yy=0;yy<height;yy+=30*u){p.setColor(0xff191c20);c.drawCircle(0,yy,7*u,p);c.drawCircle(width,yy,7*u,p);}
                r.text(title,m,base+82*u,full,28,r.ink,true,false);r.text(author,m,base+135*u,full,17,r.muted,false,false);break;
            case 43:r.text(title,width/2f,base+84*u,full,32,r.ink,false,true);r.text(author,width/2f,base+135*u,full,18,r.muted,false,true);break;
            case 46:case 47:case 48:case 49:{
                if(!o.parameters||d.exposure.isEmpty())break;
                boolean right=style>=48,bottom=style==47||style==49;float tx=right?x+w-270*u:x+20*u,ty=bottom?y+h-147*u:y+20*u;
                r.round(tx-9*u,ty-9*u,tx+250*u,ty+131*u,8*u,0xa5191c20);
                String[] lines={d.aperture,d.shutter,d.iso.isEmpty()?"":"ISO "+d.iso};
                if(o.parameters)for(int i=0;i<3;i++)r.text(lines[i],tx,ty+(28+i*37)*u,235*u,25,Color.WHITE,true,false);break;}
            case 50:
                p.setColor(r.ink);c.drawCircle(m+26*u,base+79*u,23*u,p);
                r.text(author.isEmpty()?"F":author.substring(0,1).toUpperCase(Locale.ROOT),m+26*u,base+88*u,36*u,23,paper,true,true);
                r.text(author,m+65*u,base+88*u,full*.4f,25,r.ink,true,false);
                r.text(params,m+full*.50f,base+87*u,full*.50f,19,r.muted,false,false);break;
            default:break;
        }
        return out;
    }
    private static String defaultTitle(int style,String camera){switch(style){case 15:return "A NOTE FROM THE JOURNEY";case 16:case 44:return "THE PHOTO COLLECTION";case 23:case 24:case 25:case 38:return "Little moments, forever.";case 29:case 30:case 31:return "把生活，拍成电影。";case 32:case 33:return "A JOURNEY TO REMEMBER";case 34:return "Today, through my lens.";case 35:return "A MOMENT IN TIME";case 42:return "POSTAGE / A MEMORY";case 43:return "AFTER THE GOLDEN HOUR";case 55:return "THREE MOMENTS, ONE STORY";case 56:return "A CONVERSATION IN PICTURES";case 57:return "CONTACT SHEET / 01";default:return camera;}}
    private void collage(Bitmap first,List<Bitmap> extras,int style,int count,int margin,int width,String title,String params,String author){
        int columns=style==57?2:1,rows=(count+columns-1)/columns;float cellHeight=width*(style==57?.42f:.62f),gap=18*u;
        for(int i=0;i<count;i++){float x=margin+(i%columns)*width/(float)columns,y=margin+(i/columns)*cellHeight;RectF cell=new RectF(x+gap/2,y+gap/2,x+width/(float)columns-gap/2,y+cellHeight-gap/2);
            Bitmap image=i==0?first:i-1<extras.size()?extras.get(i-1):null;
            if(image!=null)photo(image,cell);else{text("＋ 选择照片",cell.centerX(),cell.centerY(),cell.width(),25,muted,false,true);}
            if(style==57)text(String.format(Locale.ROOT,"%02d",i+1),cell.left,cell.bottom-8*u,cell.width(),15,0xffe5b978,false,false);
        }
        float y=margin+rows*cellHeight;text(title,margin,y+54*u,width,28,ink,true,false);text(params.isEmpty()?author:params,margin,y+94*u,width,17,muted,false,false);
    }
    private void ticket(String title,String params,String author,float m,float y,float width,int style){
        float tear=m+width*.75f;paint.setColor(muted);paint.setStrokeWidth(u);paint.setPathEffect(new DashPathEffect(new float[]{8*u,7*u},0));canvas.drawLine(tear,y+17*u,tear,y+175*u,paint);paint.setPathEffect(null);
        text(style==33?"BOARDING / PHOTO PASS":"TRAVEL / PHOTO TICKET",m,y+47*u,width*.7f,14,muted,true,false);text(title,m,y+92*u,width*.7f,29,ink,true,false);text(author.isEmpty()?params:author,m,y+140*u,width*.7f,18,muted,false,false);
        text("01",tear+width*.125f,y+70*u,width*.21f,40,ink,true,true);
        for(int i=0;i<37;i++){float x=tear+18*u+i*4.7f*u;rect(x,y+91*u,x+(i%3==0?2:1)*u,y+144*u,ink);}
    }
    private void photo(Bitmap bitmap,RectF box){paint.setAlpha(255);float scale=Math.min(box.width()/bitmap.getWidth(),box.height()/bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;canvas.drawBitmap(bitmap,null,new RectF(box.centerX()-w/2,box.centerY()-h/2,box.centerX()+w/2,box.centerY()+h/2),paint);}
    private void text(String value,float x,float y,float width,float size,int color,boolean bold,boolean centered){paint.setColor(color);paint.setTextSize(Math.max(1,size*u));paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign(centered?Paint.Align.CENTER:Paint.Align.LEFT);String fitted=android.text.TextUtils.ellipsize(value,new android.text.TextPaint(paint),Math.max(1,width),android.text.TextUtils.TruncateAt.END).toString();canvas.drawText(fitted,x,y,paint);paint.setTextAlign(Paint.Align.LEFT);}
    private void rect(float l,float t,float r,float b,int color){paint.setColor(color);canvas.drawRect(l,t,r,b,paint);}
    private void round(float l,float t,float r,float b,float radius,int color){paint.setColor(color);canvas.drawRoundRect(l,t,r,b,radius,radius,paint);}
    private void stroke(float l,float t,float r,float b,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,width*u));rect(l,t,r,b,color);paint.setStyle(Paint.Style.FILL);}
    private void rule(float x,float y,float x2,float y2,int color,float width){paint.setColor(color);paint.setStrokeWidth(Math.max(1,width*u));canvas.drawLine(x,y,x2,y2,paint);}
}
