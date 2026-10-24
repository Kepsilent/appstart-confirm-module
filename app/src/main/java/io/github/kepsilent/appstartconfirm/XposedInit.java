package io.github.kepsilent.appstartconfirm;

import io.github.kepsilent.appstartconfirm.hooks.AppStartConfirmHooks;
import io.github.kepsilent.appstartconfirm.xposed.XC_LoadPackage;
import io.github.kepsilent.appstartconfirm.xposed.XposedBridge;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * 独立模块入口（从 rikumi/coloros-mod 提取，仅保留“恢复始终允许应用间跳转对话框”）。
 * 无 UI、无设置：模块启用即生效。
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

    private static final String PACKAGE_SECURITY_PERMISSION = "com.oplus.securitypermission";

    private static volatile boolean sSecurityPermissionHooked = false;

    public static void log(String msg) {
        XposedBridge.log(msg);
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        // 必须先拿到框架接口，否则兼容层无法挂钩（见 XposedBridge#attachFramework）。
        XposedBridge.attachFramework(this);
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
        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam();
        lpparam.packageName = packageName;
        lpparam.processName = packageName;
        lpparam.classLoader = cl;
        lpparam.isFirstApplication = isFirst;
        AppStartConfirmHooks.hook(lpparam);
    }
}
