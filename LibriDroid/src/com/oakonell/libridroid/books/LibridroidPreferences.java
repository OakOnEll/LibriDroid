package com.oakonell.libridroid.books;

import android.os.Bundle;

import com.oakonell.libridroid.AboutLibridroidActivity;
import com.oakonell.libridroid.R;
import com.oakonell.utils.preference.CommonPreferences;

public class LibridroidPreferences extends CommonPreferences {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        postCreate(AboutLibridroidActivity.class);
    }
}
