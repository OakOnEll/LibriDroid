package com.oakonell.libridroid.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.oakonell.libridroid.R;
import com.oakonell.utils.LogHelper;

/**
 * Extracted to a helper class for backwards support of older android versions.
 * This should only be instantiated if (android.os.Build.VERSION.SDK_INT >= 8)
 * 
 */
public class AudioFocusHelper implements OnSharedPreferenceChangeListener {
    private static final int DUCK_WAIT_TIME_MS = 1000;
    private final AudioManager mAudioManager;
    private final IPlayerService service;
    private int skipBackMs;

    private OnAudioFocusChangeListener focusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        private volatile boolean gainedFocus;

        @Override
        public void onAudioFocusChange(int focusChange) {
            LogHelper.info("AudioFocusHelper", "Focus change val = " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    LogHelper.info("AudioFocusHelper", "Focus Gained");
                    gainedFocus = true;
                    // resume playback ??
                    service.libriPlay();
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                    LogHelper.info("AudioFocusHelper", "Focus Loss");
                    // Lost focus for an unbounded amount of time: stop playback
                    // and release media player
                    service.libriPause(true, true, true);
                    service.libriSkipBackward(skipBackMs, true);
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    LogHelper.info("AudioFocusHelper", "Focus Loss Transient");
                    // Lost focus for a short time, but we have to stop
                    // playback. We don't release the media player because
                    // playback is likely to resume
                    service.libriPause(false, false, true);
                    service.libriSkipBackward(skipBackMs, true);
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    LogHelper.info("AudioFocusHelper", "Focus Loss transient, can duck");
                    if (!service.libriIsPlaying()) {
                        return;
                    }
                    // Lost focus for a short time, but it's ok to keep playing
                    // at an attenuated level
                    service.libriPause(false, false, true);
                    service.libriSkipBackward(skipBackMs, true);
                    gainedFocus = false;
                    scheduleStart();
                    break;
                default:
                    // do nothing...
                    LogHelper.info("AudioFocusHelper", "Unhandled Audio focus event " + focusChange);
            }

        }

        private void scheduleStart() {
            final Handler handler = new Handler();
            Runnable startPlayingAgain = new Runnable() {
                @Override
                public void run() {
                    LogHelper.info("AudioFocusHelper", "Scheduled Focus request");
                    if (gainedFocus) {
                        LogHelper.info("AudioFocusHelper", "  Focus already Gained, done");
                        return;
                    }
                    if (!service.libriIsPlaying()) {
                        gainedFocus = service.libriPlay();
                        LogHelper.info("AudioFocusHelper", "Not playing, tried to play- result = " + gainedFocus);
                    }
                    if (!gainedFocus) {
                        LogHelper.info("AudioFocusHelper", "Focus not regained, rescheduling");
                        scheduleStart();
                    }
                }
            };
            handler.postDelayed(startPlayingAgain, DUCK_WAIT_TIME_MS);
        }
    };

    // other fields here, you'll probably hold a reference to an interface
    // that you can use to communicate the focus changes to your Service

    public AudioFocusHelper(IPlayerService service) {
        this.service = service;
        Context context = service.getApplicationContext();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        updateFromPreferences(preferences);

        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    private void updateFromPreferences(SharedPreferences preferences) {
        Context context = service.getApplicationContext();
        String backString = preferences.getString(context.getString(R.string.pref_transient_focus_loss_repeat_key),
                "10");
        skipBackMs = safeParseInt(backString, 10) * 1000;
    }

    private int safeParseInt(String string, int def) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return def;
        }
    }

    public boolean requestFocus() {
        LogHelper.info("FocusHelper", "Requesting focus");
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.requestAudioFocus(focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
    }

    public boolean abandonFocus() {
        LogHelper.info("FocusHelper", "Abandoning focus");
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.abandonAudioFocus(focusChangeListener);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context context = service.getApplicationContext();
        if (key.equals(context.getString(R.string.pref_transient_focus_loss_repeat_key))) {
            updateFromPreferences(sharedPreferences);
        }
    }

}
