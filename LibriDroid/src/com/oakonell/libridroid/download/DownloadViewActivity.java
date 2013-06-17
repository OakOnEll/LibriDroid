package com.oakonell.libridroid.download;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.oakonell.libridroid.R;
import com.oakonell.libridroid.download.Download.Downloads;
import com.oakonell.libridroid.download.DownloadService.DownloadInterface;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.MenuHelper;
import com.oakonell.utils.ByteSizeHelper;
import com.oakonell.utils.LogHelper;

public class DownloadViewActivity extends Activity {
    private ResourceCursorAdapter mAdapter;

    private DownloadService mBoundService;
    private boolean mIsBound = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_activity);

        // attempt to connect to the running service...
        LogHelper.info("Download Activity", "Initial Binding to service");
        doBindService();
        final Button pauseButton = (Button) findViewById(R.id.pause);
        updatePauseButton(pauseButton, mBoundService != null && mBoundService.userIsDownloading());
        pauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsBound && mBoundService != null) {
                    LogHelper.info("Download Activity", "Pressed pause");
                    // TODO wait for pause to "take hold"?
                    mBoundService.libriPause();
                    // doUnbindService();
                    updatePauseButton(pauseButton, true);
                } else {
                    if (mBoundService == null) {
                        LogHelper.info("Download Activity", "Pressed resume");
                        Intent service = new Intent(DownloadViewActivity.this,
                                DownloadService.class);
                        // restart the service
                        ComponentName componentName = startService(service);
                        LogHelper.info("Download activity",
                                "Starting service" + componentName == null ? "null" : componentName.toString());

                        // and then bind to it
                        doBindService();
                    }
                    updatePauseButton(pauseButton, false);
                }
            }
        });

        final ListView searchList = (ListView) findViewById(R.id.download_list);
        registerForContextMenu(searchList);

        final Cursor downloadsCursor =
                managedQuery(Download.Downloads.CONTENT_URI, null, null, null,
                        null);

        mAdapter = new ResourceCursorAdapter(this, R.layout.download_list_item,
                downloadsCursor) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                int downloadedBytes = cursor
                        .getInt(cursor
                                .getColumnIndex(Download.Downloads.COLUMN_NAME_DOWNLOADED_BYTES));
                int totalBytes = cursor
                        .getInt(cursor
                                .getColumnIndex(Download.Downloads.COLUMN_NAME_TOTAL_BYTES));

                long bookId = cursor
                        .getLong(cursor
                                .getColumnIndex(Download.Downloads.COLUMN_NAME_BOOK_ID));
                long sectionNumber = cursor
                        .getLong(cursor
                                .getColumnIndex(Download.Downloads.COLUMN_NAME_SECTION_NUM));

                Book book = Book.read(getContentResolver(), bookId);
                TextView titleView = (TextView) view.findViewById(R.id.title);
                if (book != null) {
                    titleView.setText(book.getTitle());
                } else {
                    titleView.setText("???");
                }

                TextView sectionView = (TextView) view.findViewById(R.id.section);
                sectionView.setText(Long.toString(sectionNumber));

                ProgressBar progressView = (ProgressBar) view.findViewById(R.id.downloaded_progress);
                progressView.setMax(totalBytes);
                progressView.setProgress(downloadedBytes);

                TextView downloadedView = (TextView) view.findViewById(R.id.downloaded_bytes);
                downloadedView.setText(ByteSizeHelper.getDisplayable(downloadedBytes));

                TextView totalView = (TextView) view.findViewById(R.id.total_bytes);
                totalView.setText(ByteSizeHelper.getDisplayable(totalBytes));
            }
        };

        searchList.setAdapter(mAdapter);
    }

    private void updatePauseButton(final Button pauseButton, final boolean isRunning) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && mBoundService != null) {
                    pauseButton.setText(R.string.pause);
                } else {
                    pauseButton.setText(R.string.resume);
                }

            }
        };
        runOnUiThread(runnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return MenuHelper.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return MenuHelper.onOptionsItemSelected(this, item);
    }

    @Override
    public boolean onSearchRequested() {
        return MenuHelper.onSearchRequested(this);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((DownloadService.LocalBinder) service).getService();
            final Button pauseButton = (Button) findViewById(R.id.pause);
            updatePauseButton(pauseButton, true);
            mBoundService.setDownloadInterface(new DownloadInterface() {
                @Override
                public void resumed() {
                    updatePauseButton(pauseButton, true);
                }

                @Override
                public void paused() {
                    updatePauseButton(pauseButton, false);
                }
            });
            LogHelper.info("DownloadView", "Connecting to service - button should say pause");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService.setDownloadInterface(null);
            mBoundService = null;
            Button pauseButton = (Button) findViewById(R.id.pause);
            updatePauseButton(pauseButton, false);
            LogHelper.info("DownloadView", "Disonnecting from service - button should say pause");
        }
    };

    void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(this, DownloadService.class);
        mIsBound = bindService(intent, mConnection, 0);
        LogHelper.info("Download activity", "Bind to service result = " + mIsBound);
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    private static class DownloadMenuItems {
        private static final int DELETE = Menu.FIRST;
        private static final int MOVE_TO_FIRST = DELETE + 1;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.download_list) {
            menu.setHeaderTitle(R.string.downloadMenu);
            menu.add(0, DownloadMenuItems.DELETE, Menu.NONE, R.string.deleteDownload);
            // menu.add(0, DownloadMenuItems.MOVE_TO_FIRST, Menu.NONE,
            // R.string.downloadMoveToFirst);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        int index = menuInfo.position;
        Cursor c = (Cursor) mAdapter.getItem(index);
        Long id = c.getLong(c.getColumnIndex(Downloads._ID));
        Uri downloadUri = Uri.withAppendedPath(Downloads.CONTENT_ID_URI_BASE, Long.toString(id));

        switch (item.getItemId()) {
            case DownloadMenuItems.DELETE:
                DownloadHelper.delete(this, downloadUri);
                return true;
            case DownloadMenuItems.MOVE_TO_FIRST:
            default:
                Toast toast = Toast.makeText(getApplicationContext(), "Not yet implemented",
                        LogHelper.TOAST_ERROR_DISPLAY_MS);
                toast.show();
        }
        return false;

    }

}
