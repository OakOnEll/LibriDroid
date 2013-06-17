package com.oakonell.libridroid.player;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import net.jcip.annotations.GuardedBy;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.oakonell.libridroid.R;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.libridroid.impl.Notifications;
import com.oakonell.utils.EarlierAndroidCompatibleService;
import com.oakonell.utils.LogHelper;

public class LibriDroidPlayerService extends EarlierAndroidCompatibleService implements IPlayerService {
    private static final int END_OF_FILE_POSITION_LOOSENESS_MS = 1000;
    private static final int SEEK_UPDATE_INTERVAL_MS = 1000;
    private static final int REBUFFER_FILE_WHEN_REMAIN_MS = 1000;
    private static final int BUFFERING_TIME_OUT_MS = 10000;
    private static final int BUFFER_WAIT_CYCLE_TIME_MS = 30;
    private static final int MIN_SDK_VERSION_FOR_AUDIO_FOCUS = 8;

    private volatile int skipBackTimeMs;
    private volatile int skipForwardTimeMs;

    private static class Data {
        @Nullable
        private AudioFocusHelper focusHelper;
        @Nullable
        private BroadcastReceiver noisyReceiver;

        @Nullable
        private Notification notification;
        @Nullable
        private MediaPlayer player;
        @Nullable
        private Book book;
        @Nullable
        private BookSection currentSection;
        @Nullable
        private ProgressUpdater progressUpdater;
        @Nullable
        private File file;

        private boolean isDownloading;
        @Nullable
        private DownloadHelper downloadHelper;
        private int lastSyncedDownloadedApproxMs = 0;
        @Nullable
        private Runnable statusUpdater;
        @Nullable
        private BroadcastReceiver phoneStateReceiver;
        @Nullable
        public BroadcastReceiver pauseReceiver;
    }

    @GuardedBy("this")
    @Nullable
    private Data data;

    @Nullable
    private volatile AsyncTask<Intent, Void, Void> startTask;
    @Nullable
    private volatile AsyncTask<Void, Integer, Void> progressUpdaterTask;
    private PreferenceListener preferenceListener;

    @Override
    public void onStart(final Intent intent, int startId) {
        logStart("onStart");

        AsyncTask<Intent, Void, Void> oldTask = startTask;
        if (oldTask != null) {
            oldTask.cancel(true);
            startTask = null;
        }

        startTask = new AsyncTask<Intent, Void, Void>() {
            @Override
            protected Void doInBackground(Intent... params) {
                startInThread(intent);
                return null;
            }
        };
        startTask.execute(intent);
        logEnd("onStart");
    }

    private void startInThread(Intent intent) {
        Uri bookUri = intent.getData();
        final String bookId = bookUri.getPathSegments().get(1);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        updateFromPreferences(preferences);

        preferenceListener = new PreferenceListener();
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);

        synchronized (LibriDroidPlayerService.this) {
            if (data != null) {
                // stop existing media player
                // if already playing this book, we're done
                if (libriIsPlaying()) {
                    if (Long.parseLong(bookId) == data.book.getId()) {
                        Book requestedBook = Book.read(getContentResolver(), bookUri);
                        // playing the same book, but a different
                        // section
                        // results in pausing/restarting
                        if (requestedBook.getCurrentSectionNumber() == data.currentSection.getSectionNumber()) {
                            return;
                        }
                        libriPause(false, true, false);
                    } else {
                        // if playing a different book, pause (record
                        // position,etc),
                        // then play this one.
                        libriPause(false, true, true);
                    }
                }
            } else {
                data = new Data();
            }
            setWaiting(R.string.pleaseWait);

            data.book = Book.read(getContentResolver(), bookUri);
            data.currentSection = data.book.getCurrentSection(getContentResolver());
            if (data.progressUpdater != null) {
                data.progressUpdater.updateBookAndSection(data.currentSection);
            }

            playCurrentBookSection();
        }
    }

    private void updateFromPreferences(SharedPreferences preferences) {
        String backString = preferences.getString(getString(R.string.pref_skip_back_key), "10");
        String forwardString = preferences.getString(getString(R.string.pref_skip_forward_key), "10");
        skipBackTimeMs = safeParseInt(backString, 10) * 1000;
        skipForwardTimeMs = safeParseInt(forwardString, 10) * 1000;
    }

    private int safeParseInt(String string, int def) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return def;
        }
    }

    private synchronized void playCurrentBookSection() {
        logStart("playCurrentBookSection");
        if (!conditionallyAcquireResources()) {
            setError(R.string.cannotAcquireAudioResources);
            return;
        }

        if (data.currentSection == null) {
            LogHelper.error("LibriDroidPlayer",
                    "The current section is null, for section num = " + data.book.getCurrentSectionNumber());
        }

        final File file = data.currentSection.getFile(getContentResolver());
        Runnable prepareAndPlay = new Runnable() {
            @Override
            public void run() {
                prepareAndPlay(file);
            }
        };

        if (!file.exists() || data.currentSection.getSize() > file.length()) {
            setWaiting(R.string.bufferingAudioFile);
            if (!file.exists()) {
                LogHelper.info("PlayerService", "File doesn't exist, downloading");
            } else {
                if (data.currentSection.getSize() != file.length()) {
                    LogHelper.info("PlayerService", "Section's file size " + data.currentSection.getSize()
                            + ", actual file size " + file.length() + ", downloading");
                }
            }
            // start high priority download
            data.isDownloading = true;
            // this should move to any place with bufferEnoughOfFile
            // to support other entry points like seek, and "onCompletion"
            // when file download might have been stopped for some reason
            if (data.downloadHelper == null || !data.downloadHelper.isDownloading(data.currentSection)) {
                data.downloadHelper = new DownloadHelper(data.currentSection, this, prepareAndPlay);
            }
            return;
        }

        data.downloadHelper = null;
        data.isDownloading = false;
        prepareAndPlay.run();

        logEnd("playCurrentBookSection");

    }

    private boolean conditionallyAcquireResources() {
        if (data.focusHelper == null) {
            if (android.os.Build.VERSION.SDK_INT >= MIN_SDK_VERSION_FOR_AUDIO_FOCUS) {
                LogHelper.info("PlayerService", "creating focus helper");
                data.focusHelper = new AudioFocusHelper(this);
                if (!data.focusHelper.requestFocus()) {
                    logEnd("startPlaying=- couldn't get focus");
                    return false;
                }
            }
        }

        if (data.notification == null) {
            // int icon = android.R.drawable.ic_media_play;
            int icon = R.drawable.ic_stat_notify_play;
            CharSequence tickerText = null; // getText(R.string.download_ticker);
            long when = System.currentTimeMillis();
            data.notification = new Notification(icon, tickerText, when);
            data.notification.flags |= Notification.FLAG_ONGOING_EVENT;
        }

        Context context = getApplicationContext();
        Intent notificationIntent = new Intent(this, PlayerActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        CharSequence contentTitle = getText(R.string.playing) + " " + data.book.getTitle();
        CharSequence contentText = data.currentSection.getSectionNumber() + ": " + data.currentSection.getTitle();
        data.notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        startForegroundCompat(Notifications.PLAYER_ID, data.notification);

        if (data.noisyReceiver == null) {
            LogHelper.info("PlayerService", "registering noisy receiver");
            // if skipped to next section... receiver may exist
            data.noisyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                        if (libriIsPlaying()) {
                            libriPause(true, false, true);
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            registerReceiver(data.noisyReceiver, filter);
        }
        if (data.phoneStateReceiver == null) {
            LogHelper.info("PlayerService", "registering phone state receiver");
            // if skipped to next section... receiver may exist
            data.phoneStateReceiver = new BroadcastReceiver() {
                private boolean wasPlaying;
                private boolean wasRinging;

                @Override
                public void onReceive(Context context, Intent intent) {
                    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                        LogHelper.info("Player", "Incoming call, pausing playback");
                        wasPlaying = libriIsPlaying();
                        wasRinging = true;
                        // Incoming call: Pause music
                        libriPause(false, false, true);
                    } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                        // Not in call: Play music
                        LogHelper.info("Player", "Call ended, continuing playback");
                        wasRinging = false;
                        if (wasPlaying) {
                            libriPlay();
                        }
                    } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                        // A call is dialing, active or on hold
                        if (wasRinging) {
                            LogHelper.info("Player", "answering incoming call, playback remaining paused");
                            wasRinging = false;
                        } else {
                            LogHelper.info("Player", "phone offhook (e.g., outgoing call), pausing playback");
                            wasPlaying = libriIsPlaying();
                        }
                        libriPause(false, false, true);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            registerReceiver(data.phoneStateReceiver, filter);
        }
        return true;
    }

    // don't call under a synchronized block
    private void prepareAndPlay(final File file) {
        int position;

        synchronized (this) {
            position = (int) data.book.getCurrentPosition();
            data.file = file;
            if (data.player != null) {
                data.player.release();
                data.player = null;
            }
        }

        // this wait MUST be outside a synchronize block..?
        if (!bufferEnoughOfFile(position)) {
            return;
        }
        preparePlayer(file);
        startPlaying();
    }

    private synchronized boolean preparePlayer(File file) {
        data.player = new MediaPlayer();
        try {
            FileInputStream fis = new FileInputStream(file);
            FileDescriptor fd = fis.getFD();
            data.player.setOnErrorListener(new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaplayer, int i, int j) {
                    Toast toast = Toast
                            .makeText(getApplicationContext(), "Error on media player " + i + ", " + j,
                                    LogHelper.TOAST_ERROR_DISPLAY_MS);
                    toast.show();
                    LogHelper.error("LibriDroid", "Error in media player " + i + ", " + j);
                    return false;
                }
            });
            // player.setDataSource(fd, 0, currentSection.getSize());
            data.player.setDataSource(fd);
            fis.close();
            data.player.prepare();
            int position = (int) data.book.getCurrentPosition();
            data.player.seekTo(position);
            if (data.progressUpdater != null) {
                data.progressUpdater.updateBufferProgress(0);
                data.progressUpdater.updateProgress(position);
            }

        } catch (Exception e) {
            LogHelper.error("PlayerService", "Error getting player", e);
            setError(R.string.errorGettingPlayer);
        }

        addPlayerEvents(data.player);
        return true;
    }

    // do NOT call under synchronized block...
    private boolean bufferEnoughOfFile(int progress) {
        float fraction = 0;
        DownloadHelper downloadHelper;
        synchronized (this) {
            if (!data.isDownloading) {
                return true;
            }
            if (data.player != null) {
                fraction = ((float) progress) / data.player.getDuration();
            }
            if (data.downloadHelper == null || !data.downloadHelper.isDownloading(data.currentSection)) {
                data.downloadHelper = new DownloadHelper(data.currentSection, LibriDroidPlayerService.this, null);
            }
            downloadHelper = data.downloadHelper;
        }
        long start = System.currentTimeMillis();

        setWaiting(R.string.bufferingAudioFile);
        while (!downloadHelper.canStart(fraction)) {
            LogHelper.info("PlayerService", "Waiting for download file to be large enough");
            if (System.currentTimeMillis() - start > BUFFERING_TIME_OUT_MS) {
                libriPause(true, false, true);
                clearStatus();
                setError(R.string.connectionProblem);
                return false;
            }
            try {
                Thread.sleep(BUFFER_WAIT_CYCLE_TIME_MS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        clearStatus();
        return true;
    }

    private void addPlayerEvents(MediaPlayer thePlayer) {
        thePlayer.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // apparently sometimes a bit hazy on the duration..?
                int sectionDuration;
                synchronized (LibriDroidPlayerService.this) {
                    sectionDuration = data.currentSection.getDuration().getTotalMilliseconds();
                }
                final int currentPosition = mp.getCurrentPosition();
                if (sectionDuration - currentPosition < END_OF_FILE_POSITION_LOOSENESS_MS) {
                    playNextSection(false);
                } else {
                    LogHelper.info("Player", "Hit end of file.. before buffer updated or file finished... ");
                    // wait in a new thread...
                    Runnable continuation = new Runnable() {
                        @Override
                        public void run() {
                            possiblyRepointToBufferedFile();
                            if (bufferEnoughOfFile(currentPosition)) {
                                libriPlay();
                            }
                        }
                    };
                    new Thread(continuation).start();
                }
            }
        });
    }

    private synchronized boolean startPlaying() {
        logStart("startPlaying");
        if (!conditionallyAcquireResources()) {
            return false;
        }

        possiblyRepointToBufferedFile();
        clearStatus();
        if (data.player != null) {
            data.player.start();
        }
        if (data.progressUpdater != null) {
            data.progressUpdater.externalPlay();
        }
        broadcastPlayIntent();
        startProgressUpdaterThread();
        logEnd("startPlaying");
        return true;
    }

    private void broadcastPauseIntent() {
        LogHelper.info("Player", "Sending a 'pause' widget update");

        LibridroidAppWidgetProvider.Data widgetData = createWidgetData();
        widgetData.playing = false;
        broadcastWidgetIntent(widgetData);

        if (data.pauseReceiver != null) {
            unregisterReceiver(data.pauseReceiver);
            data.pauseReceiver = null;
        }

        LogHelper.info("Player", "Sent a 'pause' widget update");
    }

    private void broadcastWidgetIntent(LibridroidAppWidgetProvider.Data widgetData) {
        // Intent intent = new Intent(widgetData.playing ?
        // Actions.PLAYING_BOOK_ACTION
        // : Actions.STOPPED_PLAYING_BOOK_ACTION);
        // intent.putExtra(Actions.PLAYING_BOOK_EXTRA_BOOK_TITLE,
        // widgetData.bookTitle);
        // intent.putExtra(Actions.PLAYING_BOOK_EXTRA_BOOK_URI,
        // widgetData.bookUriString);
        // intent.putExtra(Actions.PLAYING_BOOK_EXTRA_SECTION_NUMBER,
        // widgetData.sectionNumber);
        // intent.putExtra(Actions.PLAYING_BOOK_EXTRA_SECTION_TITLE,
        // widgetData.sectionTitle);
        //
        // sendBroadcast(intent);
        LibridroidAppWidgetProvider.updateWidgets(getApplicationContext(), widgetData);
    }

    private LibridroidAppWidgetProvider.Data createWidgetData() {
        LibridroidAppWidgetProvider.Data widgetData = new LibridroidAppWidgetProvider.Data();
        widgetData.bookUriString = data.book.getUri().toString();
        widgetData.bookTitle = data.book.getTitle();
        widgetData.sectionTitle = data.currentSection.getTitle();
        widgetData.sectionNumber = data.currentSection.getSectionNumber();
        return widgetData;
    }

    private void broadcastPlayIntent() {
        LogHelper.info("Player", "Sending a 'play' widget update");

        LibridroidAppWidgetProvider.Data widgetData = createWidgetData();
        widgetData.playing = true;
        broadcastWidgetIntent(widgetData);

        if (data.pauseReceiver == null) {
            data.pauseReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(Actions.PAUSE_BOOK)) {
                        if (libriIsPlaying()) {
                            LogHelper.info("PlayerPauseReceiver", "Received a pause request");
                            libriPause(true, false, true);
                        }
                    }
                }
            };
        }
        IntentFilter filter = new IntentFilter(Actions.PAUSE_BOOK);
        registerReceiver(data.pauseReceiver, filter);

        LogHelper.info("Player", "Sent a 'play' widget update");
    }

    private void startProgressUpdaterThread() {
        if (progressUpdaterTask != null) {
            return;
        }
        final IPlayerService me = this;
        if (data.progressUpdater != null) {
            progressUpdaterTask = new AsyncTask<Void, Integer, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    while (!isCancelled()) {
                        if (data.progressUpdater == null) {
                            break;
                        }

                        synchronized (me) {
                            // logStart("progressUpdater");
                            if (data.player != null) {
                                if (data.isDownloading) {
                                    try {
                                        // "touch" the file to get updated
                                        // length...
                                        FileInputStream fis = new FileInputStream(data.file);
                                        FileDescriptor fd = fis.getFD();
                                        fd.sync();
                                        fis.close();
                                    } catch (IOException e) {
                                        // shouldn't happen.. but no worries..
                                    }

                                    long fileLength = data.file.length();
                                    double fileFractionDownloaded = ((double) fileLength)
                                            / data.currentSection.getSize();
                                    data.lastSyncedDownloadedApproxMs = (int) (fileFractionDownloaded * data.currentSection
                                            .getDuration().getTotalMilliseconds());
                                    LogHelper.info("PlayerService",
                                            "updating buffered progress "
                                                    + data.lastSyncedDownloadedApproxMs + ", current position is "
                                                    + data.player.getCurrentPosition());
                                    data.progressUpdater.updateBufferProgress(data.lastSyncedDownloadedApproxMs);
                                    if (data.player.isPlaying()) {
                                        if (data.player.getCurrentPosition() + REBUFFER_FILE_WHEN_REMAIN_MS >=
                                        data.lastSyncedDownloadedApproxMs) {
                                            LogHelper.info("PLayerService", "less than " + REBUFFER_FILE_WHEN_REMAIN_MS
                                                    + "ms remaining, pointing to new file");
                                            possiblyRepointToBufferedFile();
                                        }
                                    }
                                    if (data.currentSection.getSize() <= fileLength) {
                                        LogHelper.info("PlayerService", "File finished downloading");
                                        possiblyRepointToBufferedFile();
                                        data.isDownloading = false;
                                        if (!data.player.isPlaying()) {
                                            // stopProgressUpdaterTask();
                                            break;
                                        }
                                    }
                                }
                                data.progressUpdater.updateProgress(data.player.getCurrentPosition());
                            }
                            // logEnd("progressUpdater");
                        }
                        try {
                            Thread.sleep(SEEK_UPDATE_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            LogHelper.debug("LibridroidPlayer", "Sleep interrupted exception while updating progress");
                        }
                    }
                    // other code accessing this ivar needs to be careful, since
                    // this task cleans up after itself
                    // TODO THIS is a problem...
                    // progressUpdaterTask = null;
                    return null;
                }

            };
            progressUpdaterTask.execute((Void) null);
        }
    }

    private void stopProgressUpdaterTask() {
        // use local access, for thread safety, as task "cleans up" after
        // itself, setting ivar to null
        AsyncTask<Void, Integer, Void> theTask = progressUpdaterTask;
        if (theTask != null) {
            theTask.cancel(true);
        }
        progressUpdaterTask = null;
    }

    @Override
    public void onDestroy() {
        logStart("onDestroy");
        super.onDestroy();
        stopDownloadHelper();
        libriPause(true, true, true);
        logEnd("onDestroy");
    }

    public class LocalBinder extends Binder {
        public LibriDroidPlayerService getService() {
            return LibriDroidPlayerService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        // play(intent);
        return mBinder;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.oakonell.libridroid.player.IPlayerService#libriPause(boolean,
     * boolean, boolean)
     */
    @Override
    public synchronized void libriPause(boolean releaseResources, boolean releasePlayer, boolean updatePosition) {
        logStart("libriPause");
        if (data == null) {
            LogHelper.info("PLayerService", "Pause requested when player not initialized properly");
            return;
        }

        if (data.player != null) {
            if (data.player.isPlaying()) {
                data.player.pause();
            }
            if (updatePosition) {
                data.book.updatePosition(getContentResolver(), data.book.getCurrentSectionNumber(),
                        data.player.getCurrentPosition());
            }
        }
        possiblyRepointToBufferedFile();

        if (releaseResources) {
            if (data.focusHelper != null) {
                data.focusHelper.abandonFocus();
                data.focusHelper = null;
            }
            stopForegroundCompat(Notifications.PLAYER_ID);
            if (data.noisyReceiver != null) {
                LogHelper.info("PlayerService", "unregistering noisy receiver");
                unregisterReceiver(data.noisyReceiver);
                data.noisyReceiver = null;
            }
            if (data.phoneStateReceiver != null) {
                LogHelper.info("PlayerService", "unregistering noisy receiver");
                unregisterReceiver(data.phoneStateReceiver);
                data.phoneStateReceiver = null;
            }
            if (data.progressUpdater != null) {
                data.progressUpdater.externallyPaused();
            }
            if (!data.isDownloading) {
                stopProgressUpdaterTask();
            }
        }
        if (releasePlayer && data.player != null) {
            data.player.release();
            data.player = null;
        }
        broadcastPauseIntent();

        logEnd("libriPause");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.oakonell.libridroid.player.IPlayerService#libriPlay()
     */
    @Override
    public boolean libriPlay() {
        logStart("libriPlay");
        boolean started = startPlaying();
        logEnd("libriPlay");
        return started;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.oakonell.libridroid.player.IPlayerService#libriIsPlaying()
     */
    @Override
    public synchronized boolean libriIsPlaying() {
        logStart("libriIsPlaying");
        if (data == null) {
            return false;
        }
        try {
            if (data.player != null) {
                return data.player.isPlaying();
            } else {
                return false;
            }
        } finally {
            logEnd("libriIsPlaying");
        }
    }

    public synchronized int libriGetPosition() {
        logStart("libriGetPosition");
        if (data == null) {
            return 0;
        }
        try {
            if (data.player != null) {
                return data.player.getCurrentPosition();
            }
            return 0;
        } finally {
            logEnd("libriGetPosition");
        }
    }

    @CheckForNull
    public synchronized Book getBook() {
        if (data == null) {
            return null;
        }
        // TODO make a thread-safe copy to send out...
        return data.book;
    }

    @CheckForNull
    public synchronized BookSection getBookSection() {
        if (data == null) {
            return null;
        }
        // TODO make a thread-safe copy to send out...
        return data.currentSection;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.oakonell.libridroid.player.IPlayerService#libriSkipForward()
     */
    @Override
    public synchronized void libriSkipForward() {
        logStart("libriSkipForward");
        if (data == null) {
            LogHelper.info("PlayerService", "User is skipping forward, when service is not properly initialized");
            return;
        }
        try {
            if (data.player == null) {
                return;
            }
            int seekPosition = Math.min(data.player.getCurrentPosition() + skipForwardTimeMs,
                    data.player.getDuration());
            seekTo(seekPosition, false);
        } finally {
            logEnd("libriSkipForward");
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.oakonell.libridroid.player.IPlayerService#libriSkipBackward()
     */
    @Override
    public synchronized void libriSkipBackward() {
        libriSkipBackward(skipBackTimeMs, false);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.oakonell.libridroid.player.IPlayerService#libriSkipBackward(int)
     */
    @Override
    public synchronized void libriSkipBackward(int skipBack, boolean externalPauseUpdate) {
        logStart("libriSkipBackward");
        if (data == null) {
            LogHelper.info("PlayerService", "User is skiping backward, when service is not properly initialized");
            return;
        }
        try {
            if (data.player == null) {
                return;
            }
            int seekPosition = Math.max(data.player.getCurrentPosition() - skipBack, 0);
            seekTo(seekPosition, externalPauseUpdate);
        } finally {
            logEnd("libriSkipBackward");
        }
    }

    public synchronized void userSetPosition(int progress) {
        logStart("setPosition");
        if (data == null) {
            LogHelper.info("PlayerService", "User is setting position, when service is not properly initialized");
            return;
        }
        try {
            if (data.player == null) {
                return;
            }
            seekTo(progress, false);
        } finally {
            logEnd("setPosition");
        }
    }

    private void seekTo(final int progress, final boolean externalPauseUpdate) {
        boolean wasPlaying = false;
        if (data.player.isPlaying()) {
            wasPlaying = true;
            data.player.pause();
            if (externalPauseUpdate) {
                data.progressUpdater.externallyPaused();
            }
        }
        final boolean finalWasPlaying = wasPlaying;
        AsyncTask<Void, Void, Void> seekTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                possiblyRepointToBufferedFile();
                if (!bufferEnoughOfFile(progress)) {
                    // stop, with error reported
                    return null;
                }
                synchronized (LibriDroidPlayerService.this) {
                    data.player.seekTo(progress);

                    if (data.progressUpdater != null) {
                        data.progressUpdater.updateProgress(data.player.getCurrentPosition());
                    }
                    if (finalWasPlaying) {
                        if (externalPauseUpdate) {
                            data.progressUpdater.externalPlay();
                        }
                        data.player.start();
                    }
                    clearStatus();
                }
                return null;
            }

        };
        seekTask.execute((Void) null);
    }

    synchronized void libriPreviousSection() {
        logStart("libriPreviousSection");
        if (data == null) {
            LogHelper.info("PlayerService", "libriPreviousSection called, when service is not properly initialized");
            return;
        }
        int current = data.book.getCurrentSectionNumber();
        int next = current - 1;
        if (next < 1) {
            next = data.book.getNumberSections();
        }
        log("cur = " + current + ", next = " + next);

        playSection(next);
        logEnd("libriPreviousSection");
    }

    private synchronized void playNextSection(boolean wrapAround) {
        logStart("playNextSection");
        int current = data.book.getCurrentSectionNumber();
        int next = current + 1;
        if (next > data.book.getNumberSections()) {
            if (wrapAround) {
                next = 1;
            } else {
                if (data.progressUpdater != null) {
                    data.progressUpdater.updateProgress(data.player.getCurrentPosition());
                }
                libriPause(true, false, true);
                return;
            }
        }
        log("cur = " + current + ", next = " + next);

        playSection(next);
        logEnd("playNextSection");
    }

    private void playSection(int next) {
        logStart("playSection " + next);
        if (data.player != null) {
            if (data.player.isPlaying()) {
                data.player.pause();
            }
            data.player.release();
            data.player = null;
        }
        data.book.updatePosition(getContentResolver(), next, 0);
        data.currentSection = data.book.getSection(getContentResolver(), next);
        // get the section's title and author?
        if (data.progressUpdater != null && data.isDownloading) {
            data.progressUpdater.updateBufferProgress(0);
        }
        if (data.downloadHelper == null || !data.downloadHelper.isDownloading(data.currentSection)) {
            stopDownloadHelper();
        }

        // data.progressUpdater.updateProgress(0, 60000, total);
        if (data.progressUpdater != null) {
            data.progressUpdater.updateSection(data.currentSection);
        }
        playCurrentBookSection();
        logEnd("playSection");
    }

    public synchronized void setProgressUpdater(ProgressUpdater updater) {
        if (data == null) {
            return;
        }
        data.progressUpdater = updater;
        if (updater != null) {
            if (data.statusUpdater != null) {
                data.statusUpdater.run();
            }
            startProgressUpdaterThread();
        } else {
            stopProgressUpdaterTask();
        }
    }

    private void logStart(String string) {
        LogHelper.info("LibriDroidPlayerService", Thread.currentThread().getName() + " - Start " + string);
    }

    private void logEnd(String string) {
        LogHelper.info("LibriDroidPlayerService", Thread.currentThread().getName() + " - End " + string);
    }

    private void log(String string) {
        LogHelper.info("LibriDroidPlayerService", string);
    }

    public void userPause() {
        libriPause(true, false, true);
    }

    public void userPlay() {
        AsyncTask<Void, Void, Void> playTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                boolean playerExists;
                synchronized (LibriDroidPlayerService.this) {
                    if (data == null || data.player == null) {
                        playerExists = false;
                    } else {
                        playerExists = true;
                    }
                }
                if (playerExists) {
                    startPlaying();
                } else {
                    playCurrentBookSection();
                }
                return null;
            }

        };
        playTask.execute((Void) null);
    }

    public void userNextSection() {
        AsyncTask<Void, Void, Void> playTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                playNextSection(true);
                return null;
            }
        };
        playTask.execute((Void) null);
    }

    public void userPreviousSection() {
        AsyncTask<Void, Void, Void> playTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                libriPreviousSection();
                return null;
            }
        };
        playTask.execute((Void) null);
    }

    public void userSkipForward() {
        libriSkipForward();
    }

    public void userSkipBackward() {
        libriSkipBackward();
    }

    private synchronized void possiblyRepointToBufferedFile() {
        if (!data.isDownloading) {
            return;
        }
        LogHelper.info("PlayerService", "Possibly repoint to buffered file");
        // attempt at updating the player's buffer of
        // streamed content
        try {
            if (!data.file.exists()) {
                // might have got here a little too early
                return;
            }
            FileInputStream fis = new FileInputStream(data.file);
            FileDescriptor fd = fis.getFD();

            final MediaPlayer oldPlayer = data.player;
            boolean isPlaying = oldPlayer.isPlaying();
            MediaPlayer newPlayer = new MediaPlayer();

            newPlayer.setDataSource(fd);
            fis.close();
            newPlayer.prepare();

            // TODO this kind of works... except skips
            // while performing this switch...
            if (isPlaying) {
                oldPlayer.pause();
            }
            newPlayer.seekTo(oldPlayer.getCurrentPosition());
            addPlayerEvents(newPlayer);
            if (isPlaying) {
                newPlayer.start();
            }
            oldPlayer.release();
            data.player = newPlayer;

            data.lastSyncedDownloadedApproxMs = (int) (((double) data.file.length()) / data.currentSection.getSize() *
                    data.currentSection.getDuration().getTotalMilliseconds());

            if (data.progressUpdater != null) {
                data.progressUpdater.updateProgress(data.player.getCurrentPosition());
                data.progressUpdater.updateBufferProgress(data.lastSyncedDownloadedApproxMs);
            }
        } catch (IOException e) {
            LogHelper.info("PlayerService", "Finished repoint to buffered file");
            throw new RuntimeException("Error trying to reopen file", e);
        }
    }

    private synchronized void stopDownloadHelper() {
        if (data.downloadHelper != null) {
            data.downloadHelper.stop();
            data.downloadHelper = null;
        }

    }

    private synchronized void clearStatus() {
        data.statusUpdater = new Runnable() {
            @Override
            public void run() {
                data.progressUpdater.stoppedWaiting();
                data.statusUpdater = null;
            }
        };
        if (data.progressUpdater != null) {
            data.statusUpdater.run();
        }
    }

    private synchronized void setError(final int message) {
        data.statusUpdater = new Runnable() {
            @Override
            public void run() {
                data.progressUpdater.error(message);

            }
        };
        if (data.progressUpdater != null) {
            data.statusUpdater.run();
        }
    }

    private synchronized void setWaiting(final int message) {
        data.statusUpdater = new Runnable() {
            @Override
            public void run() {
                data.progressUpdater.waiting(message);

            }
        };
        if (data.progressUpdater != null) {
            data.statusUpdater.run();
        }
    }

    private final class PreferenceListener implements OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(R.string.pref_skip_back_key))
                    || key.equals(getString(R.string.pref_skip_forward_key))) {
                updateFromPreferences(sharedPreferences);
            }
        }
    }
}
