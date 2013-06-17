package com.oakonell.libridroid.download;

import com.oakonell.libridroid.download.Download;
import com.oakonell.libridroid.download.DownloadContentProvider;
import com.oakonell.libridroid.download.Download.Downloads;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

public class DownloadProviderTest extends
		ProviderTestCase2<DownloadContentProvider> {
	// Contains a reference to the mocked content resolver for the provider
	// under test.
	private MockContentResolver mMockResolver;

	// Contains an SQLite database, used as test data
	private SQLiteDatabase mDb;

	// Contains some test data, as an array of BookInfo instances.
	private final DownloadInfo[] TEST_DOWNLOADS = {
			new DownloadInfo(1, 1, 1, "", 1024, 0)
			,
	};

	public DownloadProviderTest() {
		super(DownloadContentProvider.class, Download.AUTHORITY);
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
		for (int index = 0; index < TEST_DOWNLOADS.length; index++) {

			// Adds a record to the database.
			mDb.insertOrThrow(
					Download.Downloads.DOWNLOAD_TABLE_NAME, // the table name
															// for the
					// insert
					null, // column set to null if empty values map
					TEST_DOWNLOADS[index].getContentValues() // the values map
																// to
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
		String mimeType = mMockResolver.getType(Download.Downloads.CONTENT_URI);
		assertEquals(Download.Downloads.CONTENT_TYPE, mimeType);

		// Creates a URI with a pattern for book ids. The id doesn't have to
		// exist.
		Uri bookIdUri = ContentUris.withAppendedId(
				Download.Downloads.CONTENT_ID_URI_BASE, 1);
		// Gets the book ID URI MIME type.
		mimeType = mMockResolver.getType(bookIdUri);
		assertEquals(Download.Downloads.CONTENT_ITEM_TYPE, mimeType);

		Uri invalidURI =
				Uri.withAppendedPath(Download.Downloads.CONTENT_URI, "invalid");
		// Tests an invalid URI. This should throw an IllegalArgumentException.
		mimeType = mMockResolver.getType(invalidURI);
	}

	public void testDownloadInsert() {
		ContentValues values = new ContentValues();
		values.put(Download.Downloads.COLUMN_NAME_BOOK_ID, 1);
		values.put(Download.Downloads.COLUMN_NAME_SECTION_NUM, 2);
		values.put(Download.Downloads.COLUMN_NAME_SEQUENCE, 10);
		values.put(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES, 0);
		values.put(Download.Downloads.COLUMN_NAME_TOTAL_BYTES, 2048);
		values.put(Download.Downloads.COLUMN_NAME_URL, "http://foo.bar/baz.mp3");

		Uri insert = mMockResolver.insert(Download.Downloads.CONTENT_URI,
				values);
		assertEquals("1", insert.getPathSegments().get(1));
		Cursor query = mMockResolver.query(insert, null, null, null, null);
		assertEquals(1, query.getCount());
		assertTrue(query.moveToFirst());
		assertEquals(
				2,
				query.getInt(query
						.getColumnIndex(Download.Downloads.COLUMN_NAME_SECTION_NUM)));
		assertEquals(
				1,
				query.getInt(query
						.getColumnIndex(Download.Downloads.COLUMN_NAME_BOOK_ID)));
		assertEquals(
				0,
				query.getLong(query
						.getColumnIndex(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES)));
		assertEquals(2048, query.getLong(query
				.getColumnIndex(Download.Downloads.COLUMN_NAME_TOTAL_BYTES)));
		assertEquals(10, query.getLong(query
				.getColumnIndex(Download.Downloads.COLUMN_NAME_SEQUENCE)));
		assertEquals("http://foo.bar/baz.mp3", query.getString(query
				.getColumnIndex(Download.Downloads.COLUMN_NAME_URL)));
	}

	public void testDownloadUpdate() {
		ContentValues values = new ContentValues();
		values.put(Download.Downloads.COLUMN_NAME_BOOK_ID, 1);
		values.put(Download.Downloads.COLUMN_NAME_SECTION_NUM, 2);

		values.put(Download.Downloads.COLUMN_NAME_SEQUENCE, 10);
		values.put(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES, 0);
		values.put(Download.Downloads.COLUMN_NAME_TOTAL_BYTES, 2048);
		values.put(Download.Downloads.COLUMN_NAME_URL, "http://foo.bar/baz.mp3");

		Uri insert = mMockResolver.insert(Download.Downloads.CONTENT_URI,
				values);
		values.put(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES, 100);
		int updateCount = mMockResolver.update(insert, values, null, null);
		assertEquals(1, updateCount);

		Cursor query = mMockResolver.query(insert, null, null, null, null);
		assertEquals(1, query.getCount());
		assertTrue(query.moveToFirst());
		assertEquals(
				2,
				query.getInt(query
						.getColumnIndex(Download.Downloads.COLUMN_NAME_SECTION_NUM)));
		assertEquals(
				1,
				query.getInt(query
						.getColumnIndex(Download.Downloads.COLUMN_NAME_BOOK_ID)));
		assertEquals(
				100,
				query.getLong(query
						.getColumnIndex(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES)));
		assertEquals(2048, query.getLong(query
				.getColumnIndex(Download.Downloads.COLUMN_NAME_TOTAL_BYTES)));
		assertEquals(10, query.getLong(query
				.getColumnIndex(Download.Downloads.COLUMN_NAME_SEQUENCE)));
		assertEquals("http://foo.bar/baz.mp3", query.getString(query
				.getColumnIndex(Download.Downloads.COLUMN_NAME_URL)));
	}

	public void testDownloadDeleteById() {
		insertData();

		// assert the record exists
		assertTrue(findDownloadById(TEST_DOWNLOADS.length));

		// do the delete
		mMockResolver.delete(ContentUris.withAppendedId(
				Download.Downloads.CONTENT_ID_URI_BASE, 1), null, null);

		// assert the record doesn't exist
		assertFalse(findDownloadById(TEST_DOWNLOADS.length - 1));
	}

	private boolean findDownloadById(int expectedSize) {
		Cursor cursor = mMockResolver.query(
				Download.Downloads.CONTENT_URI, // the URI for the main data
												// table
				null, // no projection, get all columns
				null, // no selection criteria, get all records
				null, // no selection arguments
				null // use default sort order
				);

		assertEquals(expectedSize, cursor.getCount());
		boolean found = false;
		int idIndex = cursor.getColumnIndex(Download.Downloads._ID);
		while (cursor.moveToNext()) {
			long id = cursor.getLong(idIndex);
			if (id == 1) {
				found = true;
			}
		}
		cursor.close();
		return found;
	}

	public void testDownloadDeleteByWhere() {
		insertData();
		// assert the record exists
		assertTrue(findDownloadBySectionWhereClause(TEST_DOWNLOADS.length));

		// delete the record
		mMockResolver.delete(Download.Downloads.CONTENT_URI,
				Download.Downloads.COLUMN_NAME_BOOK_ID + " = ?",
				new String[] { "1" });

		// assert it no longer exists
		assertFalse(findDownloadBySectionWhereClause(TEST_DOWNLOADS.length - 1));
	}

	private boolean findDownloadBySectionWhereClause(int expectedSize) {
		Cursor cursor = mMockResolver.query(
				Download.Downloads.CONTENT_URI, // the URI for the main data
												// table
				null, // no projection, get all columns
				null, // no selection criteria, get all records
				null, // no selection arguments
				null // use default sort order
				);

		assertEquals(expectedSize, cursor.getCount());

		boolean found = false;
		int idIndex = cursor
				.getColumnIndex(Download.Downloads.COLUMN_NAME_BOOK_ID);
		while (cursor.moveToNext()) {
			String bookSectionId = cursor.getString(idIndex);
			if (bookSectionId.equals("1")) {
				found = true;
			}
		}
		return found;
	}

	public void testEmptyDownloadQuery() {
		// If there are no records in the table, the returned cursor from a
		// query should be empty.
		Cursor cursor = mMockResolver.query(
				Download.Downloads.CONTENT_URI, // the URI for the main data
												// table
				null, // no projection, get all columns
				null, // no selection criteria, get all records
				null, // no selection arguments
				null // use default sort order
				);

		// Asserts that the returned cursor contains no records
		assertEquals(0, cursor.getCount());
	}

	public void testFullDownloadQuery() {
		// If the table contains records, the returned cursor from a query
		// should contain records.

		// Inserts the test data into the provider's underlying data source
		insertData();

		// Gets all the columns for all the rows in the table
		Cursor cursor = mMockResolver.query(
				Download.Downloads.CONTENT_URI, // the URI for the main data
												// table
				null, // no projection, get all columns
				null, // no selection criteria, get all records
				null, // no selection arguments
				null // use default sort order
				);

		// Asserts that the returned cursor contains the same number of rows as
		// the size of the
		// test data array.
		assertEquals(TEST_DOWNLOADS.length, cursor.getCount());
	}

	public void testDownloadProjection() {
		// Defines a projection of column names to return for a query
		final String[] TEST_PROJECTION = {
				Download.Downloads.COLUMN_NAME_BOOK_ID,
				Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES,
		};

		// Query subtest 3.
		// A query that uses a projection should return a cursor with the same
		// number of columns
		// as the projection, with the same names, in the same order.
		Cursor projectionCursor = mMockResolver.query(
				Download.Downloads.CONTENT_URI, // the URI for the main data
												// table
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

	// public void testDownloadQuerySelection() {
	// // TODO disabled test for now
	// if (true)
	// return;
	// // Defines a projection of column names to return for a query
	// final String[] TEST_PROJECTION = {
	// Download.Downloads.COLUMN_NAME_TITLE,
	// Download.Downloads.COLUMN_NAME_AUTHOR,
	// };
	//
	// // Defines a query sort order
	// final String SORT_ORDER = Download.Downloads.COLUMN_NAME_TITLE + " ASC";
	//
	// // Query subtest 4
	// // Defines the selection columns for a query.
	// // Defines a selection column for the query. When the selection columns
	// // are passed
	// // to the query, the selection arguments replace the placeholders.
	// final String TITLE_SELECTION = Download.Downloads.COLUMN_NAME_TITLE
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
	// Download.Downloads.CONTENT_URI, // the URI for the main data
	// // table
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
	public void testQueriesOnDownloadIdUri() {
		// Defines the selection column for a query. The "?" is replaced by
		// entries in the
		// selection argument array
		final String SELECTION_COLUMNS = Download.Downloads.COLUMN_NAME_BOOK_ID
				+ " = " + "?";

		// Defines the argument for the selection column.
		final String[] SELECTION_ARGS = { "1" };

		// A sort order for the query.
		final String SORT_ORDER = Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES
				+ " ASC";

		// Creates a projection includes the book id column, so that book id can
		// be retrieved.
		final String[] BOOK_ID_PROJECTION = {
				Download.Downloads._ID, // The Books class extends BaseColumns,
										// which includes _ID as the column name
										// for the
										// record's id in the data model
				Download.Downloads.COLUMN_NAME_BOOK_ID }; // The note's
															// title

		// Query subtest 1.
		// Tests that a query against an empty table returns null.

		// Constructs a URI that matches the provider's notes id URI pattern,
		// using an arbitrary
		// value of 1 as the note ID.
		Uri noteIdUri = ContentUris.withAppendedId(
				Download.Downloads.CONTENT_ID_URI_BASE, 1);

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
				Download.Downloads.CONTENT_URI, // the base URI for the table
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
				Download.Downloads.CONTENT_ID_URI_BASE, inputNoteId);

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

	private static final class DownloadInfo {
		long bookId;
		long sectionNumber;
		long sequence;
		String url;
		long total_bytes;
		long downloaded_bytes;

		public DownloadInfo(long bookId, long sectionNumber, long sequence,
				String url,
				long total_bytes, long downloaded_bytes) {
			super();
			this.bookId = bookId;
			this.sectionNumber = sectionNumber;
			this.sequence = sequence;
			this.url = url;
			this.total_bytes = total_bytes;
			this.downloaded_bytes = downloaded_bytes;
		}

		/*
		 * Returns a ContentValues instance (a map) for this BookInfo instance.
		 * This is useful for inserting a BookInfo into a database.
		 */
		ContentValues getContentValues() {
			// Gets a new ContentValues object
			ContentValues v = new ContentValues();

			// Adds map entries for the user-controlled fields in the map
			v.put(Downloads.COLUMN_NAME_BOOK_ID, bookId);
			v.put(Downloads.COLUMN_NAME_SECTION_NUM, sectionNumber);
			v.put(Downloads.COLUMN_NAME_DOWNLOADED_BYTES, downloaded_bytes);
			v.put(Downloads.COLUMN_NAME_TOTAL_BYTES, total_bytes);
			v.put(Downloads.COLUMN_NAME_SEQUENCE, sequence);
			v.put(Downloads.COLUMN_NAME_URL, url);
			return v;
		}
	}
}
