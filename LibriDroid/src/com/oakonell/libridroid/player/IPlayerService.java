package com.oakonell.libridroid.player;

import android.content.Context;

public interface IPlayerService {

    void libriPause(boolean releaseResources, boolean releasePlayer, boolean updatePosition);

    boolean libriPlay();

    boolean libriIsPlaying();

    void libriSkipForward();

    void libriSkipBackward();

    void libriSkipBackward(int skipBack, boolean externalPauseUpdate);

    Context getApplicationContext();

}
