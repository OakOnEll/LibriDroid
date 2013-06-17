package com.oakonell.libridroid.books;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.oakonell.libridroid.R;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.utils.share.ShareHelper;

public class BookShareHelper {

    public static void share(Activity context, Book book) {
        final String subject = "Listening to " + book.getTitle();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String defaultText = context.getString(R.string.default_share_message);
        String parametrizedText = preferences.getString(context.getString(R.string.pref_default_share_text_key),
                defaultText);

        String text = parametrizedText.replaceAll("%t", book.getTitle());
        text = text.replaceAll("%a", book.getAuthor());
        text = text.replaceAll("%u", book.getLibrivoxUrl() + "?LibriDroidId=" + book.getLibrivoxId());

        ShareHelper.share(context, subject, text);
    }
}
