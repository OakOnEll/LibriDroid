package com.oakonell.libridroid.impl;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import com.oakonell.libridroid.R;
import com.oakonell.libridroid.books.LibridroidPreferences;
import com.oakonell.libridroid.books.LibrivoxSearchActivity;
import com.oakonell.libridroid.books.MyBooksActivity;
import com.oakonell.libridroid.download.DownloadViewActivity;

public final class MenuHelper {
    public static final int MENU_SEARCH_ID = 1;
    public static final int MENU_SETTINGS_ID = 2;
    public static final int MENU_MY_BOOKS_ID = 3;
    public static final int MENU_DOWNLOADS_ID = 4;
    public static final int MENU_SHARE_ID = 5;

    private MenuHelper() {
        // prevent instantiation
    }

    public static boolean onCreateOptionsMenu(Menu menu) {
        return onCreateOptionsMenu(menu, false);
    }

    public static boolean onCreateOptionsMenu(Menu menu, boolean includeShare) {
        MenuItem myBooksItem = menu.add(Menu.NONE, MENU_MY_BOOKS_ID, Menu.NONE, R.string.my_books);
        myBooksItem.setIcon(R.drawable.ic_menu_my_books_icon);

        if (includeShare) {
            MenuItem shareItem = menu.add(Menu.NONE, MENU_SHARE_ID, Menu.NONE, R.string.share);
            shareItem.setIcon(android.R.drawable.ic_menu_share);
        }

        MenuItem searchItem = menu.add(Menu.NONE, MENU_SEARCH_ID, Menu.NONE, R.string.search);
        searchItem.setIcon(android.R.drawable.ic_menu_search);

        MenuItem settingsItem = menu.add(Menu.NONE, MENU_SETTINGS_ID, Menu.NONE, R.string.settings);
        settingsItem.setIcon(android.R.drawable.ic_menu_preferences);

        MenuItem downloadsItem = menu.add(Menu.NONE, MENU_DOWNLOADS_ID, Menu.NONE, R.string.downloads_menu_item);
        downloadsItem.setIcon(R.drawable.ic_menu_downloads_icon);
        return true;
    }

    public static boolean onOptionsItemSelected(Activity activity, MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SEARCH_ID: {
                Intent intent = new Intent(activity, LibrivoxSearchActivity.class);
                activity.startActivity(intent);
                return true;
            }
            case MENU_MY_BOOKS_ID: {
                Intent intent = new Intent(activity, MyBooksActivity.class);
                activity.startActivity(intent);
                return true;
            }
            case MENU_DOWNLOADS_ID: {
                Intent intent = new Intent(activity, DownloadViewActivity.class);
                activity.startActivity(intent);
                return true;
            }
            case MENU_SETTINGS_ID: {
                Intent intent = new Intent(activity, LibridroidPreferences.class);
                activity.startActivity(intent);
                return true;
            }
            default:
                throw new RuntimeException("Unexpected menu item id " + item.getItemId());
        }
    }

    public static boolean onSearchRequested(Activity activity) {
        Intent intent = new Intent(activity,
                LibrivoxSearchActivity.class);
        activity.startActivity(intent);
        return true;
    }
}
