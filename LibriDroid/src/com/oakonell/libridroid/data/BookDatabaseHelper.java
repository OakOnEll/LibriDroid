package com.oakonell.libridroid.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.oakonell.libridroid.Libridroid;

public class BookDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "books.db";
    private static final int DATABASE_VERSION = 37;

    private static final String SEARCH_TABLE_NAME = Libridroid.Search.SEARCH_TABLE_NAME;

    private static final class SearchTable {
        private static final String TITLE = Libridroid.Search.COLUMN_NAME_TITLE;
        private static final String AUTHOR = Libridroid.Search.COLUMN_NAME_AUTHOR;
        private static final String COLLECTION_TITLE = Libridroid.Search.COLUMN_NAME_COLLECTION_TITLE;
        private static final String DESCRIPTION = Libridroid.Search.COLUMN_NAME_DESCRIPTION;
        private static final String RSS_URL = Libridroid.Search.COLUMN_NAME_RSS_URL;
        private static final String LIBRIVOX_ID = Libridroid.Search.COLUMN_NAME_LIBRIVOX_ID;
        private static final String NUM_SECTIONS = Libridroid.Search.COLUMN_NAME_NUM_SECTIONS;
        // added v2
        private static final String WIKI_URL = Libridroid.Search.COLUMN_NAME_WIKI_URL;
        private static final String AUTHOR_WIKI_URL = Libridroid.Search.COLUMN_NAME_AUTHOR_WIKI_URL;
        private static final String GENRE = Libridroid.Search.COLUMN_NAME_GENRE;
        private static final String CATEGORY = Libridroid.Search.COLUMN_NAME_CATEGORY;
        private static final String LIBRIVOX_URL = Libridroid.Search.COLUMN_NAME_LIBRIVOX_URL;
        private static final String LAST_USED = Libridroid.Search.COLUMN_NAME_LAST_USED;
    }

    private static final String BOOK_TABLE_NAME = Libridroid.Books.BOOK_TABLE_NAME;

    private static final class BookTable {
        private static final String TITLE = Libridroid.Books.COLUMN_NAME_TITLE;
        private static final String AUTHOR = Libridroid.Books.COLUMN_NAME_AUTHOR;
        private static final String DESCRIPTION = Libridroid.Books.COLUMN_NAME_DESCRIPTION;
        private static final String RSS_URL = Libridroid.Books.COLUMN_NAME_RSS_URL;
        private static final String LIBRIVOX_ID = Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID;
        private static final String NUM_SECTIONS = Libridroid.Books.COLUMN_NAME_NUM_SECTIONS;
        // added v2
        private static final String WIKI_URL = Libridroid.Books.COLUMN_NAME_WIKI_URL;
        private static final String AUTHOR_WIKI_URL = Libridroid.Books.COLUMN_NAME_AUTHOR_WIKI_URL;
        private static final String GENRE = Libridroid.Books.COLUMN_NAME_GENRE;
        private static final String CATEGORY = Libridroid.Books.COLUMN_NAME_CATEGORY;
        private static final String LIBRIVOX_URL = Libridroid.Books.COLUMN_NAME_LIBRIVOX_URL;

        private static final String CURRENT_SECTION = Libridroid.Books.COLUMN_NAME_CURRENT_SECTION;
        private static final String CURRENT_POSITION = Libridroid.Books.COLUMN_NAME_CURRENT_POSITION;
        private static final String LAST_LISTENED_ON = Libridroid.Books.COLUMN_NAME_LAST_LISTENED_ON;

        private static final String IS_DOWNLOADED = Libridroid.Books.COLUMN_NAME_IS_DOWNLOADED;
        private static final String TIMESTAMP = "timestamp";
    }

    private static final String BOOK_SECTION_TABLE_NAME = Libridroid.BookSections.BOOK_SECTION_TABLE_NAME;

    private static class BookSectionTable {
        private static final String OWNING_BOOK_ID = Libridroid.BookSections.COLUMN_NAME_BOOK_ID;
        private static final String SECTION_NUMBER = Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER;
        private static final String SECTION_TITLE = Libridroid.BookSections.COLUMN_NAME_SECTION_TITLE;
        private static final String SECTION_AUTHOR = Libridroid.BookSections.COLUMN_NAME_SECTION_AUTHOR;
        private static final String SECTION_URL = Libridroid.BookSections.COLUMN_NAME_URL;
        private static final String SIZE = Libridroid.BookSections.COLUMN_NAME_SIZE;
        private static final String DURATION = Libridroid.BookSections.COLUMN_NAME_DURATION;
    }

    public BookDatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    private void createTables(SQLiteDatabase sqLiteDatabase) {
        createSearchTable(sqLiteDatabase);

        String createTableString = "CREATE TABLE " + BOOK_TABLE_NAME +
                " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                BookTable.TITLE + " TEXT, " +
                BookTable.AUTHOR + " TEXT, " +
                BookTable.DESCRIPTION + " TEXT, " +
                BookTable.RSS_URL + " TEXT, " +

                BookTable.WIKI_URL + " TEXT, " +
                BookTable.AUTHOR_WIKI_URL + " TEXT, " +
                BookTable.GENRE + " TEXT, " +
                BookTable.CATEGORY + " TEXT, " +
                BookTable.LIBRIVOX_URL + " TEXT, " +

                BookTable.LIBRIVOX_ID + " TEXT UNIQUE, " +
                BookTable.NUM_SECTIONS + " INTEGER, " +

                BookTable.CURRENT_SECTION + " INTEGER, " +
                BookTable.CURRENT_POSITION + " INTEGER, " +
                BookTable.LAST_LISTENED_ON + " TEXT," +

                BookTable.IS_DOWNLOADED + " INTEGER," +
                BookTable.TIMESTAMP + " TEXT" +
                ");";
        sqLiteDatabase.execSQL(createTableString);

        createTableString = "CREATE TABLE " + BOOK_SECTION_TABLE_NAME +
                " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                BookSectionTable.OWNING_BOOK_ID + " INTEGER, " +
                BookSectionTable.SECTION_NUMBER + " INTEGER, " +
                BookSectionTable.SECTION_URL + " TEXT, " +
                BookSectionTable.SECTION_TITLE + " TEXT, " +
                BookSectionTable.SECTION_AUTHOR + " TEXT, " +
                BookSectionTable.SIZE + " TEXT, " +
                BookSectionTable.DURATION + " TEXT" +
                ");";
        sqLiteDatabase.execSQL(createTableString);
    }

    private void createSearchTable(SQLiteDatabase sqLiteDatabase) {
        String createTableString = "CREATE TABLE " + SEARCH_TABLE_NAME +
                " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                SearchTable.TITLE + " TEXT, " +
                SearchTable.COLLECTION_TITLE + " TEXT, " +
                SearchTable.AUTHOR + " TEXT, " +
                SearchTable.DESCRIPTION + " TEXT, " +
                SearchTable.RSS_URL + " TEXT, " +

                SearchTable.WIKI_URL + " TEXT, " +
                SearchTable.AUTHOR_WIKI_URL + " TEXT, " +
                SearchTable.GENRE + " TEXT, " +
                SearchTable.CATEGORY + " TEXT, " +
                SearchTable.LIBRIVOX_URL + " TEXT, " +

                SearchTable.LIBRIVOX_ID + " TEXT , " +
                SearchTable.NUM_SECTIONS + " INTEGER , " +

                SearchTable.LAST_USED + " INTEGER " +

                ");";
        sqLiteDatabase.execSQL(createTableString);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + SEARCH_TABLE_NAME + ";");
        switch (oldVersion) {
            case 35:
            case 36:
                createSearchTable(db);
                break;
            case 37:
                throw new RuntimeException("DB upgrade not yet catered to");
            default:
                db.execSQL("DROP TABLE IF EXISTS " + BOOK_TABLE_NAME + ";");
                db.execSQL("DROP TABLE IF EXISTS " + BOOK_SECTION_TABLE_NAME + ";");
                createTables(db);
        }
    }
}
