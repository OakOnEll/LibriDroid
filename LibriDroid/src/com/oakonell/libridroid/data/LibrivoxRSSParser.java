package com.oakonell.libridroid.data;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;


import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.utils.LogHelper;

import android.content.ContentValues;
import android.text.TextUtils;

public class LibrivoxRSSParser {
    public interface BookParsedCallback {
        void finishedBook(ContentValues values);
    }

    public static final String TITLE = Libridroid.Search.COLUMN_NAME_TITLE;
    public static final String COLLECTION_TITLE = Libridroid.Search.COLUMN_NAME_COLLECTION_TITLE;
    public static final String AUTHOR = Libridroid.Search.COLUMN_NAME_AUTHOR;

    public static final String DESCRIPTION = Libridroid.Search.COLUMN_NAME_DESCRIPTION;
    public static final String RSS_URL = Libridroid.Search.COLUMN_NAME_RSS_URL;
    public static final String LIBRIVOX_ID = Libridroid.Search.COLUMN_NAME_LIBRIVOX_ID;
    public static final String NUM_SECTIONS = Libridroid.Search.COLUMN_NAME_NUM_SECTIONS;

    private static final String BOOK_NODE_NAME = "book";
    private static final String TITLE_AND_AUTHOR_NODE_NAME = "title";

    private static final String LIBRIVOX_ID_NODE_NAME = "id";
    private static final String NUM_SECTIONS_NODE_NAME = "NumberOfSections";
    private static final String RSS_URL_NODE_NAME = "rssurl";
    private static final String DESCRIPTION_NODE_NAME = "description";

    public static final String WIKI_URL_NODE_NAME = "WikiBookURL";
    public static final String AUTHOR_WIKI_URL_NODE_NAME = "AuthorURL";
    public static final String GENRE_NODE_NAME = "Genre";
    public static final String CATEGORY_NODE_NAME = "Category";
    public static final String LIBRIVOX_URL_NODE_NAME = "url";

    private Map<String, String> columnNameToDBName = new HashMap<String, String>();

    private BookParsedCallback callback;
    private InputStreamReader inputReader;

    public LibrivoxRSSParser(InputStreamReader inputReader,
            BookParsedCallback callback) {
        this.inputReader = inputReader;
        this.callback = callback;

        columnNameToDBName.put(LIBRIVOX_ID_NODE_NAME, LIBRIVOX_ID);
        columnNameToDBName.put(NUM_SECTIONS_NODE_NAME, NUM_SECTIONS);
        columnNameToDBName.put(RSS_URL_NODE_NAME, RSS_URL);
        columnNameToDBName.put(DESCRIPTION_NODE_NAME, DESCRIPTION);

        columnNameToDBName.put(WIKI_URL_NODE_NAME, Libridroid.Search.COLUMN_NAME_WIKI_URL);
        columnNameToDBName.put(AUTHOR_WIKI_URL_NODE_NAME, Libridroid.Search.COLUMN_NAME_AUTHOR_WIKI_URL);
        columnNameToDBName.put(GENRE_NODE_NAME, Libridroid.Search.COLUMN_NAME_GENRE);
        columnNameToDBName.put(CATEGORY_NODE_NAME, Libridroid.Search.COLUMN_NAME_CATEGORY);
        columnNameToDBName.put(LIBRIVOX_URL_NODE_NAME, Libridroid.Search.COLUMN_NAME_LIBRIVOX_URL);
    }

    public int parse() {
        int inserted = 0;

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(inputReader);

            int eventType = xpp.getEventType();
            String startName = null;
            ContentValues bookValues = null;

            // iterative pull parsing is a useful way to extract data from
            // streams, since we don't have to hold the DOM model in memory
            // during the parsing step.

            while (eventType != XmlPullParser.END_DOCUMENT) {
                // if (eventType == XmlPullParser.START_DOCUMENT) {
                // } else if (eventType == XmlPullParser.END_DOCUMENT) {
                // } else
                if (eventType == XmlPullParser.START_TAG) {
                    startName = xpp.getName();
                    if ((startName != null)) {
                        if ((BOOK_NODE_NAME).equals(startName)) {
                            bookValues = new ContentValues();
                            // mediaEntry.put(FinchVideo.Videos.QUERY_TEXT_NAME,
                            // mQueryText);
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String endName = xpp.getName();
                    if (endName != null) {
                        if (BOOK_NODE_NAME.equals(endName)) {
                            inserted++;
                            callback.finishedBook(bookValues);
                            bookValues = null;
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    // newline can turn into an extra text event
                    String text = xpp.getText();
                    if (text != null) {
                        text = text.trim();
                        if ((startName != null) && (!"".equals(text))) {
                            if (TITLE_AND_AUTHOR_NODE_NAME.equals(startName)) {
                                // pull title and author out of librivox's title
                                // element
                                // Format looks like
                                // Author (last, first). "Title" ... extra...
                                int firstQuoteIndex = text.indexOf('"');
                                if (firstQuoteIndex < 0) {
                                    throw new RuntimeException(
                                            "Couldn't extract author and title from xml feed");
                                }
                                int nextQuoteIndex = text.indexOf('"',
                                        firstQuoteIndex + 1);
                                if (nextQuoteIndex < 0) {
                                    throw new RuntimeException(
                                            "Couldn't extract author and title from xml feed");
                                }
                                String author = text.substring(0,
                                        firstQuoteIndex)
                                        .trim();
                                if (author.trim().length() > 0) {
                                    int lastIndexOf = author.lastIndexOf('.');
                                    if (lastIndexOf > 0) {
                                        author = author.substring(0,
                                                lastIndexOf);
                                    }
                                }
                                bookValues.put(AUTHOR, author);
                                bookValues.put(TITLE, text.substring(
                                        firstQuoteIndex + 1, nextQuoteIndex)
                                        .trim());
                                if (nextQuoteIndex < text.length()) {
                                    String subtitle = text
                                            .substring(nextQuoteIndex + 1)
                                            .trim();
                                    if (!TextUtils.isEmpty(subtitle)) {
                                        bookValues.put(COLLECTION_TITLE, subtitle);
                                    }
                                }
                            } else {
                                String dbName = columnNameToDBName
                                        .get(startName);
                                if (dbName != null) {
                                    bookValues.put(dbName, text);
                                }
                            }
                        }
                    }
                }
                eventType = xpp.next();
            }

            // an alternate notification scheme, might be to notify only after
            // all entries have been inserted.

        } catch (XmlPullParserException e) {
            LogHelper.debug("LibrivoxHandler",
                    "could not parse librivox rss feed", e);
        } catch (IOException e) {
            LogHelper.debug("LibrivoxHandler",
                    "could not parse librivox rss feed", e);
        }

        return inserted;
    }

}
