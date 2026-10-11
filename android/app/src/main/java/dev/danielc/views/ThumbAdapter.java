// recyclerview image adapter to load images LIFO style
// Copyright 2023 Daniel C - https://github.com/petabyt/fujiapp
package dev.danielc.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import dev.danielc.fujiapp.Backend;
import dev.danielc.fujiapp.R;

public abstract class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.ImageViewHolder> {
    public Context context;
    public ThumbAdapter(Context ctx) {
        this.context = ctx;
    }

    private final android.util.LruCache<Integer, Bitmap> thumbnails = new android.util.LruCache<Integer, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(Integer key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    public Bitmap cachedThumbnail(int handle) { return thumbnails.get(handle); }
    public void displayCached(ImageViewHolder holder, Bitmap bitmap) { holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);holder.image.setImageBitmap(bitmap); holder.isLoaded=true; }
    public ArrayList<ImageViewHolder> holders = new ArrayList<>();

    public abstract void imageClickHandler(ImageViewHolder holder);
    public boolean imageLongClickHandler(ImageViewHolder holder) { return false; }
    public abstract void queueImage(ImageViewHolder holder, int position);
    public abstract void cancelRequest(ImageViewHolder holder);

    // Set up click event - navigate to viewer and send object handle
    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ImageViewHolder holder = ImageViewHolder.create(parent);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageClickHandler(holder);
            }
        });
        holder.itemView.setOnLongClickListener(v -> imageLongClickHandler(holder));
        return holder;
    }

    public void invalidThumb(Context ctx, ImageViewHolder holder) {
        final int expectedHandle=holder.handle;
        holder.isLoaded = false;
        holder.itemView.post(new Runnable() {
            @SuppressLint("UseCompatLoadingForDrawables")
            @Override
            public void run() {
                // TODO: Open a popup with some file information
                if (holder.handle != expectedHandle) return;
                holder.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                holder.image.setImageResource(R.drawable.baseline_landscape_24);
                holder.label.setText("此文件未提供预览");
            }
        });
    }

    public void loadThumb(ImageViewHolder holder, byte[] data) { loadThumb(holder, data, holder.handle); }
    public void loadThumb(ImageViewHolder holder, byte[] data, int expectedHandle) {
        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inScaled = true;
            opt.inDensity = 320;
            opt.inTargetDensity = 160;

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
            if (bitmap == null) {
                invalidThumb(holder.image.getContext(), holder);
                return;
            }
            thumbnails.put(expectedHandle, bitmap);
            holder.itemView.post(new Runnable() {
                @SuppressLint("UseCompatLoadingForDrawables")
                @Override
                public void run() {
                    if (holder.handle != expectedHandle) return;
                    holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    holder.image.setImageBitmap(bitmap);
                    holder.isLoaded = true;
                }
            });
        } catch (OutOfMemoryError e) {
            Backend.reportError(Backend.PTP_RUNTIME_ERR, "Out of memory");
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public void onBindViewHolder(ImageViewHolder holder, int position) {
        cancelRequest(holder); // Cancel any queued requests on this holder
        holder.image.setForeground(context.getDrawable(R.drawable.ripple));
        holder.image.setBackground(context.getDrawable(R.drawable.light_button));
        holder.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        holder.image.setImageResource(R.drawable.baseline_landscape_24);
        holder.icon.setImageBitmap(null);
        holder.isLoaded=false;
        holder.label.setText("");
        holder.itemView.setOnClickListener(v -> imageClickHandler(holder));
        if (!holders.contains(holder)) holders.add(holder);
        queueImage(holder, position);
    }

    @Override
    public void onViewRecycled(ImageViewHolder holder) {
        cancelRequest(holder);
        holder.handle=-1;
        holder.isLoaded=false;
        holder.image.setImageBitmap(null);
        holders.remove(holder);
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageView image;
        public ImageView icon;
        public FrameLayout frame;
        public volatile int handle;
        public String filename;
        public boolean isLoaded;
        public TextView label;

        public ImageViewHolder(View frame) {
            super(frame);
            this.handle = -1;
            this.isLoaded = false;
        }

        public static ImageViewHolder create(ViewGroup parent) {
            View root=LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image,parent,false);
            FrameLayout frame=root.findViewById(R.id.imageView);
            int columns=3;
            if(parent instanceof RecyclerView){RecyclerView.LayoutManager layout=((RecyclerView)parent).getLayoutManager();if(layout instanceof androidx.recyclerview.widget.GridLayoutManager)columns=((androidx.recyclerview.widget.GridLayoutManager)layout).getSpanCount();}
            int width=parent.getWidth()>0?parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight():parent.getResources().getDisplayMetrics().widthPixels;
            int gap=Math.round(6*parent.getResources().getDisplayMetrics().density);
            frame.getLayoutParams().height=Math.max(1,width/columns-gap);
            ImageViewHolder h=new ImageViewHolder(root);h.frame=frame;
            h.label=root.findViewById(R.id.frame_label);
            h.image=root.findViewById(R.id.frame_image);
            h.icon=root.findViewById(R.id.frame_icon);
            return h;
        }
    }
}
