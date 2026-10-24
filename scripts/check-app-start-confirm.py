#!/usr/bin/env python3
"""不构建 APK；用 JVM 桩验证生产 hook 的门控、范围与 Intent 保留（独立模块版）。"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/kepsilent/appstartconfirm/hooks/AppStartConfirmHooks.java'
KEY = 'app_start_confirm_always_allow_enabled'

JAVAC = shutil.which('javac')
JAVA = shutil.which('java')
if not JAVAC or not JAVA:
    # 兜底：本目录或工作区内的解压 JDK（如 Temurin）
    for candidate in sorted(Path('/home/user/Doubao/chats/38448723866192898').glob('jdk-*')):
        if (candidate / 'bin/javac').is_file():
            JAVAC = str(candidate / 'bin/javac')
            JAVA = str(candidate / 'bin/java')
            break
if not JAVAC or not JAVA:
    raise SystemExit('javac/java 不在 PATH 中，无法运行 JVM 桩验证')

STUBS = {
    'android/content/Intent.java': '''
        package android.content;
        import java.util.*;
        public class Intent {
            public String action;
            public final Map<String,Object> extras = new HashMap<>();
            public Intent(String a) { action=a; }
            public Intent(Intent i) { action=i.action; extras.putAll(i.extras); }
            public String getAction() { return action; }
            public int getIntExtra(String k,int d) {
                Object v=extras.get(k); return v instanceof Integer ? (Integer)v : d;
            }
            public Intent putExtra(String k,int v) { extras.put(k,v); return this; }
        }
    ''',
    'android/app/Activity.java': '''
        package android.app;
        import android.content.Intent;
        public class Activity {
            public Intent intent; public int setCalls;
            public Intent getIntent() { return intent; }
            public void setIntent(Intent i) { intent=i; setCalls++; }
        }
    ''',
    'android/os/Bundle.java': 'package android.os; public class Bundle {}',
    'io/github/kepsilent/appstartconfirm/XposedInit.java': '''
        package io.github.kepsilent.appstartconfirm;
        public class XposedInit {
            public static final String KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED =
                "app_start_confirm_always_allow_enabled";
            public static boolean enabled;
            public static boolean readBool(String key,boolean fallback) { return enabled; }
            public static void log(String message) {}
        }
    ''',
    'io/github/kepsilent/appstartconfirm/xposed/XC_LoadPackage.java': '''
        package io.github.kepsilent.appstartconfirm.xposed;
        public class XC_LoadPackage {
            public static class LoadPackageParam { public ClassLoader classLoader; }
        }
    ''',
    'io/github/kepsilent/appstartconfirm/xposed/XC_MethodHook.java': '''
        package io.github.kepsilent.appstartconfirm.xposed;
        public abstract class XC_MethodHook {
            protected void beforeHookedMethod(MethodHookParam param) {}
            public void fire(Object activity) {
                MethodHookParam p=new MethodHookParam(); p.thisObject=activity;
                beforeHookedMethod(p);
            }
            public static class MethodHookParam { public Object thisObject; }
        }
    ''',
    'io/github/kepsilent/appstartconfirm/xposed/XposedHelpers.java': '''
        package io.github.kepsilent.appstartconfirm.xposed;
        public class XposedHelpers {
            public static XC_MethodHook installed;
            public static void findAndHookMethod(String name,ClassLoader loader,
                    String method,Object... args) {
                if (!name.equals("com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity")
                        || !method.equals("onCreate") || args[0]!=android.os.Bundle.class)
                    throw new AssertionError("unexpected hook target");
                installed=(XC_MethodHook)args[args.length-1];
            }
        }
    ''',
    'io/github/kepsilent/appstartconfirm/hooks/AppStartConfirmTest.java': '''
        package io.github.kepsilent.appstartconfirm.hooks;
        import android.app.Activity;
        import android.content.Intent;
        import io.github.kepsilent.appstartconfirm.XposedInit;
        import io.github.kepsilent.appstartconfirm.xposed.*;
        public class AppStartConfirmTest {
            static int checks;
            static final String ACTION="oplus.app.action.CHECK_ALLOW_START_ACTIVITY";
            static final String VERSION="activity_start_confirm_version";
            static void check(boolean value) {
                if (!value) throw new AssertionError("check "+(checks+1)); checks++;
            }
            static Intent input(int type,int version) {
                Intent i=new Intent(ACTION).putExtra("start_type",type).putExtra(VERSION,version);
                i.extras.put("caller_package","caller"); i.extras.put("callee_package","callee");
                i.extras.put("caller_uid",200001); i.extras.put("callee_uid",200002);
                i.extras.put("extra_userid",2); i.extras.put("request_code",42);
                i.extras.put("android.intent.extra.INTENT",new Object());
                return i;
            }
            public static void main(String[] args) {
                check(AppStartConfirmHooks.dialogIntent(null,true)==null);
                for (int version=-1;version<=3;version++) {
                    for (int type=-1;type<=10;type++) {
                        Intent original=input(type,version);
                        check(AppStartConfirmHooks.dialogIntent(original,false)==original);
                        Intent out=AppStartConfirmHooks.dialogIntent(original,true);
                        boolean eligible=version==2&&(type==0||type==5||type==6);
                        check((out!=original)==eligible);
                        check(original.getIntExtra(VERSION,-99)==version);
                        check(out.getIntExtra(VERSION,-99)==(eligible?0:version));
                        for (String k:original.extras.keySet()) {
                            if (!k.equals(VERSION)) check(out.extras.get(k)==original.extras.get(k));
                        }
                        check(out.getAction().equals(ACTION));
                    }
                }
                Intent wrong=input(0,2); wrong.action="other.action";
                check(AppStartConfirmHooks.dialogIntent(wrong,true)==wrong);
                Intent missing=input(0,2); missing.extras.remove("start_type");
                check(AppStartConfirmHooks.dialogIntent(missing,true)==missing);
                Intent unknown=input(0,2); unknown.extras.remove(VERSION);
                check(AppStartConfirmHooks.dialogIntent(unknown,true)==unknown);
                AppStartConfirmHooks.hook(new XC_LoadPackage.LoadPackageParam());
                check(XposedHelpers.installed!=null);
                Activity a=new Activity(); a.intent=input(0,2);
                XposedHelpers.installed.fire(a);
                check(a.setCalls==0); // 默认关闭，连 setIntent 都不执行。
                XposedInit.enabled=true;
                XposedHelpers.installed.fire(a);
                check(a.setCalls==1&&a.intent.getIntExtra(VERSION,-1)==0);
                XposedInit.enabled=false;
                Activity next=new Activity(); next.intent=input(0,2);
                XposedHelpers.installed.fire(next);
                check(next.setCalls==0&&next.intent.getIntExtra(VERSION,-1)==2);
                System.out.println("PASS: "+checks+" JVM checks (mock Android; no device validation)");
            }
        }
    ''',
}

with tempfile.TemporaryDirectory(prefix='appstart-confirm-') as temp:
    base = Path(temp)
    files = []
    for name, content in STUBS.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(content))
        files.append(str(path))
    subprocess.run([JAVAC, '-d', str(base / 'classes'), str(SOURCE), *files], check=True)
    subprocess.run([JAVA, '-cp', str(base / 'classes'),
                    'io.github.kepsilent.appstartconfirm.hooks.AppStartConfirmTest'], check=True)

# 静态检查：开关键、作用域接入、字符串资源。
locales = [d.name for d in (ROOT / 'app/src/main/res').iterdir()
           if d.is_dir() and d.name.startswith('values') and (d / 'strings.xml').is_file()]
for locale in locales:
    strings = {s.attrib['name']: s.text for s in
               ET.parse(ROOT / 'app/src/main/res' / locale / 'strings.xml').getroot()}
    assert {'app_start_confirm_always_allow', 'about_project'} <= set(strings)
    assert all(strings.values())
entry = (ROOT / 'app/src/main/java/io/github/kepsilent/appstartconfirm/XposedInit.java').read_text()
main = (ROOT / 'app/src/main/java/io/github/kepsilent/appstartconfirm/MainActivity.java').read_text()
scope = (ROOT / 'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines()
assert KEY in entry and 'XposedInit.KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED' in main
assert main.count('XposedInit.KEY_APP_START_CONFIRM_ALWAYS_ALLOW_ENABLED') == 1
assert 'com.oplus.securitypermission' in entry
assert 'AppStartConfirmHooks.hook(lpparam);' in entry
assert 'com.oplus.securitypermission' in scope
print('PASS: setting key, package routing, static scope and locale resources:', ', '.join(locales))
