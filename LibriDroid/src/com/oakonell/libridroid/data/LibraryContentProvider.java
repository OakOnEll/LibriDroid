package com.oakonell.libridroid.data;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

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

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.Libridroid.Books;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.query.BufferedAsyncQueryHelper;
import com.oakonell.utils.query.Communications;
import com.oakonell.utils.xml.XMLUtils;

public class LibraryContentProvider extends ContentProvider {

    private static final UriMatcher URI_MATCHER;
    private static final int BOOK_ID_PATH_SEGMENT_NUM = 1;
    private static final int SECTION_NUMBER_PATH_SEGMENT_NUM = 3;

    private static class UriTypes {
        private static final int SEARCH = 1;
        private static final int SEARCH_ID = 2;
        private static final int BOOKS = 3;
        private static final int BOOK_ID = 4;
        private static final int SECTIONS = 5;
        private static final int SECTION_ID = 6;
    };

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(Libridroid.AUTHORITY, "search", UriTypes.SEARCH);
        URI_MATCHER.addURI(Libridroid.AUTHORITY, "search/#", UriTypes.SEARCH_ID);

        URI_MATCHER.addURI(Libridroid.AUTHORITY, "books", UriTypes.BOOKS);
        URI_MATCHER.addURI(Libridroid.AUTHORITY, "books/#", UriTypes.BOOK_ID);

        URI_MATCHER.addURI(Libridroid.AUTHORITY, "books/#/sections", UriTypes.SECTIONS);
        URI_MATCHER.addURI(Libridroid.AUTHORITY, "books/#/sections/#", UriTypes.SECTION_ID);
    }

    private BookDatabaseHelper databaseHelper;
    private BufferedAsyncQueryHelper unitTestOverrideBooksQueryHelper;
    private BufferedAsyncQueryHelper unitTestOverrideSectionsQueryHelper;

    @Override
    public boolean onCreate() {
        init();
        return true;
    }

    private void init() {
        databaseHelper = new BookDatabaseHelper(getContext(),
                BookDatabaseHelper.DATABASE_NAME, null);
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
            case UriTypes.SEARCH: {
                /**
                 * Always try to update results with the latest data from the
                 * network.
                 * 
                 * Spawning an asynchronous load task thread, guarantees that
                 * the load has no chance to block any content provider method,
                 * and therefore no chance to block the UI thread.
                 * 
                 * While the request loads, we return the cursor with existing
                 * data to the client.
                 * 
                 * If the existing cursor is empty, the UI will render no
                 * content until it receives URI notification.
                 * 
                 * Content updates that arrive when the asynchronous network
                 * request completes will appear in the already returned cursor,
                 * since that cursor query will match that of newly arrived
                 * items.
                 */
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                // the query is passed out of band of other information passed
                // to this method -- its not an argument.
                String queryText = uri.
                        getQueryParameter(Libridroid.Search.QUERY_PARAM_NAME);
                String communicationId = uri.getQueryParameter("communication_id");
                if (!TextUtils.isEmpty(queryText)) {
                    if (unitTestOverrideBooksQueryHelper != null) {
                        unitTestOverrideBooksQueryHelper.asyncQueryRequest(
                                queryText, null);
                    } else {
                        LibrivoxAsyncQueryHelper helper = new LibrivoxAsyncQueryHelper(
                                this, communicationId);
                        helper.asyncQueryRequest(queryText);
                    }
                }

                return querySearchTable(uri, projection, selection, selectionArgs,
                        sortOrder, qb);
            }

            case UriTypes.SEARCH_ID: {
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.appendWhere(Libridroid.Search.SEARCH_TABLE_NAME + "." + Libridroid.Search._ID + "="
                        + uri.getPathSegments().get(1));

                return querySearchTable(uri, projection, selection, selectionArgs,
                        sortOrder, qb);
            }

            case UriTypes.BOOKS: {
                /**
                 * Always try to update results with the latest data from the
                 * network.
                 * 
                 * Spawning an asynchronous load task thread, guarantees that
                 * the load has no chance to block any content provider method,
                 * and therefore no chance to block the UI thread.
                 * 
                 * While the request loads, we return the cursor with existing
                 * data to the client.
                 * 
                 * If the existing cursor is empty, the UI will render no
                 * content until it receives URI notification.
                 * 
                 * Content updates that arrive when the asynchronous network
                 * request completes will appear in the already returned cursor,
                 * since that cursor query will match that of newly arrived
                 * items.
                 */
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(Libridroid.Books.BOOK_TABLE_NAME);

                return queryBooksTable(uri, projection, selection, selectionArgs,
                        sortOrder, qb);
            }

            case UriTypes.BOOK_ID: {
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(Libridroid.Books.BOOK_TABLE_NAME);
                qb.appendWhere(Libridroid.Books._ID + "="
                        + uri.getPathSegments().get(1));

                return queryBooksTable(uri, projection, selection, selectionArgs,
                        sortOrder, qb);
            }

            case UriTypes.SECTIONS: {
                String bookId = uri.getPathSegments().get(1);

                SQLiteDatabase db = databaseHelper.getReadableDatabase();

                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(Libridroid.BookSections.BOOK_SECTION_TABLE_NAME);
                qb.appendWhere(
                        Libridroid.BookSections.COLUMN_NAME_BOOK_ID + "=" + bookId);

                Cursor c = qb.query(db,
                        projection, selection, selectionArgs, null, null,
                        null);
                c.setNotificationUri(getContext().getContentResolver(), uri);

                readAndInsertSections(c, uri, bookId, db);

                return c;
            }

            case UriTypes.SECTION_ID: {
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables(Libridroid.BookSections.BOOK_SECTION_TABLE_NAME);

                String bookId = uri.getPathSegments().get(BOOK_ID_PATH_SEGMENT_NUM);
                String sectionNumber = uri.getPathSegments().get(SECTION_NUMBER_PATH_SEGMENT_NUM);

                qb.appendWhere(Libridroid.BookSections.COLUMN_NAME_BOOK_ID + "="
                        + bookId);
                qb.appendWhere(" and " + Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER
                        + "=" + sectionNumber);
                SQLiteDatabase db = databaseHelper.getReadableDatabase();

                Cursor c = qb.query(
                        db,
                        projection,
                        selection,
                        selectionArgs,
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null // The sort order
                        );
                c.setNotificationUri(getContext().getContentResolver(), uri);
                return c;
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

    }

    private void readAndInsertSections(Cursor c, Uri uri, String bookId,
            SQLiteDatabase db) {
        // get the sections if they haven't been retrieved already
        String communicationId = uri.getQueryParameter("communication_id");
        if (c.getCount() > 0) {
            if (communicationId != null) {
                Communications.delete(communicationId);
            }
            return;
        }

        Cursor query = db.query(Libridroid.Books.BOOK_TABLE_NAME,
                new String[] { Libridroid.Books._ID,
                        Libridroid.Books.COLUMN_NAME_RSS_URL,
                        Libridroid.Books.COLUMN_NAME_TITLE,
                        Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID },
                Libridroid.Books._ID + " = ? ",
                new String[] { bookId }, null, null, null);
        try {
            if (!query.moveToFirst()) {
                // no such book!?
                throw new RuntimeException("No book with Id " + bookId);
            }

            String rssUrl = query.getString(1);
            if (TextUtils.isEmpty(rssUrl)) {
                String librivoxId = query.getString(3);
                rssUrl = "http://librivox.org/rss/" + librivoxId;
                // String noRSSString =
                // getContext().getString(R.string.noRSSForBook, librivoxId);
                // if (communicationId != null) {
                // CommunicationEntry entry =
                // Communications.get(communicationId);
                // entry.placeInError(noRSSString);
                // return;
                // }
                //
                // // Toast toast = Toast.makeText(getContext(),
                // // getContext().getString(R.string.app_name) + ":"
                // // + noRSSString, 3000);
                // // toast.show();
                // LogHelper.warn("LibraryContentProvider",
                // "There is no RSS URL for "
                // + bookId
                // + " (title=" + query.getString(2) + ", librivox id = "
                // + librivoxId + ")");
                // return;
            }
            if (unitTestOverrideSectionsQueryHelper != null) {
                Map<String, String> inputs = new HashMap<String, String>();
                inputs.put("bookId", bookId);

                unitTestOverrideSectionsQueryHelper.asyncQueryRequest(rssUrl,
                        inputs);
            } else {
                LibrivoxSectionsAsyncQueryHelper helper = new LibrivoxSectionsAsyncQueryHelper(
                        this, bookId, communicationId);
                helper.asyncQueryRequest(rssUrl);
            }
        } finally {
            query.close();
        }
    }

    private Cursor querySearchTable(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder,
            SQLiteQueryBuilder qb) {
        String orderBy;
        // If no sort order is specified, uses the default
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Libridroid.Search.DEFAULT_SORT_ORDER;
        } else {
            // otherwise, uses the incoming sort order
            orderBy = sortOrder;
        }
        String[] theProjection = projection;
        if (theProjection == null) {
            Set<String> columnsSet = getSearchDefaultProjection();
            theProjection = columnsSet.toArray(new String[columnsSet.size()]);
        }

        // TODO Opens the database object in "read" mode, since no writes need
        // to be
        // done. But read-only can't perform upgrade !?
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        qb.setTables(Libridroid.Search.SEARCH_TABLE_NAME + " left join " + Libridroid.Books.BOOK_TABLE_NAME
                + " on " + Libridroid.Search.SEARCH_TABLE_NAME + "." + Libridroid.Search.COLUMN_NAME_LIBRIVOX_ID
                + " = " + Libridroid.Books.BOOK_TABLE_NAME + "." + Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID);

        Map<String, String> projectionMap = getSearchProjectionMap();
        qb.setProjectionMap(projectionMap);

        /*
         * Performs the query. If no problems occur trying to read the database,
         * then a Cursor object is returned; otherwise, the cursor variable
         * contains null. If no records were selected, then the Cursor object is
         * empty, and Cursor.getCount() returns 0.
         */
        Cursor c = qb.query(
                db, // The database to query
                theProjection, // The columns to return from the query
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

    private Set<String> getSearchDefaultProjection() {
        Set<String> projection = new HashSet<String>();
        projection.addAll(getSearchColumns());
        // Thought this could work simply as below
        // but had troubles, probably due to specialness of _id column, and the
        // projection map didn't help
        // projection.add(Libridroid.Search.COLUMN_NAME_BOOK_ID);
        projection.add(Libridroid.Search.COLUMN_NAME_IS_DOWNLOADED);
        projection.add(Libridroid.Books.BOOK_TABLE_NAME + "."
                + Libridroid.Books._ID + " as " + Libridroid.Search.COLUMN_NAME_BOOK_ID);
        return projection;
    }

    private Map<String, String> getSearchProjectionMap() {
        List<String> columns = getSearchColumns();
        Map<String, String> projectionMap = new HashMap<String, String>();
        for (String each : columns) {
            projectionMap.put(each, Libridroid.Search.SEARCH_TABLE_NAME + "." + each);
        }
        projectionMap.put(Libridroid.Search.COLUMN_NAME_BOOK_ID, Libridroid.Books.BOOK_TABLE_NAME + "."
                + Libridroid.Books._ID);
        projectionMap.put(Libridroid.Search.COLUMN_NAME_IS_DOWNLOADED, Libridroid.Books.BOOK_TABLE_NAME + "."
                + Libridroid.Books.COLUMN_NAME_IS_DOWNLOADED);
        return projectionMap;
    }

    private List<String> getSearchColumns() {
        String[] columns = new String[] { Libridroid.Search._ID, Libridroid.Search.COLUMN_NAME_AUTHOR,
                Libridroid.Search.COLUMN_NAME_AUTHOR_WIKI_URL, Libridroid.Search.COLUMN_NAME_CATEGORY,
                Libridroid.Search.COLUMN_NAME_COLLECTION_TITLE,
                Libridroid.Search.COLUMN_NAME_DESCRIPTION, Libridroid.Search.COLUMN_NAME_GENRE,
                Libridroid.Search.COLUMN_NAME_LIBRIVOX_ID, Libridroid.Search.COLUMN_NAME_NUM_SECTIONS,
                Libridroid.Search.COLUMN_NAME_RSS_URL, Libridroid.Search.COLUMN_NAME_TITLE,
                Libridroid.Search.COLUMN_NAME_WIKI_URL, Libridroid.Search.COLUMN_NAME_LAST_USED };
        return Arrays.asList(columns);
    }

    private Cursor queryBooksTable(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder,
            SQLiteQueryBuilder qb) {
        String orderBy;
        // If no sort order is specified, uses the default
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Libridroid.Books.DEFAULT_SORT_ORDER;
        } else {
            // otherwise, uses the incoming sort order
            orderBy = sortOrder;
        }

        // TODO Opens the database object in "read" mode, since no writes need
        // to be
        // done. But read-only can't perform upgrade !?
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

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

        // v1.1 missed populating the librivox url
        // here we can patch it up..
        if (c.getCount() == 1) {
            c.moveToFirst();
            int urlIndex = c.getColumnIndex(Books.COLUMN_NAME_LIBRIVOX_URL);
            if (urlIndex > 0 && TextUtils.isEmpty(c.getString(urlIndex))) {
                // TODO patch up librivox url
                // shortcut for now
                fixLibrivoxUrl(uri, c);
            }
        }

        // Tells the Cursor what URI to watch, so it knows when its source data
        // changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    private void fixLibrivoxUrl(Uri uri, Cursor c) {
        int rssUrlIndex = c.getColumnIndex(Books.COLUMN_NAME_RSS_URL);
        String rssUrl = c.getString(rssUrlIndex);
        LogHelper.info("LibraryContentProvider", "No Librivox URL, updating");

        InputSource in;
        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet();
            request.setURI(new URI(rssUrl));
            HttpResponse response = client.execute(request);

            in = new InputSource(response.getEntity().getContent());
        } catch (Exception e) {
            LogHelper.warn("LibraryCOntentProvider", "Not able to update the librivox url");
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document dom;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            dom = builder.parse(in);
        } catch (Exception e) {
            LogHelper.error("LibraryContentProvider", "UNable to update the librivox url", e);
            return;
        }
        Element root = dom.getDocumentElement();

        /*
         * <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
         * xmlns:media="http://search.yahoo.com/mrss/" xmlns:creativeCommons=
         * "http://blogs.law.harvard.edu/tech/creativeCommonsRssModule"
         * version="2.0"> <channel> <title><![CDATA[Time Machine, The Version 4
         * by Wells, H. G.]]></title>
         * <link><![CDATA[http://librivox.org/the-time
         * -machine-by-h-g-wells-2/]]></link>
         */

        // String debugXML = XMLUtils.xmlDocumentToString(dom);

        List<Element> channels = XMLUtils.getChildElementsByName(root, "channel");
        if (channels.size() != 1) {
            throw new RuntimeException("Unexpected channel elements in Rss URL " + rssUrl);
        }
        Element channel = channels.get(0);
        List<Element> links = XMLUtils.getChildElementsByName(channel, "link");
        if (links.size() != 1) {
            throw new RuntimeException("Unexpected link elements in Rss URL " + rssUrl);
        }
        Element link = links.get(0);
        String librivoxUrl = XMLUtils.getTextContent(link);

        ContentValues updatedValues = new ContentValues();
        updatedValues.put(Libridroid.Books.COLUMN_NAME_LIBRIVOX_URL, librivoxUrl);

        int idIndex = c.getColumnIndex(Books._ID);
        String id = c.getString(idIndex);

        update(uri, updatedValues, Books._ID + " = " + id, null);
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

            case UriTypes.BOOKS:
                return Libridroid.Books.CONTENT_TYPE;
            case UriTypes.BOOK_ID:
                return Libridroid.Books.CONTENT_ITEM_TYPE;

            case UriTypes.SECTIONS:
                return Libridroid.BookSections.CONTENT_TYPE;
            case UriTypes.SECTION_ID:
                return Libridroid.BookSections.CONTENT_ITEM_TYPE;

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
            case UriTypes.SEARCH:
                return insertSearch(uri, initialValues);
            case UriTypes.BOOKS:
                return insertBook(uri, initialValues);
            case UriTypes.SECTIONS:
                return insertSection(uri, initialValues);
            default:
                throw new IllegalArgumentException("Unsupported insert URI '"
                        + uri.toString());
        }
    }

    private Uri insertSearch(Uri uri, ContentValues initialValues) {
        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            // Otherwise, create a new value map
            values = new ContentValues();
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insertOrThrow(
                Libridroid.Search.SEARCH_TABLE_NAME, // The table to insert
                                                     // into.
                null,
                values
                );

        if (rowId > 0) {
            Uri bookUri = ContentUris.withAppendedId(
                    Libridroid.Search.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(bookUri, null);
            return bookUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    private Uri insertSection(Uri uri, ContentValues values) {
        String bookId = uri.getPathSegments().get(1);
        Integer sectionNumber = values
                .getAsInteger(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER);

        // Opens the database object in "write" mode.
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insertOrThrow(
                Libridroid.BookSections.BOOK_SECTION_TABLE_NAME,
                null,
                values
                );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            Uri noteUri = Libridroid.BookSections.contentUri(bookId,
                    sectionNumber);

            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an
        // exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    private Uri insertBook(Uri uri, ContentValues initialValues) {
        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            // Otherwise, create a new value map
            values = new ContentValues();
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insertOrThrow(
                Libridroid.Books.BOOK_TABLE_NAME, // The table to insert
                                                  // into.
                null,
                values
                );

        if (rowId > 0) {
            Uri bookUri = ContentUris.withAppendedId(
                    Libridroid.Books.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(bookUri, null);
            return bookUri;
        }

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
            case UriTypes.SEARCH:
                count = db.delete(
                        Libridroid.Search.SEARCH_TABLE_NAME, // The database
                                                             // table
                                                             // name
                        where, // The incoming where clause column names
                        whereArgs // The incoming where clause values
                        );
                break;
            case UriTypes.BOOKS:
                count = db.delete(
                        Libridroid.Books.BOOK_TABLE_NAME, // The database table
                                                          // name
                        where, // The incoming where clause column names
                        whereArgs // The incoming where clause values
                        );
                break;

            case UriTypes.BOOK_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the desired
                 * note ID.
                 */
                finalWhere =
                        Libridroid.Books._ID + // The ID column name
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
                        Libridroid.Books.BOOK_TABLE_NAME, // The database table
                                                          // name.
                        finalWhere, // The final WHERE clause
                        whereArgs // The incoming where clause values.
                        );
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        // TODO should add this?
        db.close();

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
            case UriTypes.SEARCH:
                count = db.update(Libridroid.Search.SEARCH_TABLE_NAME, values, where, whereArgs);
                break;
            case UriTypes.BOOKS:
                count = db.update(
                        Libridroid.Books.BOOK_TABLE_NAME,
                        values, // A map of column names and new values to use.
                        where, // The where clause column names.
                        whereArgs // The where clause column values to select
                                  // on.
                        );
                break;

            case UriTypes.BOOK_ID: {
                String bookId = uri.getPathSegments().get(1);

                finalWhere =
                        Libridroid.Books._ID + // The ID column name
                                " = " + // test for equality
                                bookId;

                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Does the update and returns the number of rows updated.
                count = db.update(
                        Libridroid.Books.BOOK_TABLE_NAME, // The database table
                                                          // name.
                        values, // A map of column names and new values to use.
                        finalWhere, // The final WHERE clause to use
                                    // placeholders for whereArgs
                        whereArgs // The where clause column values to select
                                  // on, or
                                  // null if the values are in the where
                                  // argument.
                        );
            }
                break;

            case UriTypes.SECTION_ID: {
                String bookId = uri.getPathSegments().get(1);
                String sectionNum = uri.getPathSegments().get(SECTION_NUMBER_PATH_SEGMENT_NUM);

                finalWhere =
                        Libridroid.BookSections.COLUMN_NAME_BOOK_ID +
                                " = "
                                + // test for equality
                                bookId + " and " + Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER + " = "
                                + sectionNum;

                if (where != null) {
                    finalWhere = "(" + finalWhere + ") AND (" + where + ")";
                }

                count = db.update(
                        Libridroid.BookSections.BOOK_SECTION_TABLE_NAME,
                        values, // A map of column names and new values to use.
                        finalWhere, // The where clause column names.
                        whereArgs // The where clause column values to select
                                  // on.
                        );
            }
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
    BookDatabaseHelper getOpenHelperForTest() {
        return databaseHelper;
    }

    // Useful for testing, without actually going to the librivox site
    BufferedAsyncQueryHelper setBooksAsyncQueryHelper(
            BufferedAsyncQueryHelper asyncQueryHelper) {
        BufferedAsyncQueryHelper oldAsyncQueryHelper = unitTestOverrideBooksQueryHelper;
        unitTestOverrideBooksQueryHelper = asyncQueryHelper;
        return oldAsyncQueryHelper;
    }

    // Useful for testing, without actually going to the librivox site
    BufferedAsyncQueryHelper setSectionsAsyncQueryHelper(
            BufferedAsyncQueryHelper asyncQueryHelper) {
        BufferedAsyncQueryHelper oldAsyncQueryHelper = unitTestOverrideSectionsQueryHelper;
        unitTestOverrideSectionsQueryHelper = asyncQueryHelper;
        return oldAsyncQueryHelper;
    }
}
