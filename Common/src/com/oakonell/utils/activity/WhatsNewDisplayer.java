package com.oakonell.utils.activity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.oakonell.utils.R;

public class WhatsNewDisplayer {
    private static final String CHANGES_TXT_FILENAME = "changes.txt";
    // modified from
    // http://www.srombauts.fr/2011/04/21/adding-a-whats-new-screen-to-your-android-application/
    private static final String PREF_NAME = "whats_new";
    private static final String LAST_VERSION_CODE_KEY = "last_version_code";

    public static void show(Context mContext) {
        show(mContext, true);
    }

    // Show the dialog only if not already shown for this version of the
    // application
    public static void show(Context mContext, boolean conditional) {
        // Get the versionCode of the Package, which must be different
        // (incremented) in each release on the market in the
        // AndroidManifest.xml
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (NameNotFoundException e) {
            throw new RuntimeException("Error getting package info", e);
        }
        final PackageInfo packageInfo = pi;

        final SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, 0);
        final long lastVersionCode = prefs.getLong(LAST_VERSION_CODE_KEY, 0);

        if (conditional && packageInfo.versionCode == lastVersionCode) {
            return;
        }

        InputStream open;
        try {
            open = mContext.getAssets().open(CHANGES_TXT_FILENAME);
        } catch (FileNotFoundException e) {
            return;
        } catch (IOException e) {
            throw new RuntimeException("Error getting changes.txt asset", e);
        }
        String message = new java.util.Scanner(open).useDelimiter("\\A").next();
        try {
            open.close();
        } catch (IOException e) {
            throw new RuntimeException("Error Closing changes.txt asset", e);
        }

        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(mContext.getPackageName(), 0);
        } catch (final NameNotFoundException e) {
            ai = null;
        }

        final String applicationName = (String) (ai != null ? pm.getApplicationLabel(ai) : "("
                + mContext.getString(R.string.rate_unknown_app_title) + ")");

        final String title = applicationName + " v" + packageInfo.versionName;

        // Show the News since last version
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Mark this version as read
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putLong(LAST_VERSION_CODE_KEY, packageInfo.versionCode);
                        editor.commit();
                        dialogInterface.dismiss();
                    }
                });
        builder.show();

    }

    public static boolean changesFileExists(Context mContext) {
        try {
            InputStream open = mContext.getAssets().open(CHANGES_TXT_FILENAME);
            open.close();
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Error getting changes.txt asset", e);
        }
    }

}
