package dev.danielc.fujiapp;
import android.app.Activity;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;

/** Connection-owned scalar snapshot; refresh sends read commands only. */
final class CameraConsole {
 private static CameraInfo snapshotOwner;
 private static CameraParameters snapshot;
 private static boolean reading;
 private final Activity owner;
 private final LinearLayout root;
 private final TextView[] cards=new TextView[8];
 private final TextView notice;
 private final Button refresh;
 private CameraInfo renderedOwner;
 private CameraParameters renderedData;
 private boolean renderedReading;
 CameraConsole(Activity owner,LinearLayout root){
  this.owner=owner;this.root=root;
  TextView title=new TextView(owner);title.setText("拍摄控制台");title.setTextSize(20);title.setTextColor(owner.getColor(R.color.ui_text));root.addView(title);
  notice=new TextView(owner);notice.setTextSize(12);notice.setTextColor(owner.getColor(R.color.ui_muted));root.addView(notice);
  for(int row=0;row<4;row++){
   LinearLayout line=new LinearLayout(owner);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);root.addView(line,lp);
   for(int col=0;col<2;col++){
    final int i=row*2+col;TextView card=new TextView(owner);cards[i]=card;
    card.setBackgroundResource(R.drawable.ui_panel);card.setTextSize(16);card.setTextColor(owner.getColor(R.color.ui_text));card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setMinHeight(dp(100));
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(col==1)cp.leftMargin=dp(8);line.addView(card,cp);card.setOnClickListener(v->details(i));
   }
  }
  refresh=new Button(owner,null,0,R.style.UiTextButton);refresh.setText("读取相机参数");refresh.setGravity(android.view.Gravity.CENTER);root.addView(refresh,2,new LinearLayout.LayoutParams(-1,dp(48)));refresh.setOnClickListener(v->refresh());
  render(null);
 }
 private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
 void render(CameraInfo info){
  CameraParameters data=info!=null&&info==snapshotOwner?snapshot:null;
  if(renderedOwner==info&&renderedData==data&&renderedReading==reading&&cards[0].getText().length()>0)return;
  renderedOwner=info;renderedData=data;renderedReading=reading;
  notice.setText(reading?"正在读取 · 暂停图库请求":data==null?"读取当前参数后显示真实数值 · 本阶段只读":data.summary()+"\n参数快照 · "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.CHINA).format(new java.util.Date(data.readAt))+" · 点击卡片查看状态");
  for(int i=0;i<8;i++){
   String label=CameraParameters.LABELS[i],value=data==null?"待读取":data.display(i),state=data==null?"尚未查询相机":data.status(i)==0?"已读取 · 只读显示":"点击查看原因";
   android.text.SpannableString text=new android.text.SpannableString(label+"\n"+value+"\n"+state);
   text.setSpan(new android.text.style.RelativeSizeSpan(.8f),0,label.length(),0);
   text.setSpan(new android.text.style.ForegroundColorSpan(owner.getColor(R.color.ui_muted)),0,label.length(),0);
   text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),label.length()+1,label.length()+1+value.length(),0);
   text.setSpan(new android.text.style.RelativeSizeSpan(.7f),label.length()+value.length()+2,text.length(),0);cards[i].setText(text);
  }
  refresh.setEnabled(info!=null&&!reading);refresh.setText(reading?"正在读取…":data==null?"读取相机参数":"刷新参数");
 }
 private void details(int index){
  CameraParameters data=renderedData;String text=data==null?"点击读取相机参数。不同连接模式可提供的属性不同。":data.status(index)==0?"当前值："+data.display(index)+"\n原始值：0x"+Long.toHexString(data.value(index))+"（"+data.value(index)+"）"+(index==1?"\n解码方式："+(data.fuji?"富士快门编码":"标准 PTP 单位"):"")+"\n属性码：0x"+Integer.toHexString(data.code(index))+"\n访问状态："+data.access(index)+"\n本版只读取参数，调整功能需进一步验证，暂未开放。":"属性码=0x"+Integer.toHexString(data.code(index))+"\n错误码="+data.status(index)+"，PTP 响应=0x"+Integer.toHexString(data.response(index))+"\n返回数据长度="+data.payloadLength(index)+" 字节\n相机可能未在此连接模式开放该属性。可复制设置页诊断继续适配。";
  new AlertDialog.Builder(owner).setTitle(CameraParameters.LABELS[index]).setMessage(text).setPositiveButton("知道了",null).show();
 }
 private void refresh(){
  CameraInfo info=Gallery.currentInfo();DownloadTransfer.Run download=DownloadTransfer.current;
  if(info==null)return;
  if((download!=null&&download.running)||Viewer.isOpen()){Toast.makeText(owner,"请先结束下载或大图预览",Toast.LENGTH_SHORT).show();return;}
  if(!RemoteSession.acquire()){Toast.makeText(owner,"相机会话正在使用，请稍后读取",Toast.LENGTH_SHORT).show();return;}
  reading=true;render(info);Gallery.pauseAll();Handler ui=new Handler(Looper.getMainLooper());
  new Thread(()->{
   CameraParameters result=null;String error=null;
   try{
    if(!Gallery.awaitPaused(15000))throw new Exception("图库仍在读取，请稍后重试");
    result=new CameraParameters(Backend.cReadCameraParameters(),System.currentTimeMillis(),info.isFuji());
    if(result.failure()!=0){Backend.reportError(Backend.PTP_IO_ERR,"读取控制台参数时通信失败，请重新连接");error="通信失败，请重新连接相机";}
   }catch(Exception|LinkageError e){error=e.getMessage();Frontend.print("控制台读取未完成："+error);}
   finally{RemoteSession.release();}
   final CameraParameters received=result;final String issue=error;
   ui.post(()->{
    reading=false;
    if(Gallery.currentInfo()==info&&received!=null){snapshotOwner=info;snapshot=received;}
    Gallery.resumeAll();
    if(!owner.isFinishing()&&!owner.isDestroyed()){render(Gallery.currentInfo());if(issue!=null)Toast.makeText(owner,issue,Toast.LENGTH_LONG).show();else if(received!=null&&received.readCount()==0)new AlertDialog.Builder(owner).setTitle("当前连接未返回拍摄参数").setMessage("已根据连接协议读取参数，并尝试富士光圈和快门属性。当前连接模式可能仅提供图库传图。\n\n请点击参数卡片查看响应，或复制连接诊断继续适配；应用不会自动切换相机拍摄模式。").setPositiveButton("复制诊断",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)owner.getSystemService(android.content.Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("控制台诊断",Frontend.getConnectionDiagnostic()));Toast.makeText(owner,"诊断已复制",Toast.LENGTH_SHORT).show();}).setNegativeButton("关闭",null).show();}
   });
  },"fuji-console-read").start();
 }
}
