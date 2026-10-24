# 恢复始终允许应用间跳转 · AppStartConfirm

> 恢复 ColorOS 权限管理中原生 **「始终允许打开」** 复选框的应用间跳转确认对话框。
> 从 [coloros-mod](https://github.com/rikumi/coloros-mod)（MIT）提取的独立 LSPosed 模块。

![banner](docs/banner.png)

**机制级兼容** · 无版本白名单 · 已在 **ColorOS 17 / Android 17** 实测可用
（逻辑最初基于 Android 16 系权限管理 16.08.003 反编译核对）

---

## 特性

- **恢复原生「始终允许打开」复选框**：新版跳转确认框（`activity_start_confirm_version=2`）只有「30 天内允许」，本模块在 `AppStartConfirmDialogActivity#onCreate` 前把 Intent 版本改回 0，让系统走自带「始终允许打开」复选框的旧对话框
- **只 hook 一个点**：不修改点击回调、不调用启动/保存接口；支付保护、小程序拦截、黑名单、游戏支付拦截保持原生路径
- **跨进程设置**：ContentProvider（Binder）+ DE 存储 + ContentObserver 推送，开关写入后无需重启，下一次跳转即生效
- **隐藏桌面图标**：可选一键隐藏桌面入口，恢复入口保留在 LSPosed 模块列表
- **EGD 风格设置页**：液态玻璃 + 霞鹜文楷 + 签名绿，深浅双主题跟随系统
- **零冗余依赖**：纯 Java、无 Kotlin/Compose/miuix、无 androidx，APK 仅 ~360 KB

## 安装与使用

1. 手机已装 **LSPosed 2.x**（支持 libxposed API 102）或其它兼容框架
2. 安装 APK，在 LSPosed 中启用本模块
3. **作用域勾选「权限管理」（`com.oplus.securitypermission`）**，重启生效
4. 打开模块（桌面图标或 LSPosed 模块入口）开启开关
5. 用一组「尚无授权」的应用关系触发跳转，确认框应出现「始终允许打开」复选框；勾选后允许即保存长期授权

> 作用域里显示的应用名可能是「权限管理」，包名才是 `com.oplus.securitypermission`。

## 兼容性

- 机制与 **系统版本无关**（代码无版本白名单）：只要系统仍保留 `AppStartConfirmDialogActivity` 类名与 `version 2 → 旧对话框` 分支即生效
- 已实测：**ColorOS 17 / Android 17**（2026-10）
- 逻辑核对基线：权限管理 `16.08.003`（SHA256 `a07868b7dbbfb3af9bea64f3488f8a3fe847ac0aec639f0ac0f51c01b0bd9c22`）
- 若后续系统版本改动类名或分支，hook 失效仅记 `HOOK FAIL` 日志，不影响系统
- 改机有风险，请先完整备份

## 构建

需要 JDK 17+、Android SDK（platform 37）、能访问 Google Maven / Maven Central。

工程固定：AGP `8.9.2` + Gradle `8.13` + `compileSdk 37`。

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

默认 debug 签名直接可装；固定签名（升级不丢签名）见 `signing.properties.example`。

## 验证

```bash
python3 scripts/check-app-start-confirm.py
```

788 项 JVM 桩检查：默认关闭、开关切换、版本边界、风险类型边界、Intent 副本、hook 注册目标、作用域接入、字符串资源。

## 原理

ColorOS 权限管理新版跳转确认框（`activity_start_confirm_version=2`）移除了「始终允许打开」复选框。本模块在 `com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity#onCreate` 之前：

1. 读取模块开关（跨进程）；
2. 开关开启、action 为 `oplus.app.action.CHECK_ALLOW_START_ACTIVITY`、版本为 2 且 `start_type ∈ {0, 5, 6}` 时，把 Intent 副本版本改为 0；
3. 原生 `version != 2` 走旧对话框（带「始终允许打开」复选框）；
4. 勾选并允许后，系统保存不带有效期的长期授权（原生 `ALWAYS_ALLOW_START=101`）。

## 目录结构

```
app/
└── src/main/
    ├── AndroidManifest.xml            # 入口 Activity + 设置 Provider（directBootAware）
    ├── java/io.github.kepsilent.appstartconfirm/
    │   ├── XposedInit.java            # libxposed 入口 + 跨进程设置读取
    │   ├── SettingsProvider.java      # 跨进程暴露设置（Binder，绕开 SELinux）
    │   ├── MainActivity.java          # EGD 风格设置页（开关 + 隐藏图标）
    │   ├── hooks/AppStartConfirmHooks.java  # 唯一的 hook
    │   └── xposed/                    # 旧版 Xposed API 兼容层
    ├── resources/META-INF/xposed/     # java_init.list / module.prop / scope.list
    ├── assets/fonts/                  # 霞鹜文楷（OFL 1.1，子集化）
    └── res/                           # EGD 主题（深浅）+ 字符串（中/英）+ 图标
scripts/check-app-start-confirm.py
docs/banner.png
```

## 许可

- Hook 逻辑与设置通道实现源自 [rikumi/coloros-mod](https://github.com/rikumi/coloros-mod)，**MIT License**
- 字体霞鹜文楷 LXGW WenKai，**SIL OFL 1.1**（子集化版随 APK，许可证见 `assets/licenses/`）
- 图标视觉为按 OPPO 手机管家风格重绘的翻转版个人图标（个人重绘，非官方素材）
- 设置页 UI 遵循 [EGD（Elegant Geek Design）](https://github.com/Kepsilent/Elegant-Geek-Design) 设计规范
