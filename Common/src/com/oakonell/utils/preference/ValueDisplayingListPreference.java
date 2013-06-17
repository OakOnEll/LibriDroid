package com.oakonell.utils.preference;

import android.content.Context;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

/**
 * A list preference that displays the current entry value in the summary text
 * (The summary attribute will be ignored.)
 */
public class ValueDisplayingListPreference extends ListPreference {

    public ValueDisplayingListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeSummary();
    }

    public ValueDisplayingListPreference(Context context) {
        super(context);
        initializeSummary();
    }

    private void initializeSummary() {
        CharSequence entry = getEntry();
        if (TextUtils.isEmpty(entry)) {
            // TODO ..
        }
        setSummary(entry);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        setSummary(getEntry());
    }

}
