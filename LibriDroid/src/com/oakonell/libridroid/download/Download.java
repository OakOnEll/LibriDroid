package com.oakonell.libridroid.download;

import android.net.Uri;
import android.provider.BaseColumns;

public final class Download {
    private static final String SCHEME = "content://";
    public static final String AUTHORITY = "com.oakonell.libridroid.libridroid.download";

    private Download() {
        // prevent instantiation
    }

    public static final class Downloads implements BaseColumns {
        private Downloads() {
            // prevent instantiation
        }

        public static final String DOWNLOAD_TABLE_NAME = "downloads";

        private static final String PATH_DOWNLOADS = "/downloads";
        private static final String PATH_DOWNLOAD_ID = "/downloads/";

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY
                + PATH_DOWNLOADS);
        /**
         * The content URI base for a single download. Callers must append a
         * numeric id to this Uri to retrieve a download
         */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME
                + AUTHORITY + PATH_DOWNLOAD_ID);

        /**
         * The content URI match pattern for a single download, specified by its
         * ID. Use this to match incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME
                + AUTHORITY + PATH_DOWNLOAD_ID + "/#");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oakonell.bookdownload";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oakonell.bookdownload";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "sequence ASC, " + _ID + " ASC";

        public static final String COLUMN_NAME_SEQUENCE = "sequence";
        public static final String COLUMN_NAME_BOOK_ID = "book_id";
        public static final String COLUMN_NAME_SECTION_NUM = "section_number";
        public static final String COLUMN_NAME_DOWNLOADED_BYTES = "downloaded_bytes";
        public static final String COLUMN_NAME_TOTAL_BYTES = "total_bytes";
        public static final String COLUMN_NAME_URL = "url";

    }
}
