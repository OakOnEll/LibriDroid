package com.oakonell.libridroid.books;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.Libridroid.Search;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.download.Download;
import com.oakonell.libridroid.download.DownloadHelper;
import com.oakonell.libridroid.download.DownloadService;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.libridroid.impl.FileHelper;
import com.oakonell.libridroid.player.LibriDroidPlayerService;
import com.oakonell.libridroid.player.PlayerActivity;
import com.oakonell.utils.LogHelper;

public final class BooksHelper {
    private BooksHelper() {
        // prevent instantiation
    }

    public static void downloadBookFiles(final Context context, final Uri bookUri) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                BooksHelper.updateBookInLibrary(context, bookUri, 1);
                // add section files not already existing to (top of)
                // download
                // queue
                // the service will then download them
                boolean anyRequestsForThisBook = needToDownloadAnySections(context, bookUri, true);

                if (anyRequestsForThisBook) {
                    startDownloadService(context);
                }
            }
        };
        new Thread(runnable).start();
    }

    private static void startDownloadService(final Context context) {
        Intent service = new Intent(context, DownloadService.class);

        ComponentName serviceName = context.startService(service);
        if (serviceName == null) {
            LogHelper.warn("BookViewActivity", "No download service started");
        }
    }

    public static void addBookToLibrary(Context context, Uri bookUri) {
        updateBookInLibrary(context, bookUri, 1);
    }

    private static void updateBookInLibrary(Context context, final Uri bookUri, int putInLibrary) {
        ContentValues values = new ContentValues();
        values.put(Libridroid.Books.COLUMN_NAME_IS_DOWNLOADED, putInLibrary);
        context.getContentResolver().update(bookUri, values, null, null);
    }

    public static void playBook(final Context context, final Uri bookUri, final boolean launchActivity,
            final Runnable after) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                BooksHelper.updateBookInLibrary(context, bookUri, 1);
                Intent service = new Intent(context, LibriDroidPlayerService.class);
                service.setData(bookUri);

                // start the player service, or start it playing this book
                context.startService(service);

                if (launchActivity) {
                    // bring up the player's activity
                    Intent player = new Intent(context, PlayerActivity.class);
                    context.startActivity(player);
                }
                if (after != null) {
                    after.run();
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public static void playSection(Context context, Uri bookUri, int index) {
        updateBookSection(context, bookUri, index);
        playBook(context, bookUri, true, null);
    }

    public static void updateBookSection(Context context, final Uri bookUri, int section) {
        ContentValues values = new ContentValues();
        values.put(Libridroid.Books.COLUMN_NAME_CURRENT_SECTION, section);
        values.put(Libridroid.Books.COLUMN_NAME_CURRENT_POSITION, 0);
        context.getContentResolver().update(bookUri, values, null, null);
    }

    // @NonNull
    public static Uri getBookUriForSearch(Context context, Uri uri, boolean addIfMissing) {
        Cursor searchRecord = context.getContentResolver().query(uri, null, null, null, null);
        try {
            if (!searchRecord.moveToFirst()) {
                throw new RuntimeException("No search record for uri " + uri);
            }
            String bookId = searchRecord.getString(searchRecord.getColumnIndex(Search.COLUMN_NAME_BOOK_ID));
            if (bookId != null) {
                return ContentUris.withAppendedId(Libridroid.Books.CONTENT_ID_URI_BASE, Long.parseLong(bookId));
            }
            return insertBookFromSearch(context, searchRecord);
        } finally {
            searchRecord.close();
        }
    }

    // @NonNull
    private static Uri insertBookFromSearch(Context context, Cursor searchRecord) {
        // a direct map for most fields from search to new book record
        ContentValues bookValues = new ContentValues();
        for (String each : searchRecord.getColumnNames()) {
            String searchValue = searchRecord.getString(searchRecord.getColumnIndex(each));
            if (each.equals(Libridroid.Search.COLUMN_NAME_COLLECTION_TITLE)) {
                continue;
            }
            if (each.equals(Libridroid.Search.COLUMN_NAME_BOOK_ID)) {
                continue;
            }
            if (each.equals(Libridroid.Search.COLUMN_NAME_IS_DOWNLOADED)) {
                continue;
            }
            if (each.equals(Libridroid.Search.COLUMN_NAME_LAST_USED)) {
                continue;
            }
            bookValues.put(each, searchValue);
        }
        String collectionTitle = searchRecord.getString(searchRecord
                .getColumnIndex(Libridroid.Search.COLUMN_NAME_COLLECTION_TITLE));
        if (!TextUtils.isEmpty(collectionTitle)) {
            Pattern pattern = Pattern.compile("\\(in \"(.*)\"\\)");
            Matcher matcher = pattern.matcher(collectionTitle);
            if (!matcher.find()) {
                bookValues.put(Libridroid.Books.COLUMN_NAME_TITLE, collectionTitle);
            } else {
                bookValues.put(Libridroid.Books.COLUMN_NAME_TITLE, matcher.group(1));
            }
            bookValues.put(Libridroid.Books.COLUMN_NAME_AUTHOR, "Various");
        }
        return context.getContentResolver().insert(Libridroid.Books.CONTENT_URI, bookValues);
    }

    public static boolean needToDownloadAnySections(Context context, final Uri bookUri, boolean addIfMissing) {
        Book book = Book.read(context.getContentResolver(), bookUri);
        boolean anyRequests = false;

        List<BookSection> sections = book.getSections(context.getContentResolver());
        for (BookSection each : sections) {
            // if already exists, do not add
            if (DownloadHelper.downloadRecordExistsFor(context.getContentResolver(), bookUri, each.getSectionNumber())) {
                if (!addIfMissing) {
                    return true;
                }
                anyRequests = true;
                continue;
            }

            boolean sectionDownloadNeeded = sectionDownloadNeeded(context, each, addIfMissing);
            if (sectionDownloadNeeded) {
                if (!addIfMissing) {
                    return true;
                }
                anyRequests = true;
            }
        }
        return anyRequests;
    }

    public static void downloadSectionIfNeeded(final Context context, final Uri bookUri, final int sectionNum) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                addBookToLibrary(context, bookUri);
                Book book = Book.read(context.getContentResolver(), bookUri);
                boolean wasAdded = sectionDownloadNeeded(context,
                        book.getSection(context.getContentResolver(), sectionNum),
                        true);
                if (wasAdded) {
                    // start the download service
                    startDownloadService(context);
                }
            }
        };
        new Thread(runnable).start();
    }

    private static boolean sectionDownloadNeeded(Context context, BookSection section, boolean addIfMissing) {
        Book book = section.getBook(context.getContentResolver());
        File file = FileHelper.getFile(context.getContentResolver(), book.getId(), section.getSectionNumber(), false,
                book.getTitle(), book.getLibrivoxId());
        if (file.exists()) {
            // if the file is already complete, skip it
            if (file.length() >= section.getSize()) {
                return false;
            }
        }

        if (addIfMissing) {
            // Otherwise, there is no pending download request, and the file
            // either doesn't exist or is partially downloaded-
            // at a request to download it
            ContentValues values = new ContentValues();
            values.put(Download.Downloads.COLUMN_NAME_BOOK_ID, book.getId());
            values.put(Download.Downloads.COLUMN_NAME_SECTION_NUM, section.getSectionNumber());
            values.put(Download.Downloads.COLUMN_NAME_URL, section.getUrl());
            values.put(Download.Downloads.COLUMN_NAME_TOTAL_BYTES, section.getSize());

            context.getContentResolver().insert(Download.Downloads.CONTENT_URI, values);
        }
        return true;
    }

    @Deprecated
    private static void deleteBookFiles(Context context, Uri bookUri, Runnable postDelete) {
        Book book = Book.read(context.getContentResolver(), bookUri);
        deleteBookFiles(book, postDelete);
        // TODO delete pending downloads, too
    }

    private static void deleteBookFiles(Book book, Runnable postDelete) {
        File bookDirectory = FileHelper.getBookDirectory(book);
        FileHelper.deleteFiles(bookDirectory);
        if (postDelete != null) {
            postDelete.run();
        }
    }

    public static void promptAndDeleteBookFiles(final Context context, final Uri bookUri,
            final Runnable postDeleteActions) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        final ProgressDialog pd = ProgressDialog
                                .show(context, context.getText(R.string.deletingFiles),
                                        context.getText(R.string.pleaseWait), true);
                        final Runnable dismissProgressAndPostDelete = new Runnable() {
                            @Override
                            public void run() {
                                if (postDeleteActions != null) {
                                    postDeleteActions.run();
                                }
                                if (pd != null) {
                                    pd.dismiss();
                                }
                            }
                        };
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                deleteBookFiles(context, bookUri, dismissProgressAndPostDelete);
                            }
                        });
                        thread.start();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                    default:
                        throw new RuntimeException("Unexpected button was clicked");
                }
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        Book book = Book.read(context.getContentResolver(), bookUri);
        String message = context.getString(R.string.deleteAllFilesFor, book.getTitle());
        builder.setMessage(message).setPositiveButton(android.R.string.yes, dialogClickListener)
                .setNegativeButton(android.R.string.no, dialogClickListener).show();
    }

    public static void removeBookFromLibrary(final Activity context, final Uri bookUri, final Runnable postDelete) {
        final Runnable removeAndPostDelete = new Runnable() {
            @Override
            public void run() {
                BooksHelper.updateBookInLibrary(context, bookUri, 0);
                DownloadHelper.deleteDownloadsForBook(context, bookUri);
                if (postDelete != null) {
                    postDelete.run();
                }
            }
        };
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Book book = Book.read(context.getContentResolver(), bookUri);

                // if any files exist, ask to delete them
                File bookDirectory = FileHelper.getBookDirectory(book);
                if (bookDirectory.exists()) {
                    Runnable promptAndDelete = new Runnable() {
                        @Override
                        public void run() {
                            promptAndDeleteBookFiles(context, bookUri, removeAndPostDelete);
                        }
                    };
                    context.runOnUiThread(promptAndDelete);
                } else {
                    removeAndPostDelete.run();
                }
            }
        });
        thread.start();
    }

}
