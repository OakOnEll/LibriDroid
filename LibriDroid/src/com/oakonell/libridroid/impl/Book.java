package com.oakonell.libridroid.impl;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.Libridroid.BookSections;
import com.oakonell.libridroid.Libridroid.Books;
import com.oakonell.utils.Duration;
import com.oakonell.utils.LogHelper;

public final class Book {
    private ContentValues values;
    private List<BookSection> sections;

    @Deprecated
    private Book(ContentValues values) {
        this.values = new ContentValues(values);
    }

    private Book(Cursor bookCursor) {
        this(extractContentValues(bookCursor));
    }

    public Uri getUri() {
        return Uri.withAppendedPath(Books.CONTENT_ID_URI_BASE, Long.toString(getId()));
    }

    public long getId() {
        return values.getAsLong(Books._ID);
    }

    public String getTitle() {
        return values.getAsString(Books.COLUMN_NAME_TITLE);
    }

    public String getAuthor() {
        return values.getAsString(Books.COLUMN_NAME_AUTHOR);
    }

    public Date getLastListened() {
        String dateString = values.getAsString(Books.COLUMN_NAME_LAST_LISTENED_ON);
        if (TextUtils.isEmpty(dateString)) {
            return null;
        }
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            LogHelper.error("Book", "Invalid date string " + dateString + ", returning null date");
            return null;
        }
    }

    public String getLibrivoxId() {
        return values.getAsString(Books.COLUMN_NAME_LIBRIVOX_ID);
    }

    public boolean isInLibrary() {
        Integer asInt = values.getAsInteger(Books.COLUMN_NAME_IS_DOWNLOADED);
        return asInt != null && asInt > 0;
    }

    public int getNumberSections() {
        return values.getAsInteger(Books.COLUMN_NAME_NUM_SECTIONS);
    }

    public String getDecription() {
        return values.getAsString(Books.COLUMN_NAME_DESCRIPTION);
    }

    public String getCategory() {
        return values.getAsString(Books.COLUMN_NAME_CATEGORY);
    }

    public String getGenre() {
        return values.getAsString(Books.COLUMN_NAME_GENRE);
    }

    public String getLibrivoxUrl() {
        return values.getAsString(Books.COLUMN_NAME_LIBRIVOX_URL);
    }

    public String getAuthorUrl() {
        return values.getAsString(Books.COLUMN_NAME_AUTHOR_WIKI_URL);
    }

    public String getWikiUrl() {
        return values.getAsString(Books.COLUMN_NAME_WIKI_URL);
    }

    public int getCurrentSectionNumber() {
        Integer val = values.getAsInteger(Books.COLUMN_NAME_CURRENT_SECTION);
        if (val == null) {
            return 1;
        }
        return val;
    }

    public long getCurrentPosition() {
        Long val = values.getAsLong(Books.COLUMN_NAME_CURRENT_POSITION);
        if (val == null) {
            return 0;
        }
        return val;
    }

    public BookSection getCurrentSection(ContentResolver resolver) {
        return getSection(resolver, getCurrentSectionNumber());
    }

    private Duration duration;

    public Duration getDuration(ContentResolver resolver) {
        if (duration == null) {
            Duration result = new Duration(0, 0, 0);
            for (BookSection each : getSections(resolver)) {
                result = result.add(each.getDuration());
            }
            duration = result;
        }
        return duration;
    }

    public BookSection getSection(ContentResolver resolver, int section) {
        if (section > getNumberSections()) {
            throw new RuntimeException("Section number larger than number of sections");
        }
        return readSection(resolver, Long.toString(getId()), section);
    }

    public void updatePosition(ContentResolver resolver, int sectionNumber, long position) {
        if (sectionNumber > getNumberSections()) {
            throw new RuntimeException("Section number larger than number of sections");
        }

        ContentValues newValues = new ContentValues();
        newValues.put(Books.COLUMN_NAME_CURRENT_POSITION, position);
        newValues.put(Books.COLUMN_NAME_CURRENT_SECTION, sectionNumber);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        newValues.put(Books.COLUMN_NAME_LAST_LISTENED_ON, dateFormat.format(new Date()));
        values.putAll(newValues);
        resolver.update(Uri.withAppendedPath(Books.CONTENT_ID_URI_BASE, Long.toString(getId())), newValues, null, null);
    }

    public static Book read(ContentResolver contentResolver, long bookId) {
        return read(contentResolver, Long.toString(bookId));
    }

    public static Book read(ContentResolver resolver, String bookId) {
        Uri bookUri = Uri.withAppendedPath(Books.CONTENT_ID_URI_BASE,
                bookId);
        return read(resolver, bookUri);
    }

    public static Book read(ContentResolver resolver, Uri bookUri) {
        Cursor bookCursor = resolver.query(
                bookUri, null, null, null, null);

        try {
            if (!bookCursor.moveToFirst()) {
                throw new RuntimeException("No book with uri " + bookUri);
            }

            Book book = new Book(bookCursor);

            if (bookCursor.moveToNext()) {
                throw new RuntimeException("A duplicate book with uri " + bookUri);
            }
            return book;
        } finally {
            bookCursor.close();
        }
    }

    public static Book fromCursor(Cursor c) {
        return new Book(c);
    }

    static ContentValues extractContentValues(Cursor bookCursor) {
        ContentValues values = new ContentValues();
        for (int i = 0; i < bookCursor.getColumnCount(); i++) {
            String columnName = bookCursor.getColumnName(i);
            String value = bookCursor.getString(i);
            values.put(columnName, value);
        }
        return values;
    }

    public static BookSection readSection(ContentResolver resolver, String bookId, long sectionNumber) {
        Cursor bookSectionCursor = resolver.query(BookSections.contentUri(bookId, sectionNumber), null, null, null,
                null);

        try {
            if (!bookSectionCursor.moveToFirst()) {
                return null;
            }

            ContentValues values = extractContentValues(bookSectionCursor);
            BookSection bookSection = new BookSection(null, values);

            if (bookSectionCursor.moveToNext()) {
                throw new RuntimeException("A duplicate book with id " + bookId + " section " + sectionNumber);
            }
            return bookSection;
        } finally {
            bookSectionCursor.close();
        }

    }

    public List<BookSection> getSections(ContentResolver resolver) {
        if (sections == null) {
            Uri sectionsUri = Libridroid.BookSections.contentUri(Long.toString(getId()));

            Cursor sectionsCursor = resolver.query(sectionsUri, null, null, null, null);
            sections = refreshSections(sectionsCursor);
        }
        return sections;
    }

    private List<BookSection> refreshSections(Cursor sectionsCursor) {
        try {
            boolean any = sectionsCursor.moveToFirst();
            if (!any) {
                return Collections.emptyList();
            }

            List<BookSection> result = new ArrayList<BookSection>();
            do {
                ContentValues sectionValues = extractContentValues(sectionsCursor);
                BookSection section = new BookSection(this, sectionValues);
                result.add(section);
            } while (sectionsCursor.moveToNext());
            return result;
        } finally {
            sectionsCursor.close();
        }
    }

    public void refresh(ContentResolver resolver) {
        Cursor bookCursor = resolver.query(getUri(), null, null, null, null);
        try {
            if (!bookCursor.moveToFirst()) {
                throw new RuntimeException("No book with uri " + getUri());
            }

            values = extractContentValues(bookCursor);
            if (bookCursor.moveToNext()) {
                throw new RuntimeException("A duplicate book with uri " + getUri());
            }
        } finally {
            bookCursor.close();
        }
    }

    // public static Cursor query(ContentResolver resolver, ) {

}
