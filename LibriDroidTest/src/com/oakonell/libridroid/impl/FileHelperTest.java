package com.oakonell.libridroid.impl;

import java.io.File;

import com.oakonell.libridroid.impl.FileHelper;

import android.test.AndroidTestCase;

public class FileHelperTest extends AndroidTestCase {
    public void testFileNameEscape() {
        assertEquals("foo", FileHelper.escapeToSafeFilename("foo"));
        assertEquals("foo_", FileHelper.escapeToSafeFilename("foo["));
        assertEquals("_foo_bar_", FileHelper.escapeToSafeFilename("[foo]bar!"));
        assertEquals("_foo_bar___", FileHelper.escapeToSafeFilename("[foo]bar!`/"));
    }

    public void testFile() {
        String title = "The Time Machine";
        String librivoxId = "101";

        // ContentValues bookValues = new ContentValues();
        // bookValues.put(Libridroid.Books.COLUMN_NAME_AUTHOR, "Wells, H.G.");
        // bookValues.put(Libridroid.Books.COLUMN_NAME_TITLE, title);
        // bookValues.put(Libridroid.Books.COLUMN_NAME_LIBRIVOX_ID, librivoxId);
        // bookValues.put(Libridroid.Books.COLUMN_NAME_NUM_SECTIONS, "2");
        //
        // Uri insert = resolver.insert(Books.CONTENT_URI, bookValues);
        // String bookId = insert.getPathSegments().get(1);
        //
        // ContentValues sectionValues = new ContentValues();
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_BOOK_ID,
        // bookId);
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER,
        // 1);
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_DURATION,
        // "10:00");
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_SIZE, "10000");
        // resolver.insert(BookSections.contentUri(bookId), sectionValues);
        // sectionValues = new ContentValues();
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_BOOK_ID,
        // bookId);
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER,
        // 2);
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_DURATION,
        // "11:00");
        // sectionValues.put(Libridroid.BookSections.COLUMN_NAME_SIZE, "11000");

        File file = FileHelper.getFile(librivoxId, title, "foo.mp3", 1, false);
        assertTrue(file.getAbsolutePath() + " not in expected pattern",
                file.getAbsolutePath().endsWith("/101_The Time Machine/1_foo.mp3"));

        file = FileHelper.getFile(librivoxId, title, "baz.mp3", 1, false);
        assertTrue(file.getAbsolutePath() + " not in expected pattern",
                file.getAbsolutePath().endsWith("/101_The Time Machine/1_baz.mp3"));
    }
}
