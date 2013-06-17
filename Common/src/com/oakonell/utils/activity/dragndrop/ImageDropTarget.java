package com.oakonell.utils.activity.dragndrop;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/**
 * This and other classes in this package are borrowed and possibly modified
 * from Bill Lahti’s blog
 * (https://blahti.wordpress.com/2011/10/03/drag-drop-for-android-gridview/)
 * 
 * 
 * This class describes an area within a DragLayer where a dragged item can be
 * dropped in order to Perform some action. It is a subclass of ImageView so it
 * is easy to make the area appear as an icon or whatever you like.
 * 
 * <p>
 * The default implementation assumes that the ImageView supports image levels.
 * Image level 1 is the normal view. Level 2 is for use when the ImageDropTarget
 * has a dragged object over it. To change that behavior, override methods
 * onDragEnter and onDragExit.
 * 
 */

public class ImageDropTarget extends ImageView
        implements DropTarget {

    private DragController mDragController;
    private OnDropListener dropListener;
    private OnDragListener dragListener;

    public ImageDropTarget(Context context) {
        super(context);
    }

    public ImageDropTarget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageDropTarget(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
    }

    /**
     * Get the value of the DragController property.
     * 
     * @return DragController
     */

    public DragController getDragController() {
        return mDragController;
    }

    /**
     * Set the value of the DragController property.
     * 
     * @param newValue
     *            DragController
     */

    public void setDragController(DragController newValue) {
        mDragController = newValue;
    }

    // DropTarget interface implementation

    /**
     * Handle an object being dropped on the DropTarget. For a ImageDropTarget,
     * we don't really do anything because we want the view being dragged to
     * vanish.
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
    @Override
    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (isEnabled()) {
            if (dropListener != null) {
                dropListener.onDrop(this, source, x, y, xOffset, yOffset, dragView, dragInfo);
            }
        }

    }

    public void setOnDropListener(OnDropListener listener) {
        dropListener = listener;
    }

    public void setOnDragListener(OnDragListener listener) {
        dragListener = listener;
    }

    /**
     * React to a dragged object entering the area of this ImageDropTarget.
     * Provide the user with some visual feedback.
     */
    @Override
    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        // Set the image level so the image is highlighted;
        if (isEnabled()) {
            setImageLevel(2);
            if (dragListener != null) {
                dragListener.onDragEnter(this, source, x, y, xOffset, yOffset, dragView, dragInfo);
            }
        }
    }

    /**
     * React to something being dragged over the drop target.
     */
    @Override
    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (dragListener != null) {
            dragListener.onDragOver(this, source, x, y, xOffset, yOffset, dragView, dragInfo);
        }
    }

    /**
     * React to a dragged object leaving the area of this ImageDropTarget.
     * Provide the user with some visual feedback.
     */
    @Override
    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (isEnabled()) {
            setImageLevel(1);
            if (dragListener != null) {
                dragListener.onDragExit(this, source, x, y, xOffset, yOffset, dragView, dragInfo);
            }
        }
    }

    /**
     * Check if a drop action can occur at, or near, the requested location.
     * This may be called repeatedly during a drag, so any calls should return
     * quickly.
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
     * @return True if the drop will be accepted, false otherwise.
     */
    @Override
    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return isEnabled();
    }

    /**
     * Estimate the surface area where this object would land if dropped at the
     * given location.
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
     * @param recycle
     *            {@link Rect} object to be possibly recycled.
     * @return Estimated area that would be occupied if object was dropped at
     *         the given location. Should return null if no estimate is found,
     *         or if this target doesn't provide estimations.
     */
    @Override
    public Rect estimateDropLocation(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo, Rect recycle) {
        return null;
    }

    /**
 */
    // Methods

    /**
     * Return true if this ImageDropTarget is enabled. If it is, it means that
     * it will accept dropped views.
     * 
     * @return boolean
     */

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && (getVisibility() == View.VISIBLE);
    }

    /**
     * Set up the drop spot by connecting it to a drag controller. When this
     * method completes, the drop spot is listed as one of the drop targets of
     * the controller.
     * 
     * @param controller
     *            DragController
     */

    public void setup(DragController controller) {
        mDragController = controller;

        if (controller != null) {
            controller.addDropTarget(this);
        }
    }
}
