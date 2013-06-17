package com.oakonell.utils.activity.dragndrop;

import android.view.View;

public interface OnDropListener {
    /**
     * Handle an object being dropped on the DropTarget
     * 
     * @param source
     *            DragSource where the drag started
     * @param x
     *            X coordinate of the drop location
     * @param y
     *            Y coordinate of the drop location
     * @param xOffset
     *            Horizontal offset with the object being dragged where the
     *            original touch happened
     * @param yOffset
     *            Vertical offset with the object being dragged where the
     *            original touch happened
     * @param dragView
     *            The DragView that's being dragged around on screen.
     * @param dragInfo
     *            Data associated with the object being dragged
     * 
     */
    void onDrop(View target, DragSource source, int x, int y, int xOffset, int yOffset, DragView dragView,
            Object dragInfo);
}
