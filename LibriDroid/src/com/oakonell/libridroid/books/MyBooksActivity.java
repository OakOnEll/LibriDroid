package com.oakonell.libridroid.books;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.Libridroid.Books;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSort;
import com.oakonell.libridroid.impl.FileHelper;
import com.oakonell.libridroid.impl.MenuHelper;
import com.oakonell.utils.ByteSizeHelper;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.activity.AbstractFlingableActitivty;
import com.oakonell.utils.activity.AppLaunchUtils;

public class MyBooksActivity extends AbstractFlingableActitivty {

    private ResourceCursorAdapter mAdapter;
    private Runnable diskUsageUpdater;
    private Cursor bookCursor;
    private String sortBy;
    private PreferenceListener preferenceListener;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_books);

        installFlingHandler(findViewById(R.id.myBooksView));

        final ListView booksList = (ListView) findViewById(R.id.myBooksListLayout);
        registerForContextMenu(booksList);

        booksList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterview, View view, int position, long rowId) {
                Cursor c = (Cursor) adapterview.getItemAtPosition(position);

                Long id = c.getLong(c.getColumnIndex(Libridroid.Books._ID));
                Intent intent = new Intent(MyBooksActivity.this, BookViewActivity.class);
                intent.setData(ContentUris.withAppendedId(Libridroid.Books.CONTENT_ID_URI_BASE, id));
                startActivity(intent);
                transitionToLeft();
            }
        });

        createBookCursor();

        mAdapter = new ResourceCursorAdapter(this, R.layout.my_book_item, bookCursor) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                Book book = Book.fromCursor(cursor);

                TextView genreView = (TextView) view.findViewById(R.id.genre);
                genreView.setText(book.getGenre());

                TextView titleView = (TextView) view.findViewById(R.id.title);
                titleView.setText(book.getTitle());

                TextView authorView = (TextView) view.findViewById(R.id.author);
                authorView.setText(book.getAuthor());

                TextView diskUsageView = (TextView) view.findViewById(R.id.diskUsage);
                long diskUsage = FileHelper.getDiskUsage(FileHelper.getBookDirectory(book));
                if (diskUsage == 0) {
                    diskUsageView.setVisibility(View.GONE);
                } else {
                    diskUsageView.setVisibility(View.VISIBLE);
                    diskUsageView.setText(ByteSizeHelper.getDisplayable(diskUsage));
                }
            }

            @Override
            protected void onContentChanged() {
                super.onContentChanged();
                updateCount();
            }
        };

        updateCount();

        diskUsageUpdater = new Runnable() {
            @Override
            public void run() {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final long diskUsage = FileHelper.getDiskUsage(FileHelper.getRootLibridroidDirectory());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView diskUsageView = (TextView) findViewById(R.id.diskUsage);
                                diskUsageView.setText(ByteSizeHelper.getDisplayable(diskUsage));
                            }
                        });
                    }
                });
                thread.start();
            }
        };
        booksList.setAdapter(mAdapter);

        AppLaunchUtils.appLaunched(this);
    }

    private void createBookCursor() {
        if (bookCursor != null) {
            // close original cursor on resort
            bookCursor.close();
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        sortBy = preferences.getString(getString(R.string.pref_my_books_sort_key), BookSort.LAST_LISTENED.name());

        BookSort sortEnum = BookSort.LAST_LISTENED;
        try {
            sortEnum = BookSort.valueOf(sortBy);
        } catch (Exception e) {
            // ignore, and use default sorting
        }
        bookCursor = managedQuery(Libridroid.Books.CONTENT_URI, null,
                Libridroid.Books.COLUMN_NAME_IS_DOWNLOADED + " > 0 ", null,
                sortEnum.getSortBy());
        if (mAdapter != null) {
            mAdapter.changeCursor(bookCursor);
        }
        displayNoBooksHint();
    }

    private void displayNoBooksHint() {
        View noBooksLayout = findViewById(R.id.no_books_layout);
        if (bookCursor.getCount() == 0) {
            noBooksLayout.setVisibility(View.VISIBLE);
            noBooksLayout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSearchRequested();
                }
            });
        } else {
            noBooksLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        updateCount();
        diskUsageUpdater.run();
        super.onResume();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sortBy.equals(preferences.getString(getString(R.string.pref_my_books_sort_key),
                BookSort.LAST_LISTENED.name()))) {
            createBookCursor();
        } else {
            displayNoBooksHint();
        }
        preferenceListener = new PreferenceListener();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);

    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(
                preferenceListener);
    }

    private void updateCount() {
        final int count = bookCursor.getCount();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView countView = (TextView) findViewById(R.id.booksInLibrary);
                countView.setText(getResources().getQuantityString(R.plurals.books_in_library, count, count));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return MenuHelper.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return MenuHelper.onOptionsItemSelected(this, item);
    }

    @Override
    public boolean onSearchRequested() {
        return MenuHelper.onSearchRequested(this);
    }

    private final class PreferenceListener implements OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (!key.equals(getString(R.string.pref_my_books_sort_key))) {
                return;
            }
            createBookCursor();
        }
    }

    private static class BookMenuItems {
        private static final int PLAY = Menu.FIRST;
        private static final int DELETE = PLAY + 1;
        private static final int REMOVE = DELETE + 1;
        private static final int DOWNLOAD = REMOVE + 1;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.myBooksListLayout) {
            menu.setHeaderTitle(R.string.bookPlayerMenu);
            menu.add(0, BookMenuItems.PLAY, Menu.NONE, R.string.bookPlay);
            menu.add(0, BookMenuItems.DELETE, Menu.NONE, R.string.bookDelete);
            menu.add(0, BookMenuItems.REMOVE, Menu.NONE, R.string.bookRemove);
            menu.add(0, BookMenuItems.DOWNLOAD, Menu.NONE, R.string.bookDownload);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        int index = menuInfo.position;
        Cursor c = (Cursor) mAdapter.getItem(index);
        Long id = c.getLong(c.getColumnIndex(Libridroid.Books._ID));
        Uri bookUri = Uri.withAppendedPath(Books.CONTENT_ID_URI_BASE, Long.toString(id));

        Runnable postDeleteActions = new Runnable() {
            @Override
            public void run() {
                diskUsageUpdater.run();
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        bookCursor.requery();
                    }
                };
                runOnUiThread(runnable);
            }
        };

        switch (item.getItemId()) {
            case BookMenuItems.PLAY:
                BooksHelper.playBook(this, bookUri, true, null);
                return true;
            case BookMenuItems.REMOVE:
                BooksHelper.removeBookFromLibrary(this, bookUri, postDeleteActions);
                return true;
            case BookMenuItems.DOWNLOAD:
                BooksHelper.downloadBookFiles(this, bookUri);
                return true;
            case BookMenuItems.DELETE:
                BooksHelper.promptAndDeleteBookFiles(this, bookUri, postDeleteActions);
                return true;
            default:
                Toast toast = Toast.makeText(getApplicationContext(), "Not yet implemented: book id " + id,
                        LogHelper.TOAST_ERROR_DISPLAY_MS);
                toast.show();
        }
        return false;
    }

}
