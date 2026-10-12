package dev.danielc.fujiapp;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;
import org.json.JSONObject;

/** Task UI may be reopened while the app-owned serial export continues. */
public final class FrameBatchActivity extends AppCompatActivity {
 private LinearLayout previewRow;private final List<android.graphics.Bitmap> images=new ArrayList<>();private int previewGeneration;
 private FrameBatch job;private TextView status,details;private Button start,retry,cancel;private boolean closed;
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 private final Runnable poll=new Runnable(){public void run(){if(closed)return;if(job!=null){status.setText(job.progress());details.setText(job.details());start.setEnabled(!job.running()&&job.hasWork(false));retry.setEnabled(!job.running()&&job.hasWork(true));cancel.setEnabled(job.running());}handler.postDelayed(this,500);}};
 private int dp(int n){return WorkbenchNav.dp(this,n);}
 @Override protected void onCreate(Bundle state){super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(12),dp(20),dp(12));root.setBackgroundColor(0xff191c20);LinearLayout outer=new LinearLayout(this);outer.setBackgroundColor(0xff191c20);outer.addView(root,new LinearLayout.LayoutParams(-1,-1));setContentView(outer);MainActivity.applyInsets(getWindow(),outer);
  TextView title=text("批量出片",24);root.addView(title);Button back=button("返回创作");root.addView(back);back.setOnClickListener(v->finish());status=text("正在读取任务…",18);root.addView(status);root.addView(text("逐张保存，每张使用自己的拍摄参数。取消会停止后续照片，已保存作品保留。",12));
  LinearLayout actions=new LinearLayout(this);root.addView(actions);start=button("开始 / 继续");retry=button("重试失败");cancel=button("取消");start.setTextColor(0xffd6ed00);for(Button b:new Button[]{start,retry,cancel}){actions.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));b.setEnabled(false);}start.setOnClickListener(v->runJob(false));retry.setOnClickListener(v->runJob(true));cancel.setOnClickListener(v->{if(job!=null){job.cancel();status.setText("正在停止，当前照片完成后取消…");}});
  root.addView(text("前三张成品预览 · 后续照片使用同一边框",12));previewRow=new LinearLayout(this);root.addView(previewRow,new LinearLayout.LayoutParams(-1,dp(126)));Button history=button("选择历史任务");root.addView(history);history.setOnClickListener(v->loadHistory(true));ScrollView scroll=new ScrollView(this);details=text("",13);scroll.addView(details);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));String raw=getIntent().getStringExtra("batch");if(raw!=null)try{job=FrameBatch.open(this,new JSONObject(raw));loadPreview();}catch(Exception e){status.setText("任务数据无法读取");}else loadHistory(false);
 }
 private void runJob(boolean retryFailed){if(job!=null&&!job.start(retryFailed))Toast.makeText(this,"另一个批量任务正在保存，请先完成或取消",Toast.LENGTH_LONG).show();handler.removeCallbacks(poll);handler.post(poll);}
 private void loadHistory(boolean choose){android.content.Context c=getApplicationContext();FrameBatch.WORK.execute(()->{List<JSONObject> jobs=FrameBatch.list(c);runOnUiThread(()->{if(closed)return;if(jobs.isEmpty()){status.setText("还没有批量任务，请在编辑器导出面板选择批量套框。");return;}if(!choose){job=FrameBatch.open(c,jobs.get(0));loadPreview();return;}String[] names=new String[jobs.size()];for(int i=0;i<names.length;i++)names[i]=new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(jobs.get(i).optLong("created")))+" · "+jobs.get(i).optJSONArray("items").length()+" 张";new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("批量任务").setItems(names,(dialog,index)->{job=FrameBatch.open(c,jobs.get(index));loadPreview();}).setNegativeButton("取消",null).show();});});}
 private void loadPreview(){final int generation=++previewGeneration;final JSONObject snapshot=job.snapshot();android.content.Context app=getApplicationContext();FrameBatch.WORK.execute(()->{List<android.graphics.Bitmap> bitmaps=FrameBatch.previews(app,snapshot);runOnUiThread(()->{if(closed||generation!=previewGeneration){for(android.graphics.Bitmap b:bitmaps)if(b!=null)b.recycle();return;}previewRow.removeAllViews();for(android.graphics.Bitmap b:images)b.recycle();images.clear();for(int i=0;i<bitmaps.size();i++){android.graphics.Bitmap b=bitmaps.get(i);ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setContentDescription("第 "+(i+1)+" 张成品预览");if(b!=null){images.add(b);v.setImageBitmap(b);}else v.setImageResource(R.drawable.baseline_landscape_24);previewRow.addView(v,new LinearLayout.LayoutParams(0,-1,1));}});});}
 private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(0xffe9ece5);v.setPadding(0,dp(10),0,dp(10));return v;}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(0xffe4e9df);b.setBackgroundTintList(null);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xff303b39);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0xff506057);b.setBackground(bg);b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setMinHeight(dp(42));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.topMargin=dp(8);lp.bottomMargin=dp(8);b.setLayoutParams(lp);return b;}
 @Override protected void onResume(){super.onResume();handler.post(poll);}
 @Override protected void onPause(){super.onPause();handler.removeCallbacks(poll);}
 @Override protected void onDestroy(){closed=true;handler.removeCallbacks(poll);if(previewRow!=null)previewRow.removeAllViews();for(android.graphics.Bitmap b:images)b.recycle();images.clear();super.onDestroy();}
}
