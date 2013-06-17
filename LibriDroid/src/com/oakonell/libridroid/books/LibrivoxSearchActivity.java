package com.oakonell.libridroid.books;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.Libridroid.Search;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.impl.MenuHelper;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.query.BackgroundQueryHelper;

public class LibrivoxSearchActivity extends Activity {
    private ResourceCursorAdapter mAdapter;
    private EditText mSearchText;

    private BackgroundQueryHelper backgroundSearchQueryHelper;
    private Cursor searchCursor;
    private ListView searchList;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.librivox_list);

        backgroundSearchQueryHelper = new BackgroundQueryHelper(this, (ProgressBar) findViewById(R.id.search_progress),
                (TextView) findViewById(R.id.progress_message), null, R.color.error);

        searchList = (ListView) findViewById(R.id.list);
        registerForContextMenu(searchList);

        searchList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterview, View view, int position, long rowId) {
                Cursor c = (Cursor) adapterview.getItemAtPosition(position);

                Long id = c.getLong(c.getColumnIndex(Libridroid.Search._ID));
                Intent intent = new Intent(LibrivoxSearchActivity.this, BookViewActivity.class);
                intent.setData(ContentUris.withAppendedId(Libridroid.Search.CONTENT_ID_URI_BASE, id));
                startActivity(intent);
            }
        });

        mAdapter = new ResourceCursorAdapter(this, R.layout.librivox_list_item, null) {
            @Override
            protected void onContentChanged() {
                super.onContentChanged();
                updateCount();
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                String genre = cursor.getString(cursor.getColumnIndex(Libridroid.Search.COLUMN_NAME_GENRE));
                String title = cursor.getString(cursor.getColumnIndex(Libridroid.Search.COLUMN_NAME_TITLE));
                final String author = cursor.getString(cursor.getColumnIndex(Libridroid.Search.COLUMN_NAME_AUTHOR));

                TextView genreView = (TextView) view.findViewById(R.id.genre);
                genreView.setText(genre);

                TextView titleView = (TextView) view.findViewById(R.id.title);
                titleView.setText(title);

                TextView authorView = (TextView) view.findViewById(R.id.author);
                authorView.setText(author);

                TextView isInLibraryView = (TextView) view.findViewById(R.id.is_in_library);
                int isDownloadedIndex = cursor.getColumnIndex(Libridroid.Search.COLUMN_NAME_IS_DOWNLOADED);
                boolean isDownloaded = false;
                if (!cursor.isNull(isDownloadedIndex)) {
                    isDownloaded = cursor.getInt(isDownloadedIndex) != 0;
                }
                isInLibraryView.setText(isDownloaded ? "X" : "");

                // TODO allow click on author- re-open search on author name?

                // authorView.setOnClickListener(new View.OnClickListener() {
                // @Override
                // public void onClick(View view) {
                // Toast msg = Toast.makeText(
                // view.getContext(),
                // "Clicked Author " + author,
                // Toast.LENGTH_LONG);
                //
                // msg.setGravity(Gravity.CENTER,
                // msg.getXOffset() / 2,
                // msg.getYOffset() / 2);
                //
                // msg.show();
                // }
                // });
            }
        };

        searchList.setAdapter(mAdapter);

        mSearchText = (EditText) findViewById(R.id.searchText);
        mSearchText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                // a null key event observed on some devices
                if (null != keyEvent) {
                    int keyCode = keyEvent.getKeyCode();
                    if ((keyCode == KeyEvent.KEYCODE_ENTER) &&
                            (keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
                        query();
                        return true;
                    }
                }
                return false;
            }
        });

        final Button searchButton = (Button) findViewById(R.id.searchButton);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                query();
            }
        });
        searchButton.setFocusable(true);

        Button clearButton = (Button) findViewById(R.id.clear_text);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchText.setText("");
            }
        });

        doPossibleInitialSearch();
    }

    @Override
    protected void onResume() {
        updateCount();
        super.onResume();
    }

    private void doPossibleInitialSearch() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        String search = extras.getString("search");
        if (!TextUtils.isEmpty(search)) {
            extras.remove("search");
            mSearchText.setText(search);
            query();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundSearchQueryHelper.onDestroy();
    }

    // sends the query to the content provider
    void query() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
        // These are attempts to have the keyboard hidden when the search is
        // done (with an input intent search value)
        // They currently are not working
        // Button searchButton = (Button) findViewById(R.id.searchButton);
        // searchButton.requestFocus();
        // mSearchText.clearFocus();

        String input = mSearchText.getText().toString();
        if (TextUtils.isEmpty(input)) {
            return;
        }

        String queryString = Libridroid.Search.QUERY_PARAM_NAME + "=" + Uri.encode(input);
        String baseUri = Libridroid.Search.CONTENT_URI + "?" + queryString;

        // final Uri queryUri = backgroundSearchQueryHelper.beginQuery(baseUri);
        final Uri queryUri = Uri.parse(baseUri);

        String[] searchFields = new String[] {
                Libridroid.Search.SEARCH_TABLE_NAME + "." + Libridroid.Search.COLUMN_NAME_AUTHOR,
                Libridroid.Search.SEARCH_TABLE_NAME + "." + Libridroid.Search.COLUMN_NAME_TITLE,
                Libridroid.Search.SEARCH_TABLE_NAME + "." + Libridroid.Search.COLUMN_NAME_GENRE,
                Libridroid.Search.SEARCH_TABLE_NAME + "." + Libridroid.Search.COLUMN_NAME_CATEGORY
        };
        final String[] selectArguments = new String[searchFields.length];
        String likePatternInput = "%" + input + "%";
        StringBuilder selectionBuilder = new StringBuilder();
        int i = 0;
        for (String eachField : searchFields) {
            if (i != 0) {
                selectionBuilder.append(" OR ");
            }
            selectionBuilder.append(eachField);
            selectionBuilder.append(" like ? ");
            selectArguments[i] = likePatternInput;
            i++;
        }
        final String selectCriteria = selectionBuilder.toString();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (searchCursor != null) {
                    searchCursor.close();
                    stopManagingCursor(searchCursor);
                    searchCursor = null;
                }

                searchCursor = managedQuery(queryUri, null, selectCriteria,
                        selectArguments, null);
                startManagingCursor(searchCursor);

                // searchList.invalidate();

                mAdapter.changeCursor(searchCursor);
                mAdapter.notifyDataSetChanged();
                searchList.setAdapter(mAdapter);
                // mAdapter.notifyDataSetInvalidated();
            }
        });
        updateCount();
    }

    private void updateCount() {
        LogHelper.info("Search", "updateCount " + (searchCursor == null ?
                "(null)" : searchCursor.getCount()));
        if (searchCursor == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int count = searchCursor.getCount();
                TextView countView = (TextView) findViewById(R.id.booksFound);
                countView.setText(getResources().getQuantityString(R.plurals.books_found,
                        count, count));
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
        query();
        return true;
    }

    private static class SearchMenuItems {
        private static final int PLAY = Menu.FIRST;
        private static final int DELETE = PLAY + 1;
        private static final int REMOVE = DELETE + 1;
        private static final int ADD = REMOVE + 1;
        private static final int DOWNLOAD = ADD + 1;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.list) {
            menu.setHeaderTitle(R.string.bookSearchMenu);
            menu.add(0, SearchMenuItems.PLAY, Menu.NONE, R.string.bookPlay);
            menu.add(0, SearchMenuItems.ADD, Menu.NONE, R.string.bookAdd);
            menu.add(0, SearchMenuItems.DELETE, Menu.NONE, R.string.bookDelete);
            menu.add(0, SearchMenuItems.REMOVE, Menu.NONE, R.string.bookRemove);
            menu.add(0, SearchMenuItems.DOWNLOAD, Menu.NONE, R.string.bookDownload);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo =
                (AdapterContextMenuInfo) item.getMenuInfo();
        int index =
                menuInfo.position;
        Cursor c = (Cursor) mAdapter.getItem(index);
        Long searchId = c.getLong(c.getColumnIndex(Libridroid.Search._ID));
        Uri searchUri = Uri.withAppendedPath(Search.CONTENT_ID_URI_BASE,
                Long.toString(searchId));
        Uri bookUri =
                BooksHelper.getBookUriForSearch(this, searchUri, false);

        Runnable postDeleteActions = new Runnable() {

            @Override
            public void run() {
                Runnable runnable = new Runnable() {

                    @Override
                    public void run() {
                        if (searchCursor != null) {
                            searchCursor.requery();
                        }
                    }
                };
                runOnUiThread(runnable);
            }
        };

        switch (item.getItemId()) {
            case SearchMenuItems.PLAY:
                if (bookUri == null) {
                    bookUri = BooksHelper.getBookUriForSearch(this, searchUri, true);
                }
                BooksHelper.playBook(this, bookUri, true, null);
                return true;
            case SearchMenuItems.DELETE:
                if (bookUri != null) {
                    BooksHelper.promptAndDeleteBookFiles(this, bookUri, null);
                }
                return true;
            case SearchMenuItems.ADD:
                if (bookUri == null) {
                    bookUri =
                            BooksHelper.getBookUriForSearch(this, searchUri, true);
                }
                BooksHelper.addBookToLibrary(LibrivoxSearchActivity.this, bookUri);
                if (searchCursor != null) {
                    searchCursor.requery();
                }
                return true;
            case SearchMenuItems.REMOVE:
                if (bookUri != null) {
                    BooksHelper.removeBookFromLibrary(LibrivoxSearchActivity.this, bookUri,
                            postDeleteActions);
                }
                return true;
            case SearchMenuItems.DOWNLOAD:
                BooksHelper.downloadBookFiles(this, bookUri);
                return true;
            default:
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Not yet implemented: book id " + searchId,
                        LogHelper.TOAST_ERROR_DISPLAY_MS);
                toast.show();
        }
        return false;
    }

}
