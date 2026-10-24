package io.github.kepsilent.appstartconfirm;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 独立模块设置页（EGD v1.2 风格：纯白/纯黑底 + 液态玻璃卡片 + 霞鹜文楷 + 签名绿）。
 * 设置写入设备加密(DE)存储（与 SettingsProvider 同源），写入后通过
 * notifySettingsChanged 推送给被 hook 进程，无需重启即可在下一次跳转生效。
 */
public class MainActivity extends Activity {

    private static final String KEY = XposedInit.KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED;

    private Typeface fontMedium;
    private Typeface fontRegular;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fontMedium = Typeface.createFromAsset(getAssets(), "fonts/lxgw-wenkai-medium.ttf");
        fontRegular = Typeface.createFromAsset(getAssets(), "fonts/lxgw-wenkai-regular.ttf");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.bg));
        int pagePad = dp(20);
        root.setPadding(pagePad, dp(28), pagePad, dp(28));

        // ── 页头：盾牌闪电徽标 + 模块名 ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(this);
        badge.setImageResource(R.drawable.ic_shield_zap);
        int badgeSize = dp(30);
        badge.setLayoutParams(new LinearLayout.LayoutParams(badgeSize, badgeSize));
        header.addView(badge);
        TextView title = text(R.string.app_name, 20, R.color.text_primary, fontMedium, 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = dp(10);
        title.setLayoutParams(titleLp);
        header.addView(title);
        root.addView(header);

        // ── 卡片 1：恢复始终允许应用间跳转 ──
        root.addView(cardAlwaysAllow(), cardLp(0, dp(20)));

        // ── 卡片 2：隐藏桌面图标 ──
        root.addView(cardHideIcon(), cardLp(0, dp(16)));

        // ── 底部提示 ──
        TextView risk = text(R.string.settings_risk_hint, 12, R.color.text_weak, fontRegular, 0);
        root.addView(risk, cardLp(dp(4), dp(16)));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
    }

    /** 卡片 1：功能开关。 */
    private LinearLayout cardAlwaysAllow() {
        LinearLayout card = glassCard();
        Switch sw = new Switch(this);
        sw.setText(R.string.app_start_confirm_always_allow);
        sw.setTextSize(16);
        sw.setTextColor(color(R.color.text_primary));
        sw.setTypeface(fontMedium);
        styleSwitch(sw);
        sw.setChecked(readPref());
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                writePref(isChecked);
            }
        });
        card.addView(sw);
        card.addView(text(R.string.settings_switch_hint, 13, R.color.text_secondary, fontRegular, dp(12)));
        card.addView(text(R.string.settings_scope_hint, 13, R.color.text_secondary, fontRegular, dp(10)));
        return card;
    }

    /** 卡片 2：隐藏桌面图标。 */
    private LinearLayout cardHideIcon() {
        LinearLayout card = glassCard();
        Switch hideSw = new Switch(this);
        hideSw.setText(R.string.settings_hide_icon);
        hideSw.setTextSize(16);
        hideSw.setTextColor(color(R.color.text_primary));
        hideSw.setTypeface(fontMedium);
        styleSwitch(hideSw);
        hideSw.setChecked(isLauncherIconHidden());
        hideSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setLauncherIconHidden(isChecked);
            }
        });
        card.addView(hideSw);
        card.addView(text(R.string.settings_hide_icon_hint, 13, R.color.text_secondary, fontRegular, dp(12)));
        return card;
    }

    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        int pad = dp(20);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    private LinearLayout.LayoutParams cardLp(int leftMargin, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = leftMargin;
        lp.topMargin = topMargin;
        return lp;
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

    /**
     * 隐藏桌面图标：仅禁用 LAUNCHER activity-alias（.MainActivityLauncher），
     * 保留 .MainActivity 本体作为 LSPosed MODULE_SETTINGS 入口，因此图标隐藏后
     * 仍可经由 LSPosed 模块列表进入本页恢复。状态由 PackageManager 持久化。
     */
    private ComponentName launcherAlias() {
        return new ComponentName(this, getPackageName() + ".MainActivityLauncher");
    }

    private boolean isLauncherIconHidden() {
        int state = getPackageManager().getComponentEnabledSetting(launcherAlias());
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
    }

    private void setLauncherIconHidden(boolean hidden) {
        getPackageManager().setComponentEnabledSetting(launcherAlias(),
                hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                       : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private TextView text(int resId, float sp, int colorRes, Typeface tf, int topPadding) {
        TextView tv = new TextView(this);
        tv.setText(resId);
        tv.setTextSize(sp);
        tv.setTextColor(color(colorRes));
        tv.setTypeface(tf);
        tv.setLineSpacing(0f, 1.2f);
        tv.setPadding(0, topPadding, 0, 0);
        return tv;
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

    private boolean readPref() {
        return prefs().getBoolean(KEY, false);
    }

    private void writePref(boolean value) {
        if (prefs().edit().putBoolean(KEY, value).commit()) {
            SettingsProvider.notifySettingsChanged(getApplicationContext());
        }
    }
}
