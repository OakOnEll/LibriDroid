package com.oakonell.libridroid.download;

import com.oakonell.libridroid.download.Download.Downloads;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class DownloadContentProvider extends ContentProvider {

    private static final UriMatcher URI_MATCHER;

    private static class UriTypes {
        private static final int DOWNLOADS = 1;
        private static final int DOWNLOAD_ID = 2;
    };

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(Download.AUTHORITY, "downloads",
                UriTypes.DOWNLOADS);
        URI_MATCHER.addURI(Download.AUTHORITY, "downloads/#",
                UriTypes.DOWNLOAD_ID);
    }

    private DownloadDatabaseHelper databaseHelper;

    @Override
    public boolean onCreate() {
        init();
        return true;
    }

    private void init() {
        databaseHelper = new DownloadDatabaseHelper(getContext(), DownloadDatabaseHelper.DATABASE_NAME, null);
    }

    /**
     * This method is called when a client calls
     * {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}
     * . Queries the database and returns a cursor containing the results.
     * 
     * @return A cursor containing the results of the query. The cursor exists
     *         but is empty if the query returns no results or an exception
     *         occurs.
     * @throws IllegalArgumentException
     *             if the incoming URI pattern is invalid.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs,
            String sortOrder) {

        /**
         * Choose the projection and adjust the "where" clause based on URI
         * pattern-matching.
         */
        switch (URI_MATCHER.match(uri)) {
            case UriTypes.DOWNLOADS: {
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(Download.Downloads.DOWNLOAD_TABLE_NAME);
                return queryDownloadsTable(uri, projection, selection,
                        selectionArgs,
                        sortOrder, qb);
            }

            case UriTypes.DOWNLOAD_ID: {
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(Download.Downloads.DOWNLOAD_TABLE_NAME);
                qb.appendWhere(Download.Downloads._ID + "="
                        + uri.getPathSegments().get(1));

                return queryDownloadsTable(uri, projection, selection,
                        selectionArgs,
                        sortOrder, qb);
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

    }

    private Cursor queryDownloadsTable(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder,
            SQLiteQueryBuilder qb) {
        String orderBy;
        // If no sort order is specified, uses the default
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Download.Downloads.DEFAULT_SORT_ORDER;
        } else {
            // otherwise, uses the incoming sort order
            orderBy = sortOrder;
        }

        // Opens the database object in "read" mode, since no writes need to be
        // done.
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        /*
         * Performs the query. If no problems occur trying to read the database,
         * then a Cursor object is returned; otherwise, the cursor variable
         * contains null. If no records were selected, then the Cursor object is
         * empty, and Cursor.getCount() returns 0.
         */
        Cursor c = qb.query(
                db, // The database to query
                projection, // The columns to return from the query
                selection, // The columns for the where clause
                selectionArgs, // The values for the where clause
                null, // don't group the rows
                null, // don't filter by row groups
                orderBy // The sort order
                );

        // Tells the Cursor what URI to watch, so it knows when its source data
        // changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#getType(Uri)}. Returns the MIME
     * data type of the URI given as a parameter.
     * 
     * @param uri
     *            The URI whose MIME type is desired.
     * @return The MIME type of the URI.
     * @throws IllegalArgumentException
     *             if the incoming URI pattern is invalid.
     */
    @Override
    public String getType(Uri uri) {

        switch (URI_MATCHER.match(uri)) {

            case UriTypes.DOWNLOADS:
                return Download.Downloads.CONTENT_TYPE;
            case UriTypes.DOWNLOAD_ID:
                return Download.Downloads.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#insert(Uri, ContentValues)}.
     * Inserts a new row into the database. This method sets up default values
     * for any columns that are not included in the incoming map. If rows were
     * inserted, then listeners are notified of the change.
     * 
     * @return The row ID of the inserted row.
     * @throws SQLException
     *             if the insertion fails.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        switch (URI_MATCHER.match(uri)) {
            case UriTypes.DOWNLOADS:
                return insertDownload(uri, initialValues);
            default:
                throw new IllegalArgumentException("Unsupported insert URI '"
                        + uri.toString());
        }
    }

    private Uri insertDownload(Uri uri, ContentValues values) {
        // Opens the database object in "write" mode.
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        if (values.get(Downloads.COLUMN_NAME_SEQUENCE) == null) {
            values.put(Downloads.COLUMN_NAME_SEQUENCE, 0);
        }

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insertOrThrow(
                Download.Downloads.DOWNLOAD_TABLE_NAME,
                null,
                values
                );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            Uri newUri = ContentUris.withAppendedId(
                    Download.Downloads.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(newUri, null);
            return newUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an
        // exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#delete(Uri, String, String[])}.
     * Deletes records from the database. If the incoming URI matches the note
     * ID URI pattern, this method deletes the one record specified by the ID in
     * the URI. Otherwise, it deletes a a set of records. The record or records
     * must also match the input selection criteria specified by where and
     * whereArgs.
     * 
     * If rows were deleted, then listeners are notified of the change.
     * 
     * @return If a "where" clause is used, the number of rows affected is
     *         returned, otherwise 0 is returned. To delete all rows and get a
     *         row count, use "1" as the where clause.
     * @throws IllegalArgumentException
     *             if the incoming URI pattern is invalid.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // Does the delete based on the incoming URI pattern.
        switch (URI_MATCHER.match(uri)) {

            case UriTypes.DOWNLOADS:
                count = db.delete(
                        Download.Downloads.DOWNLOAD_TABLE_NAME, // The database
                                                                // table name
                        where, // The incoming where clause column names
                        whereArgs // The incoming where clause values
                        );
                break;

            case UriTypes.DOWNLOAD_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the desired
                 * note ID.
                 */
                finalWhere =
                        Download.Downloads._ID + // The ID column name
                                " = " + // test for equality
                                uri.getPathSegments().get(1); // the incoming
                                                              // note ID

                // If there were additional selection criteria, append them to
                // the
                // final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Performs the delete.
                count = db.delete(
                        Download.Downloads.DOWNLOAD_TABLE_NAME, // The database
                                                                // table
                        // name.
                        finalWhere, // The final WHERE clause
                        whereArgs // The incoming where clause values.
                        );
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#update(Uri,ContentValues,String,String[])}
     * Updates records in the database. The column names specified by the keys
     * in the values map are updated with new data specified by the values in
     * the map. If the incoming URI matches the note ID URI pattern, then the
     * method updates the one record specified by the ID in the URI; otherwise,
     * it updates a set of records. The record or records must match the input
     * selection criteria specified by where and whereArgs. If rows were
     * updated, then listeners are notified of the change.
     * 
     * @param uri
     *            The URI pattern to match and update.
     * @param values
     *            A map of column names (keys) and new values (values).
     * @param where
     *            An SQL "WHERE" clause that selects records based on their
     *            column values. If this is null, then all records that match
     *            the URI pattern are selected.
     * @param whereArgs
     *            An array of selection criteria. If the "where" param contains
     *            value placeholders ("?"), then each placeholder is replaced by
     *            the corresponding element in the array.
     * @return The number of rows updated.
     * @throws IllegalArgumentException
     *             if the incoming URI pattern is invalid.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where,
            String[] whereArgs) {

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        int count;
        String finalWhere;

        switch (URI_MATCHER.match(uri)) {
            case UriTypes.DOWNLOADS:

                count = db.update(
                        Download.Downloads.DOWNLOAD_TABLE_NAME,
                        values, // A map of column names and new values to use.
                        where, // The where clause column names.
                        whereArgs // The where clause column values to select
                                  // on.
                        );
                break;

            case UriTypes.DOWNLOAD_ID:
                String noteId = uri.getPathSegments().get(1);

                finalWhere =
                        Download.Downloads._ID + // The ID column name
                                " = " + // test for equality
                                noteId;

                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Does the update and returns the number of rows updated.
                count = db.update(
                        Download.Downloads.DOWNLOAD_TABLE_NAME, // The database
                                                                // table
                        // name.
                        values, // A map of column names and new values to use.
                        finalWhere, // The final WHERE clause to use
                                    // placeholders for whereArgs
                        whereArgs // The where clause column values to select
                                  // on, or
                                  // null if the values are in the where
                                  // argument.
                        );
                break;
            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    /**
     * A test package can call this to get a handle to the database underlying
     * NotePadProvider, so it can insert test data into the database. The test
     * case class is responsible for instantiating the provider in a test
     * context; {@link android.test.ProviderTestCase2} does this during the call
     * to setUp()
     * 
     * @return a handle to the database helper object for the provider's data.
     */
    DownloadDatabaseHelper getOpenHelperForTest() {
        return databaseHelper;
    }
}
