package com.oakonell.libridroid.download;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

public class DownloadDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "downloads.db";
    private static final int DATABASE_VERSION = 7;

    private static final String DOWNLOAD_QUEUE_TABLE_NAME = Download.Downloads.DOWNLOAD_TABLE_NAME;

    public static final class DownloadQueueTable {
        private static final String BOOK_ID = Download.Downloads.COLUMN_NAME_BOOK_ID;
        private static final String SECTION_NUM = Download.Downloads.COLUMN_NAME_SECTION_NUM;
        private static final String URL = Download.Downloads.COLUMN_NAME_URL;
        private static final String TOTAL_BYTES = Download.Downloads.COLUMN_NAME_TOTAL_BYTES;
        private static final String DOWNLOADED_BYTES = Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES;
        private static final String SEQUENCE = Download.Downloads.COLUMN_NAME_SEQUENCE;
    }

    public DownloadDatabaseHelper(Context context, String name,
            SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    private void createTables(SQLiteDatabase sqLiteDatabase) {
        String createTableString = "CREATE TABLE " + DOWNLOAD_QUEUE_TABLE_NAME +
                " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                DownloadQueueTable.URL + " TEXT, " +
                DownloadQueueTable.BOOK_ID + " INTEGER, " +
                DownloadQueueTable.SECTION_NUM + " INTEGER, " +
                DownloadQueueTable.DOWNLOADED_BYTES + " INTEGER, " +
                DownloadQueueTable.TOTAL_BYTES + " INTEGER, " +
                DownloadQueueTable.SEQUENCE + " INTEGER" +
                ");";
        sqLiteDatabase.execSQL(createTableString);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " +
                DOWNLOAD_QUEUE_TABLE_NAME + ";");

        createTables(db);

        /*
         * switch (oldVersion) { case 1: case 2: upgrade1(db); // fall through
         * in sequential order }
         */
    }

    // private void upgrade1(SQLiteDatabase db) {
    // String[] columns = new String[] { WIKI_URL, AUTHOR_WIKI_URL, GENRE,
    // CATEGORY, LIBRIVOX_URL };
    // for (String each : columns) {
    // String createvideoTable = "ALTER TABLE " + BOOK_TABLE_NAME +
    // " add " +
    // each + " TEXT" +
    // ";";
    // db.execSQL(createvideoTable);
    // }
    // }

}
