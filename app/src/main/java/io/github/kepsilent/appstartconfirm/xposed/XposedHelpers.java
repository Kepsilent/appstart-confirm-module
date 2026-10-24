package io.github.kepsilent.appstartconfirm.xposed;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;

/**
 * 旧版 XposedHelpers 的等价实现（极简裁剪版）：
 * 仅保留本模块实际使用的 findAndHookMethod 路径，其余字段操作/调用/热加载
 * 保护等死代码全部移除。底层换成新版 libxposed API 的拦截器链。
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        if (classLoader == null) classLoader = ClassLoader.getSystemClassLoader();
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(e);
        }
    }

    static void hookExecutable(Executable executable, XC_MethodHook callback) {
        XposedInterface framework = XposedBridge.framework();
        executable.setAccessible(true);
        try {
            // 被内联的方法 hook 不上(system_server 常见), 先去优化再挂钩。
            framework.deoptimize(executable);
        } catch (Throwable ignored) {
        }
        framework.hook(executable).intercept(new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                return callback.handleHookedMethod(chain);
            }
        });
    }

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0
                || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("no callback defined");
        }
        XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Object[] types = Arrays.copyOf(parameterTypesAndCallback, parameterTypesAndCallback.length - 1);
        hookExecutable(findMethodExact(clazz, methodName,
                getParameterClasses(clazz.getClassLoader(), types)), callback);
    }

    public static void findAndHookMethod(String className, ClassLoader classLoader, String methodName,
                                         Object... parameterTypesAndCallback) {
        findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    /** 沿类继承链查找方法(含父类), 找不到直接抛 NoSuchMethodError。 */
    static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Class<?>[] types = (parameterTypes == null) ? new Class<?>[0] : parameterTypes;
        Class<?> clz = clazz;
        while (clz != null) {
            try {
                Method method = clz.getDeclaredMethod(methodName, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                clz = clz.getSuperclass();
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + parametersString(types) + "#exact");
    }

    private static Class<?>[] getParameterClasses(ClassLoader classLoader, Object[] parameterTypes) {
        Class<?>[] classes = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object type = parameterTypes[i];
            if (type == null) throw new ClassNotFoundError("parameter type must not be null");
            if (type instanceof Class) classes[i] = (Class<?>) type;
            else if (type instanceof String) classes[i] = findClass((String) type, classLoader);
            else throw new ClassNotFoundError("parameter type must either be specified as Class or String: " + type);
        }
        return classes;
    }

    private static String parametersString(Class<?>[] types) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(types[i] == null ? "<null>" : types[i].getCanonicalName());
        }
        return sb.append(')').toString();
    }

    public static final class ClassNotFoundError extends Error {

        private static final long serialVersionUID = 1L;

        public ClassNotFoundError(Throwable cause) {
            super(cause);
        }

        public ClassNotFoundError(String msg) {
            super(msg);
        }
    }
}
