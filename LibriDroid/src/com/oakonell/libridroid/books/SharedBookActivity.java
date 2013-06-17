package com.oakonell.libridroid.books;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.Libridroid.Books;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.data.LibrivoxRSSParser;
import com.oakonell.libridroid.data.LibrivoxRSSParser.BookParsedCallback;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.share.ActivityLaunchAdapter;
import com.oakonell.utils.xml.XMLUtils;

public class SharedBookActivity extends Activity {

    private ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shared_book_activity);

        final Intent intent = getIntent();
        final String librivoxId = intent.getData().getQueryParameter("LibriDroidId");

        dialog = ProgressDialog.show(this, getString(R.string.pleaseWait),
                getString(R.string.gettingBookInfoFromUrl, intent.getDataString()));

        AsyncTask<Void, Void, Void> async = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {

                if (TextUtils.isEmpty(librivoxId)) {
                    // no special id parameter, either try to parse it from
                    // stream?, or
                    // simply redirect to another activity accepting this uri
                    tryToGetBookTitleFromUrlElseRedirect(intent);
                    return null;
                }
                Cursor bookCursor = getContentResolver().query(Books.CONTENT_URI, null,
                        Books.COLUMN_NAME_LIBRIVOX_ID + " = ?",
                        new String[] { librivoxId }, null);
                if (bookCursor.getCount() == 0) {
                    loadBook(bookCursor, librivoxId);
                } else if (bookCursor.getCount() == 1) {
                    launchBookView(bookCursor);
                } else {
                    updateError("Found multiple books?!?! for id " + librivoxId);
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                dialog.dismiss();
            }

        };

        async.execute((Void) null);

    }

    private void launchBookView(Cursor bookCursor) {
        Intent bookViewIntent = new Intent(this, BookViewActivity.class);
        bookCursor.moveToFirst();
        long id = bookCursor.getLong(bookCursor.getColumnIndex(Books._ID));
        bookCursor.close();
        bookViewIntent.setData(ContentUris.withAppendedId(Libridroid.Books.CONTENT_ID_URI_BASE, id));
        dialog.dismiss();
        startActivity(bookViewIntent);
    }

    private void loadBook(Cursor bookCursor, String librivoxId) {
        // if not in library, grab it from librivox, and then navigate to it
        // (if it exists)
        String url = "http://catalog.librivox.org/search_xml.php?extended=1&id=" + librivoxId;
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet();
        InputStreamReader inputReader;
        try {
            request.setURI(new URI(url));
            HttpResponse response = client.execute(request);

            HttpEntity entity = response.getEntity();

            InputStream content = entity.getContent();
            inputReader = new InputStreamReader(content, "UTF-8");
        } catch (Exception e) {
            updateError("Encountered error trying to find book with id " + librivoxId + ": " + e.getLocalizedMessage());
            return;
        }
        LibrivoxRSSParser parser = new LibrivoxRSSParser(inputReader,
                new BookParsedCallback() {
                    @Override
                    public void finishedBook(ContentValues values) {
                        LogHelper.debug("LibrivoxAsyncQueryHelper", "Inserting a row");
                        getContentResolver().insert(Libridroid.Books.CONTENT_URI, values);
                    }

                });
        int inserted = 0;
        try {
            inserted = parser.parse();
        } catch (Exception e) {
            updateError("Exception while inserting a book for librivoxId = " + librivoxId);
            return;
        }

        if (inserted != 1) {
            updateError("Didn't insert a book for librivoxId = " + librivoxId);
            return;
        }
        bookCursor.requery();
        launchBookView(bookCursor);
    }

    private void tryToGetBookTitleFromUrlElseRedirect(Intent intent) {
        // look for rss feeds in the form
        // http://librivox.org/bookfeeds/the-edge-of-the-knife-by-h-beam-piper.xml
        String rssUrl = getBookFeedRssUrl(intent);
        if (rssUrl != null) {
            // if it contains the rss book feed, it is likely a book page on
            // librivox..
            // parse the rss to look for the book title...
            // then open the search on the title
            String title = getTitleFrom(rssUrl);
            if (title != null) {
                title = title.replace("Librivox:", "");
                int authorIndex = title.lastIndexOf(" by ");
                if (authorIndex > 0) {
                    title = title.substring(0, authorIndex);
                }
                Intent searchIntent = new Intent(this, LibrivoxSearchActivity.class);
                searchIntent.putExtra("search", title.trim());
                dialog.dismiss();
                startActivity(searchIntent);
                return;
            }
        }

        redirectToOtherActivity(intent);
    }

    private String getTitleFrom(String rssUrl) {
        InputStream rssContent;
        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet rssRequest = new HttpGet();
            rssRequest.setURI(new URI(rssUrl));
            HttpResponse rssResponse = client.execute(rssRequest);
            rssContent = rssResponse.getEntity().getContent();

        } catch (Exception e) {
            updateError("Unable to load/parse rss uri " + rssUrl);
            return null;
        }

        Document dom = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            dom = builder.parse(rssContent);
            rssContent.close();
        } catch (Exception e) {
            updateError("Error creating/parsing xml document from url "
                    + rssUrl);
            return null;
        }
        // String xmlString = XMLUtils.xmlDocumentToString(dom);
        // LogHelper.info("", xmlString);

        Element root = dom.getDocumentElement();
        List<Element> channels = XMLUtils.getChildElementsByName(root, "channel");
        if (channels.isEmpty()) {
            updateError("Incorrect book feed on url "
                    + rssUrl);
            return null;
        }
        Element channel = channels.get(0);
        List<Element> titles = XMLUtils.getChildElementsByName(channel, "title");
        if (titles.size() != 1) {
            updateError("Incorrect book feed on url " + rssUrl);
            return null;
        }
        return XMLUtils.getTextContent(titles.get(0));
    }

    private String getBookFeedRssUrl(Intent intent) {
        String html;
        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet();
            request.setURI(new URI(intent.getDataString()));
            HttpResponse response = client.execute(request);

            InputStream content = response.getEntity().getContent();
            html = new Scanner(content).useDelimiter("\\A").next();
            content.close();
        } catch (Exception e) {
            updateError("Error connecting to  " + intent.getDataString());
            return null;
        }

        Pattern bookfeedPattern = Pattern.compile("\"(http://librivox.org/bookfeeds/[^\"]*)\"");
        Matcher matcher = bookfeedPattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void redirectToOtherActivity(final Intent intent) {
        final Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setData(intent.getData());
        final List<ResolveInfo> activities = getPackageManager().queryIntentActivities(newIntent, 0);

        for (Iterator<ResolveInfo> iter = activities.iterator(); iter.hasNext();) {
            ResolveInfo each = iter.next();

            ActivityInfo activity = each.activityInfo;
            ComponentName name = new ComponentName(activity.applicationInfo.packageName, activity.name);

            if (name.getClassName().equals(SharedBookActivity.class.getName())) {
                iter.remove();
            }
        }

        if (activities.size() == 1) {
            redirectToSingleActivity(newIntent, activities);
            return;
        }

        redirectToMultiple(newIntent, activities);
    }

    private void redirectToMultiple(final Intent newIntent, final List<ResolveInfo> activities) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.chooseAlternateActivity);

        ActivityLaunchAdapter adapter = new ActivityLaunchAdapter(this, R.id.label, activities);
        builder.setAdapter(adapter, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                ResolveInfo resolveInfo = activities.get(which);
                ActivityInfo activity = resolveInfo.activityInfo;

                ComponentName name = new ComponentName(activity.applicationInfo.packageName, activity.name);
                newIntent.setComponent(name);

                startActivity(newIntent);
            }

        }
                );
        dialog.dismiss();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.create().show();
            }
        });
    }

    private void redirectToSingleActivity(final Intent newIntent, final List<ResolveInfo> activities) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        ResolveInfo resolveInfo = activities.get(0);
                        ActivityInfo activity = resolveInfo.activityInfo;

                        ComponentName name = new ComponentName(activity.applicationInfo.packageName, activity.name);
                        newIntent.setComponent(name);

                        startActivity(newIntent);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                    default:
                        throw new RuntimeException("Unexpected button was clicked");
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String message = getString(R.string.urlIsNotForLibriDroid);
        builder.setMessage(message).setPositiveButton(android.R.string.yes, dialogClickListener)
                .setCancelable(true).show();

        return;
    }

    private void updateError(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView errorView = (TextView) findViewById(R.id.shareError);
                errorView.setText(text);
            }
        });

    }

}
