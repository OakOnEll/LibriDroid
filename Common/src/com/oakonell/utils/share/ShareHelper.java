package com.oakonell.utils.share;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.oakonell.utils.R;

public class ShareHelper {

    public static void share(final Activity context, final String subject, final String text) {

        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        // Dang facebook app doesn't obey this properly- ONLY takes any URL from
        // the text
        // Intent intentChooser = Intent.createChooser(shareIntent,
        // context.getString(R.string.shareVia));
        // context.startActivity(intentChooser);

        // Alternative attempt, if user chooses facebook, then copy text to
        // clipboard, to allow user to paste into form input directly

        // first of all we need to get all the activities
        // that can take the send intent
        final List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(shareIntent, 0);

        // Now we need to create a dialog to choose between different activities
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.shareVia);

        ActivityLaunchAdapter adapter = new ActivityLaunchAdapter(context, R.id.label, activities);
        builder.setAdapter(adapter, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                ResolveInfo resolveInfo = activities.get(which);
                ActivityInfo activity = resolveInfo.activityInfo;

                ComponentName name = new ComponentName(activity.applicationInfo.packageName, activity.name);
                if (name.getClassName().equals("com.facebook.katana.ShareLinkActivity")) {
                    shareViaFacebook(context, name, subject, text);
                    return;
                }

                startActivity(context, subject, text, name);
            }

        }
                );

        builder.create().show();
    }

    private static void startActivity(final Activity context, final String subject, final String text,
            ComponentName name) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setComponent(name);

        i.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        i.putExtra(android.content.Intent.EXTRA_TEXT, text);

        context.startActivity(i);
    }

    private static void shareViaFacebook(final Activity context, final ComponentName name, final String subject,
            final String text) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String copyPrefString = preferences.getString(
                context.getString(com.oakonell.utils.R.string.pref_copy_for_face_book_key), "PROMPT");
        Boolean faceBookCopyToClipboard = null;
        if (copyPrefString.equals("YES")) {
            faceBookCopyToClipboard = true;
        } else if (copyPrefString.equals("NO")) {
            faceBookCopyToClipboard = false;
        }

        // conditionally copy text to clipboard
        if (faceBookCopyToClipboard != null) {
            if (faceBookCopyToClipboard) {
                copyTextToClipboard(context, text);
            }
            startActivity(context, subject, text, name);
        } else {
            final Dialog dialog = new Dialog(context);
            dialog.setContentView(R.layout.copy_for_facebook_dlg);
            // dialog.setTitle(titleId)
            dialog.setCancelable(true);
            Button yesButton = (Button) dialog.findViewById(R.id.yes_button);
            Button noButton = (Button) dialog.findViewById(R.id.no_button);
            final CheckBox rememberCheckBox = (CheckBox) dialog.findViewById(R.id.remember_checkbox);

            yesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean remember = rememberCheckBox.isChecked();
                    possiblyUpdateCopyPreference(context, preferences, remember, true);
                    copyTextToClipboard(context, text);
                    startActivity(context, subject, text, name);
                    dialog.dismiss();
                }

            });
            noButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean remember = rememberCheckBox.isChecked();
                    possiblyUpdateCopyPreference(context, preferences, remember, false);
                    startActivity(context, subject, text, name);
                    dialog.dismiss();
                }
            });

            dialog.show();

        }
    }

    private static void possiblyUpdateCopyPreference(Activity context, SharedPreferences preferences, boolean remember,
            boolean b) {
        if (!remember) {
            return;
        }
        Editor edit = preferences.edit();

        edit.putString(context.getString(R.string.pref_copy_for_face_book_key), b ? "YES" : "NO");
        edit.commit();
    }

    private static void copyTextToClipboard(final Activity context, final String text) {
        ClipboardManager clipboard = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(text);

        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

}
