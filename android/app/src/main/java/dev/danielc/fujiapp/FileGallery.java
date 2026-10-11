package dev.danielc.fujiapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Saved files stay available without a camera; only index changes trigger background I/O. */
public class FileGallery extends AppCompatActivity {
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor thumbnails=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String key,Bitmap image){return image.getByteCount();}};
    private final ArrayList<SavedLibrary.Photo> all=new ArrayList<>();
    private final LibraryAdapter adapter=new LibraryAdapter();
    private volatile boolean closed;
    private boolean loading,resumed,readFailed;
    private int filter,sort,unavailable,frameStyle=-1;
    private String search="";
    private long seenRevision=-1;
    private final Runnable searchChanged=()->applyFilter();
    private final Runnable observeLibrary=new Runnable(){public void run(){if(!resumed||closed)return;if(!loading&&SavedLibrary.revision()!=seenRevision)reload();updateCaption(adapter.differ.getCurrentList());ui.postDelayed(this,1000);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.library);setSupportActionBar(findViewById(R.id.ui_toolbar));getSupportActionBar().setTitle("照片与作品");
        frameStyle=getIntent().getIntExtra("frame_style",-1);if(frameStyle>=0){getSupportActionBar().setTitle("选择照片制作边框");getSupportActionBar().setDisplayHomeAsUpEnabled(true);filter=1;}
        MainActivity.applyInsets(getWindow(),findViewById(R.id.library_root));WorkbenchNav.attach(this,WorkbenchNav.LIBRARY);
        if(frameStyle>=0)findViewById(R.id.workbench_nav).setVisibility(View.GONE);
        sort=Math.max(0,Math.min(2,MySettings.getSetting(this,"library_sort",0)));
        RecyclerView grid=findViewById(R.id.library_grid);grid.setLayoutManager(new GridLayoutManager(this,3));grid.setAdapter(adapter);grid.setItemAnimator(null);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener(){public void onScrollStateChanged(RecyclerView view,int value){if(value==RecyclerView.SCROLL_STATE_DRAGGING)hideKeyboard();}});
        EditText field=findViewById(R.id.library_search);
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString().trim();ui.removeCallbacks(searchChanged);ui.postDelayed(searchChanged,150);}public void afterTextChanged(Editable e){}});
        field.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){ui.removeCallbacks(searchChanged);applyFilter();hideKeyboard();return true;}return false;});
        if(state!=null){filter=state.getInt("filter");field.setText(state.getString("search",""));}
        findViewById(R.id.library_sort).setOnClickListener(v->{PopupMenu menu=new PopupMenu(this,v);for(int i=0;i<LibraryOrdering.TITLES.length;i++)menu.getMenu().add(0,i,i,LibraryOrdering.TITLES[i]).setCheckable(true).setChecked(sort==i);menu.getMenu().setGroupCheckable(0,true,true);menu.setOnMenuItemClickListener(item->{sort=item.getItemId();MySettings.setSetting(this,"library_sort",sort);renderSort();applyFilter();((RecyclerView)findViewById(R.id.library_grid)).scrollToPosition(0);return true;});menu.show();});
        renderChips();renderSort();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);frameStyle=intent.getIntExtra("frame_style",-1);if(frameStyle>=0)filter=1;getSupportActionBar().setTitle(frameStyle>=0?"选择照片制作边框":"照片与作品");getSupportActionBar().setDisplayHomeAsUpEnabled(frameStyle>=0);findViewById(R.id.workbench_nav).setVisibility(frameStyle>=0?View.GONE:View.VISIBLE);renderChips();applyFilter();}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}
    private void renderSort(){((Button)findViewById(R.id.library_sort)).setText(LibraryOrdering.TITLES[sort]+" ▾");}
    @Override protected void onResume(){super.onResume();resumed=true;reload();ui.removeCallbacks(observeLibrary);ui.post(observeLibrary);}
    @Override protected void onStop(){resumed=false;ui.removeCallbacks(observeLibrary);ui.removeCallbacks(searchChanged);super.onStop();}
    private void reload(){
        if(loading||closed)return;loading=true;readFailed=false;
        if(all.isEmpty())((TextView)findViewById(R.id.library_count)).setText("正在核对已保存文件…");
        files.execute(()->{try{
            SavedLibrary store=SavedLibrary.get(this);store.importPrevious();long revision=SavedLibrary.revision();List<SavedLibrary.Photo> readable=new ArrayList<>();java.util.Set<String> seen=new java.util.HashSet<>();int missing=0;
            for(SavedLibrary.Photo photo:store.list()){if(closed)return;if(!seen.add(SavedLibrary.sourceKey(photo.uri())))continue;if(store.exists(photo))readable.add(photo);else missing++;}
            final int gone=missing;
            ui.post(()->{if(closed)return;loading=false;seenRevision=revision;all.clear();all.addAll(readable);unavailable=gone;applyFilter();});
        }catch(Exception e){ui.post(()->{if(closed)return;loading=false;readFailed=true;seenRevision=SavedLibrary.revision();((TextView)findViewById(R.id.library_count)).setText("照片库读取失败，请点击右上角刷新");if(all.isEmpty()){TextView empty=findViewById(R.id.library_empty);empty.setText("请稍后重试");empty.setVisibility(View.VISIBLE);}});}});
    }
    private void renderChips(){WorkbenchNav.chips(this,findViewById(R.id.library_chips),frameStyle>=0?new String[]{"可制作的照片"}:new String[]{"全部","照片","RAW","视频","作品"},frameStyle>=0?0:filter,value->{if(frameStyle<0)filter=value;renderChips();applyFilter();});}
    private void applyFilter(){
        if(closed)return;
        List<SavedLibrary.Photo> next=new ArrayList<>();for(SavedLibrary.Photo photo:all)if(MediaFile.matches(photo.name,filter==4?1:filter,search)&&(filter!=4||photo.name.startsWith("Fuji-frame-")))next.add(photo);
        Collections.sort(next,LibraryOrdering.comparator(sort));adapter.differ.submitList(next,()->{if(!closed)updateCaption(adapter.differ.getCurrentList());});updateCaption(next);
        TextView empty=findViewById(R.id.library_empty);empty.setText(loading&&all.isEmpty()?"正在读取手机中的照片…":all.isEmpty()?"还没有可用的已下载照片\n可以从创作首页选择手机相册照片，或从相机导入。":"没有匹配的文件\n试试其他格式或文件名。");empty.setVisibility(next.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void updateCaption(List<SavedLibrary.Photo> rows){
        if(readFailed||(loading&&all.isEmpty()))return;long bytes=0;for(SavedLibrary.Photo photo:rows)bytes+=Math.max(0,photo.size);
        DownloadTransfer.Run run=DownloadTransfer.current;
        String status=rows.size()+" 个文件 · "+String.format(Locale.CHINA,"%.1f MB",bytes/1048576.0)+(run!=null&&run.running?" · 下载中，自动更新":" · 可离线浏览")+(unavailable>0?"\n"+unavailable+" 个原文件已不可用":"");
        TextView caption=findViewById(R.id.library_count);if(!status.contentEquals(caption.getText()))caption.setText(status);
    }
    @Override public boolean onCreateOptionsMenu(Menu menu){menu.add(0,3,0,"从手机相册创作");menu.add(0,1,1,"相机导入任务");menu.add(0,2,1,"刷新照片库");return true;}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}if(item.getItemId()==3){startActivity(new Intent(this,FrameHome.class).putExtra("pick_photo",true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));return true;}if(item.getItemId()==1){DownloadTaskCenter.show(this,Gallery.getInstance());return true;}if(item.getItemId()==2){reload();return true;}return super.onOptionsItemSelected(item);}
    @Override public void onBackPressed(){if(frameStyle>=0)finish();else WorkbenchNav.open(this,WorkbenchNav.CREATE);}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("filter",filter);out.putString("search",search);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){closed=true;ui.removeCallbacksAndMessages(null);files.shutdownNow();thumbnails.shutdownNow();cache.evictAll();super.onDestroy();}

    private final class LibraryAdapter extends RecyclerView.Adapter<Holder>{
        final AsyncListDiffer<SavedLibrary.Photo> differ=new AsyncListDiffer<>(this,new DiffUtil.ItemCallback<SavedLibrary.Photo>(){
            public boolean areItemsTheSame(SavedLibrary.Photo a,SavedLibrary.Photo b){return a.id==b.id;}
            public boolean areContentsTheSame(SavedLibrary.Photo a,SavedLibrary.Photo b){return a.target.equals(b.target)&&a.name.equals(b.name)&&a.size==b.size&&a.saved==b.saved;}
        });
        LibraryAdapter(){setHasStableIds(true);}
        public int getItemCount(){return differ.getCurrentList().size();}
        public long getItemId(int position){return differ.getCurrentList().get(position).id;}
        public Holder onCreateViewHolder(ViewGroup parent,int type){View root=LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image,parent,false);FrameLayout image=root.findViewById(R.id.imageView);int width=parent.getWidth()>0?parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight():getResources().getDisplayMetrics().widthPixels-WorkbenchNav.dp(FileGallery.this,24);image.getLayoutParams().height=Math.max(1,width/3-WorkbenchNav.dp(FileGallery.this,6));return new Holder(root);}
        public void onBindViewHolder(Holder h,int position){
            if(h.job!=null)h.job.cancel(true);SavedLibrary.Photo photo=differ.getCurrentList().get(position);h.target=photo.target;
            h.label.setText(photo.name.startsWith("Fuji-frame-")?"边框作品\n"+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new java.util.Date(photo.saved)):photo.name+"\n"+String.format(Locale.CHINA,"%s · %.1f MB",photo.kind()==3?"视频":photo.kind()==2?"RAW":"照片",photo.size/1048576.0));
            h.image.setImageResource(photo.kind()==3?R.drawable.baseline_movie_24:R.drawable.baseline_landscape_24);h.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);h.itemView.setContentDescription(photo.name+"，已保存到手机");
            h.itemView.setOnClickListener(v->{if(photo.kind()==1){if(frameStyle>=0)PhotoStudio.open(FileGallery.this,photo.uri(),photo.name,frameStyle);else if(photo.name.startsWith("Fuji-frame-"))startActivity(new Intent(FileGallery.this,LocalViewer.class).putExtra("target",photo.target).putExtra("name",photo.name));else PhotoStudio.open(FileGallery.this,photo.uri(),photo.name);}else LocalMedia.open(FileGallery.this,photo.uri(),photo.name);});
            h.itemView.setOnLongClickListener(v->{new androidx.appcompat.app.AlertDialog.Builder(FileGallery.this).setTitle(photo.name).setItems(new String[]{"查看原文件","分享原文件","用其他应用打开"},(d,w)->{if(w==0&&photo.kind()==1)startActivity(new Intent(FileGallery.this,LocalViewer.class).putExtra("target",photo.target).putExtra("name",photo.name));else if(w==1)LocalMedia.share(FileGallery.this,photo.uri(),photo.name);else LocalMedia.open(FileGallery.this,photo.uri(),photo.name);}).show();return true;});
            Bitmap cached=cache.get(photo.target);if(cached!=null){show(h,cached);return;}
            h.job=thumbnails.submit(()->{Bitmap bitmap=LocalMedia.thumbnail(FileGallery.this,photo);if(bitmap==null||Thread.currentThread().isInterrupted()||closed){if(bitmap!=null)bitmap.recycle();return;}cache.put(photo.target,bitmap);ui.post(()->{if(!closed&&photo.target.equals(h.target))show(h,bitmap);});});
        }
        private void show(Holder h,Bitmap bitmap){h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);h.image.setImageBitmap(bitmap);}
        public void onViewRecycled(Holder h){h.target="";if(h.job!=null)h.job.cancel(true);h.image.setImageDrawable(null);thumbnails.purge();}
    }
    private static final class Holder extends RecyclerView.ViewHolder {final ImageView image;final TextView label;String target="";Future<?> job;Holder(View root){super(root);image=root.findViewById(R.id.frame_image);label=root.findViewById(R.id.frame_label);}}
}
