package dev.danielc.fujiapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class FujiApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        RunLog.initialize(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity activity){DebugUi.apply(activity);}
            public void onActivityCreated(Activity activity,Bundle state){}
            public void onActivityStarted(Activity activity){}
            public void onActivityPaused(Activity activity){}
            public void onActivityStopped(Activity activity){}
            public void onActivitySaveInstanceState(Activity activity,Bundle state){}
            public void onActivityDestroyed(Activity activity){}
        });
    }
}
