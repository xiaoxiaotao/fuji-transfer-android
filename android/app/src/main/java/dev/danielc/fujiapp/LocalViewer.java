package dev.danielc.fujiapp;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.jsibbold.zoomage.ZoomageView;
/** Offline previews use the saved URI and never issue camera commands. */
public final class LocalViewer extends AppCompatActivity {
 private volatile boolean closed;private Bitmap shown;
 @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_viewer);setSupportActionBar(findViewById(R.id.ui_toolbar));MainActivity.applyInsets(getWindow(),findViewById(R.id.viewer_root));getSupportActionBar().setDisplayHomeAsUpEnabled(true);String name=getIntent().getStringExtra("name"),target=getIntent().getStringExtra("target");if(name==null||target==null){finish();return;}getSupportActionBar().setTitle(name);Uri uri=target.startsWith("content:")||target.startsWith("file:")?Uri.parse(target):Uri.fromFile(new java.io.File(target));findViewById(R.id.viewer_clear).setVisibility(View.GONE);((android.widget.Button)findViewById(R.id.viewer_save)).setText("详情 / 边框");findViewById(R.id.viewer_save).setOnClickListener(v->PhotoStudio.open(this,uri,name));findViewById(R.id.viewer_share).setOnClickListener(v->LocalMedia.share(this,uri,name));((TextView)findViewById(R.id.fileInfo)).setText("正在读取手机中的原文件…");new Thread(()->{Bitmap bitmap=null;String error=null;try{bitmap=LocalMedia.decode(this,uri,2560);if(bitmap==null)error="此格式暂不能在应用内预览，可分享给其他应用。";}catch(Exception|OutOfMemoryError e){error="无法读取照片，文件可能已被移除。";}final Bitmap result=bitmap;final String message=error;runOnUiThread(()->{if(closed){if(result!=null)result.recycle();return;}shown=result;((ZoomageView)findViewById(R.id.zoom_view)).setImageBitmap(result);((TextView)findViewById(R.id.fileInfo)).setText(message==null?"已保存到手机 · 离线预览\n"+name:message);});},"local-photo-preview").start();}
 @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
 @Override protected void onDestroy(){closed=true;if(shown!=null){((ZoomageView)findViewById(R.id.zoom_view)).setImageDrawable(null);shown.recycle();shown=null;}super.onDestroy();}
}
