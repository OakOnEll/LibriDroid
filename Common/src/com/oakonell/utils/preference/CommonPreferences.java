package com.oakonell.utils.preference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Log;

import com.oakonell.utils.R;
import com.oakonell.utils.activity.WhatsNewDisplayer;

public abstract class CommonPreferences extends PreferenceActivity {

    protected void postCreate(final Class<? extends Activity> aboutActivityClass) {
        final CommonPreferences me = this;
        Preference aboutPref = findPreference(getString(R.string.pref_about_key));
        aboutPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent aboutIntent = new Intent(me, aboutActivityClass);
                startActivity(aboutIntent);
                return true;
            }
        });
        Preference resetPref = findPreference(getString(R.string.pref_reset_preferences_key));
        resetPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                resetPreferences();
                return true;
            }
        });

        Preference changesPref = findPreference(getString(R.string.pref_changes_key));
        if (changesPref == null) {
            Log.i("CommonPreferences", "No changes preference.xml entry exists");
            return;
        }
        changesPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                WhatsNewDisplayer.show(me, false);
                return true;
            }
        });
        if (!WhatsNewDisplayer.changesFileExists(this)) {
            PreferenceCategory otherPrefCat = (PreferenceCategory) findPreference(getString(R.string.pref_other_category));
            if (otherPrefCat != null) {
                otherPrefCat.removePreference(changesPref);
            }
        }
    }

    protected void resetPreferences() {
        final CommonPreferences me = this;
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(me);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.clear();
                        editor.commit();
                        finish();
                        startActivity(getIntent());
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                    default:
                        throw new RuntimeException("Unexpected button was clicked");
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String message = getResources().getString(R.string.pref_confirm_reset_preferences);
        builder.setMessage(message).setPositiveButton(android.R.string.yes, dialogClickListener)
                .setNegativeButton(android.R.string.no, dialogClickListener).show();
    }
}
