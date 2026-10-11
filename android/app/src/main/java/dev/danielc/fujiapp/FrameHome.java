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
    private static final int[][] GROUPS=PhotoFrame.GROUPS;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Bitmap> samples=new ArrayList<>(), recentBitmaps=new ArrayList<>();
    private final List<ImageView> sampleViews=new ArrayList<>();
    private final List<Integer> sampleStyles=new ArrayList<>();
    private ImageView banner;
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
        LinearLayout header=findViewById(R.id.creation_header);
        TextView emblem=text("F",22,0xff191c20);emblem.setTypeface(null,Typeface.BOLD);emblem.setGravity(Gravity.CENTER);emblem.setBackground(surface(0xfff4f5f7,32));header.addView(emblem,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView wordmark=text("Fuji图库",22,0xfff4f5f7);wordmark.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);titleParams.setMarginStart(dp(14));header.addView(wordmark,titleParams);
        ImageButton help=new ImageButton(this);help.setImageResource(R.drawable.baseline_question_mark_24);help.setColorFilter(0xffeeeeee);help.setBackground(surface(0xff34393f,32));help.setContentDescription("使用帮助");header.addView(help,new LinearLayout.LayoutParams(dp(36),dp(36)));help.setOnClickListener(v->startActivity(new Intent(this,Help.class)));
        FrameLayout hero=new FrameLayout(this);hero.setBackground(surface(0xff303438,10));hero.setClipToOutline(true);
        banner=new ImageView(this);banner.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(banner,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x10000000,0x44000000,0x70000000}));hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout wording=column();wording.setGravity(Gravity.CENTER);TextView heading=text("Fuji图库",29,0xffffffff);heading.setTypeface(null,Typeface.BOLD);heading.setLetterSpacing(.12f);heading.setGravity(Gravity.CENTER);wording.addView(heading);TextView subtitle=text("为你的照片，留下光影",13,0xffffffff);subtitle.setLetterSpacing(.10f);wording.addView(subtitle,spacing(-2,-2,8));hero.addView(wording,new FrameLayout.LayoutParams(-1,-1));
        TextView mark=text(PhotoFrame.NAMES.length+" 款边框  /  富士传图",10,0xfff0f1ed);mark.setGravity(Gravity.CENTER);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-1,dp(28),Gravity.BOTTOM);mp.bottomMargin=dp(8);hero.addView(mark,mp);
        hero.setContentDescription("从手机相册选图，开始边框创作");hero.setOnClickListener(v->{pendingStyle=8;pickPhoto();});content.addView(hero,spacing(-1,dp(192),0));
        LinearLayout sources=new LinearLayout(this);sources.setGravity(Gravity.CENTER_VERTICAL);sources.setBackground(surface(0xff292d31,10));sources.setPadding(dp(2),0,dp(2),0);
        String[] sourceTitles={"＋ 相册选图","▧ 本地照片","↗ 富士相机"};
        for(int i=0;i<3;i++){final int sourceIndex=i;Button action=button(sourceTitles[i],0x00000000,i==0?0xffd6ed00:0xffe1e3e4);action.setTextSize(12);action.setPadding(0,0,0,0);action.setMinimumWidth(0);action.setMinWidth(0);if(i==0)action.setId(R.id.creation_pick);sources.addView(action,new LinearLayout.LayoutParams(0,dp(42),1));action.setOnClickListener(v->{if(sourceIndex==0){pendingStyle=8;pickPhoto();}else if(sourceIndex==1)pickImported(8);else WorkbenchNav.open(this,WorkbenchNav.CAMERA);});}content.addView(sources,spacing(-1,-2,12));
        final View[] anchors=new View[GROUPS.length];HorizontalScrollView categoryScroll=new HorizontalScrollView(this);categoryScroll.setHorizontalScrollBarEnabled(false);LinearLayout categoryRow=new LinearLayout(this);categoryScroll.addView(categoryRow);content.addView(categoryScroll,spacing(-1,-2,16));
        for(int i=0;i<GROUPS.length;i++){final int category=i;Button chip=button(PhotoFrame.CATEGORIES[i],0xff303539,0xffe1e3e4);chip.setTextSize(12);chip.setMinHeight(0);chip.setMinimumHeight(0);chip.setPadding(dp(10),0,dp(10),0);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(96),dp(38));cp.setMarginEnd(dp(8));categoryRow.addView(chip,cp);chip.setOnClickListener(v->{if(anchors[category]!=null)((android.widget.ScrollView)findViewById(R.id.creation_scroll)).smoothScrollTo(0,anchors[category].getTop()-dp(12));});}
        String[] names=PhotoFrame.CATEGORIES;
        for(int group=0;group<GROUPS.length;group++){
            final int initialStyle=GROUPS[group][0];LinearLayout section=section(names[group],GROUPS[group].length+" 款",24);anchors[group]=section;
            ImageButton upload=new ImageButton(this);upload.setImageResource(R.drawable.ui_upload);upload.setColorFilter(0xffdddddd);upload.setBackgroundColor(0x00000000);upload.setContentDescription("选择照片制作"+names[group]);section.addView(upload,new LinearLayout.LayoutParams(dp(40),dp(40)));upload.setOnClickListener(v->{pendingStyle=initialStyle;pickPhoto();});
            HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);LinearLayout row=new LinearLayout(this);scroll.addView(row);content.addView(scroll,spacing(-1,-2,10));
            for(int style:GROUPS[group]){
                LinearLayout card=column();ImageView sample=new ImageView(this);sample.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(sample,new LinearLayout.LayoutParams(-1,dp(138)));sampleViews.add(sample);sampleStyles.add(style);
                TextView caption=text(PhotoFrame.NAMES[style],14,0xffe0e1e2);card.addView(caption,spacing(-1,-2,9));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(164),-2);cp.setMarginEnd(dp(14));row.addView(card,cp);
                card.setContentDescription(PhotoFrame.NAMES[style]+"，选择照片制作");card.setFocusable(true);card.setOnClickListener(v->{pendingStyle=style;pickPhoto();});card.setOnLongClickListener(v->{chooseSource(style);return true;});
            }
        }
        section("最近照片","本地",24).setOnClickListener(v->WorkbenchNav.open(this,WorkbenchNav.LIBRARY));
        recentNote=text("正在读取可用照片…",12,0xffb5b8ba);content.addView(recentNote,spacing(-1,-2,8));recent=new LinearLayout(this);content.addView(recent,spacing(-1,-2,12));
        loadSamples();handleImportIntent();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleImportIntent();}
    private void handleImportIntent(){if(getIntent().getBooleanExtra("pick_photo",false)){getIntent().removeExtra("pick_photo");pendingStyle=8;pickPhoto();}}
    private LinearLayout section(String title,String badge,int top){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(title,20,0xffe4e5e6);label.setTypeface(null,Typeface.BOLD);row.addView(label);
        TextView tag=text(badge,11,0xff222600);tag.setTypeface(null,Typeface.BOLD);tag.setPadding(dp(6),dp(3),dp(6),dp(3));tag.setBackground(surface(0xffd6ed00,8));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.setMarginStart(dp(8));row.addView(tag,bp);row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));content.addView(row,spacing(-1,-2,top));return row;
    }
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
    private void loadSamples(){worker.execute(()->{
        List<Bitmap> photos=new ArrayList<>(), rendered=new ArrayList<>();
        String[] assets={"studio/forest.png","studio/coast.png","pexels-thevibrantmachine-3066867.jpg"};
        try{
            for(String asset:assets)try(InputStream in=getAssets().open(asset)){BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=4;Bitmap photo=BitmapFactory.decodeStream(in,null,decode);if(photo!=null)photos.add(photo);}
            if(photos.isEmpty())return;
            PhotoDetails details=new PhotoDetails();
            for(int i=0;i<sampleStyles.size();i++){PhotoFrame.Options options=new PhotoFrame.Options();options.style=sampleStyles.get(i);options.parameters=false;options.title="Photography";options.paper=PhotoFrame.defaultPaper(options.style);List<Bitmap> exampleExtras=new ArrayList<>();for(int j=1;j<6;j++)exampleExtras.add(photos.get((i+j)%photos.size()));rendered.add(PhotoFrame.render(photos.get(i%photos.size()),details,options,exampleExtras));}
        }catch(Exception|OutOfMemoryError e){android.util.Log.w("frame-home","Template samples unavailable",e);}
        runOnUiThread(()->{if(closed){for(Bitmap bitmap:photos)bitmap.recycle();for(Bitmap bitmap:rendered)bitmap.recycle();return;}samples.addAll(photos);samples.addAll(rendered);if(photos.size()>1)banner.setImageBitmap(photos.get(1));for(int i=0;i<rendered.size()&&i<sampleViews.size();i++)sampleViews.get(i).setImageBitmap(rendered.get(i));});
    });}
    @Override protected void onResume(){super.onResume();loadRecent();}
    private void loadRecent(){final int request=++generation;worker.execute(()->{List<SavedLibrary.Photo> photos=new ArrayList<>();List<Bitmap> images=new ArrayList<>();try{SavedLibrary library=SavedLibrary.get(this);library.importPrevious();java.util.Set<String> seen=new java.util.HashSet<>();for(SavedLibrary.Photo photo:library.list()){if(closed)break;if(photo.kind()==1&&seen.add(SavedLibrary.sourceKey(photo.uri()))&&library.exists(photo)){photos.add(photo);images.add(LocalMedia.thumbnail(this,photo));if(photos.size()==3)break;}}}catch(Exception e){android.util.Log.w("frame-home","Recent photos unavailable",e);}runOnUiThread(()->{if(closed||request!=generation){for(Bitmap bitmap:images)if(bitmap!=null)bitmap.recycle();return;}recent.removeAllViews();for(Bitmap bitmap:recentBitmaps)bitmap.recycle();recentBitmaps.clear();recentNote.setText(photos.isEmpty()?"还没有导入照片，可以直接从手机相册开始。":"点选照片，直接进入边框创作");for(int i=0;i<photos.size();i++){SavedLibrary.Photo photo=photos.get(i);Bitmap bitmap=images.get(i);LinearLayout tile=column();ImageView view=new ImageView(this);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setImageResource(R.drawable.baseline_landscape_24);if(bitmap!=null){view.setImageBitmap(bitmap);recentBitmaps.add(bitmap);}tile.addView(view,new LinearLayout.LayoutParams(-1,dp(84)));TextView name=text(photo.name.startsWith("Fuji-frame-")?"边框作品":photo.name,10,0xffadbdb3);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name,spacing(-1,-2,8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginEnd(dp(8));recent.addView(tile,lp);tile.setContentDescription(photo.name.startsWith("Fuji-frame-")?"查看边框作品":"用 "+photo.name+" 制作边框");tile.setOnClickListener(v->{if(photo.name.startsWith("Fuji-frame-"))startActivity(new Intent(this,LocalViewer.class).putExtra("target",photo.target).putExtra("name",photo.name));else PhotoStudio.open(this,photo.uri(),photo.name);});}});});}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("pending_style",pendingStyle);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){moveTaskToBack(true);}
    @Override protected void onDestroy(){closed=true;worker.shutdownNow();for(ImageView image:sampleViews)image.setImageDrawable(null);banner.setImageDrawable(null);recent.removeAllViews();for(Bitmap bitmap:samples)bitmap.recycle();for(Bitmap bitmap:recentBitmaps)bitmap.recycle();super.onDestroy();}
}
