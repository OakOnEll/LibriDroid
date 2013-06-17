package com.oakonell.libridroid.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.data.LibrivoxRSSParser.BookParsedCallback;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.ServiceReadToDBBufferer;
import com.oakonell.utils.query.AbstractAsyncQueryHelper;

public class LibrivoxAsyncQueryHelper extends AbstractAsyncQueryHelper {
    private LibraryContentProvider provider;

    /** uri for querying librivox books, expects appending keywords. */
    private static final String QUERY_URI =
            "http://catalog.librivox.org/search_xml.php?extended=1&simple=";

    public LibrivoxAsyncQueryHelper(LibraryContentProvider provider,
            String communicationId) {
        super(provider.getContext(), communicationId, provider.getContext().getString(R.string.progress_parsing),
                provider.getContext()
                        .getString(R.string.progress_reading));
        this.provider = provider;
    }

    @Override
    protected String getQueryUri(String queryText) {
        return QUERY_URI + ServiceReadToDBBufferer.encode(queryText);
    }

    @Override
    protected int parseResponseEntity(HttpEntity entity, Uri uri) throws IOException {
        InputStream content = entity.getContent();
        InputStreamReader inputReader = new InputStreamReader(content, "UTF-8");

        final StringBuilder searchIdsToUpdate = new StringBuilder();
        LibrivoxRSSParser parser = new LibrivoxRSSParser(inputReader,
                new BookParsedCallback() {

                    @Override
                    public void finishedBook(ContentValues values) {
                        /*
                         * Directly invoke insert on the provider, without using
                         * content resolver. We would not want the content
                         * provider to sync this data back to itself.
                         */
                        String librivoxId = values
                                .getAsString(Libridroid.Search.COLUMN_NAME_LIBRIVOX_ID);
                        String title = values
                                .getAsString(Libridroid.Search.COLUMN_NAME_TITLE);
                        Long id = existingIdFor(librivoxId, title);
                        if (id != null) {
                            // allow cleanup of old searches to preserve space
                            // mark recently found entries with date

                            if (searchIdsToUpdate.length() != 0) {
                                searchIdsToUpdate.append(",");
                            }
                            searchIdsToUpdate.append("'");
                            searchIdsToUpdate.append(id.toString());
                            searchIdsToUpdate.append("'");
                            return;
                        }
                        values.put(Libridroid.Search.COLUMN_NAME_LAST_USED, System.currentTimeMillis());
                        LogHelper.debug("LibrivoxAsyncQueryHelper", "Inserting a row");
                        provider.insert(Libridroid.Search.CONTENT_URI, values);
                    }

                });

        int inserted = parser.parse();

        // perform updates to last_used for all the results
        LogHelper.debug("LibrivoxAsyncQueryHelper", "Updating search rows");
        ContentValues updatedValues = new ContentValues();
        updatedValues.put(Libridroid.Search.COLUMN_NAME_LAST_USED, System.currentTimeMillis());
        int update = provider.update(Libridroid.Search.CONTENT_URI,
                updatedValues, Libridroid.Search._ID + " in (" + searchIdsToUpdate.toString() + ")",
                new String[] {});
        LogHelper.debug("LibrivoxAsyncQueryHelper", "Updated " + update + " search rows");

        // only flush old state now that new state has arrived
        deleteOld();

        return inserted;
    }

    private Long existingIdFor(String librivoxId, String title) {
        Cursor cursor = null;
        Long id = null;
        try {
            cursor = provider.query(
                    Libridroid.Search.CONTENT_URI, null,
                    Libridroid.Search.SEARCH_TABLE_NAME + "." +
                            Libridroid.Search.COLUMN_NAME_LIBRIVOX_ID
                            + " = ? AND " + Libridroid.Search.SEARCH_TABLE_NAME + "."
                            + Libridroid.Search.COLUMN_NAME_TITLE
                            + " = ?",
                    new String[] { librivoxId, title }, null);
            if (cursor.moveToFirst()) {
                id = cursor.getLong(cursor
                        .getColumnIndex(Libridroid.Search._ID));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return id;
    }

    private void deleteOld() {
        // delete search entries older than a week
        // TODO make this time frame configurable?
        long oldDateMillis = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000;
        int numDeleted = provider.delete(Libridroid.Search.CONTENT_URI,
                Libridroid.Search.COLUMN_NAME_LAST_USED + " <  ? ", new String[] { Long.toString(oldDateMillis) });
        if (numDeleted > 0) {
            LogHelper.debug("LibrivoxAsyncQueryHelper", "Deleted " + numDeleted + " old search records.");
        }
    }
}
