package com.oakonell.libridroid.player;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.oakonell.libridroid.R;
import com.oakonell.libridroid.books.BookShareHelper;
import com.oakonell.libridroid.books.BookViewActivity;
import com.oakonell.libridroid.books.LibrivoxSearchActivity;
import com.oakonell.libridroid.impl.Book;
import com.oakonell.libridroid.impl.BookSection;
import com.oakonell.libridroid.impl.MenuHelper;
import com.oakonell.utils.Duration;
import com.oakonell.utils.LogHelper;
import com.oakonell.utils.activity.AbstractFlingableActitivty;

public class PlayerActivity extends AbstractFlingableActitivty {
    private static final int MS_IN_SECOND = 1000;

    private LibriDroidPlayerService mBoundService;
    private boolean mIsBound = false;
    private BookSection bookSection;

    // cache frequently updated views
    private SeekBar sectionSeekBar;
    private TextView sectionProgressText;
    private TextView sectionRemainingText;
    private ProgressBar bookProgressBar;
    private TextView bookPositionText;
    private TextView bookRemainingText;
    private boolean acceptedError;
    private ProgressDialog pd;
    private PlayerProgressUpdater progressUpdater;

    private Runnable postBind;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        LogHelper.info("Player", "onSaveInstance");
        if (bookSection != null) {
            outState.putString("bookId", bookSection.getBookId());
            outState.putLong("section", bookSection.getSectionNumber());
        }
        outState.putBoolean("acceptedError", acceptedError);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.info("Player", "onCreate");
        setContentView(R.layout.player_activity);

        installFlingHandler(findViewById(R.id.player));

        if (savedInstanceState != null) {
            acceptedError = savedInstanceState.getBoolean("acceptedError");
        }

        if (savedInstanceState != null) {
            String bookId = savedInstanceState.getString("bookId");
            long sectionNumber = savedInstanceState.getLong("section");
            if (bookId != null) {
                bookSection = Book.readSection(getContentResolver(), bookId, sectionNumber);
            }
        }

        postBind = new Runnable() {
            @Override
            public void run() {
                if (!mIsBound) {
                    // restore from the saved state
                    Runnable runOnUI = new Runnable() {
                        @Override
                        public void run() {
                            populateView();
                        }
                    };
                    runOnUiThread(runOnUI);
                }
            }
        };

        sectionProgressText = (TextView) findViewById(R.id.position);
        sectionRemainingText = (TextView) findViewById(R.id.section_remaining);

        bookProgressBar = (ProgressBar) findViewById(R.id.bookProgress);
        bookPositionText = (TextView) findViewById(R.id.bookPosition);
        bookRemainingText = (TextView) findViewById(R.id.book_remaining);

        sectionSeekBar = (SeekBar) findViewById(R.id.seek_position);
        sectionSeekBar.setOnSeekBarChangeListener(new SectionSeekBarListener());

        final TextView titleText = (TextView) findViewById(R.id.title);
        titleText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bookSection != null) {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(PlayerActivity.this, BookViewActivity.class);
                            intent.setData(bookSection.getBookUri());
                            startActivity(intent);
                        }
                    };
                    new Thread(runnable).start();
                }
            }
        });

        TextView authorView = (TextView) findViewById(R.id.author);
        authorView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Book book = bookSection.getBook(getContentResolver());
                String author = book.getAuthor();

                Intent intent = new Intent(PlayerActivity.this, LibrivoxSearchActivity.class);
                intent.putExtra("search", author);
                startActivity(intent);
            }
        });

        TextView sectionAuthorView = (TextView) findViewById(R.id.sectionAuthor);
        sectionAuthorView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String author = bookSection.getAuthor();

                Intent intent = new Intent(PlayerActivity.this, LibrivoxSearchActivity.class);
                intent.putExtra("search", author);
                startActivity(intent);
            }
        });

        final Button playButton = (Button) findViewById(R.id.play);
        Button skipForwardButton = (Button) findViewById(R.id.skip_forward);
        skipForwardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBoundService.userSkipForward();
            }
        });
        Button skipBackButton = (Button) findViewById(R.id.skip_back);
        skipBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBoundService.userSkipBackward();
            }
        });
        Button previousSectionButton = (Button) findViewById(R.id.previous_section);
        previousSectionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBoundService.userPreviousSection();
            }
        });
        Button nextSectionButton = (Button) findViewById(R.id.next_section);
        nextSectionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBoundService.userNextSection();
            }
        });

        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBoundService.libriIsPlaying()) {
                    mBoundService.userPause();
                } else {
                    mBoundService.userPlay();
                }
                setPlayLabel();
            }
        });

        setPlayLabel();
    }

    private void setPlayLabel() {
        final Button playButton = (Button) findViewById(R.id.play);
        if (mIsBound && mBoundService == null) {
            // the service should be connected asynchronously soon, and update
            // the label
            return;
        }
        if (!mIsBound || !mBoundService.libriIsPlaying()) {
            playButton.setText(R.string.play);
        } else {
            playButton.setText(R.string.pause);
        }
    }

    private void populateView() {
        if (bookSection == null) {
            return;
        }
        Book book = bookSection.getBook(getContentResolver());
        View bookView = findViewById(R.id.player);

        updateText(bookView, R.id.title, book.getTitle());
        updateText(bookView, R.id.author, book.getAuthor());
        updateText(bookView, R.id.num_sections, "" + book.getNumberSections());

        populateSectionInfo();
        if (!mIsBound || mBoundService == null) {
            populateSectionProgressInfo((int) book.getCurrentPosition());
        } else {
            populateSectionProgressInfo(mBoundService.libriGetPosition());
            sectionSeekBar.setSecondaryProgress(0);
        }
    }

    private void populateSectionInfo() {
        Book book = bookSection.getBook(getContentResolver());
        View bookView = findViewById(R.id.player);

        updateText(bookView, R.id.section, "" + bookSection.getSectionNumber());
        updateText(bookView, R.id.sectionTitle, bookSection.getTitle());
        String author = bookSection.getAuthor();
        updateText(bookView, R.id.sectionAuthor, author);

        Duration sectionDuration = bookSection.getDuration();
        sectionSeekBar.setMax(sectionDuration.getTotalMilliseconds());
        updateText(bookView, R.id.duration, sectionDuration.toString());

        Duration bookDuration = book.getDuration(getContentResolver());
        updateText(bookView, R.id.bookDuration, bookDuration.toString());

        bookProgressBar.setMax(bookDuration.getTotalMilliseconds());
        bookProgressBar.setProgress(bookSection.getBookDurationAtStart(getContentResolver()).getTotalMilliseconds());
    }

    private void populateSectionProgressInfo(int positionMs) {
        sectionSeekBar.setProgress(positionMs);

        Duration duration = new Duration(0, 0, positionMs / MS_IN_SECOND);
        sectionProgressText.setText(duration.toString());
        Duration remaining = bookSection.getDuration().subtract(duration);
        sectionRemainingText.setText(remaining.toString());

        Duration bookDuration = bookSection.getBookDurationAtStart(getContentResolver()).add(duration);
        bookPositionText.setText(bookDuration.toString());
        Duration totalBookDuration = bookSection.getBook(getContentResolver()).getDuration(getContentResolver());
        bookRemainingText.setText(totalBookDuration.subtract(bookDuration).toString());

        bookProgressBar.setProgress(bookDuration.getTotalMilliseconds());
    }

    private void updateText(View parent, int resId, String value) {
        TextView textView = (TextView) parent.findViewById(resId);
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            Spanned markUp = Html.fromHtml(value);
            textView.setText(markUp.toString());
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            LogHelper.info("PLayer", "onServiceConnected");
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((LibriDroidPlayerService.LocalBinder) service).getService();

            setProgressUpdater();

            bookSection = mBoundService.getBookSection();
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    populateView();
                    setPlayLabel();
                }
            };
            runOnUiThread(runnable);

        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            LogHelper.info("PLayer", "onServiceDisconnected");
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

    private void setProgressUpdater() {
        progressUpdater = new PlayerProgressUpdater();
        mBoundService.setProgressUpdater(progressUpdater);
    }

    void doBindService(Runnable runAfterBind) {
        LogHelper.info("PLayer", "doBindService");
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(PlayerActivity.this, LibriDroidPlayerService.class);
        mIsBound = bindService(intent, mConnection, 0);
        if (runAfterBind != null) {
            runAfterBind.run();
        }
    }

    void doUnbindService() {
        LogHelper.info("PLayer", "doUnbindService");
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogHelper.info("Player", "onDestroy");
        doUnbindService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogHelper.info("PLayerActivity", "onPause");
        if (mBoundService != null) {
            mBoundService.setProgressUpdater(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsBound || mBoundService == null) {
            LogHelper.info("PLayerActivity", "onResume - no bound service- binding");
            doBindService(postBind);
        } else {
            LogHelper.info("PLayerActivity", "onResume - set updater");
            bookSection = mBoundService.getBookSection();
            setProgressUpdater();
        }
        populateView();
        setPlayLabel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return MenuHelper.onCreateOptionsMenu(menu, true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MenuHelper.MENU_SHARE_ID) {
            Book book = bookSection.getBook(getContentResolver());
            BookShareHelper.share(this, book);
            return true;
        }
        return MenuHelper.onOptionsItemSelected(this, item);
    }

    @Override
    public boolean onSearchRequested() {
        return MenuHelper.onSearchRequested(this);
    }

    private final class SectionSeekBarListener implements OnSeekBarChangeListener {
        private int progress;
        private boolean wasPlaying;

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (mBoundService == null) {
                return;
            }
            mBoundService.userSetPosition(progress);
            if (wasPlaying) {
                mBoundService.userPlay();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mBoundService == null) {
                return;
            }
            wasPlaying = mBoundService.libriIsPlaying();
            if (wasPlaying) {
                mBoundService.userPause();
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int updatedProgress, boolean fromUser) {
            if (!fromUser) {
                return;
            }
            progress = updatedProgress;
            progressUpdater.updateProgress(updatedProgress);
        }
    }

    private final class PlayerProgressUpdater implements ProgressUpdater {
        @Override
        public void updateProgress(final int position) {
            Runnable updateView = new Runnable() {
                @Override
                public void run() {
                    populateSectionProgressInfo(position);
                }

            };
            runOnUiThread(updateView);
        }

        @Override
        public void updateBufferProgress(final int downloaded) {
            Runnable updateView = new Runnable() {
                @Override
                public void run() {
                    sectionSeekBar.setSecondaryProgress(downloaded);
                }
            };
            runOnUiThread(updateView);
        }

        @Override
        public void updateSection(final BookSection section) {
            bookSection = section;
            Runnable updateView = new Runnable() {
                @Override
                public void run() {
                    populateSectionInfo();
                }

            };
            runOnUiThread(updateView);
        }

        @Override
        public void externallyPaused() {
            setPlayLabelOnUI();
        }

        private void setPlayLabelOnUI() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setPlayLabel();
                }
            });
        }

        @Override
        public void externalPlay() {
            setPlayLabelOnUI();
        }

        @Override
        public void waiting(final int message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (pd != null) {
                        pd.dismiss();
                    }
                    pd = ProgressDialog.show(PlayerActivity.this, "Waiting", getResources().getString(message),
                            true, true);
                }
            });
        }

        @Override
        public void stoppedWaiting() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (pd != null) {
                        pd.dismiss();
                    }
                    updateText(findViewById(R.id.player), R.id.error, "");
                    acceptedError = false;
                }
            });
        }

        @Override
        public void error(final int message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String messageString = getResources().getString(message);
                    if (!acceptedError) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
                        builder.setMessage(messageString)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        acceptedError = true;
                                    }
                                }).show();
                    }
                    updateText(findViewById(R.id.player), R.id.error, messageString);
                }
            });
        }

        @Override
        public void updateBookAndSection(BookSection section) {
            bookSection = section;
            Runnable updateView = new Runnable() {
                @Override
                public void run() {
                    populateView();
                }

            };
            runOnUiThread(updateView);
        }
    }

    @Override
    public boolean swipeLeftToRight() {
        // swipe back to the current book
        Intent intent = new Intent(PlayerActivity.this, BookViewActivity.class);
        intent.setData(bookSection.getBookUri());
        startActivity(intent);
        return true;
    }

}
