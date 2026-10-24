# LSPosed 入口：java_init.list 按全限定名加载，禁止删除/混淆
-keep class io.github.kepsilent.appstartconfirm.XposedInit { *; }

# Manifest 组件（AGP 一般自动保留，这里显式声明兜底）
-keep class io.github.kepsilent.appstartconfirm.MainActivity { *; }
-keep class io.github.kepsilent.appstartconfirm.SettingsProvider { *; }

# 兼容层与 hook 类被 XposedInit 代码级引用，shrink 自动保留；不混淆
-dontobfuscate

# hook 目标类名是字符串字面量，不受影响；不需要 keep 任何系统类
