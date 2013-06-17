package com.oakonell.utils.share;

import java.util.List;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.oakonell.utils.R;

public class ActivityLaunchAdapter extends ArrayAdapter<ResolveInfo> {
    private Activity owner;

    public ActivityLaunchAdapter(Activity context, int textViewResourceId, List<ResolveInfo> objects) {
        super(context, textViewResourceId, objects);
        owner = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = newView(parent);
        }
        bindView(position, convertView);

        return convertView;
    }

    private View newView(ViewGroup parent) {
        return (owner.getLayoutInflater().inflate(R.layout.share_row, parent, false));
    }

    private void bindView(int position, View row) {
        PackageManager pm = owner.getPackageManager();

        TextView label = (TextView) row.findViewById(R.id.label);
        label.setText(getItem(position).loadLabel(pm));

        ImageView icon = (ImageView) row.findViewById(R.id.icon);
        icon.setImageDrawable(getItem(position).loadIcon(pm));
    }

}