package com.oakonell.utils.activity;

import android.content.Context;

public class AppLaunchUtils {
    public static void appLaunched(Context mContext) {
        AppRater.app_launched(mContext);
        WhatsNewDisplayer.show(mContext);
    }
}
