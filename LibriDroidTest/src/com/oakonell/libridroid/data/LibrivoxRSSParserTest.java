package com.oakonell.libridroid.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.oakonell.libridroid.data.LibrivoxRSSParser;
import com.oakonell.libridroid.data.LibrivoxRSSParser.BookParsedCallback;

import android.content.ContentValues;
import android.test.AndroidTestCase;

public class LibrivoxRSSParserTest extends AndroidTestCase {
	public void testSimple() {
		InputStream in = new ByteArrayInputStream(
				"<results><book><title>Wells, H. G.. \"First Men in the Moon, The\"</title><description>A Description</description><id>1430</id><rssurl>http://librivox.org/bookfeeds/the-first-men-in-the-moon-by-hg-wells.xml</rssurl><NumberOfSections>26</NumberOfSections></book></results>"
						.getBytes());
		InputStreamReader reader = new InputStreamReader(in);

		final List<ContentValues> books = new ArrayList<ContentValues>();

		LibrivoxRSSParser parser = new LibrivoxRSSParser(reader,
				new BookParsedCallback() {
					@Override
					public void finishedBook(ContentValues values) {
						books.add(values);
					}
				});

		int num = parser.parse();
		assertEquals(1, num);
		ContentValues bookValues = books.get(0);
		assertEquals("Wells, H. G.",
				bookValues.getAsString(LibrivoxRSSParser.AUTHOR));
		assertEquals("First Men in the Moon, The",
				bookValues.getAsString(LibrivoxRSSParser.TITLE));
		assertEquals(null,
				bookValues.getAsString(LibrivoxRSSParser.COLLECTION_TITLE));
		assertEquals("A Description",
				bookValues.getAsString(LibrivoxRSSParser.DESCRIPTION));
		assertEquals("1430",
				bookValues.getAsString(LibrivoxRSSParser.LIBRIVOX_ID));
		assertEquals(
				"http://librivox.org/bookfeeds/the-first-men-in-the-moon-by-hg-wells.xml",
				bookValues.getAsString(LibrivoxRSSParser.RSS_URL));
		assertEquals("26",
				bookValues.getAsString(LibrivoxRSSParser.NUM_SECTIONS));
	}

	public void testSubtitle() {
		InputStream in = new ByteArrayInputStream(
				"<results><book><title>Wells, H. G.. \"First Men in the Moon, The\" (in \"Foo Bar\")</title><description>A Description</description><id>1430</id><rssurl>http://librivox.org/bookfeeds/the-first-men-in-the-moon-by-hg-wells.xml</rssurl><NumberOfSections>26</NumberOfSections></book></results>"
						.getBytes());
		InputStreamReader reader = new InputStreamReader(in);

		final List<ContentValues> books = new ArrayList<ContentValues>();

		LibrivoxRSSParser parser = new LibrivoxRSSParser(reader,
				new BookParsedCallback() {
					@Override
					public void finishedBook(ContentValues values) {
						books.add(values);
					}
				});

		int num = parser.parse();
		assertEquals(1, num);
		ContentValues bookValues = books.get(0);
		assertEquals("Wells, H. G.",
				bookValues.getAsString(LibrivoxRSSParser.AUTHOR));
		assertEquals("First Men in the Moon, The",
				bookValues.getAsString(LibrivoxRSSParser.TITLE));
		assertEquals("(in \"Foo Bar\")",
				bookValues.getAsString(LibrivoxRSSParser.COLLECTION_TITLE));
		assertEquals("A Description",
				bookValues.getAsString(LibrivoxRSSParser.DESCRIPTION));
		assertEquals("1430",
				bookValues.getAsString(LibrivoxRSSParser.LIBRIVOX_ID));
		assertEquals(
				"http://librivox.org/bookfeeds/the-first-men-in-the-moon-by-hg-wells.xml",
				bookValues.getAsString(LibrivoxRSSParser.RSS_URL));
		assertEquals("26",
				bookValues.getAsString(LibrivoxRSSParser.NUM_SECTIONS));
	}

	public void testMultiple() {
		InputStream in = new ByteArrayInputStream(
				("<results><book><title>Austen, Jane. \"Emma (version 2)\"</title><description>Another Description</description><id>1936</id><rssurl>http://librivox.org/bookfeeds/emma-by-jane-austen.xml</rssurl><NumberOfSections>55</NumberOfSections></book>"
						+
						"<book><title>Wells, H. G.. \"First Men in the Moon, The\"</title><description>A Description</description><id>1430</id><rssurl>http://librivox.org/bookfeeds/the-first-men-in-the-moon-by-hg-wells.xml</rssurl><NumberOfSections>26</NumberOfSections></book>"
						+
						"</results>")
						.getBytes());
		InputStreamReader reader = new InputStreamReader(in);

		final List<ContentValues> books = new ArrayList<ContentValues>();

		LibrivoxRSSParser parser = new LibrivoxRSSParser(reader,
				new BookParsedCallback() {
					@Override
					public void finishedBook(ContentValues values) {
						books.add(values);
					}
				});

		int num = parser.parse();
		assertEquals(2, num);
		ContentValues bookValues = books.get(0);
		assertEquals("Austen, Jane",
				bookValues.getAsString(LibrivoxRSSParser.AUTHOR));
		assertEquals("Emma (version 2)",
				bookValues.getAsString(LibrivoxRSSParser.TITLE));
		assertEquals(null,
				bookValues.getAsString(LibrivoxRSSParser.COLLECTION_TITLE));
		assertEquals("Another Description",
				bookValues.getAsString(LibrivoxRSSParser.DESCRIPTION));
		assertEquals("1936",
				bookValues.getAsString(LibrivoxRSSParser.LIBRIVOX_ID));
		assertEquals(
				"http://librivox.org/bookfeeds/emma-by-jane-austen.xml",
				bookValues.getAsString(LibrivoxRSSParser.RSS_URL));
		assertEquals("55",
				bookValues.getAsString(LibrivoxRSSParser.NUM_SECTIONS));

		bookValues = books.get(1);
		assertEquals("Wells, H. G.",
				bookValues.getAsString(LibrivoxRSSParser.AUTHOR));
		assertEquals("First Men in the Moon, The",
				bookValues.getAsString(LibrivoxRSSParser.TITLE));
		assertEquals(null,
				bookValues.getAsString(LibrivoxRSSParser.COLLECTION_TITLE));
		assertEquals("A Description",
				bookValues.getAsString(LibrivoxRSSParser.DESCRIPTION));
		assertEquals("1430",
				bookValues.getAsString(LibrivoxRSSParser.LIBRIVOX_ID));
		assertEquals(
				"http://librivox.org/bookfeeds/the-first-men-in-the-moon-by-hg-wells.xml",
				bookValues.getAsString(LibrivoxRSSParser.RSS_URL));
		assertEquals("26",
				bookValues.getAsString(LibrivoxRSSParser.NUM_SECTIONS));
	}
}
