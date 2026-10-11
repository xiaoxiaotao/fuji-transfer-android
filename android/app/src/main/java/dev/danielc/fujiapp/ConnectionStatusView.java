package dev.danielc.fujiapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** The same live connection card is used on the camera and gallery screens. */
public final class ConnectionStatusView extends LinearLayout {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TextView title, detail;
    private final TextView[] steps = new TextView[4];
    private final boolean gallery;
    private String rendered = "";
    private final Runnable tick = new Runnable(){public void run(){render(ConnectionState.shared.snapshot());ui.postDelayed(this,250);}};
    public ConnectionStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gallery=attrs!=null&&"gallery".equals(attrs.getAttributeValue("http://schemas.android.com/apk/res/android","tag"));
        boolean compact=gallery&&(getResources().getConfiguration().screenWidthDp<=360||getResources().getConfiguration().screenHeightDp<=680);
        setOrientation(VERTICAL);setPadding(dp(12),dp(compact?6:10),dp(12),dp(compact?6:10));
        setBackgroundResource(R.drawable.ui_card);
        title = text(14, R.color.ui_text);title.setTypeface(null,android.graphics.Typeface.BOLD);
        addView(title);
        for(int row=0;row<2;row++) {
            LinearLayout line = new LinearLayout(context);
            for(int col=0;col<2;col++) {
                TextView label = text(compact?11:12,R.color.ui_muted);
                label.setPadding(0,dp(compact?3:5),dp(4),0);
                steps[row*2+col]=label;
                line.addView(label,new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1));
            }
            addView(line);
        }
        detail = text(compact?11:12,R.color.ui_muted);detail.setPadding(0,dp(compact?4:6),0,0);
        addView(detail);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        render(ConnectionState.shared.snapshot());
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(int size,int color){TextView view=new TextView(getContext());view.setTextSize(size);view.setTextColor(ContextCompat.getColor(getContext(),color));return view;}
    void render(ConnectionState.Snapshot state) {
        String key = state.title+state.detail+java.util.Arrays.toString(state.steps);
        if(key.equals(rendered))return;rendered=key;
        title.setText(gallery?getResources().getString(R.string.connection_status_details_title,state.title):state.title);detail.setText(state.detail);
        detail.setVisibility(gallery&&state.steps[0]!=ConnectionState.Status.DISCONNECTED&&state.steps[3]==ConnectionState.Status.READY&&!state.closing&&!state.ended?GONE:VISIBLE);
        for(ConnectionState.Step step:ConnectionState.Step.values()) {
            TextView view = steps[step.ordinal()];view.setText(state.label(step));
            int color;
            switch(state.steps[step.ordinal()]) {
                case READY:color=0xff68d49b;break;
                case ERROR:color=0xffff8a86;break;
                case CONNECTING:case RETRYING:color=0xfff0bb67;break;
                default:color=ContextCompat.getColor(getContext(),R.color.ui_muted);
            }
            view.setTextColor(color);
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.removeCallbacks(tick);if(getWindowVisibility()==VISIBLE)ui.post(tick);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(ui!=null){ui.removeCallbacks(tick);if(visibility==VISIBLE&&isAttachedToWindow())ui.post(tick);}}
    @Override protected void onDetachedFromWindow(){ui.removeCallbacks(tick);super.onDetachedFromWindow();}
}
