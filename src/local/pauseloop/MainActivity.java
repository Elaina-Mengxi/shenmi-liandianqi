package local.pauseloop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Restored from this project's archived v1.0 APK; see docs/SOURCE_PROVENANCE.md. */
public class MainActivity extends Activity {
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        TextView description = new TextView(this);
        description.setText("暂停循环\n\n暂停 → 等待 0.2 秒 → 继续 → 等待 2 秒 → 重复\n\n"
            + "1. 开启下方无障碍服务。\n2. 显示悬浮控制条，切换到跑步页面。\n"
            + "3. 在正在跑步时选取暂停位置。\n4. 手动暂停后选取绿色继续位置，再手动恢复跑步。\n"
            + "5. 点击试跑3次，确认位置后再开始循环。\n\n"
            + "选点时点击屏幕即可，选点本身不会触发跑步按钮。启动有3秒倒计时。锁屏或旋转屏幕会停止。\n\n"
            + "若安装后无法开启无障碍，在系统应用信息右上角检查是否需“允许受限设置”。");
        description.setTextSize(18);
        layout.addView(description);

        Button settings = new Button(this);
        settings.setText("打开无障碍设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);

        Button show = new Button(this);
        show.setText("显示悬浮控制条");
        show.setOnClickListener(v -> {
            if (LoopService.instance == null) {
                Toast.makeText(this, "请先开启“暂停循环”无障碍服务", Toast.LENGTH_LONG).show();
            } else {
                LoopService.instance.showPanel();
            }
        });
        layout.addView(show);
    }
}
