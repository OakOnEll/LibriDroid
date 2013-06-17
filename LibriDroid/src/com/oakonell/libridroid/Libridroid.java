package com.oakonell.libridroid;

import android.net.Uri;
import android.provider.BaseColumns;

public final class Libridroid {

    private Libridroid() {
        // prevent instantiation
    }

    private static final String SCHEME = "content://";
    public static final String AUTHORITY = "com.oakonell.libridroid.libridroid";

    public static final class Search implements BaseColumns {
        private Search() {
            // prevent instantiation
        }

        public static final String SEARCH_TABLE_NAME = "search";

        private static final String PATH_BOOKS = "/search";
        private static final String PATH_BOOK_ID = "/search/";

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_BOOKS);
        /**
         * The content URI base for a single book. Callers must append a numeric
         * note id to this Uri to retrieve a book
         */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_BOOK_ID);

        /**
         * The content URI match pattern for a single note, specified by its ID.
         * Use this to match incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_BOOK_ID + "/#");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oakonell.search";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oakonell.search";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = SEARCH_TABLE_NAME + ".title ASC";

        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_AUTHOR = "author";
        public static final String COLUMN_NAME_COLLECTION_TITLE = "collection_title";
        public static final String COLUMN_NAME_DESCRIPTION = "description";
        public static final String COLUMN_NAME_RSS_URL = "rss_url";
        public static final String COLUMN_NAME_LIBRIVOX_ID = "librivox_id";
        public static final String COLUMN_NAME_NUM_SECTIONS = "num_sections";
        public static final String COLUMN_NAME_WIKI_URL = "wiki_url";
        public static final String COLUMN_NAME_AUTHOR_WIKI_URL = "author_wiki_url";
        public static final String COLUMN_NAME_GENRE = "genre";
        public static final String COLUMN_NAME_CATEGORY = "category";
        public static final String COLUMN_NAME_LIBRIVOX_URL = "librivox_url";
        public static final String COLUMN_NAME_BOOK_ID = "book_id";
        public static final String COLUMN_NAME_LAST_USED = "last_used";

        public static final String COLUMN_NAME_IS_DOWNLOADED = "is_downloaded";

        // useful for generic searches- which will hit librivox
        public static final String QUERY_PARAM_NAME = "q";

    }

    public static final class Books implements BaseColumns {
        private Books() {
            // prevent instantiation
        }

        public static final String BOOK_TABLE_NAME = "books";

        private static final String PATH_BOOKS = "/books";
        private static final String PATH_BOOK_ID = "/books/";

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_BOOKS);
        /**
         * The content URI base for a single book. Callers must append a numeric
         * note id to this Uri to retrieve a book
         */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_BOOK_ID);

        /**
         * The content URI match pattern for a single note, specified by its ID.
         * Use this to match incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_BOOK_ID + "/#");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oakonell.book";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oakonell.book";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "title ASC";

        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_AUTHOR = "author";
        public static final String COLUMN_NAME_DESCRIPTION = "description";
        public static final String COLUMN_NAME_RSS_URL = "rss_url";
        public static final String COLUMN_NAME_LIBRIVOX_ID = "librivox_id";
        public static final String COLUMN_NAME_NUM_SECTIONS = "num_sections";
        public static final String COLUMN_NAME_WIKI_URL = "wiki_url";
        public static final String COLUMN_NAME_AUTHOR_WIKI_URL = "author_wiki_url";
        public static final String COLUMN_NAME_GENRE = "genre";
        public static final String COLUMN_NAME_CATEGORY = "category";
        public static final String COLUMN_NAME_LIBRIVOX_URL = "librivox_url";
        public static final String COLUMN_NAME_IS_DOWNLOADED = "is_downloaded";

        public static final String COLUMN_NAME_CURRENT_SECTION = "current_section_number";
        public static final String COLUMN_NAME_CURRENT_POSITION = "current_position";
        public static final String COLUMN_NAME_LAST_LISTENED_ON = "last_listened_on";
    }

    public static final class BookSections implements BaseColumns {
        private BookSections() {
            // prevent instantiation
        }

        public static final String BOOK_SECTION_TABLE_NAME = "book_sections";
        public static final String COLUMN_NAME_BOOK_ID = "book_id";
        public static final String COLUMN_NAME_SECTION_NUMBER = "section_number";
        public static final String COLUMN_NAME_URL = "url";
        public static final String COLUMN_NAME_SIZE = "size";
        public static final String COLUMN_NAME_DURATION = "duration";
        public static final String COLUMN_NAME_SECTION_TITLE = "title";
        public static final String COLUMN_NAME_SECTION_AUTHOR = "author";

        public static final String DEFAULT_SORT_ORDER = "book_id ASC, section_number ASC";

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oakonell.book.section";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oakonell.book.section";

        public static final String CONTENT_PATH_STRING = "sections/";

        public static Uri contentUri(String bookId) {
            return Uri.parse(SCHEME + AUTHORITY + Books.PATH_BOOK_ID + bookId
                    + "/"
                    + CONTENT_PATH_STRING);
        }

        public static Uri contentUri(String bookId, long sectionNumber) {
            return Uri.parse(SCHEME + AUTHORITY + Books.PATH_BOOK_ID + bookId
                    + "/"
                    + CONTENT_PATH_STRING + "/" + Long.toString(sectionNumber));
        }

    }
}
