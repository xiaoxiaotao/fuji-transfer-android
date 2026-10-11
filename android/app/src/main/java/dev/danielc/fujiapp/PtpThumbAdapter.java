package dev.danielc.fujiapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import dev.danielc.views.DownloadQueue;
import dev.danielc.views.ThumbAdapter;

public class PtpThumbAdapter extends ThumbAdapter {
    public PtpThumbAdapter(Context ctx) {
        super(ctx);
        queue = new Queue();setHasStableIds(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void sort() {
        // TODO:
        notifyDataSetChanged();
    }

    public void setupHolderFromInfo(ImageViewHolder holder, JSONObject info) {
        final int expectedHandle=holder.handle;
        ((Gallery)context).observeInfo(expectedHandle,info);
        holder.image.post(new Runnable() {
            @Override
            public void run() {
                if (holder.handle != expectedHandle) return;
                try {
                    String date=info.optString("dateCreated");if(date.length()>=8)date=date.substring(0,4)+"/"+date.substring(4,6)+"/"+date.substring(6,8);
                    holder.label.setText(info.getString("filename")+"\n"+String.format(java.util.Locale.CHINA,"%.1f MB · %s",info.optLong("compressedSize")/1048576.0,date));
                    int format = info.getInt("format_int");

                    if (format == Backend.PTP_OF_JPEG) {
                        holder.icon.setImageResource(R.drawable.baseline_landscape_24);
                    } else if (format == Backend.PTP_OF_MOV) {
                        holder.icon.setImageResource(R.drawable.baseline_movie_24);
                    } else if (format == Backend.PTP_OF_RAW) {
                        holder.icon.setImageResource(R.drawable.baseline_data_array_24);
                    } else {
                        holder.icon.setImageResource(R.drawable.baseline_question_mark_24);
                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void updateInfoOnHolder(int handle, JSONObject info) {
        if (info == null) return;
        ((Gallery)context).observeInfo(handle,info);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            for (ImageViewHolder holder : holders) if (holder.handle == handle) setupHolderFromInfo(holder,info);
        });
    }

    public static class Request {
        Context ctx;
        ImageViewHolder holder;
        int position;
        int object_id;
    }

    Queue queue;
    class Queue extends DownloadQueue<Request> {
        @Override
        public void perform(Request req) {
            if (stopDownloading||Backend.cGetKillSwitch()) return;
            if (req.holder.handle != req.object_id) return;
            JSONObject metadata=Backend.cPtpObjectServiceGet(req.object_id);
            String key=metadata==null?null:((Gallery)context).receiptKey(req.object_id,metadata);
            byte[] jpegByteArray=PhotoDiskCache.thumb(context,key);
            if(jpegByteArray==null){jpegByteArray=Backend.cFujiGetThumb(req.object_id);if(jpegByteArray!=null){if(key==null){metadata=Backend.cPtpObjectServiceGet(req.object_id);if(metadata!=null)key=((Gallery)context).receiptKey(req.object_id,metadata);}if(jpegByteArray.length>0)PhotoDiskCache.thumb(context,key,jpegByteArray);}}
            if(stopDownloading)return;
            if (jpegByteArray == null) {
                Backend.reportError(Backend.PTP_IO_ERR, "无法获取缩略图");
                return;
            } else if (req.holder.handle != req.object_id) return;
            else if (jpegByteArray.length == 0) {
                // Unable to find thumbnail - assume it's a folder or non-jpeg
                invalidThumb(req.holder.image.getContext(), req.holder);
                return;
            }
            loadThumb(req.holder, jpegByteArray, req.object_id);
        }
        @Override
        public boolean nothingToDo() {
            if(stopDownloading||Backend.cGetKillSwitch())return true;
            int rc = Backend.cPtpObjectServiceStep();
            if(stopDownloading)return true;
            if (rc < 0) {
                Backend.reportError(Backend.PTP_IO_ERR, "无法获取照片信息");
                stopRequestThread();
            }
            if (rc > 0) {
                updateInfoOnHolder(rc, Backend.cPtpObjectServiceGet(rc));
                return false;
            }
            return true;
        }
    }
    @Override
    public void imageClickHandler(ImageViewHolder holder) {
        if (((Gallery)context).onPhotoTap(holder.handle)) return;
        if(Viewer.isOpen()){android.widget.Toast.makeText(context,"上一张照片仍在读取，请稍候",android.widget.Toast.LENGTH_SHORT).show();return;}
        if (holder.isLoaded) {
            Intent intent = new Intent(holder.image.getContext(), Viewer.class);
            intent.putExtra("handle", holder.handle);
            holder.image.getContext().startActivity(intent);
        }
    }

    @Override public boolean imageLongClickHandler(ImageViewHolder holder) {
        ((Gallery)context).beginSelection(holder.handle); return true;
    }
    public void refreshSelection() { for(ImageViewHolder holder:holders)bindSelection(holder); }
    private void bindSelection(ImageViewHolder holder) {
        Gallery gallery=(Gallery)context;
        holder.label.setVisibility(gallery.detailsMode()?android.view.View.VISIBLE:android.view.View.GONE);
        holder.itemView.findViewById(R.id.download_badge).setVisibility(gallery.isSaved(holder.handle)?android.view.View.VISIBLE:android.view.View.GONE);
        boolean selected=gallery.isSelected(holder.handle);
        holder.itemView.findViewById(R.id.selection_border).setVisibility(selected?android.view.View.VISIBLE:android.view.View.GONE);
        android.widget.TextView check=holder.itemView.findViewById(R.id.selection_check);
        check.setVisibility(gallery.isSelecting()?android.view.View.VISIBLE:android.view.View.GONE);
        check.setText(selected?"✓":"○");
        check.setBackgroundResource(selected?R.drawable.ui_check_selected:R.drawable.ui_check_empty);
        holder.itemView.setContentDescription((selected?"已选择，":"")+"照片 "+holder.handle);
    }
    @Override
    public void queueImage(ImageViewHolder holder, int position) {
        Request req = new Request();
        req.ctx = context;
        req.holder = holder;
        req.position = position;
        req.object_id = ((Gallery)context).visibleHandle(position);
        holder.handle = req.object_id;
        bindSelection(holder);
        android.graphics.Bitmap cached=cachedThumbnail(req.object_id);
        if (cached != null) displayCached(holder,cached);
        else queue.enqueue(req);

        long start = System.nanoTime();

        JSONObject info = Backend.cPtpObjectServiceGet(holder.handle);
        if (info != null) {
            setupHolderFromInfo(holder, info);
        }
    }

    @Override
    public void cancelRequest(ImageViewHolder holder) {
        synchronized(queue.requests) {
        for (int i = queue.requests.size()-1; i >= 0; i--) {
            if (queue.requests.get(i).holder == holder) {
                queue.requests.remove(i);
            }
        }
        }
    }

    private int[] order=new int[0];
    public void updateOrder(){int[] next=((Gallery)context).visibleSnapshot();final int[] previous=order;order=next;androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback(){public int getOldListSize(){return previous.length;}public int getNewListSize(){return next.length;}public boolean areItemsTheSame(int a,int b){return previous[a]==next[b];}public boolean areContentsTheSame(int a,int b){return a==b;}},false).dispatchUpdatesTo(this);}
    @Override public long getItemId(int position){return Integer.toUnsignedLong(((Gallery)context).visibleHandle(position));}
    @Override
    public int getItemCount() {
        return ((Gallery)context).visibleCount();
    }
}
