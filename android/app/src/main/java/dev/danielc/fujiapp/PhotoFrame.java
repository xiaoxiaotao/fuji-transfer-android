package dev.danielc.fujiapp;
import android.graphics.*;

/** Bounded share image, with upright source pixels and no changes to the original. */
final class PhotoFrame {
    static Bitmap render(Bitmap photo, PhotoDetails details, int style) {
        int margin = Math.max(24, photo.getWidth()/24);
        int footer = style == 2 ? margin : Math.max(140, photo.getWidth()/8);
        Bitmap output = Bitmap.createBitmap(photo.getWidth()+margin*2, photo.getHeight()+margin+footer, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        output.eraseColor(style == 1 ? Color.rgb(22,24,23) : Color.WHITE);
        canvas.drawBitmap(photo, margin, margin, null);
        if (style != 2) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(style == 1 ? Color.WHITE : Color.rgb(30,40,35));
            paint.setTextSize(Math.max(18, photo.getWidth()/40f));
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float y = photo.getHeight()+margin+footer*0.40f;
            String camera = details.camera.isEmpty() ? "Fuji图库" : details.camera;
            drawLine(canvas, camera, margin, y, output.getWidth()-margin*2, paint);
            paint.setTypeface(Typeface.DEFAULT); paint.setTextSize(Math.max(16, photo.getWidth()/48f));
            drawLine(canvas, details.exposure, margin, y+footer*0.30f, output.getWidth()-margin*2, paint);
        }
        return output;
    }
    private static void drawLine(Canvas canvas,String text,float x,float y,int width,Paint paint) {
        CharSequence fitted=android.text.TextUtils.ellipsize(text,new android.text.TextPaint(paint),width,android.text.TextUtils.TruncateAt.END);
        canvas.drawText(fitted.toString(),x,y,paint);
    }
}
