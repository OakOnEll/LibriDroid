package com.oakonell.libridroid.data;

import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.test.AndroidTestCase;

public class QueryBuilderOuterJoinTest extends AndroidTestCase {

    public void testJoin() {
        DBHelper helper = new DBHelper(getContext(), "testDB", null, 1);
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("A", "a");
        values.put("B", "b");
        db.insert("foo", null, values);

        values = new ContentValues();
        values.put("C", "a");
        values.put("D", "d");
        db.insert("bar", null, values);

        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables("foo left  join bar on foo.a = bar.c");
        HashMap<String, String> projectionMap = new HashMap<String, String>();
        projectionMap.put("A", "foo.A");
        projectionMap.put("B", "foo.B");
        projectionMap.put("D", "bar.D");
        builder.setProjectionMap(projectionMap);

        Cursor query = builder.query(db, new String[] { "A", "B", "D" }, null, null, null, null, null);
        int dColIndex = query.getColumnIndex("D");
        assertTrue(dColIndex > 0);
        assertTrue(query.moveToFirst());
        String dVal = query.getString(dColIndex);
        assertEquals("d", dVal);

    }

    private static class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context, String name, CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase sqlitedatabase) {
            String createTableString = "CREATE TABLE foo " +
                    " (" +
                    "_ID  INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "A TEXT, " +
                    "B TEXT);";
            sqlitedatabase.execSQL(createTableString);

            createTableString = "CREATE TABLE bar " +
                    " (" +
                    "_ID  INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "C TEXT, " +
                    "D TEXT);";
            sqlitedatabase.execSQL(createTableString);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int i, int j) {
            db.execSQL("DROP TABLE IF EXISTS foo;");
            db.execSQL("DROP TABLE IF EXISTS bar;");
            onCreate(db);
        }

    }

}
