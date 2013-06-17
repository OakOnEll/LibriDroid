package com.oakonell.utils;

import android.util.Log;

public final class LogHelper {
    public static final String TAG = "LibriDroid";
    public static final int TOAST_ERROR_DISPLAY_MS = 3000;

    private LogHelper() {
        // prevent instantiation
    }

    public static void info(String category, String text) {
        Log.i(TAG, category + ":" + text);
    }

    public static void warn(String category, String text) {
        Log.w(TAG, category + ":" + text);
    }

    public static void warn(String category, String text, Exception e) {
        Log.w(TAG, category + ":" + text, e);
    }

    public static void error(String category, String text) {
        Log.e(TAG, category + ":" + text);
    }

    public static void error(String category, String text, Exception e) {
        Log.e(TAG, category + ":" + text, e);
    }

    public static void debug(String category, String text) {
        Log.d(TAG, category + ":" + text);
    }

    public static void debug(String category, String text, Exception e) {
        Log.d(TAG, category + ":" + text, e);
    }
}
