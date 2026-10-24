package io.github.kepsilent.appstartconfirm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 独立模块设置页（EGD 极简：功能开关默认开 + 关于一行）。
 * 设置写入设备加密(DE)存储（与 SettingsProvider 同源），写入后通过
 * notifySettingsChanged 推送给被 hook 进程，无需重启即可在下一次跳转生效。
 */
public class MainActivity extends Activity {

    private static final String KEY = XposedInit.KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED;

    private Typeface font;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        font = Typeface.createFromAsset(getAssets(), "fonts/lxgw-wenkai-regular.ttf");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.bg));
        int pagePad = dp(20);
        root.setPadding(pagePad, dp(28), pagePad, dp(28));

        // 功能开关（默认开：模块启用即生效）
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        int pad = dp(14);
        card.setPadding(pad, dp(6), pad, dp(6));
        card.addView(switchRow());
        root.addView(card);

        // 关于：项目地址，点击打开 GitHub
        TextView about = new TextView(this);
        about.setText(R.string.about_project);
        about.setTextSize(12);
        about.setTextColor(color(R.color.text_weak));
        about.setTypeface(font);
        about.setPadding(dp(4), dp(16), 0, 0);
        about.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://" + getString(R.string.about_project)))));
        root.addView(about);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
    }

    private Switch switchRow() {
        Switch sw = new Switch(this);
        sw.setText(R.string.app_start_confirm_always_allow);
        sw.setTextSize(16);
        sw.setTextColor(color(R.color.text_primary));
        sw.setTypeface(font);
        styleSwitch(sw);
        sw.setChecked(readPref());
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                writePref(isChecked);
            }
        });
        return sw;
    }

    /** EGD 签名绿 Switch 着色（深浅主题自动跟随）。 */
    private void styleSwitch(Switch sw) {
        ColorStateList track = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{color(R.color.switch_track_on), color(R.color.switch_track_off)});
        ColorStateList thumb = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{color(R.color.switch_thumb), color(R.color.switch_thumb)});
        sw.setTrackTintList(track);
        sw.setThumbTintList(thumb);
    }

    private int color(int resId) {
        return getResources().getColor(resId);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private SharedPreferences prefs() {
        return getApplicationContext().createDeviceProtectedStorageContext()
                .getSharedPreferences(XposedInit.PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 默认开启：无记录时按 true 处理，模块启用即生效。 */
    private boolean readPref() {
        return prefs().getBoolean(KEY, true);
    }

    private void writePref(boolean value) {
        if (prefs().edit().putBoolean(KEY, value).commit()) {
            SettingsProvider.notifySettingsChanged(getApplicationContext());
        }
    }
}
