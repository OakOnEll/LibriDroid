package com.oakonell.utils.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.RatingBar.OnRatingBarChangeListener;
import android.widget.TextView;

import com.oakonell.utils.R;

public class AppRater {
    // adapted from
    // http://www.androidsnippets.com/prompt-engaged-users-to-rate-your-app-in-the-android-market-appirater

    private static final String PREF_NAME = "apprater";
    private static final int DAYS_UNTIL_PROMPT = 1;
    private static final int PROMPT_EVERY_N_LAUNCHES = 5;

    private static final int MILLIS_UNTIL_PROMPT = DAYS_UNTIL_PROMPT * 24 * 60 * 60 * 1000;
    private static final String DATE_FIRSTLAUNCH = "date_firstlaunch";
    private static final String LAUNCH_COUNT = "launch_count";
    private static final String DONT_SHOW_AGAIN = "dont_show_again";

    public static void app_launched(Context mContext) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, 0);
        if (prefs.getBoolean(DONT_SHOW_AGAIN, false)) {
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();

        // Increment launch counter
        long launchCount = prefs.getLong(LAUNCH_COUNT, 0) + 1;
        editor.putLong(LAUNCH_COUNT, launchCount);

        // Get date of first launch
        Long date_firstLaunch = prefs.getLong(DATE_FIRSTLAUNCH, 0);
        if (date_firstLaunch == 0) {
            date_firstLaunch = System.currentTimeMillis();
            editor.putLong(DATE_FIRSTLAUNCH, date_firstLaunch);
        }

        if ((launchCount % PROMPT_EVERY_N_LAUNCHES == 0) &&
                System.currentTimeMillis() >= date_firstLaunch + MILLIS_UNTIL_PROMPT) {
            showRateDialog(mContext, editor);
        }

        editor.commit();
    }

    public static void showRateDialog(final Context mContext, final SharedPreferences.Editor editor) {
        final Dialog dialog = new Dialog(mContext);

        final PackageManager pm = mContext.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(mContext.getPackageName(), 0);
        } catch (final NameNotFoundException e) {
            ai = null;
        }
        final String applicationName = (String) (ai != null ? pm.getApplicationLabel(ai) : "("
                + mContext.getString(R.string.rate_unknown_app_title) + ")");

        dialog.setTitle(mContext.getString(R.string.rate_app, applicationName));

        LinearLayout ll = new LinearLayout(mContext);
        ll.setOrientation(LinearLayout.VERTICAL);

        TextView tv = new TextView(mContext);
        tv.setText(mContext.getString(R.string.rate_text, applicationName));
        tv.setWidth(240);
        tv.setPadding(4, 0, 4, 10);
        ll.addView(tv);

        // Button b1 = new Button(mContext);
        // b1.setText(mContext.getString(R.string.rate_app, applicationName));
        RatingBar b1 = new RatingBar(mContext);
        b1.setNumStars(5);
        b1.setRating(4);
        b1.setOnRatingBarChangeListener(new OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                editor.putBoolean(DONT_SHOW_AGAIN, true);
                editor.commit();
                mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="
                        + mContext.getPackageName())));
                dialog.dismiss();
            }
        });
        ll.addView(b1);

        TextView rateCommentView = new TextView(mContext);
        rateCommentView.setText("(" + mContext.getString(R.string.rate_redirects_to_market) + ")");
        rateCommentView.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.addView(rateCommentView);

        Button b2 = new Button(mContext);
        b2.setText(mContext.getText(R.string.rate_remind_later));
        b2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        ll.addView(b2);

        Button b3 = new Button(mContext);
        b3.setText(mContext.getText(R.string.rate_no_thanks));
        b3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editor != null) {
                    editor.putBoolean(DONT_SHOW_AGAIN, true);
                    editor.commit();
                }
                dialog.dismiss();
            }
        });
        ll.addView(b3);

        dialog.setContentView(ll);
        dialog.show();
    }
}
