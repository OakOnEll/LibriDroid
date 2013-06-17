package com.oakonell.libridroid.impl;

import com.oakonell.libridroid.Libridroid;

public enum BookSort {
    /**
     * The constant names must match the string-array "books_order"
     */
    TITLE(Libridroid.Books.COLUMN_NAME_TITLE), AUTHOR(Libridroid.Books.COLUMN_NAME_AUTHOR), LAST_LISTENED(
            Libridroid.Books.COLUMN_NAME_LAST_LISTENED_ON + " DESC");
    private final String sortByClause;

    private BookSort(String sortByClause) {
        this.sortByClause = sortByClause;
    }

    public String getSortBy() {
        return sortByClause;
    }
}
