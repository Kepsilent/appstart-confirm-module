# LSPosed 入口：java_init.list 按全限定名加载，禁止删除/混淆
-keep class io.github.kepsilent.appstartconfirm.XposedInit { *; }

# libxposed service 库：框架通过 binder 反射调用其方法（XposedService 等），
# 必须完整保留，禁止任何收缩/混淆——R8 收缩会导致模块静默加载失败（实测）。
-keep class io.github.libxposed.service.** { *; }
-keep interface io.github.libxposed.service.** { *; }

# 兼容层与 hook 类被 XposedInit 代码级引用，shrink 自动保留；不混淆
-dontobfuscate

# hook 目标类名是字符串字面量，不受影响；不需要 keep 任何系统类
