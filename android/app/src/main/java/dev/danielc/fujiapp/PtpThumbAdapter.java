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
    public void refreshSelection() { for(ImageViewHolder holder:holders)bindSelection(holder);for(HeaderHolder holder:headers)bindHeader(holder); }
    private void bindSelection(ImageViewHolder holder) {
        Gallery gallery=(Gallery)context;
        holder.label.setVisibility(gallery.detailsMode()?android.view.View.VISIBLE:android.view.View.GONE);
        android.widget.TextView status=holder.itemView.findViewById(R.id.download_badge);status.setVisibility(android.view.View.VISIBLE);status.setText(gallery.isSaved(holder.handle)?"✓ 原文件":"预览");status.setContentDescription(gallery.isSaved(holder.handle)?"原文件已保存到手机":"相机预览，原文件尚未保存");
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
        req.object_id = rows.get(position).handle;
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

    private java.util.List<PhotoGroups.Row> rows=new java.util.ArrayList<>();
    boolean isHeader(int position){return position>=0&&position<rows.size()&&rows.get(position).header;}
    int photoHandle(int position){return position>=0&&position<rows.size()&&!isHeader(position)?rows.get(position).handle:0;}
    void selectDragRange(int from,int to){Gallery gallery=(Gallery)context;for(int i=Math.min(from,to);i<=Math.max(from,to)&&i<rows.size();i++)if(i>=0&&!isHeader(i))gallery.addSelection(rows.get(i).handle);gallery.refreshSelectionUi();}
    private final java.util.List<HeaderHolder> headers=new java.util.ArrayList<>();
    private void bindHeader(HeaderHolder holder){if(holder.row==null)return;PhotoGroups.Row row=holder.row;int selected=0;for(int handle:row.members)if(((Gallery)context).isSelected(handle))selected++;holder.label.setText(row.date+"   ·   "+row.members.length+" 张"+(selected==0?"": " · 已选 "+selected)+"    "+(selected==row.members.length?"取消本组":"选择本组"));}
    private static final class HeaderHolder extends ImageViewHolder {PhotoGroups.Row row;HeaderHolder(android.widget.TextView view){super(view);label=view;}}
    @Override public int getItemViewType(int position){return isHeader(position)?1:0;}
    @Override public ImageViewHolder onCreateViewHolder(android.view.ViewGroup parent,int type){if(type==0)return super.onCreateViewHolder(parent,type);android.widget.TextView label=new android.widget.TextView(context);label.setTextColor(androidx.core.content.ContextCompat.getColor(context,R.color.ui_text));label.setTextSize(15);int pad=Math.round(12*context.getResources().getDisplayMetrics().density);label.setPadding(pad,pad*2,pad,pad);label.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,-2));return new HeaderHolder(label);}
    @Override public void onBindViewHolder(ImageViewHolder holder,int position){if(holder instanceof HeaderHolder){PhotoGroups.Row row=rows.get(position);HeaderHolder header=(HeaderHolder)holder;header.row=row;if(!headers.contains(header))headers.add(header);bindHeader(header);holder.itemView.setOnClickListener(v->((Gallery)context).selectGroup(row.members));return;}super.onBindViewHolder(holder,position);}
    @Override public void onViewRecycled(ImageViewHolder holder){if(holder instanceof HeaderHolder){headers.remove((HeaderHolder)holder);((HeaderHolder)holder).row=null;holder.itemView.setOnClickListener(null);return;}super.onViewRecycled(holder);}
    public void updateOrder(){Gallery gallery=(Gallery)context;final java.util.List<PhotoGroups.Row> previous=rows,next=PhotoGroups.build(gallery.visibleSnapshot(),gallery.photoDates(),gallery.groupDates());rows=next;androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback(){public int getOldListSize(){return previous.size();}public int getNewListSize(){return next.size();}public boolean areItemsTheSame(int a,int b){return previous.get(a).id()==next.get(b).id();}public boolean areContentsTheSame(int a,int b){return java.util.Arrays.equals(previous.get(a).members,next.get(b).members);}},false).dispatchUpdatesTo(this);}
    @Override public long getItemId(int position){return rows.get(position).id();}
    @Override public int getItemCount(){return rows.size();}
}
