package com.leovp.reflection.wrappers;

import android.os.IInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class PowerManager {
    private final IInterface manager;
    private Method isScreenOnMethod;

    public PowerManager(IInterface manager) {
        this.manager = manager;
    }

    private Method getIsScreenOnMethod() throws NoSuchMethodException {
        if (isScreenOnMethod == null) {
            // Since minSdk is 21, Build.VERSION_CODES.KITKAT_WATCH (API 20) is always satisfied.
            isScreenOnMethod = manager.getClass().getMethod("isInteractive");
        }
        return isScreenOnMethod;
    }

    public boolean isScreenOn() {
        try {
            Method method = getIsScreenOnMethod();
            return (boolean) method.invoke(manager);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            return false;
        }
    }
}
