package com.oakonell.libridroid.download;

import com.oakonell.libridroid.download.Download.Downloads;
import com.oakonell.utils.LogHelper;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public final class DownloadHelper {
    private DownloadHelper() {
        // prevent instantiation
    }

    public static Cursor getExistingDownloadRecordFor(ContentResolver resolver, Uri bookUri, long sectionNumber) {
        String bookIdString = bookUri.getPathSegments().get(1);
        Cursor query = resolver.query(
                Download.Downloads.CONTENT_URI,
                null,
                Download.Downloads.COLUMN_NAME_BOOK_ID + " = ? AND " + Download.Downloads.COLUMN_NAME_SECTION_NUM +
                        " = ? ",
                new String[] { bookIdString,
                        Long.toString(sectionNumber) }, null);
        try {
            if (query.moveToFirst()) {
                LogHelper.info("BookViewActivity",
                        "Download for (book, section)=("
                                + bookIdString
                                + ", "
                                + sectionNumber
                                + " already exists, not adding to download queue");
                return query;
            } else {
                return null;
            }
        } finally {
            query.close();
        }
    }

    public static boolean downloadRecordExistsFor(ContentResolver resolver, Uri bookUri, long sectionNumber) {
        return getExistingDownloadRecordFor(resolver, bookUri, sectionNumber) != null;
    }

    public static void delete(Context context, Uri downloadUri) {
        context.getContentResolver().delete(downloadUri, Downloads._ID + " = ?",
                new String[] { downloadUri.getPathSegments().get(1) });
    }

    public static void deleteDownloadsForBook(Context context, Uri bookUri) {
        // TODO delete any downloads for the given book

    }
}
