package dev.danielc.fujiapp;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Paginated local history with a live transfer summary and session-safe retry actions. */
final class DownloadTaskCenter {
    static void show(Activity activity,Gallery gallery){new DownloadTaskCenter(activity,gallery).open();}
    private final Activity activity;
    private final Gallery gallery;
    private final DownloadTasks store;
    private final Dialog dialog;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final TasksAdapter adapter=new TasksAdapter();
    private TextView summary,empty;
    private Button retry,remaining,stop,more,recover;
    private int limit=60;
    private boolean loading,closed;
    private final androidx.lifecycle.LifecycleEventObserver lifecycle;
    private final Runnable poll=()->refresh();
    private DownloadTaskCenter(Activity activity,Gallery gallery){this.activity=activity;this.gallery=gallery;store=DownloadTasks.get(activity);dialog=new Dialog(activity);lifecycle=(source,event)->{if(event==androidx.lifecycle.Lifecycle.Event.ON_DESTROY)dialog.dismiss();};}
    private int dp(int size){return Math.round(size*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(androidx.core.content.ContextCompat.getColor(activity,R.color.ui_text));return t;}
    private Button button(String title){Button b=new Button(activity);b.setText(title);b.setTextSize(13);b.setPadding(dp(8),0,dp(8),0);b.setAllCaps(false);b.setTextColor(androidx.core.content.ContextCompat.getColor(activity,R.color.ui_text));b.setBackgroundResource(R.drawable.light_button);return b;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void open(){
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=column();box.setPadding(dp(16),dp(20),dp(16),dp(12));box.setBackgroundResource(R.drawable.ui_card);
        LinearLayout header=new LinearLayout(activity);header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title=label("下载任务",23);title.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        Button close=button("关闭");close.setOnClickListener(v->dialog.dismiss());header.addView(close,new LinearLayout.LayoutParams(dp(72),dp(48)));box.addView(header);
        summary=label("正在读取下载记录…",13);summary.setPadding(0,dp(12),0,dp(12));box.addView(summary);
        empty=label("",14);box.addView(empty);
        RecyclerView list=new RecyclerView(activity);list.setLayoutManager(new LinearLayoutManager(activity));list.setAdapter(adapter);list.setItemAnimator(null);box.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        more=button("加载更早的记录");more.setOnClickListener(v->{limit+=60;refresh();});box.addView(more,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));
        LinearLayout actions=new LinearLayout(activity);actions.setPadding(0,dp(12),0,0);
        retry=button("重试失败项");remaining=button("继续已停止项");
        retry.setOnClickListener(v->{if(gallery!=null)gallery.retryTasks(DownloadTask.State.FAILED);refresh();});
        remaining.setOnClickListener(v->{if(gallery!=null)gallery.retryTasks(DownloadTask.State.STOPPED);refresh();});
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(48),1);half.setMarginEnd(dp(8));actions.addView(retry,half);actions.addView(remaining,new LinearLayout.LayoutParams(0,dp(48),1));box.addView(actions);
        recover=button("恢复上次未完成下载");recover.setOnClickListener(v->{if(gallery!=null)gallery.recoverPreviousDownloads();});box.addView(recover,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        stop=button("停止剩余下载");stop.setOnClickListener(v->{if(gallery!=null)gallery.requestTransferStop();else if(DownloadTransfer.current!=null)DownloadTransfer.current.stop=true;summary.setText("当前文件完成后停止剩余任务…");});LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));stopParams.topMargin=dp(8);box.addView(stop,stopParams);
        TextView hint=label("关闭此窗口不会停止下载。重连同一已配对相机后，可核对并恢复未完成下载。旧版未记录照片身份的任务需要重新选择。",11);hint.setTextColor(androidx.core.content.ContextCompat.getColor(activity,R.color.ui_muted));hint.setPadding(0,dp(12),0,0);box.addView(hint);
        dialog.setContentView(box);dialog.setOnDismissListener(d->{closed=true;handler.removeCallbacksAndMessages(null);worker.shutdown();if(activity instanceof androidx.lifecycle.LifecycleOwner)((androidx.lifecycle.LifecycleOwner)activity).getLifecycle().removeObserver(lifecycle);});
        if(activity instanceof androidx.lifecycle.LifecycleOwner)((androidx.lifecycle.LifecycleOwner)activity).getLifecycle().addObserver(lifecycle);
        dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(24),(int)(activity.getResources().getDisplayMetrics().heightPixels*0.85f));}
        refresh();
    }
    private void refresh(){
        if(closed||loading)return;loading=true;handler.removeCallbacks(poll);
        worker.execute(()->{
            try {
                List<DownloadTasks.Row> rows=store.page(Long.MAX_VALUE,limit+1);
                int failures=gallery==null?0:store.countRetryable(gallery.downloadSession,DownloadTask.State.FAILED);
                int stopped=gallery==null?0:store.countRetryable(gallery.downloadSession,DownloadTask.State.STOPPED);
                int pending=gallery==null?0:store.recoverable(gallery.recoveryCamera(),gallery.downloadSession).size();
                boolean hasMore=rows.size()>limit;if(hasMore)rows.remove(rows.size()-1);
                handler.post(()->{
                    if(closed)return;loading=false;
                    adapter.rows=rows;adapter.notifyDataSetChanged();
                    empty.setText(rows.isEmpty()?"还没有下载任务。先在图库选择照片并下载。":"");empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
                    more.setVisibility(hasMore?View.VISIBLE:View.GONE);
                    boolean ready=gallery!=null&&gallery.canStartTransfers();
                    recover.setText("恢复上次未完成 · "+pending);recover.setEnabled(ready&&pending>0);recover.setAlpha(recover.isEnabled()?1f:0.4f);
                    retry.setText("重试失败 · "+failures);retry.setEnabled(ready&&failures>0);retry.setAlpha(retry.isEnabled()?1f:0.4f);
                    remaining.setText("继续剩余 · "+stopped);remaining.setEnabled(ready&&stopped>0);remaining.setAlpha(remaining.isEnabled()?1f:0.4f);
                    DownloadTransfer.Run service=DownloadTransfer.current;
                    stop.setVisibility(service!=null&&service.running?View.VISIBLE:View.GONE);
                    DownloadTask active=gallery==null?(DownloadTransfer.current==null?null:DownloadTransfer.current.active):gallery.activeTask;
                    stop.setEnabled(service!=null&&!service.stop);
                    summary.setText(gallery!=null&&gallery.isTransferring()&&gallery.isStopRequested()?"当前文件完成后停止剩余任务…":active!=null?active.name+" · "+status(active)+"\n"+(gallery!=null?gallery.transferSummary():service.summary()+"\n"+(service.meter==null?"":service.meter.detail())):gallery!=null?gallery.transferSummary():"手机上的下载记录 · 最新记录在上方");
                    handler.postDelayed(poll,1000);
                });
            }catch(Exception e){handler.post(()->{if(closed)return;loading=false;summary.setText("无法读取任务记录："+e.getMessage());});}
        });
    }
    private static String status(DownloadTask task){switch(task.state()){
        case RUNNING:return "下载中 "+task.progress()+"%";
        case SAVING:return "正在保存到手机";
        case DONE:return "已保存";
        case SKIPPED:return "已存在，已跳过";
        case FAILED:return "失败";
        case STOPPED:return "已停止";
        default:return "等待下载";
    }}
    private final class TasksAdapter extends RecyclerView.Adapter<TaskHolder>{
        List<DownloadTasks.Row> rows=new ArrayList<>();
        TasksAdapter(){setHasStableIds(true);}
        @Override public long getItemId(int position){return rows.get(position).task.id;}
        @Override public int getItemCount(){return rows.size();}
        @Override public TaskHolder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=column();row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackgroundResource(R.drawable.ui_tile);RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);params.bottomMargin=dp(8);row.setLayoutParams(params);
            TextView name=label("",15),state=label("",13),detail=label("",11);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);detail.setMaxLines(3);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);state.setPadding(0,dp(6),0,dp(4));detail.setTextColor(androidx.core.content.ContextCompat.getColor(activity,R.color.ui_muted));
            ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);row.addView(name);row.addView(state);row.addView(progress,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(4)));row.addView(detail);
            return new TaskHolder(row,name,state,detail,progress);
        }
        @Override public void onBindViewHolder(TaskHolder h,int position){
            DownloadTasks.Row row=rows.get(position);DownloadTask task=row.task;
            DownloadTask active=gallery==null?(DownloadTransfer.current==null?null:DownloadTransfer.current.active):gallery.activeTask;if(active!=null&&active.id==task.id)task=active;
            h.name.setText(task.name);h.state.setText(status(task));h.state.setTextColor(Color.parseColor(task.state()==DownloadTask.State.FAILED?"#F1AAA2":task.state()==DownloadTask.State.DONE?"#A8E5BD":"#F0F2EE"));
            h.progress.setVisibility(task.state()==DownloadTask.State.RUNNING||task.state()==DownloadTask.State.SAVING?View.VISIBLE:View.GONE);h.progress.setIndeterminate(task.state()==DownloadTask.State.SAVING);h.progress.setProgress(task.progress());
            String session=gallery!=null&&gallery.downloadSession.equals(row.session)?"当前连接":"历史记录";
            h.detail.setText(session+" · 尝试 "+task.attempts()+" 次"+(task.error().isEmpty()?"":"\n"+task.error()));
        }
    }
    private static class TaskHolder extends RecyclerView.ViewHolder{
        final TextView name,state,detail;final ProgressBar progress;
        TaskHolder(View item,TextView name,TextView state,TextView detail,ProgressBar progress){super(item);this.name=name;this.state=state;this.detail=detail;this.progress=progress;}
    }
}
