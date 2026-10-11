package dev.danielc.fujiapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Grid follows fitCenter image bounds, excluding the black bars. */
public final class MonitorGrid extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int frameWidth,frameHeight;
    public MonitorGrid(Context context,AttributeSet attrs){super(context,attrs);paint.setColor(0x99ffffff);paint.setStrokeWidth(getResources().getDisplayMetrics().density);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setFrameSize(int width,int height){frameWidth=width;frameHeight=height;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(frameWidth<=0||frameHeight<=0)return;
        float scale=Math.min(getWidth()/(float)frameWidth,getHeight()/(float)frameHeight);
        float w=frameWidth*scale,h=frameHeight*scale,left=(getWidth()-w)/2,top=(getHeight()-h)/2;
        for(int i=1;i<3;i++){canvas.drawLine(left+w*i/3,top,left+w*i/3,top+h,paint);canvas.drawLine(left,top+h*i/3,left+w,top+h*i/3,paint);}
    }
}
