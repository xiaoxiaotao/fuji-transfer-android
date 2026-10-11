package dev.danielc.fujiapp;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.core.content.ContextCompat;
/** Reorders existing page activities without clearing a live Gallery or restarting PTP. */
final class WorkbenchNav {
 static final int CAMERA=0,GALLERY=1,LIBRARY=2,SETTINGS=3,CREATE=4;
 static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
 static void attach(Activity a,int page){final int selectedPage=page==GALLERY?CAMERA:page;LinearLayout bar=a.findViewById(R.id.workbench_nav);bar.removeAllViews();String[] titles={"创作","照片","相机","我的"};int[] destinations={CREATE,LIBRARY,CAMERA,SETTINGS};int[] icons={R.drawable.ui_nav_create,R.drawable.baseline_landscape_24,R.drawable.ui_nav_camera,R.drawable.baseline_settings_24};for(int i=0;i<4;i++){final int target=destinations[i];LinearLayout tab=new LinearLayout(a);tab.setOrientation(LinearLayout.VERTICAL);tab.setGravity(Gravity.CENTER);tab.setPadding(0,dp(a,7),0,dp(a,7));tab.setMinimumHeight(dp(a,54));tab.setBackgroundResource(target==selectedPage?R.drawable.light_button:R.drawable.ui_text_button);int color=ContextCompat.getColor(a,target==selectedPage?R.color.ui_accent:R.color.ui_muted);ImageView icon=new ImageView(a);icon.setImageResource(icons[i]);icon.setColorFilter(color);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tab.addView(icon,new LinearLayout.LayoutParams(dp(a,22),dp(a,22)));TextView label=new TextView(a);label.setText(titles[i]);label.setTextSize(10);label.setTextColor(color);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(a,3),0,0);label.setTypeface(null,target==selectedPage?Typeface.BOLD:Typeface.NORMAL);tab.addView(label);tab.setContentDescription(titles[i]+(target==selectedPage?"，当前页面":""));tab.setSelected(target==selectedPage);tab.setFocusable(true);tab.setOnClickListener(v->{if(target!=page)open(a,target);});bar.addView(tab,new LinearLayout.LayoutParams(0,-2,1));}}
 static void open(Activity a,int page){if(page==GALLERY&&Backend.cGetKillSwitch()){Toast.makeText(a,"先连接相机，再浏览相机照片",Toast.LENGTH_SHORT).show();page=CAMERA;}Class<?> destination=page==CREATE?FrameHome.class:page==CAMERA?MainActivity.class:page==GALLERY?Gallery.class:page==LIBRARY?FileGallery.class:MySettings.class;Intent intent=new Intent(a,destination).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(page==LIBRARY)intent.putExtra("frame_style",-1);if(page==GALLERY)intent.putExtra("resume_transfer",true);a.startActivity(intent);}
 interface Choice {void select(int value);}
 static void chips(Activity a,LinearLayout group,String[] labels,int selected,Choice action){group.removeAllViews();for(int i=0;i<labels.length;i++){final int value=i;Button button=new Button(a);button.setAllCaps(false);button.setText(labels[i]);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(a,16),0,dp(a,16),0);button.setTextColor(ContextCompat.getColor(a,i==selected?R.color.ui_on_accent:R.color.ui_muted));button.setBackgroundResource(i==selected?R.drawable.go_button:R.drawable.ui_text_button);button.setBackgroundTintList(null);button.setForeground(ContextCompat.getDrawable(a,R.drawable.ripple));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(a,48));params.setMarginEnd(dp(a,4));group.addView(button,params);button.setSelected(i==selected);button.setOnClickListener(v->action.select(value));}}
}
