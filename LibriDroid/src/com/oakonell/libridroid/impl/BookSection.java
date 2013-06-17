package com.oakonell.libridroid.impl;

import java.io.File;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.utils.Duration;

public class BookSection {
    private Book book;
    private ContentValues values = new ContentValues();

    public static BookSection fromCursor(Cursor c) {
        ContentValues values = Book.extractContentValues(c);
        return new BookSection(null, values);
    }

    BookSection(Book book, ContentValues values) {
        this.values = new ContentValues(values);
    }

    public String getBookId() {
        return values.getAsString(Libridroid.BookSections.COLUMN_NAME_BOOK_ID);
    }

    public Book getBook(ContentResolver resolver) {
        if (book == null) {
            book = Book.read(resolver, getBookId());
        }
        return book;
    }

    public long getSectionNumber() {
        return values.getAsLong(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER);
    }

    public String getTitle() {
        return values.getAsString(Libridroid.BookSections.COLUMN_NAME_SECTION_TITLE);
    }

    public String getUrl() {
        return values.getAsString(Libridroid.BookSections.COLUMN_NAME_URL);
    }

    public String getAuthor() {
        return values.getAsString(Libridroid.BookSections.COLUMN_NAME_SECTION_AUTHOR);
    }

    public Duration getDuration() {
        return Duration.from(values.getAsString(Libridroid.BookSections.COLUMN_NAME_DURATION));
    }

    public long getSize() {
        return values.getAsLong(Libridroid.BookSections.COLUMN_NAME_SIZE);
    }

    public BookSection getNextSection(ContentResolver resolver) {
        return Book.readSection(resolver, getBookId(), getSectionNumber() + 1);
    }

    public File getFile(ContentResolver resolver) {
        Book theBook = getBook(resolver);
        File file = FileHelper.getFile(resolver, Long.parseLong(getBookId()), getSectionNumber(), false,
                theBook.getTitle(),
                theBook.getLibrivoxId());
        return file;
    }

    private Duration durationAtStart;

    public Duration getBookDurationAtStart(ContentResolver resolver) {
        if (durationAtStart == null) {
            Duration duration = new Duration(0, 0, 0);
            Book myBook = getBook(resolver);
            for (BookSection each : myBook.getSections(resolver)) {
                if (each.getSectionNumber() < getSectionNumber()) {
                    duration = duration.add(each.getDuration());
                }
            }
            durationAtStart = duration;
        }
        return durationAtStart;
    }

    public Uri getBookUri() {
        return Uri.withAppendedPath(Libridroid.Books.CONTENT_ID_URI_BASE, getBookId());
    }
}
