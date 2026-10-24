# 恢复始终允许应用间跳转 · AppStartConfirm

> 恢复 ColorOS 权限管理中原生 **「始终允许打开」** 复选框的应用间跳转确认对话框。
> 从 [coloros-mod](https://github.com/rikumi/coloros-mod)（MIT）提取的独立 LSPosed 模块。

![banner](docs/banner.png)

**机制级兼容** · 无版本白名单 · **10.3KB** · 已在 **ColorOS 17 / Android 17** 实测可用
（逻辑基线：权限管理 16.08.003，反编译核对）

---

## 特性

- **恢复原生「始终允许打开」复选框**：新版跳转确认框（`activity_start_confirm_version=2`）只有「30 天内允许」，本模块在 `AppStartConfirmDialogActivity#onCreate` 前把 Intent 版本改回 0，让系统走自带「始终允许打开」复选框的旧对话框
- **只 hook 一个点**：不修改点击回调、不调用启动/保存接口；支付保护、小程序拦截、黑名单、游戏支付拦截保持原生路径
- **启用即生效**：无界面、无设置、无桌面图标，静态作用域预加载；模块启用后无需任何配置
- **10.3KB**：dex 7KB + 三件套 + 两个字符串；零死重（无 service 库、无 Kotlin/Compose/androidx）
- **诊断日志**：每次确认框触发输出 `action/version/start_type/hit` 到 LSPosed 日志，一次定位问题

## 安装与使用

1. 手机已装 **LSPosed 2.x**（libxposed API 102）或其它兼容框架
2. 安装 APK，在 LSPosed 中启用本模块
3. **作用域勾选「权限管理」（`com.oplus.securitypermission`）**，重启生效
4. 用一组「尚无授权」的应用关系触发跳转，确认框应出现「始终允许打开」复选框；勾选后允许即保存长期授权

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
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

release 开启 R8 收缩（`minifyEnabled true`，已配置 keep 规则，体积 10.3KB）。默认 debug 签名直接可装；固定签名（升级不丢签名）见 `signing.properties.example`。

## 验证

```bash
python3 scripts/check-app-start-confirm.py
```

726 项 JVM 桩检查：版本边界、风险类型边界、Intent 副本、hook 注册目标、作用域接入、字符串资源。

## 原理

ColorOS 权限管理新版跳转确认框（`activity_start_confirm_version=2`）移除了「始终允许打开」复选框。本模块在 `com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity#onCreate` 之前：

1. action 为 `oplus.app.action.CHECK_ALLOW_START_ACTIVITY`、版本为 2 且 `start_type ∈ {0, 5, 6}` 时，把 Intent 副本版本改为 0；
2. 原生 `version != 2` 走旧对话框（带「始终允许打开」复选框）；
3. 勾选并允许后，系统保存不带有效期的长期授权（原生 `ALWAYS_ALLOW_START=101`）。

`start_type=7`（黑名单拦截）与支付/小程序等场景保持原生路径，不做改写。

## 目录结构

```
app/src/main/
├── AndroidManifest.xml            # 仅 label/description（无 Activity/Provider/图标）
├── java/io.github.kepsilent.appstartconfirm/
│   ├── XposedInit.java            # libxposed 入口（onModuleLoaded/Loaded/Ready）
│   ├── hooks/AppStartConfirmHooks.java  # 唯一的 hook + 诊断日志
│   └── xposed/                    # 旧版 Xposed API 兼容层（极简裁剪）
└── resources/META-INF/xposed/     # java_init.list / module.prop / scope.list
scripts/check-app-start-confirm.py
docs/banner.png
```

## 体积演进

| 版本 | 体积 | 说明 |
| --- | --- | --- |
| v1.x | ~365 KB | 原始提取，含完整 UI 与设置体系 |
| v3.0.4 | 37.8 KB | 无图标 + 单开关 + R8 |
| v3.0.8 | 27.9 KB | 兼容层手工裁剪（830 → 130 行） |
| **v3.0.9** | **10.3 KB** | **去掉 service 库死重 + R8 业务收缩** |

## 许可

- Hook 逻辑与实现源自 [rikumi/coloros-mod](https://github.com/rikumi/coloros-mod)，**MIT License**
- Banner 视觉遵循 [EGD（Elegant Geek Design）](https://github.com/Kepsilent/Elegant-Geek-Design) 设计规范
