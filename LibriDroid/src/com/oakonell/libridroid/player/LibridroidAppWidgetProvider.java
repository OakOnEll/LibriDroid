package com.oakonell.libridroid.player;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;

import com.oakonell.libridroid.Libridroid;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.books.BookViewActivity;
import com.oakonell.libridroid.books.LibrivoxSearchActivity;
import com.oakonell.libridroid.books.MyBooksActivity;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.libridroid.impl.BookSort;
import com.oakonell.utils.LogHelper;

public class LibridroidAppWidgetProvider extends AppWidgetProvider {

    static class Data {
        String bookUriString;
        String bookTitle;
        String sectionTitle;
        long sectionNumber;
        boolean playing = false;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateDataFromDB(context);
        context.startService(new Intent(context, WidgetUpdateService.class));
    }

    private static void updateDataFromDB(Context context) {
        LogHelper.info("AppWidget", "Update from DB");
        // Toast.makeText(this, "Update from DB", 3000).show();
        Data data = new Data();
        // try to get the latest book listened to... or at least the
        // first
        // book in the library
        Cursor query = context.getContentResolver().query(Libridroid.Books.CONTENT_URI, null,
                Libridroid.Books.COLUMN_NAME_IS_DOWNLOADED + " > 0 ", null,
                BookSort.LAST_LISTENED.getSortBy());
        if (query.moveToFirst()) {
            Book book = Book.fromCursor(query);
            data.bookUriString = book.getUri().toString();
            data.bookTitle = book.getTitle();
            BookSection currentSection =
                    book.getCurrentSection(context.getContentResolver());
            data.sectionTitle = currentSection.getTitle();
            data.sectionNumber = currentSection.getSectionNumber();
        } else {
            query.close();
            updateWithSearchMessage(context);
            return;
        }
        query.close();

        LibridroidAppWidgetProvider.updateWidgets(context, data);
    }

    private static void updateWithSearchMessage(Context context) {
        ComponentName thisWidget = new ComponentName(context, LibridroidAppWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.libridroid_appwidget_message);
        views.setTextViewText(R.id.message, context.getResources().getString(R.string.no_books_start_searching_widget));

        Intent sectionIntent = new Intent(context, LibrivoxSearchActivity.class);
        sectionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingSectionIntent = PendingIntent.getActivity(context, 1, sectionIntent, 0);
        views.setOnClickPendingIntent(R.id.message, pendingSectionIntent);

        manager.updateAppWidget(thisWidget, views);
    }

    static void updateWidgets(Context context, Data data) {
        ComponentName thisWidget = new ComponentName(context, LibridroidAppWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        RemoteViews updateViews = buildUpdate(context, data);
        manager.updateAppWidget(thisWidget, updateViews);
    }

    private static RemoteViews buildUpdate(Context context, Data data) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.librivox_appwidget);

        if (data.bookUriString == null) {
            views.setViewVisibility(R.id.play_widget_img, View.GONE);

            Intent sectionIntent = new Intent(context, MyBooksActivity.class);
            sectionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingSectionIntent = PendingIntent.getActivity(context, 0, sectionIntent, 0);
            views.setOnClickPendingIntent(R.id.section_info, pendingSectionIntent);

            views.setTextViewText(R.id.book_title, context.getString(R.string.click_to_find_books));
            views.setTextViewText(R.id.section_number, "");
            views.setTextViewText(R.id.section_title, "");
            return views;
        }

        if (!data.playing) {
            views.setViewVisibility(R.id.play_widget_img, View.VISIBLE);
            views.setViewVisibility(R.id.pause_widget_img, View.GONE);
            // views.setImageViewResource(R.id.play_widget_img,
            // android.R.drawable.ic_media_play);

            LogHelper.info("AppWidget", "making play button visible");

            Intent service = new Intent(context, LibriDroidPlayerService.class);
            service.setData(Uri.parse(data.bookUriString));

            PendingIntent pendingIntent = PendingIntent.getService(context, 0, service, 0);
            views.setOnClickPendingIntent(R.id.control_section, pendingIntent);
        } else {
            views.setViewVisibility(R.id.pause_widget_img, View.VISIBLE);
            views.setViewVisibility(R.id.play_widget_img, View.GONE);
            // views.setImageViewResource(R.id.pause_widget_img,
            // android.R.drawable.ic_media_pause);

            Intent intent = new Intent(Actions.PAUSE_BOOK);
            LogHelper.info("AppWidget", "making pause button visible");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            views.setOnClickPendingIntent(R.id.control_section, pendingIntent);
        }

        Intent sectionIntent = new Intent(context, BookViewActivity.class);
        sectionIntent.setData(Uri.parse(data.bookUriString));
        sectionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingSectionIntent = PendingIntent.getActivity(context, 1, sectionIntent, 0);
        views.setOnClickPendingIntent(R.id.section_info, pendingSectionIntent);

        views.setTextViewText(R.id.book_title, data.bookTitle);
        views.setTextViewText(R.id.section_number, data.sectionNumber + "");
        views.setTextViewText(R.id.section_title, data.sectionTitle);
        return views;
    }

    public static class WidgetUpdateService extends Service {

        @Override
        public void onStart(Intent intent, int startId) {
            super.onStart(intent, startId);

            // default to update from DB
            updateDataFromDB(this);

            // bind to player service... might update the state of the widget
            // from the player service
            doBindService();

            stopSelf();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            doUnbindService();
        }

        private void updateDataFromPlayerService() {
            LogHelper.info("AppWidget", "Update from Service");
            // Toast.makeText(this, "Update from Service", 3000).show();
            Data data = new Data();

            Book book = mBoundService.getBook();
            if (book == null) {
                return;
            }
            data.bookUriString = book.getUri().toString();
            data.bookTitle = book.getTitle();
            BookSection currentSection = book.getCurrentSection(getContentResolver());
            data.sectionTitle = currentSection.getTitle();
            data.sectionNumber = currentSection.getSectionNumber();
            data.playing = mBoundService.libriIsPlaying();

            LibridroidAppWidgetProvider.updateWidgets(this, data);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private LibriDroidPlayerService mBoundService;
        private boolean mIsBound = false;

        void doBindService() {
            // Establish a connection with the service. We use an explicit
            // class name because we want a specific service implementation that
            // we know will be running in our own process (and thus won't be
            // supporting component replacement by other applications).
            Intent intent = new Intent(this, LibriDroidPlayerService.class);
            mIsBound = bindService(intent, mConnection, 0);
        }

        void doUnbindService() {
            if (mIsBound) {
                // Detach our existing connection.
                unbindService(mConnection);
                mIsBound = false;
            }
        }

        private ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // This is called when the connection with the service has been
                // established, giving us the service object we can use to
                // interact with the service. Because we have bound to a
                // explicit
                // service that we know is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                mBoundService = ((LibriDroidPlayerService.LocalBinder) service).getService();
                LogHelper.info("AppWIdget", "bound to service");
                updateDataFromPlayerService();
                // doUnbindService();
                // stopSelf();
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                // Because it is running in our same process, we should never
                // see this happen.
                if (mBoundService != null) {
                    mBoundService.setProgressUpdater(null);
                }
                mBoundService = null;
            }
        };
    }

}
