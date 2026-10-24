package io.github.kepsilent.appstartconfirm.xposed;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * 兼容层入口: 持有框架注入的 XposedInterface, 提供 log 与框架访问。
 * 仅保留本模块实际使用的能力。
 */
public final class XposedBridge {

    private static volatile XposedInterface sFramework;

    private XposedBridge() {
    }

    /** 由模块入口 (XposedInit) 在 onModuleLoaded 中调用。 */
    public static void attachFramework(XposedInterface framework) {
        sFramework = framework;
    }

    static XposedInterface framework() {
        XposedInterface fx = sFramework;
        if (fx == null) {
            throw new IllegalStateException("Xposed framework not attached yet");
        }
        return fx;
    }

    public static void log(String msg) {
        XposedInterface fx = sFramework;
        if (fx != null) {
            fx.log(Log.ERROR, "AppStartConfirm", msg);
        } else {
            // 框架还没挂上时至少还能在 logcat 里看到, 避免静默丢日志。
            Log.e("AppStartConfirm", msg);
        }
    }

    public static void log(Throwable t) {
        log(Log.getStackTraceString(t));
    }
}
