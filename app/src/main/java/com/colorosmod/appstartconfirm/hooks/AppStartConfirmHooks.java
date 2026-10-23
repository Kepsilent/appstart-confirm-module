package com.colorosmod.appstartconfirm.hooks;

import static com.colorosmod.appstartconfirm.XposedInit.KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED;
import static com.colorosmod.appstartconfirm.XposedInit.log;
import static com.colorosmod.appstartconfirm.XposedInit.readBool;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.colorosmod.appstartconfirm.xposed.XC_LoadPackage;
import com.colorosmod.appstartconfirm.xposed.XC_MethodHook;
import com.colorosmod.appstartconfirm.xposed.XposedHelpers;

/**
 * SecurityPermission 16.08.003 的原生应用间跳转确认框，运行在 :ui 进程。
 *
 * 原项目: https://github.com/rikumi/coloros-mod (MIT License)
 * 提取说明见 docs/investigations/app-start-confirm-always-allow.md。
 */
public final class AppStartConfirmHooks {
    private static final String ACTION_CONFIRM = "oplus.app.action.CHECK_ALLOW_START_ACTIVITY";
    private static final String VERSION = "activity_start_confirm_version";

    public static void hook(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity",
                    lp.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            Intent original = activity.getIntent();
                            Intent restored = dialogIntent(original,
                                    readBool(KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED, false));
                            if (restored != original) activity.setIntent(restored);
                        }
                    });
            log("HOOK OK AppStartConfirmDialogActivity#onCreate (restore always allow)");
        } catch (Throwable t) {
            log("HOOK FAIL AppStartConfirmDialogActivity#onCreate: " + t);
        }
    }

    static Intent dialogIntent(Intent original, boolean enabled) {
        if (!enabled || original == null || !ACTION_CONFIRM.equals(original.getAction())
                || original.getIntExtra(VERSION, 0) != 2) return original;
        int type = original.getIntExtra("start_type", -1);
        // i9.a$b: 普通跳转 0、推荐 30 天 5、推荐单次 6 均由 i9.k 创建。
        // 7 也是 i9.k，但属于黑名单拦截，必须保留其仅单次允许的原生策略。
        if (type != 0 && type != 5 && type != 6) return original;
        // i9.k#a 在 version != 2 时选择原生复选框对话框 r()；复选框默认未勾选。
        // 只有 k() 收到用户点击“允许”且勾选时才保存不带 valid_time 的原生授权。
        // 不改点击回调、不调用启动/保存接口，也不修改 Activity 外部持有的 Intent。
        return new Intent(original).putExtra(VERSION, 0);
    }

    private AppStartConfirmHooks() {}
}
