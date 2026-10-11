package dev.danielc.fujiapp;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;

/** Local-only details and explicit derivative export. */
public final class PhotoStudio extends AppCompatActivity {
    private Uri source; private String name; private PhotoDetails details;
    private Bitmap photo, framed; private ImageView preview; private TextView info;
    private Button save, share; private Spinner styles; private boolean closed, exporting; private int style;
    static void open(android.content.Context c, Uri source, String name) {
        c.startActivity(new Intent(c,PhotoStudio.class).putExtra("target",source.toString()).putExtra("name",name));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String target=getIntent().getStringExtra("target"); name=getIntent().getStringExtra("name");
        if(target==null||name==null){finish();return;} source=Uri.parse(target);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,20,20,20);
        androidx.appcompat.widget.Toolbar bar=new androidx.appcompat.widget.Toolbar(this);bar.setTitle("照片详情与边框");root.addView(bar);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        info=new TextView(this);info.setText("正在读取原图参数…");info.setTextSize(15);content.addView(info);
        styles=new Spinner(this);styles.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"白色参数边框","深色参数边框","简洁白边"}));content.addView(styles);
        TextView note=new TextView(this);note.setText("导出 JPEG 副本，最长边约 2048 像素；不包含 GPS 和原图 EXIF，拍摄参数仅显示在边框上。");content.addView(note);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);content.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);save=new Button(this);save.setText("保存边框副本");share=new Button(this);share.setText("分享边框副本");actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));actions.addView(share,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        setContentView(root);setSupportActionBar(bar);getSupportActionBar().setDisplayHomeAsUpEnabled(true);MainActivity.applyInsets(getWindow(),root);enable(false);
        styles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){style=pos;render();}});
        save.setOnClickListener(v->export(false));share.setOnClickListener(v->export(true));
        new Thread(()->{
            Bitmap loaded=null;PhotoDetails metadata=null;String error=null;
            try{metadata=PhotoDetails.read(this,source,name);}catch(Exception e){metadata=new PhotoDetails();metadata.lines.add("拍摄参数不可读取；可预览的照片仍可添加边框。");}
            try{loaded=LocalMedia.decode(this,source,2048);if(loaded!=null && Math.max(loaded.getWidth(),loaded.getHeight())>2048){float factor=2048f/Math.max(loaded.getWidth(),loaded.getHeight());Bitmap scaled=Bitmap.createScaledBitmap(loaded,Math.max(1,Math.round(loaded.getWidth()*factor)),Math.max(1,Math.round(loaded.getHeight()*factor)),true);if(scaled!=loaded)loaded.recycle();loaded=scaled;}if(loaded==null)error="此格式不支持边框预览，请使用 JPEG 照片。";}catch(Exception|OutOfMemoryError e){error="照片读取失败，文件可能已移除或过大。";}
            final Bitmap bitmap=loaded;final PhotoDetails d=metadata;final String problem=error;
            runOnUiThread(()->{if(closed){if(bitmap!=null)bitmap.recycle();return;}details=d;photo=bitmap;info.setText(d.text()+(problem==null?"":"\n"+problem));render();});
        },"photo-details").start();
    }
    private void enable(boolean value){save.setEnabled(value);share.setEnabled(value);styles.setEnabled(!exporting);}
    private void render(){if(photo==null||exporting)return;Bitmap old=framed;try{framed=PhotoFrame.render(photo,details,style);preview.setImageBitmap(framed);enable(true);}catch(OutOfMemoryError e){enable(false);Toast.makeText(this,"内存不足，无法生成边框",Toast.LENGTH_LONG).show();}if(old!=null&&old!=framed)old.recycle();}
    private void export(boolean sharing){if(framed==null||exporting)return;
        if(!sharing && android.os.Build.VERSION.SDK_INT<29 && androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){androidx.core.app.ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},42);Toast.makeText(this,"允许保存权限后，请再次点击保存",Toast.LENGTH_LONG).show();return;}exporting=true;enable(false);final Bitmap image=framed;
        new Thread(()->{File file=null;Uri uri=null;String error=null;try{
            File folder=sharing?new File(getCacheDir(),"frames"):new File(PhotoStorage.stagingRoot(this,false),"fudge");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建保存目录");
            if(sharing){File[] old=folder.listFiles();if(old!=null)for(File cached:old)if(cached.getName().startsWith("Fuji-frame-") && System.currentTimeMillis()-cached.lastModified()>7L*24*60*60*1000)cached.delete();}
            file=new File(folder,"Fuji-frame-"+java.util.UUID.randomUUID()+".jpg");try(OutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IOException("图片编码失败");}
            if(sharing)uri=Uri.fromFile(file);else{String target=PhotoStorage.publish(this,file.getAbsolutePath(),false);uri=target.startsWith("content:")?Uri.parse(target):Uri.fromFile(new File(target));if(android.os.Build.VERSION.SDK_INT>=29)file.delete();}
        }catch(Exception e){error="导出失败："+e.getLocalizedMessage();if(file!=null)file.delete();}
        Frontend.print(error==null?"边框副本导出成功":"边框副本导出失败");
        final Uri result=uri;final String message=error;runOnUiThread(()->{exporting=false;if(closed){release();return;}enable(true);if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();else if(sharing)LocalMedia.share(this,result,"Fuji-frame.jpg");else Toast.makeText(this,"边框副本已保存到手机相册",Toast.LENGTH_LONG).show();});},"photo-frame-export").start();
    }
    private void release(){if(framed!=null){framed.recycle();framed=null;}if(photo!=null){photo.recycle();photo=null;}}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onDestroy(){closed=true;preview.setImageDrawable(null);if(!exporting)release();super.onDestroy();}
}
