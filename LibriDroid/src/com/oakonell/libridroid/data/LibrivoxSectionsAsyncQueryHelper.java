package com.oakonell.libridroid.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.ContentValues;
import android.net.Uri;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.R;
import com.oakonell.utils.query.AbstractAsyncQueryHelper;
import com.oakonell.utils.xml.XMLUtils;

public class LibrivoxSectionsAsyncQueryHelper extends AbstractAsyncQueryHelper {
    private static final Pattern SECTION_TITLE_AUTHOR_PATTERN = Pattern.compile("(\\d+) \"([^\"]*)\" (by)? (.*)");
    private static final int SECTION_AUTHOR_GROUP = 4;
    private static final int SECTION_TITLE_GROUP = 2;

    private final LibraryContentProvider provider;
    private final String bookId;

    public LibrivoxSectionsAsyncQueryHelper(
            LibraryContentProvider provider, String bookId,
            String communicationId) {
        super(provider.getContext(), communicationId, provider.getContext().getString(R.string.progress_parsing),
                provider.getContext()
                        .getString(R.string.progress_reading));
        this.provider = provider;
        this.bookId = bookId;
    }

    @Override
    protected String getQueryUri(String queryText) {
        return queryText;
    }

    @Override
    protected int parseResponseEntity(HttpEntity entity, Uri uri) throws IOException {
        InputStream content = entity.getContent();

        Document dom = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            dom = builder.parse(content);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error creating/parsing xml document from url "
                            + uri.toString(), e);
        }
        Element root = dom.getDocumentElement();
        NodeList channel = root.getElementsByTagName("channel");
        if (channel.getLength() == 0) {
            return 0;
        }
        if (channel.getLength() > 1) {
            throw new RuntimeException(
                    "Expected only one 'channel' element from url "
                            + uri.toString());
        }
        Element item = (Element) channel.item(0);
        NodeList items = item.getElementsByTagName("item");

        int max = items.getLength();
        for (int i = 0; i < max; i++) {
            handleSectionItem((Element) items.item(i), i + 1, uri);
        }
        return max;
    }

    private void handleSectionItem(Element item, int sectionNumber, Uri uri) {
        /*
         * <enclosure url=
         * "http://www.archive.org/download/emma_solo_librivox/emma_01_01_austen_64kb.mp3"
         * length="10377635" type="audio/mpeg" />
         * 
         * <itunes:explicit>No</itunes:explicit> <itunes:block>No</itunes:block>
         * <itunes:duration>21:37</itunes:duration>
         */
        NodeList enclosures = item.getElementsByTagName("enclosure");
        if (enclosures.getLength() != 1) {
            throw new RuntimeException(
                    "Unexpected number of enclosure elements "
                            + enclosures.getLength() + " from "
                            + uri.toString());
        }
        Element enclosure = (Element) enclosures.item(0);
        String length = enclosure.getAttribute("length");
        String url = enclosure.getAttribute("url");

        String duration = XMLUtils.getTextContent(item, "itunes:duration");

        ContentValues values = new ContentValues();
        values.put(Libridroid.BookSections.COLUMN_NAME_BOOK_ID, bookId);
        values.put(Libridroid.BookSections.COLUMN_NAME_DURATION, duration);
        values.put(Libridroid.BookSections.COLUMN_NAME_SECTION_NUMBER, sectionNumber);
        values.put(Libridroid.BookSections.COLUMN_NAME_SIZE, length);
        values.put(Libridroid.BookSections.COLUMN_NAME_URL, url);

        String xmlTitle = XMLUtils.getTextContent(item, "title");
        String title = "";
        String author = "";
        if (xmlTitle.contains("\"")) {
            Matcher matcher = SECTION_TITLE_AUTHOR_PATTERN.matcher(xmlTitle);
            if (matcher.matches()) {
                title = matcher.group(SECTION_TITLE_GROUP);
                author = matcher.group(SECTION_AUTHOR_GROUP);
            } else {
                title = xmlTitle;
            }
        } else {
            title = xmlTitle;
        }

        values.put(Libridroid.BookSections.COLUMN_NAME_SECTION_TITLE, title);
        values.put(Libridroid.BookSections.COLUMN_NAME_SECTION_AUTHOR, author);

        provider.insert(Libridroid.BookSections.contentUri(bookId), values);
    }
}
