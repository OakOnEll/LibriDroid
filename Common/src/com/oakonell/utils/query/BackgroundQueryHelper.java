package com.oakonell.utils.query;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.oakonell.utils.LogHelper;
import com.oakonell.utils.Utils;
import com.oakonell.utils.query.Communications.CommunicationEntry;
import com.oakonell.utils.query.Communications.State;

/**
 * This class provides an activity with the ability to show indeterminate
 * progress/messages while performing a query (e.g, in another thread).
 * 
 * Instantiate an instance, passing in the activity, (indefinite) progress bar
 * view, text view, and a cleanup Runnable. (The activity should mark the
 * progress bar as visibilty=GONE to start with. This observer will display/hide
 * as necessary.)
 * 
 * Then, use the beginQuery(Uri) method to construct a "wrapped" Uri with the
 * additional communication entry as a query parameter.
 * 
 * The ContentProvider that uses the returned modified Uri can then get the
 * communication entry to perform updates. Whenever the communication entry is
 * updated, the message on the activity will be updated appropriately. When the
 * action is complete, the cleanup will be run, as well as hiding the progress
 * and text view again.
 * 
 */
public class BackgroundQueryHelper {
    private final Activity activity;

    private final ProgressBar progressView;
    private final TextView progressText;
    private final int errorColorId;
    private final int regularColor;

    private CommunicationObserver communicationObserver;
    private Runnable onFinish;

    public final class CommunicationObserver {
        private final CommunicationEntry entry;

        CommunicationObserver(CommunicationEntry newEntry) {
            entry = newEntry;
            entry.registerChangeListener(this);
        }

        public void onChange() {
            LogHelper.info("BackgroundHelper", "communication onChange " + entry.getId());

            final Runnable cleanupUI = new Runnable() {
                @Override
                public void run() {
                    if (progressText != null) {
                        progressText.setVisibility(View.GONE);
                    }
                    progressView.setVisibility(View.GONE);
                    communicationObserver.unregister();
                }
            };
            Runnable cleanup = new Runnable() {
                @Override
                public void run() {
                    activity.runOnUiThread(cleanupUI);
                    if (onFinish != null) {
                        onFinish.run();
                    }
                }
            };

            if (entry == null) {
                LogHelper.info("BackgroundHelper", "onChange - no records");
                cleanup.run();
                LogHelper.info("BackgroundHelper", "onChange - done");
                return;
            }
            LogHelper.info("BackgroundHelper", "onChange - a record");

            State status = entry.getStatus();
            switch (status) {
                case COMPLETE:
                    cleanup.run();
                    break;
                case ERROR: {
                    final String message = entry.getMessage();
                    if (progressText == null) {
                        Runnable updateMessage = new Runnable() {
                            @Override
                            public void run() {
                                Toast toast = Toast.makeText(activity,
                                        Utils.getAppName(activity) + ":" + message,
                                        Toast.LENGTH_LONG);
                                toast.show();
                            }
                        };
                        activity.runOnUiThread(updateMessage);
                    } else {
                        Runnable updateMessage = new Runnable() {
                            @Override
                            public void run() {
                                progressText.setText(message);
                                progressText.setTextColor(activity.getResources().getColor(errorColorId));
                                progressView.setVisibility(View.GONE);
                            }
                        };
                        activity.runOnUiThread(updateMessage);
                    }
                    communicationObserver.unregister();
                    if (onFinish != null) {
                        onFinish.run();
                    }
                }
                    break;
                case WORKING: {
                    // update message..
                    if (progressText != null) {
                        final String message = entry.getMessage();
                        Runnable updateMessage = new Runnable() {
                            @Override
                            public void run() {
                                progressText.setTextColor(regularColor);
                                progressText.setText(message);
                            }
                        };
                        activity.runOnUiThread(updateMessage);
                    }
                }
                    break;
                default:
                    throw new RuntimeException("Unexpected status " + status);
            }
            LogHelper.info("BackgroundHelper", "onChange - done");
        }

        protected void unregister() {
            entry.unregisterChangeListener(this);
        }
    }

    /**
     * Create a new background query helper for an activity, linked to an
     * indeterminate progress bar and a message text.
     * 
     * @param activity
     *            the host activity
     * @param progressView
     *            the required indeterminate progress bar
     * @param progressText
     *            the optional message text view
     * @param onFinish
     *            some optional final code to run on finish
     */
    public BackgroundQueryHelper(Activity activity, ProgressBar progressView,
            TextView progressText, Runnable onFinish, int errorColorId) {
        this.activity = activity;
        this.onFinish = onFinish;
        this.errorColorId = errorColorId;

        if (progressView == null) {
            throw new IllegalArgumentException("Need a progress bar view");
        }
        if (progressText != null) {
            progressText.setText("");
            regularColor = progressText.getTextColors().getDefaultColor();
        } else {
            regularColor = 0;
        }
        this.progressView = progressView;
        this.progressText = progressText;
    }

    /**
     * Create a communication entry to communicate changes between threads
     * performing background queries. The returned Uri should be used to pass to
     * subsequent users.
     * 
     * @param baseUri
     *            the original Uri
     * @return a modified Uri
     */
    public Uri beginQuery(String baseUri) {
        // insert a query communication
        progressView.setVisibility(View.VISIBLE);

        if (progressText != null) {
            progressText.setVisibility(View.VISIBLE);
        }

        CommunicationEntry newEntry = Communications.create();
        String communicationId = newEntry.getId();
        if (communicationObserver != null) {
            communicationObserver.unregister();
            communicationObserver = null;
        }
        communicationObserver = new CommunicationObserver(newEntry);

        LogHelper.info("BackgroundHelper", "beginQuery " + baseUri + " " + communicationId);
        if (baseUri.contains("?")) {
            return Uri.parse(baseUri + "&communication_id=" + communicationId);
        } else {
            return Uri.parse(baseUri + "?communication_id=" + communicationId);
        }
    }

    /**
     * When the activity is destroyed, various cleanup should occur- e.g., so
     * that the defunct activity is no longer notified of changes.
     */
    public void onDestroy() {
        if (communicationObserver != null) {
            communicationObserver.unregister();
            communicationObserver = null;
        }

    }

}
