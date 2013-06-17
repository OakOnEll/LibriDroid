package com.oakonell.utils.activity.dragndrop;

import android.view.View;

public interface OnDragListener {
    /**
     * React to something started to be dragged.
     */
    void onDragEnter(View target, DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo);

    /**
     * React to something being dragged over the drop target.
     */
    void onDragOver(View target, DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo);

    /**
     * React to a drag
     */
    void onDragExit(View target, DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo);

}
