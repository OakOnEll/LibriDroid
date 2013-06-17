package com.oakonell.libridroid.books;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.libridroid.impl.FileHelper;
import com.oakonell.libridroid.impl.MenuHelper;
import com.oakonell.libridroid.player.LibriDroidPlayerService;
import com.oakonell.libridroid.player.ProgressUpdater;
import com.oakonell.utils.ByteSizeHelper;
import com.oakonell.utils.Duration;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.activity.AbstractFlingableActitivty;
import com.oakonell.utils.query.BackgroundQueryHelper;

/**
 * Display a single book's information
 */
public class BookViewActivity extends AbstractFlingableActitivty {
    private static final UriMatcher URI_MATCHER;

    private static class UriTypes {
        private static final int SEARCH_ID = 1;
        private static final int BOOK_ID = 2;
    }
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(Libridroid.AUTHORITY, "search/#", UriTypes.SEARCH_ID);
        URI_MATCHER.addURI(Libridroid.AUTHORITY, "books/#", UriTypes.BOOK_ID);
    }

    // @NonNull
    private BackgroundQueryHelper backgroundSectionQueryHelper;
    // @NonNull
    private Cursor sectionsCursor;
    // @NonNull
    private ResourceCursorAdapter sectionsAdapter;

    @Nullable
    private LibriDroidPlayerService mBoundService;
    private boolean mIsBound = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.book_activity);

        installFlingHandler(findViewById(R.id.section_list));

        ListView sectionsList = (ListView) findViewById(R.id.section_list);

        registerForContextMenu(sectionsList);

        // add the book's main description view
        // to this listView as a header, so it scrolls nicely
        LayoutInflater inflater = LayoutInflater.from(this);
        View header = inflater.inflate(R.layout.book_description_view, null);
        sectionsList.addHeaderView(header);

        Intent intent = getIntent();
        Uri inputUri = intent.getData();

        Uri bookUri = getBookUri(inputUri);

        book = Book.read(getContentResolver(), bookUri);

        populateView();
        installURLClickHandlers();

        playButton = (Button) findViewById(R.id.play);

        final Button addToLibraryButton = (Button) findViewById(R.id.addToLibrary);
        final Button downloadButton = (Button) findViewById(R.id.download);
        final Button deleteButton = (Button) findViewById(R.id.delete);

        Runnable onSectionLoadCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                // as sections are added, the downloadability state may change
                // (e.g., no sections would mean no need to download)
                // when done loading the sections, update the button text and
                // enable the button
                updateDownloadButton(downloadButton);
            }
        };
        backgroundSectionQueryHelper = new BackgroundQueryHelper(this,
                (ProgressBar) findViewById(R.id.section_progress), (TextView) findViewById(R.id.sectionLoadMessage),
                onSectionLoadCompleteRunnable, R.color.error);

        String bookId = bookUri.getPathSegments().get(1);
        // this has to go before any thing else that might fault the sections
        // cursor, for proper section loading progress notification
        populateSections(bookId);

        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Book playersBook = mBoundService != null ? mBoundService.getBook() : null;
                if (mBoundService != null && mBoundService.libriIsPlaying()
                        && (playersBook != null && playersBook.getUri().equals(book.getUri()))) {
                    mBoundService.userPause();
                } else {
                    Runnable after = new Runnable() {
                        @Override
                        public void run() {
                            transitionToLeft();
                        }
                    };
                    BooksHelper.playBook(BookViewActivity.this, book.getUri(), true, after);

                    book.refresh(getContentResolver());
                    // throw new RuntimeException("Requery didn't work");
                    updateDownloadButton(downloadButton);
                }
            }
        });

        final Runnable postDeleteActions = new Runnable() {
            @Override
            public void run() {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        book.refresh(getContentResolver());
                        sectionsCursor.requery();
                        // throw new
                        // RuntimeException("Requery didn't work");
                        calculateAndDisplayBookDurationAndSize();
                        updateDownloadButton(downloadButton);
                        updateDeleteButton(deleteButton);
                        updateAddToLibraryButtonText(addToLibraryButton);
                    }
                };
                runOnUiThread(runnable);
            }
        };

        updateDownloadButton(downloadButton);
        downloadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        final boolean needAnySections = BooksHelper.needToDownloadAnySections(BookViewActivity.this,
                                book.getUri(), false);
                        Runnable updateLabel = new Runnable() {
                            @Override
                            public void run() {
                                if (needAnySections) {
                                    BooksHelper.downloadBookFiles(BookViewActivity.this, book.getUri());
                                    postDeleteActions.run();
                                }
                            }
                        };
                        runOnUiThread(updateLabel);
                    }
                };
                new Thread(runnable).start();
            }

        });

        updateAddToLibraryButtonText(addToLibraryButton);
        addToLibraryButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isCurrentlyDownloaded = book.isInLibrary();
                if (isCurrentlyDownloaded) {
                    BooksHelper.removeBookFromLibrary(BookViewActivity.this, book.getUri(), postDeleteActions);
                } else {
                    BooksHelper.addBookToLibrary(BookViewActivity.this, book.getUri());
                    book.refresh(getContentResolver());
                    updateAddToLibraryButtonText(addToLibraryButton);
                }
            }
        });

        updateDeleteButton(deleteButton);
        deleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                File bookDirectory = FileHelper.getBookDirectory(book);
                if (bookDirectory.exists()) {
                    com.oakonell.libridroid.download.DownloadHelper.deleteDownloadsForBook(BookViewActivity.this,
                            book.getUri());
                    BooksHelper.promptAndDeleteBookFiles(BookViewActivity.this, book.getUri(), postDeleteActions);
                }

            }
        });
        doBindService(null);
    }

    private Uri getBookUri(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case UriTypes.BOOK_ID:
                return uri;

            case UriTypes.SEARCH_ID: {
                return BooksHelper.getBookUriForSearch(this, uri, true);
            }

            default:
                throw new RuntimeException("Unhandled Uri " + uri);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundSectionQueryHelper.onDestroy();
        doUnbindService();
    }

    private void updateAddToLibraryButtonText(Button button) {
        boolean isDownloaded = book.isInLibrary();
        if (isDownloaded) {
            button.setText(R.string.remove);
        } else {
            button.setText(R.string.addToLibrary);
        }
    }

    private void updateDownloadButton(final Button button) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // TODO do this need to download any in different thread..
                final boolean needAnySections = BooksHelper.needToDownloadAnySections(BookViewActivity.this,
                        book.getUri(),
                        false);
                Runnable updateLabel = new Runnable() {
                    @Override
                    public void run() {
                        boolean hasSections = sectionsCursor.getCount() > 0;
                        boolean shouldBeEnabled = !hasSections || needAnySections;
                        button.setEnabled(shouldBeEnabled);
                    }
                };
                runOnUiThread(updateLabel);
            }
        };
        new Thread(runnable).start();
    }

    private void updateDeleteButton(Button button) {
        File bookDirectory = FileHelper.getBookDirectory(book);
        button.setEnabled(bookDirectory.exists());
    }

    private void populateSections(final String bookId) {
        final ListView sectionsList = (ListView) findViewById(R.id.section_list);
        final Uri plainSectionUri = Libridroid.BookSections.contentUri(bookId);

        sectionsList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterview, View view, int position, long rowId) {
                Cursor c = (Cursor) adapterview.getItemAtPosition(position);
                Long sectionNum = c.getLong(c.getColumnIndex(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER));

                BooksHelper.playSection(BookViewActivity.this, book.getUri(), sectionNum.intValue());
                book.refresh(getContentResolver());
            }
        });

        Uri sectionsUri = backgroundSectionQueryHelper.beginQuery(plainSectionUri.toString());

        sectionsCursor = managedQuery(sectionsUri, null, null, null,
                null);

        LogHelper.info("BookViewActivity",
                "Original calculation of book duration.");
        calculateAndDisplayBookDurationAndSize();
        book.refresh(getContentResolver());

        // Maps video entries from the database to views
        sectionsAdapter = new ResourceCursorAdapter(this, R.layout.section_list_item,
                sectionsCursor) {

            @Override
            protected void onContentChanged() {
                super.onContentChanged();
                LogHelper.info("BookViewActivity", "Content changed- calculating book duration.");
                calculateAndDisplayBookDurationAndSize();
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                int sectionNumber = cursor
                        .getInt(cursor
                                .getColumnIndex(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER));
                String duration = cursor
                        .getString(cursor
                                .getColumnIndex(Libridroid.BookSections.COLUMN_NAME_DURATION));
                long size = cursor
                        .getLong(cursor
                                .getColumnIndex(Libridroid.BookSections.COLUMN_NAME_SIZE));
                String sectionTitle = cursor
                        .getString(cursor
                                .getColumnIndex(Libridroid.BookSections.COLUMN_NAME_SECTION_TITLE));
                String sectionAuthor = cursor
                        .getString(cursor
                                .getColumnIndex(Libridroid.BookSections.COLUMN_NAME_SECTION_AUTHOR));

                TextView isCurrentView = (TextView) view.findViewById(R.id.isCurrentSection);
                if (sectionNumber == book.getCurrentSectionNumber()) {
                    isCurrentView.setVisibility(View.VISIBLE);
                } else {
                    isCurrentView.setVisibility(View.INVISIBLE);
                }

                TextView sectionNumberView = (TextView) view
                        .findViewById(R.id.number);
                sectionNumberView.setText("" + sectionNumber);

                TextView durationView = (TextView)
                        view.findViewById(R.id.duration);
                if (!TextUtils.isEmpty(duration)) {
                    Duration dur = Duration.from(duration);
                    duration = dur.toString();
                }
                durationView.setText(duration);

                long diskUsage = FileHelper.getFile(getContentResolver(), Long.parseLong(bookId), sectionNumber, false,
                        book.getTitle(), book.getLibrivoxId()).length();
                TextView diskUsageView = (TextView)
                        view.findViewById(R.id.diskUsage);
                diskUsageView.setText(ByteSizeHelper.getDisplayable(diskUsage));

                TextView sizeView = (TextView)
                        view.findViewById(R.id.size);
                sizeView.setText(ByteSizeHelper.getDisplayable(size));

                TextView sectionTitleView = (TextView) view
                        .findViewById(R.id.title);
                sectionTitleView.setText(sectionTitle);

                TextView sectionAuthorView = (TextView) view
                        .findViewById(R.id.author);
                sectionAuthorView.setText(sectionAuthor);
            }
        };

        sectionsList.setAdapter(sectionsAdapter);
    }

    private void calculateAndDisplayBookDurationAndSize() {
        LogHelper.info("BookView", "start calculate size and duration");
        int position = sectionsCursor.getPosition();
        boolean any = sectionsCursor.moveToFirst();

        final List<BookSection> sections = new ArrayList<BookSection>();
        if (any) {
            do {
                BookSection section = BookSection.fromCursor(sectionsCursor);
                sections.add(section);
            } while (sectionsCursor.moveToNext());
        }
        sectionsCursor.move(position);

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Duration dur = new Duration(0, 0, 0);
                long size = 0;
                long diskUsage = 0;

                book.refresh(getContentResolver());
                for (BookSection each : sections) {
                    dur = dur.add(each.getDuration());
                    size += each.getSize();
                    long sectionNum = each.getSectionNumber();

                    File file = FileHelper.getFile(getContentResolver(), book.getId(), sectionNum, false,
                            book.getTitle(),
                            book.getLibrivoxId());
                    diskUsage += file.length();
                }

                final Duration finalDur = dur;
                final long finalSize = size;
                final long finalDiskUsage = diskUsage;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView durationTextView = (TextView) findViewById(R.id.duration);
                        durationTextView.setText(finalDur.toString());

                        TextView sizeTextView = (TextView) findViewById(R.id.size);
                        sizeTextView.setText(ByteSizeHelper.getDisplayable(finalSize));

                        TextView diskUsageView = (TextView) findViewById(R.id.diskUsage);
                        diskUsageView.setText(ByteSizeHelper.getDisplayable(finalDiskUsage));
                    }
                });
                LogHelper.info("BookView", "Done calculate size and duration");
                return null;
            }

        };
        task.execute((Void[]) null);

    }

    private void installURLClickHandlers() {
        Map<String, Integer> queryToViewMappings = new HashMap<String, Integer>();
        queryToViewMappings.put(Libridroid.Books.COLUMN_NAME_LIBRIVOX_URL, R.id.librivox_url);
        queryToViewMappings.put(Libridroid.Books.COLUMN_NAME_AUTHOR_WIKI_URL, R.id.author_url);
        queryToViewMappings.put(Libridroid.Books.COLUMN_NAME_WIKI_URL, R.id.wiki_url);

        View bookView = findViewById(R.id.single_book_view);
        installURLOnClick(bookView, R.id.librivox_url, book.getLibrivoxUrl());
        installURLOnClick(bookView, R.id.author_url, book.getAuthorUrl());
        installURLOnClick(bookView, R.id.wiki_url, book.getWikiUrl());
    }

    private void installURLOnClick(View bookView, Integer id, final String value) {
        TextView urlView = (TextView) bookView.findViewById(id);
        if (isBlankOrNull(value)) {
            urlView.setVisibility(View.GONE);
        } else {
            urlView.setVisibility(View.VISIBLE);
            urlView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
                    startActivity(browserIntent);
                }
            });
        }
    }

    private boolean isBlankOrNull(String value) {
        if (value == null) {
            return true;
        }
        return value.trim().length() == 0;
    }

    private void populateView() {
        View bookView = findViewById(R.id.single_book_view);

        updateTextView(bookView, R.id.title, book.getTitle());
        updateTextView(bookView, R.id.author, book.getAuthor());
        updateTextView(bookView, R.id.description, book.getDecription());
        updateTextView(bookView, R.id.category, book.getCategory());
        updateTextView(bookView, R.id.genre, book.getGenre());
        updateTextView(bookView, R.id.num_sections, book.getNumberSections() + "");

        Date lastListened = book.getLastListened();
        String lastListenedString = getString(R.string.never_listened_to);
        if (lastListened != null) {
            String lastListenedDateString = DateUtils.formatDateTime(this, lastListened.getTime(),
                    DateUtils.FORMAT_SHOW_DATE
                            | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY);
            lastListenedString = getString(R.string.last_listened_on, lastListenedDateString);
        }
        updateTextView(bookView, R.id.last_listened_on, lastListenedString);

        TextView authorView = (TextView) bookView.findViewById(R.id.author);
        authorView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String author = book.getAuthor();

                Intent intent = new Intent(BookViewActivity.this, LibrivoxSearchActivity.class);
                intent.putExtra("search", author);
                startActivity(intent);
            }
        });

    }

    private void updateTextView(View bookView, Integer id, String value) {
        TextView textView = (TextView) bookView.findViewById(id);
        if (isBlankOrNull(value)) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            Spanned markUp = Html.fromHtml(value);
            textView.setText(markUp.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogHelper.info("BookView", "onResume");
        // this allows references to bookQuery on a resume to remain valid.
        // would have thought a managed query would do this automatically
        book.refresh(getContentResolver());

        // update the disk usage, which may have changed...
        if (sectionsCursor != null) {
            // trigger the "current playing" to be updated...
            sectionsAdapter.notifyDataSetChanged();

            calculateAndDisplayBookDurationAndSize();
        }
        if (mBoundService != null) {
            setProgressUpdater();
        }
        updatePlayButtonLabel();
        updateDeleteButton((Button) findViewById(R.id.delete));
        updateDownloadButton((Button) findViewById(R.id.download));
        updateAddToLibraryButtonText((Button) findViewById(R.id.addToLibrary));
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((LibriDroidPlayerService.LocalBinder) service).getService();

            setProgressUpdater();

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    // trigger the "current playing" to be updated...
                    book.refresh(getContentResolver());
                    Book playerBook = mBoundService != null ? mBoundService.getBook() : null;
                    if (playerBook != null && playerBook.getUri().equals(book.getUri())) {
                        updatePlayButtonLabel();
                    }
                }

            };
            runOnUiThread(runnable);

        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            if (mBoundService != null) {
                mBoundService.setProgressUpdater(null);
            }
            mBoundService = null;
        }
    };

    private void updatePlayButtonLabel() {
        Runnable update = new Runnable() {
            @Override
            public void run() {
                Book playerBook = mBoundService != null ? mBoundService.getBook() : null;
                if (mBoundService != null && mBoundService.libriIsPlaying()
                        && (playerBook.getUri().equals(book.getUri()))) {
                    // && (book != null && book.getUri().equals(bookUri))) {
                    playButton.setText(R.string.pause);
                } else {
                    playButton.setText(R.string.play);
                }
            }
        };
        runOnUiThread(update);
    }
    private BookViewProgressUpdater progressUpdater;

    private Button playButton;
    private Book book;

    private void setProgressUpdater() {
        progressUpdater = new BookViewProgressUpdater();
        mBoundService.setProgressUpdater(progressUpdater);
    }

    void doBindService(Runnable postBind) {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(BookViewActivity.this, LibriDroidPlayerService.class);
        mIsBound = bindService(intent, mConnection, 0);
        if (postBind != null) {
            postBind.run();
        }
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onPause() {
        LogHelper.info("BookView", "onPause");
        if (mBoundService != null) {
            mBoundService.setProgressUpdater(null);
        }
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return MenuHelper.onCreateOptionsMenu(menu, true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MenuHelper.MENU_SHARE_ID) {
            BookShareHelper.share(this, book);
            return true;
        }
        return MenuHelper.onOptionsItemSelected(this, item);
    }

    @Override
    public boolean onSearchRequested() {
        return MenuHelper.onSearchRequested(this);
    }

    private static class SectionMenuItems {
        private static final int PLAY = Menu.FIRST;
        private static final int DELETE = PLAY + 1;
        private static final int DOWNLOAD = DELETE + 1;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.section_list) {
            menu.setHeaderTitle(R.string.sectionPlayerMenu);
            menu.add(0, SectionMenuItems.PLAY, Menu.NONE, R.string.sectionPlay);
            menu.add(0, SectionMenuItems.DELETE, Menu.NONE, R.string.sectionDelete);
            menu.add(0, SectionMenuItems.DOWNLOAD, Menu.NONE, R.string.sectionDownload);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        final int index = menuInfo.position;

        switch (item.getItemId()) {
            case SectionMenuItems.PLAY:
                BooksHelper.playSection(this, book.getUri(), index);
                return true;
            case SectionMenuItems.DELETE:
                final Runnable uiPostDelete = new Runnable() {
                    @Override
                    public void run() {
                        sectionsCursor.requery();
                    }
                };
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        Book playerBook = Book.read(getContentResolver(), book.getUri());
                        File file = FileHelper.getFile(getContentResolver(), playerBook.getId(), index, false,
                                playerBook.getTitle(), playerBook.getLibrivoxId());
                        if (file.exists()) {
                            if (!file.delete()) {
                                Toast.makeText(BookViewActivity.this, R.string.errorDeletingSection,
                                        LogHelper.TOAST_ERROR_DISPLAY_MS);
                            }
                        }
                        runOnUiThread(uiPostDelete);
                    }
                };
                new Thread(runnable).start();
                return true;
            case SectionMenuItems.DOWNLOAD:
                BooksHelper.downloadSectionIfNeeded(this, book.getUri(), index);
                return true;
            default:
                Toast toast = Toast.makeText(getApplicationContext(), "Not yet implemented",
                        LogHelper.TOAST_ERROR_DISPLAY_MS);
                toast.show();
        }
        return false;
    }

    private class BookViewProgressUpdater implements ProgressUpdater {

        @Override
        public void updateProgress(int position) {
            // do nothing
        }

        @Override
        public void updateBufferProgress(int downloaded) {
            // do nothing
        }

        @Override
        public void waiting(int message) {
            // TODO display buffer/wait somewhere on activity
        }

        @Override
        public void stoppedWaiting() {
            // TODO clear the messge
        }

        @Override
        public void error(int message) {
            // TODO display error somewhere on activity
        }

        @Override
        public void externalPlay() {
            updatePlayButtonLabel();
        }

        @Override
        public void externallyPaused() {
            updatePlayButtonLabel();
        }

        @Override
        public void updateSection(BookSection section) {
            book.refresh(getContentResolver());
        }

        @Override
        public void updateBookAndSection(BookSection updatedSection) {
            book.refresh(getContentResolver());
            // might be a different book playing..?
            updatePlayButtonLabel();
        }

    }

    @Override
    public boolean swipeLeftToRight() {
        // Swipe back to "My Books"
        startActivity(new Intent(getApplicationContext(), MyBooksActivity.class));
        return true;
    }
}
