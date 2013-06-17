package com.oakonell.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;

// This is borrowed from 
//     http://developer.android.com/
//           reference/android/app/Service.html#startForeground%28int,%20android.app.Notification%29
//  which describes
//      If you need your application to run on platform versions prior to API level 5, 
//      you can use the following model to call the the older setForeground() or this modern method as appropriate: 
public abstract class EarlierAndroidCompatibleService extends Service {
    private NotificationManager mNM;
    private Method oldSetForegroundMethod;
    private Method newStartForegroundMethod;
    private Method newStopForegroundMethod;

    private void invokeMethod(Method method, Object[] args) {
        try {
            method.invoke(this, args);
        } catch (InvocationTargetException e) {
            // Should not happen.
            LogHelper.warn("ApiDemos", "Unable to invoke method", e);
        } catch (IllegalAccessException e) {
            // Should not happen.
            LogHelper.warn("ApiDemos", "Unable to invoke method", e);
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    protected void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (newStartForegroundMethod != null) {
            Object[] mStartForegroundArgs = new Object[2];
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(newStartForegroundMethod, mStartForegroundArgs);
            return;
        }

        // Fall back on the old API.
        Object[] mSetForegroundArgs = new Object[1];
        mSetForegroundArgs[0] = Boolean.TRUE;
        invokeMethod(oldSetForegroundMethod, mSetForegroundArgs);
        mNM.notify(id, notification);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    protected void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (newStopForegroundMethod != null) {
            Object[] mStopForegroundArgs = new Object[1];
            mStopForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(newStopForegroundMethod, mStopForegroundArgs);
            return;
        }

        // Fall back on the old API. Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
        Object[] mSetForegroundArgs = new Object[1];
        mSetForegroundArgs[0] = Boolean.FALSE;
        invokeMethod(oldSetForegroundMethod, mSetForegroundArgs);
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Class<?>[] mStartForegroundSignature = new Class[] {
                int.class, Notification.class };
        Class<?>[] mStopForegroundSignature = new Class[] {
                boolean.class };
        try {
            newStartForegroundMethod = getClass().getMethod("startForeground",
                    mStartForegroundSignature);
            newStopForegroundMethod = getClass().getMethod("stopForeground",
                    mStopForegroundSignature);
            return;
        } catch (NoSuchMethodException e) {
            // Running on an older platform.
            newStartForegroundMethod = null;
            newStopForegroundMethod = null;
        }

        Class<?>[] mSetForegroundSignature = new Class[] {
                boolean.class };
        try {
            oldSetForegroundMethod = getClass().getMethod("setForeground",
                    mSetForegroundSignature);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "OS doesn't have Service.startForeground OR Service.setForeground!");
        }
    }

    @Override
    public void onDestroy() {
        // Make sure our notification is gone.
        // stopForegroundCompat(R.string.foreground_service_started);
    }
}
