package com.oakonell.utils.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

import com.oakonell.utils.R;

/**
 * An edit text preference that displays the current value in the summary text,
 * using the special oakonell namespaced attribute 'formattedSummary' with a
 * '%s' placeholder to display the string value. (The summary attribute will be
 * ignored if formattedSummary is used.)
 * 
 * If the special attribute is not used, it will act as a normal
 * EditTextPreference.
 */
public class ValueDisplayingEditTextPreference extends EditTextPreference {
    private int summaryResId;

    public ValueDisplayingEditTextPreference(Context context) {
        super(context);
        summaryResId = 0;
    }

    public ValueDisplayingEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setVariables(context, attrs);
    }

    private void setVariables(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ValueDisplayingEditTextPreference);
        summaryResId = a.getResourceId(R.styleable.ValueDisplayingEditTextPreference_formattedSummary, 0);
        if (summaryResId != 0) {
            setSummary(getContext().getString(summaryResId, getText()));
        }
    }

    public ValueDisplayingEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVariables(context, attrs);
    }

    @Override
    public void setText(String text) {
        if (summaryResId != 0) {
            setSummary(getContext().getString(summaryResId, text));
        } else {
            setSummary(text);
        }
        super.setText(text);
    }

}
