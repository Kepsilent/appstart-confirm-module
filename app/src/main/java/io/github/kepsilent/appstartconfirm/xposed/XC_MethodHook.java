package io.github.kepsilent.appstartconfirm.xposed;

import java.lang.reflect.Member;

import io.github.libxposed.api.XposedInterface;

/**
 * 旧版 XC_MethodHook 的等价实现（极简裁剪版）：在新版拦截器链上还原
 * beforeHookedMethod / afterHookedMethod 的语义（含 setResult 短路原方法、
 * 异常传播、以及 before 抛异常时回滚副作用）。
 */
public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static final class MethodHookParam {

        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result = null;
        private Throwable throwable = null;
        private boolean returnEarly = false;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        /** 丢弃回调已经产生的返回值/异常, 回到"未拦截"状态。 */
        void resetResult() {
            result = null;
            throwable = null;
            returnEarly = false;
        }

        boolean isReturnEarly() {
            return returnEarly;
        }
    }

    /** 在拦截器链上跑一次 before -> 原方法 -> after。 */
    Object handleHookedMethod(XposedInterface.Chain chain) throws Throwable {
        MethodHookParam param = new MethodHookParam();
        param.method = (Member) chain.getExecutable();
        param.thisObject = chain.getThisObject();
        param.args = chain.getArgs().toArray();

        try {
            beforeHookedMethod(param);
        } catch (Throwable t) {
            XposedBridge.log(t);
            param.resetResult();
        }

        if (!param.isReturnEarly()) {
            try {
                param.result = chain.proceed(param.args);
                param.throwable = null;
                if (param.thisObject == null) {
                    // 部分框架实现在构造方法执行前拿不到 this, 执行后补上。
                    param.thisObject = chain.getThisObject();
                }
            } catch (Throwable t) {
                param.throwable = t;
                param.result = null;
            }
        }

        Object lastResult = param.result;
        Throwable lastThrowable = param.throwable;
        try {
            afterHookedMethod(param);
        } catch (Throwable t) {
            XposedBridge.log(t);
            if (lastThrowable == null) param.setResult(lastResult);
            else param.setThrowable(lastThrowable);
        }

        if (param.throwable != null) throw param.throwable;
        return param.result;
    }
}
