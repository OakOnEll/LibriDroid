package com.oakonell.libridroid.data;

import java.util.Map;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;
import android.util.Log;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.utils.query.BufferedAsyncQueryHelper;

public class LibraryProviderTest extends
        ProviderTestCase2<LibraryContentProvider> {
    // Contains a reference to the mocked content resolver for the provider
    // under test.
    private MockContentResolver mMockResolver;

    // Contains an SQLite database, used as test data
    private SQLiteDatabase mDb;

    // Contains some test data, as an array of BookInfo instances.
    private final BookInfo[] TEST_BOOKS = {
            new BookInfo(
                    "War of the Worlds",
                    "Wells, H. G.",
                    "http://librivox.org/bookfeeds/the-war-of-the-worlds-by-h-g-wells-group.xml",
                    436),
            new BookInfo(
                    "Emma",
                    "Austen, Jane",
                    "http://librivox.org/bookfeeds/emma-by-jane-austen-solo.xml",
                    86),
            new BookInfo(
                    "Time Machine",
                    "Well, H. G.",
                    "http://librivox.org/bookfeeds/the-time-machine-by-hg-wells.xml",
                    817),
            new BookInfo(
                    "Doll's House",
                    "Ibsen, Henrik",
                    "http://librivox.org/bookfeeds/a-dolls-house-by-henrik-ibsen.xml",
                    1984), };

    public LibraryProviderTest() {
        super(LibraryContentProvider.class, Libridroid.AUTHORITY);
    }

    /*
     * Sets up the test environment before each test method. Creates a mock
     * content resolver, gets the provider under test, and creates a new
     * database for the provider.
     */
    @Override
    protected void setUp() throws Exception {
        // Calls the base class implementation of this method.
        super.setUp();

        // Gets the resolver for this test.
        mMockResolver = getMockContentResolver();

        /*
         * Gets a handle to the database underlying the provider. Gets the
         * provider instance created in super.setUp(), gets the
         * DatabaseOpenHelper for the provider, and gets a database object from
         * the helper.
         */
        mDb = getProvider().getOpenHelperForTest().getWritableDatabase();
    }

    /*
     * This method is called after each test method, to clean up the current
     * fixture. Since this sample test case runs in an isolated context, no
     * cleanup is necessary.
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /*
     * Sets up test data. The test data is in an SQL database. It is created in
     * setUp() without any data, and populated in insertData if necessary.
     */
    private void insertData() {
        // Sets up test data
        for (BookInfo element : TEST_BOOKS) {

            // Adds a record to the database.
            mDb.insertOrThrow(
                    Libridroid.Books.BOOK_TABLE_NAME, // the table name for the
                                                      // insert
                    null, // column set to null if empty values map
                    element.getContentValues() // the values map to
                                               // insert
            );
        }
    }

    /*
     * Tests the provider's publicly available URIs. If the URI is not one that
     * the provider understands, the provider should throw an exception. It also
     * tests the provider's getType() method for each URI, which should return
     * the MIME type associated with the URI.
     */
    public void testUriAndGetType() {
        // Tests the MIME type for the books table URI.
        String mimeType = mMockResolver.getType(Libridroid.Books.CONTENT_URI);
        assertEquals(Libridroid.Books.CONTENT_TYPE, mimeType);

        // Creates a URI with a pattern for book ids. The id doesn't have to
        // exist.
        Uri bookIdUri = ContentUris.withAppendedId(
                Libridroid.Books.CONTENT_ID_URI_BASE, 1);
        // Gets the book ID URI MIME type.
        mimeType = mMockResolver.getType(bookIdUri);
        assertEquals(Libridroid.Books.CONTENT_ITEM_TYPE, mimeType);

        Uri sectionsUri = Uri.withAppendedPath(bookIdUri, "/sections");
        mimeType = mMockResolver.getType(sectionsUri);
        assertEquals(Libridroid.BookSections.CONTENT_TYPE, mimeType);

        mimeType = mMockResolver.getType(Uri
                .withAppendedPath(sectionsUri, "/1"));
        assertEquals(Libridroid.BookSections.CONTENT_ITEM_TYPE, mimeType);

        Uri invalidURI =
                Uri.withAppendedPath(Libridroid.Books.CONTENT_URI, "invalid");
        // Tests an invalid URI. This should throw an IllegalArgumentException.
        mimeType = mMockResolver.getType(invalidURI);
    }

    public void testBookInsert() {
        ContentValues values = new ContentValues();
        values.put(Libridroid.Books.COLUMN_NAME_TITLE, "Title");
        values.put(Libridroid.Books.COLUMN_NAME_AUTHOR, "Author");
        values.put(Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID, "123");

        Uri insert = mMockResolver.insert(Libridroid.Books.CONTENT_URI, values);
        assertEquals("1", insert.getPathSegments().get(1));
        Cursor query = mMockResolver.query(insert, null, null, null, null);
        assertEquals(1, query.getCount());
        assertTrue(query.moveToFirst());
        assertEquals("Title", query.getString(query
                .getColumnIndex(Libridroid.Books.COLUMN_NAME_TITLE)));
        assertEquals("Author", query.getString(query
                .getColumnIndex(Libridroid.Books.COLUMN_NAME_AUTHOR)));
        assertEquals("123", query.getString(query
                .getColumnIndex(Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID)));
    }

    public void testBookUpdate() {
        ContentValues values = new ContentValues();
        values.put(Libridroid.Books.COLUMN_NAME_TITLE, "Title");
        values.put(Libridroid.Books.COLUMN_NAME_AUTHOR, "Author");
        values.put(Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID, "123");

        Uri insert = mMockResolver.insert(Libridroid.Books.CONTENT_URI, values);
        values.put(Libridroid.Books.COLUMN_NAME_TITLE, "Title2");
        int updateCount = mMockResolver.update(insert, values, null, null);
        assertEquals(1, updateCount);

        Cursor query = mMockResolver.query(insert, null, null, null, null);
        assertEquals(1, query.getCount());
        assertTrue(query.moveToFirst());
        assertEquals("Title2", query.getString(query
                .getColumnIndex(Libridroid.Books.COLUMN_NAME_TITLE)));
        assertEquals("Author", query.getString(query
                .getColumnIndex(Libridroid.Books.COLUMN_NAME_AUTHOR)));
        assertEquals("123", query.getString(query
                .getColumnIndex(Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID)));
    }

    public void testBookDeleteById() {
        insertData();

        // assert the record exists
        assertTrue(findBookById(TEST_BOOKS.length));

        // do the delete
        mMockResolver.delete(ContentUris.withAppendedId(
                Libridroid.Books.CONTENT_ID_URI_BASE, 1), null, null);

        // assert the record doesn't exist
        assertFalse(findBookById(TEST_BOOKS.length - 1));
    }

    private boolean findBookById(int expectedSize) {
        Cursor cursor = mMockResolver.query(
                Libridroid.Books.CONTENT_URI, // the URI for the main data table
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );

        assertEquals(expectedSize, cursor.getCount());
        boolean found = false;
        int idIndex = cursor.getColumnIndex(Libridroid.Books._ID);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idIndex);
            if (id == 1) {
                found = true;
            }
        }
        cursor.close();
        return found;
    }

    public void testBookDeleteByWhere() {
        insertData();
        // assert the record exists
        assertTrue(findBookByTitleWhereClause(TEST_BOOKS.length));

        // delete the record
        mMockResolver.delete(Libridroid.Books.CONTENT_URI,
                Libridroid.Books.COLUMN_NAME_TITLE + " = ?",
                new String[] { "Emma" });

        // assert it no longer exists
        assertFalse(findBookByTitleWhereClause(TEST_BOOKS.length - 1));
    }

    private boolean findBookByTitleWhereClause(int expectedSize) {
        Cursor cursor = mMockResolver.query(
                Libridroid.Books.CONTENT_URI, // the URI for the main data table
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );

        assertEquals(expectedSize, cursor.getCount());

        boolean found = false;
        int idIndex = cursor.getColumnIndex(Libridroid.Books.COLUMN_NAME_TITLE);
        while (cursor.moveToNext()) {
            String title = cursor.getString(idIndex);
            if (title.equals("Emma")) {
                found = true;
            }
        }
        return found;
    }

    public void testEmptyBookQuery() {
        // If there are no records in the table, the returned cursor from a
        // query should be empty.
        Cursor cursor = mMockResolver.query(
                Libridroid.Books.CONTENT_URI, // the URI for the main data table
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );

        // Asserts that the returned cursor contains no records
        assertEquals(0, cursor.getCount());
    }

    public void testFullBookQuery() {
        // If the table contains records, the returned cursor from a query
        // should contain records.

        // Inserts the test data into the provider's underlying data source
        insertData();

        // Gets all the columns for all the rows in the table
        Cursor cursor = mMockResolver.query(
                Libridroid.Books.CONTENT_URI, // the URI for the main data table
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );

        // Asserts that the returned cursor contains the same number of rows as
        // the size of the
        // test data array.
        assertEquals(TEST_BOOKS.length, cursor.getCount());
    }

    public void testBookProjection() {
        // Defines a projection of column names to return for a query
        final String[] TEST_PROJECTION = {
                Libridroid.Books.COLUMN_NAME_TITLE,
                Libridroid.Books.COLUMN_NAME_AUTHOR,
        };

        // Query subtest 3.
        // A query that uses a projection should return a cursor with the same
        // number of columns
        // as the projection, with the same names, in the same order.
        Cursor projectionCursor = mMockResolver.query(
                Libridroid.Books.CONTENT_URI, // the URI for the main data table
                TEST_PROJECTION, // get the title, note, and mod date columns
                null, // no selection columns, get all the records
                null, // no selection criteria
                null // use default the sort order
                );

        // Asserts that the number of columns in the cursor is the same as in
        // the projection
        assertEquals(TEST_PROJECTION.length, projectionCursor.getColumnCount());

        // Asserts that the names of the columns in the cursor and in the
        // projection are the same.
        // This also verifies that the names are in the same order.
        assertEquals(TEST_PROJECTION[0], projectionCursor.getColumnName(0));
        assertEquals(TEST_PROJECTION[1], projectionCursor.getColumnName(1));

    }

    // public void testBookQuerySelection() {
    // // TODO disabled test for now
    // if (true)
    // return;
    // // Defines a projection of column names to return for a query
    // final String[] TEST_PROJECTION = {
    // Libridroid.Books.COLUMN_NAME_TITLE,
    // Libridroid.Books.COLUMN_NAME_AUTHOR,
    // };
    //
    // // Defines a query sort order
    // final String SORT_ORDER = Libridroid.Books.COLUMN_NAME_TITLE + " ASC";
    //
    // // Query subtest 4
    // // Defines the selection columns for a query.
    // // Defines a selection column for the query. When the selection columns
    // // are passed
    // // to the query, the selection arguments replace the placeholders.
    // final String TITLE_SELECTION = Libridroid.Books.COLUMN_NAME_TITLE
    // + " = "
    // + "?";
    // final String SELECTION_COLUMNS =
    // TITLE_SELECTION + " OR " + TITLE_SELECTION;
    //
    // // Defines the arguments for the selection columns.
    // final String[] SELECTION_ARGS = { "Emma", "Time Machine" };
    //
    // // A query that uses selection criteria should return only those rows
    // // that match the
    // // criteria. Use a projection so that it's easy to get the data in a
    // // particular column.
    // Cursor projectionCursor = mMockResolver.query(
    // Libridroid.Books.CONTENT_URI, // the URI for the main data table
    // TEST_PROJECTION, // get the title, note, and mod date columns
    // SELECTION_COLUMNS, // select on the title column
    // SELECTION_ARGS, // select titles "Note0", "Note1", or "Note5"
    // SORT_ORDER // sort ascending on the title column
    // );
    //
    // // Asserts that the cursor has the same number of rows as the number of
    // // selection arguments
    // assertEquals(SELECTION_ARGS.length, projectionCursor.getCount());
    //
    // int index = 0;
    // while (projectionCursor.moveToNext()) {
    //
    // // Asserts that the selection argument at the current index matches
    // // the value of
    // // the title column (column 0) in the current record of the cursor
    // assertEquals(SELECTION_ARGS[index], projectionCursor.getString(0));
    //
    // index++;
    // }
    //
    // // Asserts that the index pointer is now the same as the number of
    // // selection arguments, so
    // // that the number of arguments tested is exactly the same as the number
    // // of rows returned.
    // assertEquals(SELECTION_ARGS.length, index);
    //
    // }

    /*
     * Tests queries against the provider, using the book id URI. This URI
     * encodes a single record ID. The provider should only return 0 or 1
     * record.
     */
    public void testQueriesOnBookIdUri() {
        // Defines the selection column for a query. The "?" is replaced by
        // entries in the
        // selection argument array
        final String SELECTION_COLUMNS = Libridroid.Books.COLUMN_NAME_TITLE
                + " = " + "?";

        // Defines the argument for the selection column.
        final String[] SELECTION_ARGS = { "Emma" };

        // A sort order for the query.
        final String SORT_ORDER = Libridroid.Books.COLUMN_NAME_TITLE + " ASC";

        // Creates a projection includes the book id column, so that book id can
        // be retrieved.
        final String[] BOOK_ID_PROJECTION = {
                Libridroid.Books._ID, // The Books class extends BaseColumns,
                                      // which includes _ID as the column name
                                      // for the
                                      // record's id in the data model
                Libridroid.Books.COLUMN_NAME_TITLE }; // The note's title

        // Query subtest 1.
        // Tests that a query against an empty table returns null.

        // Constructs a URI that matches the provider's notes id URI pattern,
        // using an arbitrary
        // value of 1 as the note ID.
        Uri noteIdUri = ContentUris.withAppendedId(
                Libridroid.Books.CONTENT_ID_URI_BASE, 1);

        // Queries the table with the notes ID URI. This should return an empty
        // cursor.
        Cursor cursor = mMockResolver.query(
                noteIdUri, // URI pointing to a single record
                null, // no projection, get all the columns for each record
                null, // no selection criteria, get all the records in the table
                null, // no need for selection arguments
                null // default sort, by ascending title
                );

        // Asserts that the cursor is null.
        assertEquals(0, cursor.getCount());

        // Query subtest 2.
        // Tests that a query against a table containing records returns a
        // single record whose ID
        // is the one requested in the URI provided.

        // Inserts the test data into the provider's underlying data source.
        insertData();

        // Queries the table using the URI for the full table.
        cursor = mMockResolver.query(
                Libridroid.Books.CONTENT_URI, // the base URI for the table
                BOOK_ID_PROJECTION, // returns the ID and title columns of rows
                SELECTION_COLUMNS, // select based on the title column
                SELECTION_ARGS, // select title of "Note1"
                SORT_ORDER // sort order returned is by title, ascending
                );

        // Asserts that the cursor contains only one row.
        assertEquals(1, cursor.getCount());

        // Moves to the cursor's first row, and asserts that this did not fail.
        assertTrue(cursor.moveToFirst());

        // Saves the record's note ID.
        int inputNoteId = cursor.getInt(0);

        // Builds a URI based on the provider's content ID URI base and the
        // saved note ID.
        noteIdUri = ContentUris.withAppendedId(
                Libridroid.Books.CONTENT_ID_URI_BASE, inputNoteId);

        // Queries the table using the content ID URI, which returns a single
        // record with the
        // specified note ID, matching the selection criteria provided.
        cursor = mMockResolver.query(noteIdUri, // the URI for a single note
                BOOK_ID_PROJECTION, // same projection, get ID and title columns
                SELECTION_COLUMNS, // same selection, based on title column
                SELECTION_ARGS, // same selection arguments, title = "Note1"
                SORT_ORDER // same sort order returned, by title, ascending
                );

        // Asserts that the cursor contains only one row.
        assertEquals(1, cursor.getCount());

        // Moves to the cursor's first row, and asserts that this did not fail.
        assertTrue(cursor.moveToFirst());

        // Asserts that the note ID passed to the provider is the same as the
        // note ID returned.
        assertEquals(inputNoteId, cursor.getInt(0));
    }

    private static final class BookInfo {
        String title;
        String author;
        int librivoxId;
        String rssurl;

        BookInfo(String title, String author, String rssurl,
                int librivoxId) {
            this.title = title;
            this.author = author;
            this.rssurl = rssurl;
            this.librivoxId = librivoxId;
        }

        /*
         * Returns a ContentValues instance (a map) for this BookInfo instance.
         * This is useful for inserting a BookInfo into a database.
         */
        ContentValues getContentValues() {
            // Gets a new ContentValues object
            ContentValues v = new ContentValues();

            // Adds map entries for the user-controlled fields in the map
            v.put(Libridroid.Books.COLUMN_NAME_TITLE, title);
            v.put(Libridroid.Books.COLUMN_NAME_AUTHOR, author);
            v.put(Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID, librivoxId);
            v.put(Libridroid.Books.COLUMN_NAME_RSS_URL, rssurl);
            return v;
        }
    }

    public void testSimpleMockLibraryRead() {
        BufferedAsyncQueryHelper helper = new BufferedAsyncQueryHelper() {
            @Override
            public void asyncQueryRequest(String queryText,
                    Map<String, String> inputs) {
                insertData();
            }
        };

        String queryString =
                Libridroid.Search.QUERY_PARAM_NAME + "=" +
                        "Emma";
        Uri queryUri =
                Uri.parse(Libridroid.Search.CONTENT_URI + "?" +
                        queryString);

        BufferedAsyncQueryHelper oldHelper = getProvider()
                .setBooksAsyncQueryHelper(
                        helper);

        try {
            Cursor cursor = mMockResolver.query(
                    queryUri, // the URI for the main data
                              // table
                    null, // no projection, get all columns
                    null, // no selection criteria, get all records
                    null, // no selection arguments
                    null // use default sort order
                    );

            // Asserts that the returned cursor contains the same number of rows
            // as
            // the size of the
            // test data array.
            assertEquals(TEST_BOOKS.length, cursor.getCount());
        } finally {
            getProvider().setBooksAsyncQueryHelper(oldHelper);
        }
    }

    public void testActualSearchLibrivoxRead() throws InterruptedException {
        String queryString =
                Libridroid.Search.QUERY_PARAM_NAME + "=" +
                        "Emma";
        Uri queryUri =
                Uri.parse(Libridroid.Search.CONTENT_URI + "?" +
                        queryString);

        Cursor cursor = mMockResolver.query(
                queryUri, // the URI for the main data table
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );
        assertNotNull(cursor);
        // hmm.. this will be in another thread... how to tell when done.
        Thread.sleep(4000);
        // requery is deprecated... but cursor does not have new data unless
        // this is called...
        cursor.requery();
        // Asserts that the returned cursor contains the same number of
        // rows as
        // the size of the
        // test data array.
        Log.d("LibraryProviderTest", "About to get cursor count");

        assertTrue(1 < cursor.getCount());
    }

    public void testSectionInsert() {
        long bookId = mDb.insertOrThrow(
                Libridroid.Books.BOOK_TABLE_NAME, // the table name for the
                                                  // insert
                null, // column set to null if empty values map
                TEST_BOOKS[0].getContentValues() // the values map to
                                                 // insert
                );
        mDb.insertOrThrow(
                Libridroid.Books.BOOK_TABLE_NAME, // the table name for the
                                                  // insert
                null, // column set to null if empty values map
                TEST_BOOKS[1].getContentValues() // the values map to
                                                 // insert
        );

        ContentValues values = new ContentValues();
        values.put(Libridroid.BookSections.COLUMN_NAME_BOOK_ID, bookId);
        values.put(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER, 1);
        values.put(Libridroid.BookSections.COLUMN_NAME_SIZE, 1024);
        values.put(Libridroid.BookSections.COLUMN_NAME_URL,
                "http://foo.bar/baz.mp3");
        values.put(Libridroid.BookSections.COLUMN_NAME_DURATION, "10");

        mMockResolver.insert(
                Libridroid.BookSections.contentUri(Long.toString(bookId)),
                values);

        Cursor cursor = mMockResolver.query(
                Libridroid.BookSections.contentUri(Long.toString(bookId)),
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );
        assertEquals(1, cursor.getCount());

    }

    public void testSectionQuery() {
        long bookId = mDb.insertOrThrow(
                Libridroid.Books.BOOK_TABLE_NAME, // the table name for the
                                                  // insert
                null, // column set to null if empty values map
                TEST_BOOKS[0].getContentValues() // the values map to
                                                 // insert
                );
        long book2Id = mDb.insertOrThrow(
                Libridroid.Books.BOOK_TABLE_NAME, // the table name for the
                                                  // insert
                null, // column set to null if empty values map
                TEST_BOOKS[1].getContentValues() // the values map to
                                                 // insert
                );
        ContentValues section = new ContentValues();
        section.put(Libridroid.BookSections.COLUMN_NAME_BOOK_ID, bookId);
        section.put(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER, 1);
        section.put(Libridroid.BookSections.COLUMN_NAME_SIZE, 10000);
        section.put(Libridroid.BookSections.COLUMN_NAME_URL,
                "http://foo.bar/test.mp3");
        mDb.insertOrThrow(
                Libridroid.BookSections.BOOK_SECTION_TABLE_NAME,
                null, section);

        section = new ContentValues();
        section.put(Libridroid.BookSections.COLUMN_NAME_BOOK_ID, book2Id);
        section.put(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER, 1);
        section.put(Libridroid.BookSections.COLUMN_NAME_SIZE, 10000);
        section.put(Libridroid.BookSections.COLUMN_NAME_URL,
                "http://foo.bar/test.mp3");
        mDb.insertOrThrow(
                Libridroid.BookSections.BOOK_SECTION_TABLE_NAME,
                null, section);

        Cursor cursor = mMockResolver.query(
                Libridroid.BookSections.contentUri(Long.toString(bookId)),
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );
        assertEquals(1, cursor.getCount());

    }

    public void testActualSectionLibrivoxRead() throws InterruptedException {
        // populate sample books
        insertData();

        Uri queryUri = Libridroid.BookSections.contentUri("1");
        Cursor cursor = mMockResolver.query(
                queryUri, // the URI for the main data table
                null, // no projection, get all columns
                null, // no selection criteria, get all records
                null, // no selection arguments
                null // use default sort order
                );

        assertNotNull(cursor);
        // hmm.. this will be in another thread... how to tell when done.
        Thread.sleep(4000);
        // requery is deprecated... but cursor does not have new data unless
        // this is called...
        cursor.requery();
        // Asserts that the returned cursor contains the same number of
        // rows as
        // the size of the
        // test data array.
        Log.d("LibraryProviderTest", "About to get cursor count");
        assertTrue(1 < cursor.getCount());
    }

    public void testSectionReadOnlyOnce() throws InterruptedException {
        // populate sample books
        insertData();
        final int[] count = new int[] { 0 };

        BufferedAsyncQueryHelper oldHelper = getProvider()
                .setSectionsAsyncQueryHelper(
                        new BufferedAsyncQueryHelper() {
                            @Override
                            public void asyncQueryRequest(String queryText,
                                    Map<String, String> extraInputs) {
                                // insert section data
                                ContentValues values = new ContentValues();
                                String bookId = extraInputs.get("bookId");
                                values.put(
                                        Libridroid.BookSections.COLUMN_NAME_BOOK_ID,
                                        bookId);
                                values.put(
                                        Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER,
                                        1);
                                values.put(
                                        Libridroid.BookSections.COLUMN_NAME_SIZE,
                                        1024);
                                values.put(
                                        Libridroid.BookSections.COLUMN_NAME_URL,
                                        "http://foo.bar/baz.mp3");
                                values.put(
                                        Libridroid.BookSections.COLUMN_NAME_DURATION,
                                        "10");

                                mMockResolver.insert(
                                        Libridroid.BookSections
                                                .contentUri(bookId),
                                        values);
                                // increment count
                                count[0]++;
                            }
                        });
        try {
            Uri queryUri = Libridroid.BookSections.contentUri("1");
            Cursor cursor = mMockResolver.query(
                    queryUri, // the URI for the main data table
                    null, // no projection, get all columns
                    null, // no selection criteria, get all records
                    null, // no selection arguments
                    null // use default sort order
                    );
            assertEquals(1, count[0]);
            // requery, due to ordering of cursor and lazy loading...
            cursor.requery();
            assertEquals(1, cursor.getCount());

            cursor = mMockResolver.query(
                    queryUri, // the URI for the main data table
                    null, // no projection, get all columns
                    null, // no selection criteria, get all records
                    null, // no selection arguments
                    null // use default sort order
                    );
            assertEquals(1, count[0]);
            cursor.requery();
            assertEquals(1, cursor.getCount());
        } finally {
            getProvider().setSectionsAsyncQueryHelper(oldHelper);
        }
    }
}
