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
    private Button save;
    private Bitmap ambient;private ImageView backdrop;
    private final PhotoFrame.Options options=new PhotoFrame.Options();
    private final java.util.List<View> editors=new java.util.ArrayList<>();
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh=()->render();
    private Bitmap small; private LinearLayout templateRow;
    private final java.util.List<Bitmap> templateBitmaps=new java.util.ArrayList<>();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(0xffeef2ea);v.setPadding(0,dp(12),0,dp(8));return v;}
    private android.graphics.drawable.GradientDrawable background(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void highlight(LinearLayout row,int selected){for(int n=0;n<row.getChildCount();n++){View b=row.getChildAt(n);int id=b.getTag() instanceof Integer?(Integer)b.getTag():n;android.graphics.drawable.GradientDrawable bg=background(0xff33373b,12);bg.setStroke(dp(id==selected?2:1),id==selected?0xffd6ed00:0xff585d62);b.setBackground(bg);}}
    private void editorEnabled(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)editorEnabled(((android.view.ViewGroup)view).getChildAt(i),enabled);}
 private boolean closed, exporting; private int exportPreset=1;
    static void open(android.content.Context c, Uri source, String name) {
        c.startActivity(new Intent(c,PhotoStudio.class).putExtra("target",source.toString()).putExtra("name",name));
    }
    static void open(android.content.Context c, Uri source, String name, int style) {
        c.startActivity(new Intent(c,PhotoStudio.class).putExtra("target",source.toString()).putExtra("name",name).putExtra("style",style));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences prefs=getSharedPreferences("frame_studio",0);
        options.style=Math.max(0,Math.min(PhotoFrame.NAMES.length-1,prefs.getInt("style",8)));options.paper=Math.max(0,Math.min(4,prefs.getInt("paper",0)));options.spacing=Math.max(3,Math.min(15,prefs.getInt("spacing",6)));options.sidebarRight=prefs.getBoolean("sidebar_right",false);options.blur=Math.max(1,Math.min(8,prefs.getInt("blur",4)));options.corners=Math.max(0,Math.min(36,prefs.getInt("corners",18)));options.shadow=Math.max(0,Math.min(32,prefs.getInt("shadow",18)));options.author=prefs.getString("author","");options.parameters=prefs.getBoolean("parameters",true);
        if(!prefs.getBoolean("light_frames_seen",false))options.style=8;
        int requested=getIntent().getIntExtra("style",-1);if(state==null&&requested>=0&&requested<PhotoFrame.NAMES.length)options.style=requested;
        if(state!=null)options.title=state.getString("title","");
        String target=getIntent().getStringExtra("target"); name=getIntent().getStringExtra("name");
        if(target==null||name==null){finish();return;} source=Uri.parse(target);
        FrameLayout canvas=new FrameLayout(this);canvas.setBackgroundColor(0xff191c20);
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setColorFilter(0xa8000000,android.graphics.PorterDuff.Mode.SRC_ATOP);canvas.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);canvas.addView(root,new FrameLayout.LayoutParams(-1,-1));
        androidx.appcompat.widget.Toolbar bar=new androidx.appcompat.widget.Toolbar(this);bar.setTitle("编辑图片");bar.setTitleTextColor(0xffffffff);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        save=new Button(this);save.setText("↓ 导出图片");save.setAllCaps(false);save.setTextSize(13);save.setTextColor(0xffffffff);save.setPadding(dp(12),0,dp(12),0);save.setBackground(background(0xff62696d,10));save.setBackgroundTintList(null);androidx.appcompat.widget.Toolbar.LayoutParams exportParams=new androidx.appcompat.widget.Toolbar.LayoutParams(dp(108),dp(40),Gravity.END);exportParams.setMarginEnd(dp(8));bar.addView(save,exportParams);
        preview=new com.jsibbold.zoomage.ZoomageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setPadding(dp(18),dp(16),dp(18),dp(16));preview.setContentDescription("照片预览，支持双指缩放");root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(0,dp(10),0,0);android.graphics.drawable.GradientDrawable sheetBackground=background(0xed303539,24);sheetBackground.setCornerRadii(new float[]{dp(24),dp(24),dp(24),dp(24),0,0,0,0});sheetBackground.setStroke(dp(1),0xff555c60);sheet.setBackground(sheetBackground);LinearLayout.LayoutParams sheetSize=new LinearLayout.LayoutParams(-1,dp(304));root.addView(sheet,sheetSize);
        View handle=new View(this);handle.setBackground(background(0xff737a7e,4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(30),dp(3));hp.gravity=Gravity.CENTER_HORIZONTAL;sheet.addView(handle,hp);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(16));scroll.addView(content);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(8),dp(4),dp(8),dp(4));sheet.addView(tabs,new LinearLayout.LayoutParams(-1,dp(54)));
        final LinearLayout[] panels=new LinearLayout[6];String[] tabNames={"模板","布局","参数","主题","阴影","边距"};
        for(int i=0;i<6;i++){
            panels[i]=new LinearLayout(this);panels[i].setOrientation(LinearLayout.VERTICAL);panels[i].setVisibility(i==0?View.VISIBLE:View.GONE);content.addView(panels[i]);
            final int index=i;LinearLayout tab=new LinearLayout(this);tab.setOrientation(LinearLayout.VERTICAL);tab.setGravity(Gravity.CENTER);TextView titleLabel=new TextView(this);titleLabel.setText(tabNames[i]);titleLabel.setTextSize(13);titleLabel.setTypeface(null,android.graphics.Typeface.BOLD);titleLabel.setGravity(Gravity.CENTER);titleLabel.setTextColor(i==0?0xffffffff:0xff999fa3);tab.addView(titleLabel,new LinearLayout.LayoutParams(-1,dp(32)));View underline=new View(this);underline.setBackground(background(i==0?0xffffffff:0x00000000,2));tab.addView(underline,new LinearLayout.LayoutParams(dp(22),dp(3)));tabs.addView(tab,new LinearLayout.LayoutParams(0,-1,1));tab.setContentDescription(tabNames[i]);tab.setFocusable(true);
            tab.setOnClickListener(v->{for(int n=0;n<6;n++){panels[n].setVisibility(n==index?View.VISIBLE:View.GONE);LinearLayout item=(LinearLayout)tabs.getChildAt(n);((TextView)item.getChildAt(0)).setTextColor(n==index?0xffffffff:0xff999fa3);item.getChildAt(1).setBackground(background(n==index?0xffffffff:0x00000000,2));}scroll.scrollTo(0,0);});
        }
        editors.add(tabs);
        root.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{int height=Math.max(dp(164),Math.min(dp(330),(bottom-top)*44/100));if(sheet.getLayoutParams().height!=height){sheet.getLayoutParams().height=height;sheet.requestLayout();}});
        panels[0].addView(label("选择边框",15));
        android.widget.HorizontalScrollView templates=new android.widget.HorizontalScrollView(this);templates.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);templates.addView(row);panels[0].addView(templates);
        templateRow=row;
        for(int i=0;i<PhotoFrame.NAMES.length;i++){final int index=i;LinearLayout card=new LinearLayout(this);card.setTag(index);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(6),dp(6),dp(6),dp(6));ImageView sample=new ImageView(this);sample.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(sample,new LinearLayout.LayoutParams(-1,dp(90)));TextView caption=label(PhotoFrame.NAMES[i],12);caption.setGravity(android.view.Gravity.CENTER);caption.setPadding(0,dp(5),0,0);card.addView(caption);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(110),dp(146));cp.setMargins(0,dp(8),dp(8),dp(8));row.addView(card,cp);card.setOnClickListener(v->{if(exporting)return;options.style=index;options.paper=index==1?2:(index==0||index==2?1:0);highlight(row,index);render();});}highlight(row,options.style);editors.add(row);LinearLayout categories=new LinearLayout(this);panels[0].addView(categories,1);String[] categoryNames={"光影卡片","参数留白","艺术胶片"};for(int i=0;i<3;i++){final int category=i;Button chip=new Button(this);styleButton(chip);chip.setText(categoryNames[i]);chip.setTextSize(12);chip.setAllCaps(false);categories.addView(chip,new LinearLayout.LayoutParams(0,dp(42),1));chip.setOnClickListener(v->showCategory(row,templates,categories,category));}showCategory(row,templates,categories,templateCategory(options.style));editors.add(categories);
        panels[1].addView(label("参数布局",15));LinearLayout layouts=new LinearLayout(this);panels[1].addView(layouts);String[] placement={"参数在左","参数在右","底部卡片"};for(int i=0;i<3;i++){final int placementIndex=i;Button button=new Button(this);styleButton(button);button.setText(placement[i]);button.setTextSize(12);button.setAllCaps(false);layouts.addView(button,new LinearLayout.LayoutParams(0,dp(44),1));button.setOnClickListener(v->{options.style=placementIndex==2?8:9;options.sidebarRight=placementIndex==1;highlight(row,options.style);showCategory(row,templates,categories,0);render();});}editors.add(layouts);
        panels[3].addView(label("主题与纸色",15));
        LinearLayout colors=new LinearLayout(this);panels[3].addView(colors);TextView paperHint=label("光影系列自动使用照片模糊背景；纸色控制卡片底色。胶卷与宽银幕固定黑底。",11);paperHint.setTextColor(0xffa2a6aa);panels[3].addView(paperHint);String[] colorNames={"暖白","雪白","墨黑","鼠尾草","沙色"};
        for(int i=0;i<colorNames.length;i++){final int index=i;Button chip=new Button(this);chip.setText(colorNames[i]);chip.setTextSize(12);chip.setTextColor(i==2?0xffeeeeee:0xff33373b);chip.setBackground(background(PhotoFrame.PAPERS[i],12));chip.setBackgroundTintList(null);chip.setPadding(0,0,0,0);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(44),1);cp.setMargins(dp(3),dp(6),dp(3),dp(8));colors.addView(chip,cp);chip.setOnClickListener(v->{if(exporting)return;options.paper=index;render();});}editors.add(colors);
        TextView spacing=label("留白宽度 · "+options.spacing+"%",12);panels[5].addView(label("边距与留白",15));panels[5].addView(spacing);SeekBar width=new SeekBar(this);width.setMax(12);width.setProgress(options.spacing-3);panels[5].addView(width);editors.add(width);width.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){render();}public void onProgressChanged(SeekBar v,int n,boolean user){options.spacing=n+3;spacing.setText("留白宽度 · "+options.spacing+"%");}});
        addEffectControl(panels[3],"背景虚化",8,options.blur,value->options.blur=Math.max(1,value));
        panels[4].addView(label("卡片质感",15));addEffectControl(panels[4],"卡片圆角",36,options.corners,value->options.corners=value);
        addEffectControl(panels[4],"悬浮阴影",32,options.shadow,value->options.shadow=value);
        panels[2].addView(label("文字与拍摄参数",15));
        EditText title=new EditText(this);title.setHint("作品标题 · 留空使用模板标题");title.setText(options.title);title.setSingleLine(true);title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});styleInput(title);panels[2].addView(title);editors.add(title);
        EditText author=new EditText(this);author.setHint("署名 · 画廊模板右下角");author.setText(options.author);author.setSingleLine(true);author.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});styleInput(author);LinearLayout.LayoutParams authorParams=new LinearLayout.LayoutParams(-1,-2);authorParams.topMargin=dp(10);panels[2].addView(author,authorParams);editors.add(author);
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int st,int count,int after){}public void onTextChanged(CharSequence t,int st,int before,int count){options.title=title.getText().toString();options.author=author.getText().toString();handler.removeCallbacks(refresh);handler.postDelayed(refresh,250);}public void afterTextChanged(android.text.Editable e){}};title.addTextChangedListener(watcher);author.addTextChangedListener(watcher);
        androidx.appcompat.widget.SwitchCompat parameters=new androidx.appcompat.widget.SwitchCompat(this);parameters.setText("显示真实拍摄参数");parameters.setChecked(options.parameters);panels[2].addView(parameters);editors.add(parameters);parameters.setOnCheckedChangeListener((button,value)->{options.parameters=value;render();});
        Button disclosure=new Button(this);styleButton(disclosure);disclosure.setText("查看原图拍摄详情  ＋");disclosure.setAllCaps(false);panels[2].addView(disclosure);info=new TextView(this);info.setText("正在读取原图参数…");info.setTextSize(13);info.setTextColor(0xffbfc3c6);info.setPadding(dp(12),dp(12),dp(12),dp(12));info.setBackground(background(0xff41474b,12));info.setVisibility(View.GONE);panels[2].addView(info);disclosure.setOnClickListener(v->{boolean show=info.getVisibility()!=View.VISIBLE;info.setVisibility(show?View.VISIBLE:View.GONE);disclosure.setText(show?"收起原图拍摄详情  −":"查看原图拍摄详情  ＋");});
        exportPreset=Math.max(0,Math.min(2,prefs.getInt("export_preset",1)));
        setContentView(canvas);setSupportActionBar(bar);getSupportActionBar().setDisplayHomeAsUpEnabled(true);MainActivity.applyInsets(getWindow(),canvas);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(canvas,(view,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()),ime=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return androidx.core.view.WindowInsetsCompat.CONSUMED;});
        androidx.core.view.ViewCompat.requestApplyInsets(canvas);enable(false);
        save.setOnClickListener(v->showExportSheet());
        new Thread(()->{
            Bitmap loaded=null;PhotoDetails metadata=null;String error=null;
            try{metadata=PhotoDetails.read(this,source,name);}catch(Exception e){metadata=new PhotoDetails();metadata.lines.add("拍摄参数不可读取；可预览的照片仍可添加边框。");}
            try{loaded=LocalMedia.decode(this,source,2048);if(loaded!=null && Math.max(loaded.getWidth(),loaded.getHeight())>2048){float factor=2048f/Math.max(loaded.getWidth(),loaded.getHeight());Bitmap scaled=Bitmap.createScaledBitmap(loaded,Math.max(1,Math.round(loaded.getWidth()*factor)),Math.max(1,Math.round(loaded.getHeight()*factor)),true);if(scaled!=loaded)loaded.recycle();loaded=scaled;}if(loaded==null)error="此格式不支持边框预览，请使用 JPEG 照片。";}catch(Exception|OutOfMemoryError e){error="照片读取失败，文件可能已移除或过大。";}
            final Bitmap bitmap=loaded;final PhotoDetails d=metadata;final String problem=error;
            runOnUiThread(()->{if(closed){if(bitmap!=null)bitmap.recycle();return;}details=d;photo=bitmap;if(photo!=null){float ratio=Math.min(1,800f/Math.max(photo.getWidth(),photo.getHeight()));small=Bitmap.createScaledBitmap(photo,Math.max(1,Math.round(photo.getWidth()*ratio)),Math.max(1,Math.round(photo.getHeight()*ratio)),true);}if(small!=null){ambient=LightFrame.blur(small,8);backdrop.setImageBitmap(ambient);}makeTemplatePreviews();info.setText(d.text()+(problem==null?"":"\n"+problem));render();});
        },"photo-details").start();
    }
    private void styleButton(Button button){button.setBackgroundTintList(null);button.setBackground(background(0xff444b4f,10));button.setTextColor(0xffe8eaeb);button.setPadding(dp(4),0,dp(4),0);button.setMinWidth(0);button.setMinimumWidth(0);}
    private void styleInput(EditText field){field.setTextSize(14);field.setTextColor(0xfff5f5f5);field.setHintTextColor(0xffbbc0c3);field.setPadding(dp(12),dp(14),dp(12),dp(14));android.graphics.drawable.GradientDrawable bg=background(0xff464d51,12);bg.setStroke(dp(1),0xff62696d);field.setBackground(bg);}
    private void showExportSheet(){
        if(photo==null||exporting)return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(24),dp(8),dp(24),dp(16));
        Spinner quality=new Spinner(this);quality.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"轻巧分享 · 1080px","标准画质 · 2048px","高清作品 · 4096px"}));quality.setSelection(exportPreset);panel.addView(quality,new LinearLayout.LayoutParams(-1,dp(52)));TextView note=label("保存带边框的作品，原照片保留。",12);panel.addView(note);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("导出作品").setView(panel).setPositiveButton("保存到相册",(dialog,which)->{selectExportQuality(quality.getSelectedItemPosition());export(false);}).setNeutralButton("分享作品",(dialog,which)->{selectExportQuality(quality.getSelectedItemPosition());export(true);}).setNegativeButton("取消",null).show();
    }
    private void selectExportQuality(int value){exportPreset=Math.max(0,Math.min(2,value));getSharedPreferences("frame_studio",0).edit().putInt("export_preset",exportPreset).apply();}
    private void makeTemplatePreviews(){if(small==null)return;float ratio=Math.min(1,200f/Math.max(small.getWidth(),small.getHeight()));Bitmap tiny=Bitmap.createScaledBitmap(small,Math.max(1,Math.round(small.getWidth()*ratio)),Math.max(1,Math.round(small.getHeight()*ratio)),true);try{for(int i=0;i<templateRow.getChildCount();i++){PhotoFrame.Options preset=new PhotoFrame.Options();preset.style=i;preset.paper=(i==1?2:0);Bitmap sample=PhotoFrame.render(tiny,details,preset);templateBitmaps.add(sample);((ImageView)((LinearLayout)templateRow.getChildAt(i)).getChildAt(0)).setImageBitmap(sample);}}catch(OutOfMemoryError e){Frontend.print("模板缩略图内存不足，保留主预览");}finally{if(tiny!=small)tiny.recycle();}}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("title",options.title);super.onSaveInstanceState(state);}
    private static int templateCategory(int style){return style>=8?0:style==0||style==1||style==2||style==7?1:2;}
    private void showCategory(LinearLayout row,HorizontalScrollView scroll,LinearLayout chips,int category){for(int i=0;i<row.getChildCount();i++){View card=row.getChildAt(i);card.setVisibility(templateCategory(i)==category?View.VISIBLE:View.GONE);}for(int i=0;i<chips.getChildCount();i++){chips.getChildAt(i).setBackground(background(i==category?0xff555c60:0xff33373b,10));((Button)chips.getChildAt(i)).setTextColor(i==category?0xffffffff:0xffa3a8ac);}scroll.post(()->scroll.scrollTo(0,0));}
    private interface EffectValue{void set(int value);}
    private void addEffectControl(LinearLayout content,String caption,int max,int current,EffectValue action){TextView text=label(caption+" · "+current+"（光影系列）",12);content.addView(text);SeekBar slider=new SeekBar(this);slider.setMax(max);slider.setProgress(current);content.addView(slider);editors.add(slider);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){render();}public void onProgressChanged(SeekBar view,int value,boolean user){if(caption.equals("背景虚化")&&value==0){view.setProgress(1);return;}action.set(value);text.setText(caption+" · "+value+"（光影系列）");}});}
    private void enable(boolean value){save.setEnabled(value);for(View editor:editors)editorEnabled(editor,!exporting);}
    private void render(){if(photo==null||exporting||closed)return;getSharedPreferences("frame_studio",0).edit().putBoolean("light_frames_seen",true).putInt("style",options.style).putInt("paper",options.paper).putInt("spacing",options.spacing).putBoolean("sidebar_right",options.sidebarRight).putInt("blur",options.blur).putInt("corners",options.corners).putInt("shadow",options.shadow).putBoolean("parameters",options.parameters).putString("author",options.author).apply();Bitmap old=framed;try{framed=PhotoFrame.render(small,details,options);preview.setImageBitmap(framed);enable(true);}catch(OutOfMemoryError e){enable(false);Toast.makeText(this,"内存不足，无法生成边框",Toast.LENGTH_LONG).show();}if(old!=null&&old!=framed)old.recycle();}
    private void export(boolean sharing){if(framed==null||exporting)return;
        if(!sharing && android.os.Build.VERSION.SDK_INT<29 && androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){androidx.core.app.ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},42);Toast.makeText(this,"允许保存权限后，请再次点击保存",Toast.LENGTH_LONG).show();return;}exporting=true;enable(false);final PhotoFrame.Options snapshot=options.copy();final int edge=new int[]{1080,2048,4096}[exportPreset];final int jpegQuality=exportPreset==0?85:95;
        Frontend.print("边框导出：照片最长边上限 "+edge+"px，JPEG "+jpegQuality+"%");
        new Thread(()->{File file=null;Uri uri=null;String error=null;Bitmap image=null,exportPhoto=null;try{exportPhoto=LocalMedia.decode(this,source,edge);if(exportPhoto==null)throw new IOException("无法读取原图");if(Math.max(exportPhoto.getWidth(),exportPhoto.getHeight())>edge){float factor=(float)edge/Math.max(exportPhoto.getWidth(),exportPhoto.getHeight());Bitmap scaled=Bitmap.createScaledBitmap(exportPhoto,Math.max(1,Math.round(exportPhoto.getWidth()*factor)),Math.max(1,Math.round(exportPhoto.getHeight()*factor)),true);if(scaled!=exportPhoto)exportPhoto.recycle();exportPhoto=scaled;}image=PhotoFrame.render(exportPhoto,details,snapshot);
            File folder=sharing?new File(getCacheDir(),"frames"):new File(PhotoStorage.stagingRoot(this,false),"fudge");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建保存目录");
            if(sharing){File[] old=folder.listFiles();if(old!=null)for(File cached:old)if(cached.getName().startsWith("Fuji-frame-") && System.currentTimeMillis()-cached.lastModified()>7L*24*60*60*1000)cached.delete();}
            file=new File(folder,"Fuji-frame-"+java.util.UUID.randomUUID()+".jpg");try(OutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.JPEG,jpegQuality,out))throw new IOException("图片编码失败");}
            if(sharing)uri=Uri.fromFile(file);else{String target=PhotoStorage.publish(this,file.getAbsolutePath(),false);uri=target.startsWith("content:")?Uri.parse(target):Uri.fromFile(new File(target));if(android.os.Build.VERSION.SDK_INT>=29)file.delete();}
        }catch(Exception|OutOfMemoryError e){error="导出失败，请检查存储空间，或选择较低的导出画质";if(file!=null)file.delete();}finally{if(image!=null)image.recycle();if(exportPhoto!=null)exportPhoto.recycle();}
        Frontend.print(error==null?"边框副本导出成功":"边框副本导出失败");
        final Uri result=uri;final String message=error;runOnUiThread(()->{exporting=false;if(closed){release();return;}enable(true);if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();else if(sharing)LocalMedia.share(this,result,"Fuji-frame.jpg","分享边框作品");else Toast.makeText(this,"边框副本已保存到手机相册",Toast.LENGTH_LONG).show();});},"photo-frame-export").start();
    }
    private void release(){if(framed!=null){framed.recycle();framed=null;}if(small!=null&&small!=photo)small.recycle();small=null;if(photo!=null){photo.recycle();photo=null;}}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onDestroy(){closed=true;handler.removeCallbacks(refresh);if(templateRow!=null)for(int i=0;i<templateRow.getChildCount();i++)((ImageView)((LinearLayout)templateRow.getChildAt(i)).getChildAt(0)).setImageDrawable(null);for(Bitmap bitmap:templateBitmaps)bitmap.recycle();templateBitmaps.clear();if(preview!=null)preview.setImageDrawable(null);if(backdrop!=null)backdrop.setImageDrawable(null);if(ambient!=null)ambient.recycle();if(!exporting)release();super.onDestroy();}
}
