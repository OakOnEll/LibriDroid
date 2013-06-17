package com.oakonell.libridroid;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.oakonell.utils.Utils;

public class AboutLibridroidActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_activity);

        TextView versionText = (TextView) findViewById(R.id.version);
        versionText.setText(Utils.getVersion(this));
    }
}
