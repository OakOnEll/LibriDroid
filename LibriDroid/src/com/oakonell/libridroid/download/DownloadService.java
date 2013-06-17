package com.oakonell.libridroid.download;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import net.jcip.annotations.GuardedBy;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;

import com.oakonell.libridroid.Libridroid.BookSections;
import com.oakonell.libridroid.R;
import com.oakonell.libridroid.download.Download.Downloads;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.libridroid.impl.FileHelper;
import com.oakonell.libridroid.impl.Notifications;
import com.oakonell.utils.EarlierAndroidCompatibleService;
import com.oakonell.utils.LogHelper;

public class DownloadService extends EarlierAndroidCompatibleService {
    private static final int WAIT_FOR_OLD_DOWNLOAD_FINISH_MS = 3000;
    private static final int FILE_WRITE_BUFFER_SIZE = 1024 * 12;
    private static final long DOWNLOAD_UPDATES_MS = 500;
    private static final int MIN_BYTES_BUFERED_FOR_PLAY = 8 * 1024;

    // For later android versions
    // @Override
    // public int onStartCommand(Intent intent, int flags, int startId) {

    private DownloadInterface downloadInterface;

    public void setDownloadInterface(DownloadInterface downloadInterface) {
        this.downloadInterface = downloadInterface;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        startDownloading();
    }

    private synchronized void startDownloading() {
        libriPause();
        final DownloadTask oldTask = downloadTask;
        downloadTask = new DownloadTask(oldTask);
        downloadTask.execute((Void) null);
    }

    private void waitForTaskToFinish(DownloadTask oldTask) {
        if (oldTask == null) {
            return;
        }
        long start = System.currentTimeMillis();
        while (!oldTask.isFinished()) {
            if (System.currentTimeMillis() - start > WAIT_FOR_OLD_DOWNLOAD_FINISH_MS) {
                throw new RuntimeException("Waited too long for old download task to finish");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloadTask != null) {
            downloadTask.cancel(true);
        }
        stopForegroundCompat(Notifications.DOWNLOAD_ID);
    }

    private final class DownloadTask extends AsyncTask<Void, Void, Void> {
        private final DownloadTask oldTask;
        private Notification notification;
        private volatile boolean finished;

        private DownloadTask(DownloadTask oldTask) {
            this.oldTask = oldTask;
        }

        @Override
        protected Void doInBackground(Void... params) {
            waitForTaskToFinish(oldTask);
            if (downloadInterface != null) {
                downloadInterface.resumed();
            }
            startDownload(true);
            finished = true;
            if (downloadInterface != null) {
                downloadInterface.paused();
            }
            return null;
        }

        public boolean isFinished() {
            return finished;
        }

        private void startDownload(final boolean allowEmptyQueue) {
            LogHelper.info("DownloadService", "StartDownload");
            Intent notificationIntent = new Intent(DownloadService.this, DownloadViewActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(DownloadService.this, 0,
                    notificationIntent, 0);

            int icon = R.drawable.ic_stat_notify_download_book;
            CharSequence tickerText = getText(R.string.download_ticker);
            long when = System.currentTimeMillis();
            CharSequence contentTitle = getText(R.string.download_service_title);
            CharSequence contentText = getText(R.string.download_view_progress);

            notification = new Notification(icon, tickerText, when);
            notification.setLatestEventInfo(getApplicationContext(), contentTitle,
                    contentText,
                    contentIntent);
            notification.flags |= Notification.FLAG_ONGOING_EVENT;

            startForegroundCompat(Notifications.DOWNLOAD_ID, notification);

            downloadQueuedFiles(allowEmptyQueue);
        }

        private void downloadQueuedFiles(boolean allowEmptyQueue) {
            if (isCancelled()) {
                return;
            }
            LogHelper.info("DownloadService", "downloadQueuedFiles");
            Cursor downloadsCursor = getContentResolver().query(
                    Download.Downloads.CONTENT_URI, null, null, null, null);
            WifiLock mWifiLock = null;
            try {
                // Create the Wifi lock (this does not acquire the lock,
                // this just
                // creates it)
                mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "downloadLock");
                mWifiLock.acquire();
                if (downloadsCursor.getCount() == 0) {
                    LogHelper.info("DownloadService", "no downloadQueuedFiles?!");
                    if (!allowEmptyQueue) {
                        throw new RuntimeException("There are no queued files");
                    }
                }
                while (!isCancelled() && downloadsCursor.getCount() > 0) {
                    if (!downloadsCursor.moveToFirst()) {
                        LogHelper.warn("DownloadService",
                                "There was no first record in cursor, despite size >0");
                        break;
                    }

                    long downloadId = downloadsCursor.getLong(downloadsCursor
                            .getColumnIndex(Download.Downloads._ID));
                    String url = downloadsCursor.getString(downloadsCursor
                            .getColumnIndex(Download.Downloads.COLUMN_NAME_URL));
                    long bytes = downloadsCursor
                            .getLong(downloadsCursor
                                    .getColumnIndex(Download.Downloads.COLUMN_NAME_TOTAL_BYTES));
                    long bookId = downloadsCursor
                            .getLong(downloadsCursor
                                    .getColumnIndex(Download.Downloads.COLUMN_NAME_BOOK_ID));
                    long sectionNumber = downloadsCursor
                            .getLong(downloadsCursor
                                    .getColumnIndex(Download.Downloads.COLUMN_NAME_SECTION_NUM));
                    downloadsCursor.deactivate();

                    Book book = Book.read(getContentResolver(), bookId);

                    try {
                        if (notification != null) {
                            notification.setLatestEventInfo(
                                    getApplicationContext(),
                                    getText(R.string.download_service_title),
                                    book.getTitle()
                                            + " " + sectionNumber,
                                    notification.contentIntent);
                            NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                            mNM.notify(Notifications.DOWNLOAD_ID, notification);
                        }
                        downloadFile(downloadId, bookId, sectionNumber, url, bytes,
                                book.getTitle(), book.getLibrivoxId());
                        if (isCancelled()) {
                            break;
                        }
                        getContentResolver().delete(
                                Uri.withAppendedPath(
                                        Download.Downloads.CONTENT_ID_URI_BASE,
                                        Long.toString(downloadId)), null, null);
                    } catch (Exception e) {
                        // improve this error handling later
                        throw new RuntimeException("Error downloding file", e);
                    }

                    if (!downloadsCursor.requery()) {
                        break;
                    }
                }

            } finally {
                if (mWifiLock != null) {
                    mWifiLock.release();
                }
                stopForegroundCompat(Notifications.DOWNLOAD_ID);
                stopSelf();
                if (!isCancelled()) {
                    // TODO this might be a problem...
                    // downloadTask = null;
                    cancel(true);
                }
                LogHelper.info("DownloadService", "Stop downloading queued files");
            }

        }

        private void downloadFile(long downloadId, long bookId,
                long sectionNumber, String urlString,
                long totalBytes, String title, String librivoxId)
                throws IOException {

            long timeOfLastUpdate = System.currentTimeMillis();
            URL url = new URL(urlString);

            File file = FileHelper.getFile(getContentResolver(), bookId, sectionNumber, true, title,
                    librivoxId);
            LogHelper.info("DownloadService", "Download file " + file.getAbsolutePath());

            URLConnectionPartialDownloadInfo urlConnectionInfo;
            try {
                urlConnectionInfo = getURLConnectionFOrPartialDownload(
                        url, file, totalBytes, downloadId);
            } catch (Exception e) {
                // TODO replace this with better error handling
                LogHelper.error("DownloadService",
                        "Error getting URL connection for file " + file.getAbsolutePath(), e);
                return;
            }
            HttpURLConnection urlConnection = urlConnectionInfo.urlConnection;
            try {
                totalBytes = urlConnectionInfo.totalBytes;
                long downloaded = urlConnectionInfo.downloaded;

                BufferedInputStream in = new
                        BufferedInputStream(urlConnection.getInputStream(),
                                FILE_WRITE_BUFFER_SIZE);
                FileOutputStream fos = new FileOutputStream(file, (downloaded != 0));
                BufferedOutputStream bout = new BufferedOutputStream(fos,
                        FILE_WRITE_BUFFER_SIZE);
                try {
                    byte[] data = new byte[FILE_WRITE_BUFFER_SIZE];
                    int numBytesRead = 0;
                    while ((numBytesRead = in.read(data, 0, FILE_WRITE_BUFFER_SIZE)) >= 0) {
                        bout.write(data, 0, numBytesRead);
                        bout.flush();
                        downloaded += numBytesRead;
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - timeOfLastUpdate > DOWNLOAD_UPDATES_MS) {
                            updateProgress(downloadId, downloaded, totalBytes);
                            timeOfLastUpdate = currentTime;
                            LogHelper.info("DownloadService", "Downloaded " + downloaded + "/" + totalBytes
                                    + " of " + file.getAbsolutePath());
                        }
                        Thread.yield();
                        if (isCancelled()) {
                            break;
                        }
                    }
                } finally {
                    updateProgress(downloadId, downloaded, totalBytes);
                    if (bout != null) {
                        bout.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                }
            } finally {
                urlConnection.disconnect();
                LogHelper.info("DownloadService", "Download file");
            }
        }
    }

    private static class URLConnectionPartialDownloadInfo {
        private HttpURLConnection urlConnection;
        private long downloaded;
        private long totalBytes;
    }

    URLConnectionPartialDownloadInfo getURLConnectionFOrPartialDownload(
            URL url, File file, long totalBytes, long downloadId)
            throws IOException {
        // To deal with partial file downloads ... from
        // http://stackoverflow.com/questions/6237079/resume-http-file-download-in-java
        long downloaded = file.length();

        HttpURLConnection urlConnection = (HttpURLConnection) url
                .openConnection();

        // set up some things on the connection
        urlConnection.setRequestMethod("GET");
        if (downloaded > 0) {
            urlConnection.setRequestProperty("Range", "bytes=" + downloaded
                    + "-");
        }

        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(true);

        int contentLength = urlConnection.getContentLength();
        if (contentLength == -1) {
            // TODO handle this... it is being seen a little too frequently
            throw new RuntimeException("Url Connection returned -1 content length for " + url.toString()
                    + " range-bytes=" + downloaded);
        }
        if (downloaded != 0) {
            if (totalBytes - downloaded != contentLength) {
                LogHelper.warn("Libridroid.DownloadService",
                        "The content length of "
                                + url.toString()
                                + " (" + contentLength
                                + ") does not match previous total ("
                                + totalBytes + ")- current file size ("
                                + downloaded + ")... restarting download.");
                downloaded = 0;
                urlConnection.disconnect();

                // reconnect, without the range request parameter
                urlConnection = (HttpURLConnection) url.openConnection();

                // set up some things on the connection
                urlConnection.setRequestMethod("GET");

                urlConnection.setDoOutput(true);
                contentLength = urlConnection.getContentLength();
                updateProgress(downloadId, 0, contentLength);
                URLConnectionPartialDownloadInfo result = new URLConnectionPartialDownloadInfo();
                result.downloaded = downloaded;
                result.urlConnection = urlConnection;
                result.totalBytes = contentLength;
                return result;
            } else {
                LogHelper.info("Libridroid.DownloadService",
                        "Continuing download of " + url + " to "
                                + file.getAbsolutePath() + " "
                                + contentLength + " bytes remaining");
                URLConnectionPartialDownloadInfo result = new URLConnectionPartialDownloadInfo();
                result.downloaded = downloaded;
                result.urlConnection = urlConnection;
                result.totalBytes = totalBytes;
                return result;
            }
        } else {
            if (contentLength != totalBytes) {
                LogHelper.warn("Libridroid.DownloadService",
                        "The content length of "
                                + url.toString()
                                + " does not match the section's download size- updating the section data.");
                updateSectionFileSize(downloadId, contentLength);
                updateProgress(downloadId, 0, contentLength);
            } else {
                LogHelper.info("Libridroid.DownloadService", "Starting download of "
                        + url + " to " + file.getAbsolutePath());
            }
            URLConnectionPartialDownloadInfo result = new URLConnectionPartialDownloadInfo();
            result.downloaded = 0;
            result.urlConnection = urlConnection;
            result.totalBytes = contentLength;
            return result;
        }

    }

    private void updateSectionFileSize(long downloadId, int contentLength) {
        Cursor query = getContentResolver().query(Download.Downloads.CONTENT_URI, null, Downloads._ID + " = ?",
                new String[] { Long.toString(downloadId) }, null);
        if (!query.moveToFirst()) {
            throw new RuntimeException("Expected to find a download record");
        }
        try {
            String bookId = query.getString(query.getColumnIndex(Downloads.COLUMN_NAME_BOOK_ID));
            long sectionNum = query.getLong(query.getColumnIndex(Downloads.COLUMN_NAME_SECTION_NUM));

            ContentValues values = new ContentValues();
            values.put(BookSections.COLUMN_NAME_SIZE, contentLength);

            getContentResolver().update(BookSections.contentUri(bookId, sectionNum), values, null, null);
        } finally {
            query.close();
        }

    }

    private void updateProgress(long downloadId, long downloadedSize,
            long totalSize) {
        ContentValues values = new ContentValues();
        values.put(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES,
                downloadedSize);
        values.put(Download.Downloads.COLUMN_NAME_TOTAL_BYTES, totalSize);
        getContentResolver().update(
                Uri.withAppendedPath(Download.Downloads.CONTENT_ID_URI_BASE,
                        Long.toString(downloadId)), values, null, null);
    }

    public class LocalBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();
    @GuardedBy("this")
    private volatile DownloadTask downloadTask;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void libriPause() {
        synchronized (this) {
            if (downloadTask == null) {
                return;
            }
            downloadTask.cancel(true);
        }
    }

    public synchronized boolean userIsDownloading() {
        return downloadTask != null && !downloadTask.isCancelled();
    }

    public void startPriorityDownload(final BookSection currentSection) {
        libriPause();
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                LogHelper.info("DownloadService", "Starting priority download ");
                // update any that are currently
                // "high priority.. probably due to error..."
                ContentValues zeroValues = new ContentValues();
                zeroValues.put(Download.Downloads.COLUMN_NAME_SEQUENCE, 0);
                int zeroedUpdated = getContentResolver().update(
                        Download.Downloads.CONTENT_URI, zeroValues, Download.Downloads.COLUMN_NAME_SEQUENCE + " = -1",
                        null);
                LogHelper.info("DownloadService", "lowered priority on " + zeroedUpdated + " records");

                // add a record if not exist else change priority, and start
                // download
                Cursor existingRecord = DownloadHelper.getExistingDownloadRecordFor(getContentResolver(),
                        currentSection.getBookUri(), currentSection.getSectionNumber());
                if (existingRecord == null) {
                    LogHelper.info("DownloadService", "Inserting download record");
                    // insert the record
                    ContentValues values = new ContentValues();
                    values.put(Download.Downloads.COLUMN_NAME_BOOK_ID, currentSection.getBookId());
                    values.put(Download.Downloads.COLUMN_NAME_SECTION_NUM, currentSection.getSectionNumber());
                    values.put(Download.Downloads.COLUMN_NAME_URL, currentSection.getUrl());
                    values.put(Download.Downloads.COLUMN_NAME_TOTAL_BYTES, currentSection.getSize());
                    values.put(Download.Downloads.COLUMN_NAME_SEQUENCE, -1);

                    Uri insert = getContentResolver().insert(Download.Downloads.CONTENT_URI, values);
                    if (insert == null) {
                        throw new RuntimeException("SHould have inserted a download row");
                    }
                } else {
                    LogHelper.info("DownloadService", "Updating download record");
                    // update the priority/sequence
                    ContentValues values = new ContentValues();
                    values.put(Download.Downloads.COLUMN_NAME_SEQUENCE, -1);
                    int updated = getContentResolver()
                            .update(
                                    Download.Downloads.CONTENT_URI,
                                    values,
                                    Download.Downloads.COLUMN_NAME_BOOK_ID + " = ? and "
                                            + Download.Downloads.COLUMN_NAME_SECTION_NUM
                                            + " = ? ",
                                    new String[] { currentSection.getBookId(),
                                            Long.toString(currentSection.getSectionNumber()) });
                    // assert that the number updated is 1\
                    if (updated != 1) {
                        throw new RuntimeException("SHould have updated a download row");
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                startDownloading();
            }

        };
        task.execute((Void) null);

    }

    public void stopPriorityDownload(final BookSection currentSection) {
        libriPause();
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // reset the priority of suggested section
                ContentValues values = new ContentValues();
                values.put(Download.Downloads.COLUMN_NAME_SEQUENCE, 0);
                getContentResolver().update(
                        Download.Downloads.CONTENT_URI,
                        values,
                        Download.Downloads.COLUMN_NAME_BOOK_ID + " = ? and "
                                + Download.Downloads.COLUMN_NAME_SECTION_NUM
                                + " = ? ",
                        new String[] { currentSection.getBookId(), Long.toString(currentSection.getSectionNumber()) });
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                // TODO only restart download if was originally downloading
                // before any "priority" downloads
                startDownloading();
            }

        };
        task.execute((Void) null);
    }

    public boolean canStartPlayingPriorityDownload(BookSection currentSection, float positionFraction) {
        Book book = currentSection.getBook(getContentResolver());
        File file = FileHelper.getFile(getContentResolver(), Long.parseLong(currentSection.getBookId()),
                currentSection.getSectionNumber(), false,
                book.getTitle(), book.getLibrivoxId());
        long fileLength = file.length();
        if (file.exists()) {
            LogHelper.info("DownloadService", "file " + file.getAbsolutePath() + " size is " + fileLength);
        } else {
            LogHelper.info("DownloadService", "file " + file.getAbsolutePath() + " doesn't exist");
        }
        return file.exists() && fileLength > MIN_BYTES_BUFERED_FOR_PLAY + positionFraction * currentSection.getSize();
    }

    public interface DownloadInterface {
        void paused();

        void resumed();
    }

}
