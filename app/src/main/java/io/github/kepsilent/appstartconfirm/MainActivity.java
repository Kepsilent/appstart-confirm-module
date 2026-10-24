package io.github.kepsilent.appstartconfirm;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 独立模块设置页（EGD 极简）：纯「关于」页——模块启用即默认生效，无需任何开关。
 * 标题、作者（点击直达 GitHub 项目）、开源项目地址、版本、一句功能描述。
 */
public class MainActivity extends Activity {

    private static final String GITHUB_URL = "https://" + "github.com/Kepsilent/appstart-confirm-module";

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

        // 标题：关于
        TextView title = new TextView(this);
        title.setText(R.string.about_title);
        title.setTextSize(22);
        title.setTypeface(font, Typeface.BOLD);
        title.setTextColor(color(R.color.text_primary));
        root.addView(title);

        // 玻璃卡片：作者 / 开源项目 / 版本
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        int pad = dp(14);
        card.setPadding(pad, dp(8), pad, dp(8));
        card.addView(row(R.string.about_author, getString(R.string.about_author_name), true));
        card.addView(row(R.string.about_project, getString(R.string.about_project_url), true));
        card.addView(row(R.string.about_version, "v" + BuildConfig.VERSION_NAME, false));
        root.addView(card);

        // 一句功能描述
        TextView desc = new TextView(this);
        desc.setText(R.string.xposed_description);
        desc.setTextSize(12);
        desc.setTextColor(color(R.color.text_weak));
        desc.setTypeface(font);
        desc.setPadding(dp(4), dp(16), 0, 0);
        root.addView(desc);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
    }

    /** 一行：左侧标签（弱色），右侧值（主色）；link=true 时整行可点击直达 GitHub。 */
    private LinearLayout row(int labelRes, String value, boolean link) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int vPad = dp(10);
        line.setPadding(0, vPad, 0, vPad);

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(13);
        label.setTextColor(color(R.color.text_weak));
        label.setTypeface(font);
        line.addView(label);

        if (value != null) {
            TextView valueView = new TextView(this);
            valueView.setText(value);
            valueView.setTextSize(14);
            valueView.setTextColor(color(R.color.text_primary));
            valueView.setTypeface(font);
            valueView.setPadding(dp(12), 0, 0, 0);
            line.addView(valueView);
        }

        if (link) {
            line.setClickable(true);
            line.setOnClickListener(v -> openGitHub());
        }
        return line;
    }

    private void openGitHub() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
    }

    private int color(int resId) {
        return getResources().getColor(resId);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
