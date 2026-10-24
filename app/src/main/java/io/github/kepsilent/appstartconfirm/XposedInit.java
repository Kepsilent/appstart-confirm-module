package io.github.kepsilent.appstartconfirm;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

import io.github.kepsilent.appstartconfirm.hooks.AppStartConfirmHooks;
import io.github.kepsilent.appstartconfirm.xposed.XC_LoadPackage;
import io.github.kepsilent.appstartconfirm.xposed.XposedBridge;
import io.github.kepsilent.appstartconfirm.xposed.XposedHelpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * 独立模块入口（从 rikumi/coloros-mod 提取，仅保留“恢复始终允许应用间跳转对话框”）。
 *
 * 关键机制（详见原项目 docs/investigations/app-start-confirm-always-allow.md）：
 * ColorOS 权限管理(com.oplus.securitypermission) 的应用间跳转确认框在新版(version=2)只提供
 * “30天内允许”，不再有“始终允许打开”复选框。本模块在 AppStartConfirmDialogActivity#onCreate
 * 前把 Intent 中的 activity_start_confirm_version 从 2 改回 0，让原生代码走带复选框的旧对话框；
 * 用户勾选后点击允许时，系统会保存不带有效期的长期授权（ALWAYS_ALLOW_START=101）。
 * 仅作用于普通跳转(start_type 0/5/6)；支付保护、小程序拦截、黑名单(7)等保持原路径。
 */
public class XposedInit extends XposedModule {

    public static final String TAG = "AppStartConfirm";
    public static final String PREF_NAME = "settings";
    public static final String SETTINGS_AUTHORITY = "io.github.kepsilent.appstartconfirm.settings";
    public static final String SETTINGS_ALL_KEY = "__all__";

    public static final String KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED =
            "app_start_confirm_always_allow_enabled";

    private static final String PACKAGE_SECURITY_PERMISSION = "com.oplus.securitypermission";
    private static final String TARGET_ACTIVITY =
            "com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity";

    // ---- 跨进程设置读取 ----
    /** 后台预热的全量快照（零 IPC 读）。 */
    private static volatile Map<String, Integer> sSnapshot = Collections.emptyMap();
    /** 是否已成功加载过全量快照；为 true 时不再走 IPC，读不到的键直接回落默认值。 */
    private static volatile boolean sSettingsLoaded = false;
    /** 粘性缓存 <key, [时间戳, 值]>：Provider 临时不可达时回退上一次成功读到的值。 */
    private static final ConcurrentHashMap<String, Object[]> sCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 2000L;
    private static final long FIRST_LOAD_WAIT_MS = 1000L;

    private static volatile Context sAppContext;
    private static volatile boolean sSecurityPermissionHooked = false;

    // ---- 后台设置同步（受控 HandlerThread + ContentObserver push） ----
    private static volatile HandlerThread sThread;
    private static volatile Handler sHandler;
    private static volatile ContentObserver sObserver;
    private static volatile int sRetryBackoffMs = 500;

    private static final Runnable REFRESH = new Runnable() {
        @Override
        public void run() {
            refreshSettings();
        }
    };

    public static void log(String msg) {
        XposedBridge.log(msg);
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        // 必须先拿到框架接口，否则兼容层无法挂钩（见 XposedBridge#attachFramework）。
        XposedBridge.attachFramework(this);
        // 尽早启动设置预热，使目标进程首次弹窗时已能读到开关。
        sAppContext = currentApplication();
        if (sAppContext != null) {
            startSettingsLoader();
        }
        log("module loaded: framework=" + getFrameworkName() + " v" + getFrameworkVersion()
                + " api=" + getApiVersion());
    }

    // onPackageLoaded: 早期事件，先装 hook（classLoader 可能还不是最终 Application 的）。
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!PACKAGE_SECURITY_PERMISSION.equals(packageName)) return;
        hookSecurityPermission(param.getDefaultClassLoader(), packageName, param.isFirstPackage());
    }

    // onPackageReady: 最终 ClassLoader，兜底安装（与 onPackageLoaded 去重）。
    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!PACKAGE_SECURITY_PERMISSION.equals(packageName)) return;
        hookSecurityPermission(param.getClassLoader(), packageName, param.isFirstPackage());
    }

    private static void hookSecurityPermission(ClassLoader cl, String packageName, boolean isFirst) {
        if (sSecurityPermissionHooked) return;
        sSecurityPermissionHooked = true;
        if (sAppContext == null) {
            sAppContext = currentApplication();
        }
        startSettingsLoader();
        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam();
        lpparam.packageName = packageName;
        lpparam.processName = packageName;
        lpparam.classLoader = cl;
        lpparam.isFirstApplication = isFirst;
        AppStartConfirmHooks.hook(lpparam);
    }

    // ------------------------------------------------------------------ 设置读取

    // 跨进程读取开关，走 Binder(ContentProvider) 通道，不受 SELinux 对 app_data_file 的限制。
    public static boolean readBool(String key, boolean def) {
        Object v = settingsValue(key);
        if (v instanceof Number) return ((Number) v).intValue() == 1;
        return def;
    }

    // 取值顺序: 后台预热的全量快照(零 IPC) -> (首次)有限等待预热 -> 同步 Provider 查询 -> 粘性缓存 -> 无。
    private static Object settingsValue(String key) {
        Integer v = sSnapshot.get(key);
        if (v != null) return v;
        if (sSettingsLoaded) return null;
        ensureFirstLoad();
        v = sSnapshot.get(key);
        if (v != null) return v;
        return queryProviderSync(key);
    }

    // 同步 Provider 查询(兜底通道)；结果按 TTL 写入 sCache 作粘性缓存。
    private static Object queryProviderSync(String key) {
        Object[] cached = sCache.get(key);
        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < CACHE_TTL_MS) {
            return cached[1];
        }
        try {
            Context ctx = sAppContext;
            if (ctx == null) {
                ctx = currentApplication();
                sAppContext = ctx;
            }
            if (ctx != null) {
                ContentResolver cr = ctx.getContentResolver();
                Uri uri = Uri.parse("content://" + SETTINGS_AUTHORITY + "/" + key);
                Cursor c = cr.query(uri, null, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst() && c.getColumnCount() > 0) {
                            int v = c.getInt(0);
                            sCache.put(key, new Object[]{System.currentTimeMillis(), v});
                            return v;
                        }
                    } finally {
                        c.close();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Provider 取不到(模块 App 未运行等): 回退上一次成功取到的值(粘性, 即使已过期)。
        return cached != null ? cached[1] : null;
    }

    // ------------------------------------------------------------------ 后台预热 + push

    public static void startSettingsLoader() {
        if (sThread != null) return;
        synchronized (XposedInit.class) {
            if (sThread != null) return;
            HandlerThread thread = new HandlerThread("appstartconfirm-settings");
            thread.start();
            sThread = thread;
            sHandler = new Handler(thread.getLooper());
            sHandler.post(REFRESH);
        }
    }

    // 初始预热与 ContentObserver 通知共用：全量拉取 -> 发布快照；失败按指数退避重试(0.5s->30s)。
    private static void refreshSettings() {
        ensureObserver();
        try {
            Context ctx = sAppContext;
            if (ctx == null) {
                ctx = currentApplication();
                sAppContext = ctx;
            }
            if (ctx == null) {
                scheduleRetry();
                return;
            }
            ContentResolver cr = ctx.getContentResolver();
            Uri uri = Uri.parse("content://" + SETTINGS_AUTHORITY + "/" + SETTINGS_ALL_KEY);
            Cursor c = cr.query(uri, null, null, null, null);
            if (c == null) {
                scheduleRetry();
                return;
            }
            try {
                Map<String, Integer> all = new HashMap<String, Integer>();
                while (c.moveToNext()) {
                    String k = c.getString(0);
                    if (k == null) continue;
                    all.put(k, c.getInt(1));
                }
                sSnapshot = all;
                sSettingsLoaded = true;
                sRetryBackoffMs = 500;
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            scheduleRetry();
        }
    }

    // 注册 ContentObserver：设置写入方 notifySettingsChanged 后，在 worker 线程 250ms 合并刷新。
    private static void ensureObserver() {
        if (sObserver != null) return;
        Context ctx = sAppContext;
        if (ctx == null) return;
        try {
            ContentObserver observer = new ContentObserver(sHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    sHandler.removeCallbacks(REFRESH);
                    sHandler.postDelayed(REFRESH, 250L);
                }
            };
            ctx.getContentResolver().registerContentObserver(
                    Uri.parse("content://" + SETTINGS_AUTHORITY + "/" + SETTINGS_ALL_KEY),
                    true, observer);
            sObserver = observer;
        } catch (Throwable t) {
            log("registerContentObserver fail: " + t);
        }
    }

    private static void scheduleRetry() {
        if (sHandler == null) return;
        sHandler.postDelayed(REFRESH, sRetryBackoffMs);
        sRetryBackoffMs = Math.min(sRetryBackoffMs * 2, 30000);
    }

    // 首个 readBool 到达时后台预热可能还没完成，最多等这么久；整个进程只等一次。
    private static void ensureFirstLoad() {
        if (sSettingsLoaded) return;
        long deadline = System.currentTimeMillis() + FIRST_LOAD_WAIT_MS;
        while (!sSettingsLoaded && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // 反射获取当前进程 Application(Context)，用于 ContentResolver 跨进程查询设置。
    public static Context currentApplication() {
        try {
            Class<?> at = XposedHelpers.findClass("android.app.ActivityThread",
                    ClassLoader.getSystemClassLoader());
            return (Context) XposedHelpers.callStaticMethod(at, "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }
}
