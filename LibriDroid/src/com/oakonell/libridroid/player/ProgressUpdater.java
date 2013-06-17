package com.oakonell.libridroid.player;

import com.oakonell.libridroid.impl.BookSection;

public interface ProgressUpdater {
    void updateProgress(int position);

    void updateBufferProgress(int downloaded);

    void updateSection(BookSection section);

    void externallyPaused();

    void waiting(int message);

    void stoppedWaiting();

    void error(int message);

    void externalPlay();

    void updateBookAndSection(BookSection currentSection);
}
