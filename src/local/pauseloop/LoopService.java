package local.pauseloop;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Restored v1.0 behavior: fixed coordinates, completion-based waits, manual recovery. */
public class LoopService extends AccessibilityService {
    public static LoopService instance;
    private WindowManager wm;
    private LinearLayout panel;
    private View picker;
    private TextView status;
    private final Handler h = new Handler();
    private boolean running = false;
    private int token = 0, count = 0, limit = 0;
    private float ax = -1, ay = -1, bx = -1, by = -1;
    private final BroadcastReceiver off = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            stop("已锁屏，停止");
        }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        showPanel();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stop("服务中断"); }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    public void showPanel() {
        if (panel != null) return;
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setBackgroundColor(0xee263448);
        status = new TextView(this);
        status.setTextColor(-1);
        status.setTextSize(13);
        status.setText("暂停循环 · 先选两个点击点");
        panel.addView(status);

        LinearLayout select = new LinearLayout(this);
        select.addView(button("选暂停", () -> pick(true)));
        select.addView(button("选继续", () -> pick(false)));
        panel.addView(select);

        LinearLayout run = new LinearLayout(this);
        run.addView(button("试跑3次", () -> start(3)));
        run.addView(button("开始循环", () -> start(0)));
        panel.addView(run);

        LinearLayout end = new LinearLayout(this);
        end.addView(button("停止", () -> stop("已停止 · 完成" + count + "次")));
        end.addView(button("关闭", () -> {
            stop("已关闭");
            wm.removeView(panel);
            panel = null;
        }));
        panel.addView(end);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(260), -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = dp(50);
        wm.addView(panel, params);
    }

    private void pick(final boolean first) {
        stop("选点中");
        panel.setVisibility(View.GONE);
        TextView view = new TextView(this);
        view.setText(first ? "点一下“暂停”中心\n（此次点击只记录位置）"
            : "点一下绿色“继续”中心\n（此次点击只记录位置）");
        view.setTextColor(-1);
        view.setTextSize(22);
        view.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        view.setPadding(0, dp(140), 0, 0);
        view.setBackgroundColor(0x33223344);
        picker = view;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (first) { ax = event.getRawX(); ay = event.getRawY(); }
                else { bx = event.getRawX(); by = event.getRawY(); }
                removePicker();
                status.setText("暂停:" + (int) ax + "," + (int) ay
                    + " 继续:" + (int) bx + "," + (int) by);
            }
            return true;
        });
        wm.addView(view, params);
    }

    private void removePicker() {
        if (picker != null) { wm.removeView(picker); picker = null; }
        if (panel != null) panel.setVisibility(View.VISIBLE);
    }

    private void start(int max) {
        stop("");
        if (ax < 0 || bx < 0) {
            status.setText("请先选取暂停和继续两个位置");
            return;
        }
        if (Math.abs(ax - bx) < dp(15) && Math.abs(ay - by) < dp(15)) {
            status.setText("两个点过近，请重新选点");
            return;
        }
        count = 0;
        limit = max;
        running = true;
        int id = token;
        status.setText("3秒后开始 · 请保持正在跑步");
        h.postDelayed(() -> tap(true, id), 3000);
    }

    private void tap(final boolean pause, final int id) {
        if (!running || id != token) return;
        Path path = new Path();
        path.moveTo(pause ? ax : bx, pause ? ay : by);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 30)).build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (!running || id != token) return;
                if (!pause) {
                    count++;
                    if (limit > 0 && count >= limit) {
                        stop("试跑完成 · " + count + "次");
                        return;
                    }
                }
                status.setText((pause ? "暂停后等待0.2秒" : "继续后等待2秒")
                    + " · " + count + "次");
                h.postDelayed(() -> tap(!pause, id), pause ? 200 : 2000);
            }
            @Override public void onCancelled(GestureDescription description) {
                if (id == token) stop("点击被中断，请检查界面后重启");
            }
        }, h);
        if (!accepted) stop("系统拒绝点击，请检查无障碍服务");
    }

    private void stop(String message) {
        running = false;
        token++;
        h.removeCallbacksAndMessages(null);
        removePicker();
        if (status != null) status.setText(message);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        stop("屏幕方向变化，请重新选点");
        bx = ax = -1;
    }

    @Override public void onDestroy() {
        stop("服务关闭");
        if (panel != null) wm.removeView(panel);
        panel = null;
        instance = null;
        try { unregisterReceiver(off); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
