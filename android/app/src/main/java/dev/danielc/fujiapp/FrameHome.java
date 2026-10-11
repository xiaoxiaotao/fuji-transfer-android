package dev.danielc.fujiapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creation is available without initializing Bluetooth, Wi-Fi or the native camera session. */
public final class FrameHome extends AppCompatActivity {
    private static final int PICK_PHOTO=501;
    private static final int[][] GROUPS={{8,9,10},{0,1,2,7},{3,4,5,6}};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Bitmap> samples=new ArrayList<>(), recentBitmaps=new ArrayList<>();
    private final List<ImageView> sampleViews=new ArrayList<>();
    private final List<View> templateCards=new ArrayList<>();
    private LinearLayout content, recent;
    private TextView recentNote;
    private int pendingStyle=8, generation;
    private volatile boolean closed;

    private int dp(int value){return WorkbenchNav.dp(this,value);}
    private GradientDrawable surface(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
    private TextView text(String value,int size,int color){TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);return label;}
    private LinearLayout column(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);return row;}
    private LinearLayout.LayoutParams spacing(int width,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.topMargin=dp(top);return p;}
    private Button button(String title,int color,int ink){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(ink);b.setBackground(surface(color,16));b.setBackgroundTintList(null);b.setMinHeight(dp(50));return b;}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.frame_home);
        MainActivity.applyInsets(getWindow(),findViewById(R.id.creation_root));WorkbenchNav.attach(this,WorkbenchNav.CREATE);
        pendingStyle=state==null?8:state.getInt("pending_style",8);
        content=findViewById(R.id.creation_content);
        TextView wordmark=text("Fuji图库",24,0xfff4f5f7);wordmark.setTypeface(null,Typeface.BOLD);content.addView(wordmark);
        content.addView(text("边框创作 · 让照片成为作品",12,0xffadbdb3));
        LinearLayout hero=column();hero.setPadding(dp(16),dp(16),dp(16),dp(16));
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff34483c,0xff1c2922}));
        ((GradientDrawable)hero.getBackground()).setCornerRadius(dp(24));
        TextView eyebrow=text("FRAME COLLECTION / 01",10,0xffb8d8c2);eyebrow.setLetterSpacing(.12f);hero.addView(eyebrow);
        TextView heading=text("让照片成为作品",25,0xfff4f5f7);heading.setTypeface(null,Typeface.BOLD);hero.addView(heading,spacing(-1,-2,10));
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setContentDescription("光影参数边框示例，点击选照片制作");hero.addView(cover,spacing(-1,dp(144),12));sampleViews.add(cover);cover.setOnClickListener(v->chooseSource(9));
        LinearLayout caption=new LinearLayout(this);caption.setGravity(Gravity.CENTER_VERTICAL);
        TextView feature=text("光影参数",14,0xffeef2ea);caption.addView(feature,new LinearLayout.LayoutParams(0,-2,1));caption.addView(text("照片背景 / 分区排版  ↗",11,0xffbdd0c2));hero.addView(caption,spacing(-1,-2,8));content.addView(hero,spacing(-1,-2,20));
        Button start=button("＋  从手机相册开始创作",0xffb7d3b2,0xff18251d);start.setId(R.id.creation_pick);start.setOnClickListener(v->{pendingStyle=8;pickPhoto();});content.addView(start,spacing(-1,dp(54),14));
        LinearLayout sources=new LinearLayout(this);
        Button local=button("已导入照片  ↗",0xff202d25,0xffd7e4da);local.setOnClickListener(v->pickImported(8));
        Button camera=button("从富士相机导入  ↗",0xff202d25,0xffd7e4da);camera.setOnClickListener(v->WorkbenchNav.open(this,WorkbenchNav.CAMERA));
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(48),1);half.setMarginEnd(dp(8));sources.addView(local,half);sources.addView(camera,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(sources,spacing(-1,-2,10));
        section("边框精选","11 款可调整模板",28);
        LinearLayout categories=new LinearLayout(this);content.addView(categories,spacing(-1,-2,10));
        HorizontalScrollView carousel=new HorizontalScrollView(this);carousel.setHorizontalScrollBarEnabled(false);LinearLayout cards=new LinearLayout(this);carousel.addView(cards);content.addView(carousel,spacing(-1,-2,12));
        for(int style=0;style<PhotoFrame.NAMES.length;style++){
            final int selected=style;LinearLayout card=column();card.setTag(style);card.setPadding(dp(10),dp(10),dp(10),dp(12));card.setBackground(surface(0xff1d2922,18));
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(image,new LinearLayout.LayoutParams(-1,dp(132)));sampleViews.add(image);
            TextView title=text(PhotoFrame.NAMES[style],14,0xffeef2ea);title.setTypeface(null,Typeface.BOLD);card.addView(title,spacing(-1,-2,10));card.addView(text(style>=8?"照片背景 · 悬浮排版":"纸张留白 · 经典排版",10,0xffa1b3a7),spacing(-1,-2,5));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(166),-2);cp.setMarginEnd(dp(10));cards.addView(card,cp);templateCards.add(card);card.setContentDescription(PhotoFrame.NAMES[style]+"，选择照片制作");card.setFocusable(true);card.setOnClickListener(v->chooseSource(selected));
        }
        renderCategories(categories,carousel,0);
        section("最近照片","查看全部  ↗",26).setOnClickListener(v->WorkbenchNav.open(this,WorkbenchNav.LIBRARY));
        recentNote=text("正在读取可用照片…",12,0xffadbdb3);content.addView(recentNote,spacing(-1,-2,8));recent=new LinearLayout(this);content.addView(recent,spacing(-1,-2,12));
        TextView footer=text("从相册到作品，再到分享。",11,0xff819488);footer.setGravity(Gravity.CENTER);content.addView(footer,spacing(-1,-2,24));
        loadSamples();handleImportIntent();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleImportIntent();}
    private void handleImportIntent(){if(getIntent().getBooleanExtra("pick_photo",false)){getIntent().removeExtra("pick_photo");pendingStyle=8;pickPhoto();}}
    private LinearLayout section(String title,String aside,int top){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView label=text(title,20,0xfff4f5f7);label.setTypeface(null,Typeface.BOLD);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.addView(text(aside,11,0xffadbdb3));content.addView(row,spacing(-1,-2,top));return row;}
    private void renderCategories(LinearLayout chips,HorizontalScrollView scroll,int category){WorkbenchNav.chips(this,chips,new String[]{"光影卡片","参数留白","艺术胶片"},category,value->renderCategories(chips,scroll,value));for(int i=0;i<templateCards.size();i++){boolean visible=false;for(int style:GROUPS[category])if(i==style)visible=true;templateCards.get(i).setVisibility(visible?View.VISIBLE:View.GONE);}scroll.post(()->scroll.scrollTo(0,0));}
    private void chooseSource(int style){pendingStyle=style;new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("制作 · "+PhotoFrame.NAMES[style]).setItems(new String[]{"从手机相册选择","使用已导入照片","从富士相机获取照片"},(dialog,index)->{if(index==0)pickPhoto();else if(index==1)pickImported(style);else WorkbenchNav.open(this,WorkbenchNav.CAMERA);}).setNegativeButton("取消",null).show();}
    private void pickImported(int style){startActivity(new Intent(this,FileGallery.class).putExtra("frame_style",style));}
    private void pickPhoto(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),PICK_PHOTO);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"系统相册不可用，请使用已导入照片",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_PHOTO||result!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();final int style=pendingStyle;
        boolean retained=false;
        try{
            if((data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0){
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);retained=true;
            }
        }catch(SecurityException ignored){android.util.Log.i("frame-home","Provider granted temporary photo access");}
        final boolean persist=retained;
        worker.execute(()->{
            String name="照片.jpg";long size=0;
            try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
                if(cursor!=null&&cursor.moveToFirst()){
                    int nameColumn=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),sizeColumn=cursor.getColumnIndex(OpenableColumns.SIZE);
                    if(nameColumn>=0){String value=cursor.getString(nameColumn);if(value!=null&&!value.isEmpty())name=value;}
                    if(sizeColumn>=0&&!cursor.isNull(sizeColumn))size=cursor.getLong(sizeColumn);
                }
            }catch(RuntimeException ignored){}
            if(persist)try{SavedLibrary library=SavedLibrary.get(this);boolean known=false;String key=SavedLibrary.sourceKey(uri);for(SavedLibrary.Photo stored:library.list())if(key.equals(SavedLibrary.sourceKey(stored.uri()))){known=true;break;}if(!known)library.record(uri.toString(),name,size,System.currentTimeMillis());}catch(RuntimeException e){android.util.Log.w("frame-home","Could not index selected photo",e);}
            final String title=name;
            runOnUiThread(()->{if(!closed){PhotoStudio.open(this,uri,title,style);loadRecent();}});
        });
    }
    private void loadSamples(){worker.execute(()->{List<Bitmap> rendered=new ArrayList<>();Bitmap photo=null;try(InputStream in=getAssets().open("pexels-thevibrantmachine-3066867.jpg")){BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=8;photo=BitmapFactory.decodeStream(in,null,decode);if(photo==null)return;PhotoDetails details=new PhotoDetails();for(int n=-1;n<PhotoFrame.NAMES.length;n++){PhotoFrame.Options options=new PhotoFrame.Options();options.style=n==-1?9:n;options.parameters=false;options.title="LIGHT & FRAME";options.paper=n==1?2:0;rendered.add(PhotoFrame.render(photo,details,options));}}catch(Exception|OutOfMemoryError e){android.util.Log.w("frame-home","Template samples unavailable",e);}finally{if(photo!=null)photo.recycle();}runOnUiThread(()->{if(closed){for(Bitmap bitmap:rendered)bitmap.recycle();return;}samples.addAll(rendered);for(int i=0;i<rendered.size()&&i<sampleViews.size();i++)sampleViews.get(i).setImageBitmap(rendered.get(i));});});}
    @Override protected void onResume(){super.onResume();loadRecent();}
    private void loadRecent(){final int request=++generation;worker.execute(()->{List<SavedLibrary.Photo> photos=new ArrayList<>();List<Bitmap> images=new ArrayList<>();try{SavedLibrary library=SavedLibrary.get(this);library.importPrevious();java.util.Set<String> seen=new java.util.HashSet<>();for(SavedLibrary.Photo photo:library.list()){if(closed)break;if(photo.kind()==1&&seen.add(SavedLibrary.sourceKey(photo.uri()))&&library.exists(photo)){photos.add(photo);images.add(LocalMedia.thumbnail(this,photo));if(photos.size()==3)break;}}}catch(Exception e){android.util.Log.w("frame-home","Recent photos unavailable",e);}runOnUiThread(()->{if(closed||request!=generation){for(Bitmap bitmap:images)if(bitmap!=null)bitmap.recycle();return;}recent.removeAllViews();for(Bitmap bitmap:recentBitmaps)bitmap.recycle();recentBitmaps.clear();recentNote.setText(photos.isEmpty()?"还没有导入照片，可以直接从手机相册开始。":"点选照片，直接进入边框创作");for(int i=0;i<photos.size();i++){SavedLibrary.Photo photo=photos.get(i);Bitmap bitmap=images.get(i);LinearLayout tile=column();ImageView view=new ImageView(this);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setImageResource(R.drawable.baseline_landscape_24);if(bitmap!=null){view.setImageBitmap(bitmap);recentBitmaps.add(bitmap);}tile.addView(view,new LinearLayout.LayoutParams(-1,dp(84)));TextView name=text(photo.name.startsWith("Fuji-frame-")?"边框作品":photo.name,10,0xffadbdb3);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name,spacing(-1,-2,8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginEnd(dp(8));recent.addView(tile,lp);tile.setContentDescription(photo.name.startsWith("Fuji-frame-")?"查看边框作品":"用 "+photo.name+" 制作边框");tile.setOnClickListener(v->{if(photo.name.startsWith("Fuji-frame-"))startActivity(new Intent(this,LocalViewer.class).putExtra("target",photo.target).putExtra("name",photo.name));else PhotoStudio.open(this,photo.uri(),photo.name);});}});});}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("pending_style",pendingStyle);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){moveTaskToBack(true);}
    @Override protected void onDestroy(){closed=true;worker.shutdownNow();for(ImageView image:sampleViews)image.setImageDrawable(null);recent.removeAllViews();for(Bitmap bitmap:samples)bitmap.recycle();for(Bitmap bitmap:recentBitmaps)bitmap.recycle();super.onDestroy();}
}
