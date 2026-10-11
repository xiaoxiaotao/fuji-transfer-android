package dev.danielc.fujiapp;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Visibility preference only: never disables diagnostic recording. */
final class DebugUi {
    static boolean enabled(Context context) { return MySettings.getSetting(context,"show_debug",0)==1; }
    static void apply(Activity activity) {
        boolean enabled=enabled(activity);
        for(int id:new int[]{R.id.error_msg,R.id.gallery_logs,R.id.bluetooth_status,R.id.settings_diagnostic}) {
            View view=activity.findViewById(id);
            if(view!=null)view.setVisibility(enabled?View.VISIBLE:View.GONE);
        }
        applyTags(activity.getWindow().getDecorView(),enabled);
    }
    private static void applyTags(View view,boolean enabled) {
        if("debug_only".equals(view.getTag()))view.setVisibility(enabled?View.VISIBLE:View.GONE);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)applyTags(((ViewGroup)view).getChildAt(i),enabled);
    }
}
