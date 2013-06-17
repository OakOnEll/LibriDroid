package com.oakonell.libridroid.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.oakonell.libridroid.download.DownloadService;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.utils.LogHelper;

public class DownloadHelper {
    private BookSection currentSection;
    private IPlayerService libriDroidPlayerService;

    private DownloadService mBoundService;
    private boolean mIsBound = false;
    private Runnable onStart;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((DownloadService.LocalBinder) service).getService();
            mBoundService.startPriorityDownload(currentSection);
            if (onStart != null) {
                Thread thread = new Thread(onStart);
                thread.start();
            }
            LogHelper.info("DownloadView", "Connecting to service - button should say pause");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            LogHelper.info("DownloadView", "Disonnecting from service - button should say pause");
        }
    };

    public DownloadHelper(BookSection currentSection, LibriDroidPlayerService libriDroidPlayerService,
            Runnable runnable) {
        this.currentSection = currentSection;
        this.libriDroidPlayerService = libriDroidPlayerService;
        onStart = runnable;

        doBindService();

    }

    private void doBindService() {
        Context context = libriDroidPlayerService.getApplicationContext();
        Intent intent = new Intent(context, DownloadService.class);
        mIsBound = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (!mIsBound) {
            throw new RuntimeException("Could not connect to download service...");
        }
    }

    private void doUnbindService() {
        if (mIsBound) {
            Context context = libriDroidPlayerService.getApplicationContext();
            // Detach our existing connection.
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void stop() {
        if (mBoundService != null) {
            mBoundService.stopPriorityDownload(currentSection);
        }
        doUnbindService();
    }

    public boolean canStart(float positionFraction) {
        if (mBoundService == null) {
            return false;
        }
        return mBoundService.canStartPlayingPriorityDownload(currentSection, positionFraction);
    }

    public boolean isDownloading() {
        if (mBoundService == null) {
            return false;
        }
        return mBoundService.userIsDownloading();
    }

    public boolean isDownloading(BookSection currentSection2) {
        return currentSection.getBookId().equals(currentSection2.getBookId())
                && currentSection.getSectionNumber() == currentSection2.getSectionNumber();
    }

}
