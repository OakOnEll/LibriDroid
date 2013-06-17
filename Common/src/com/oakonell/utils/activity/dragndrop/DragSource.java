/*
 * This is a modified version of a class from the Android Open Source Project. 
 * The original copyright and license information follows.
 * 
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oakonell.utils.activity.dragndrop;

import android.view.View;

/**
 * This and other classes in this package are borrowed and possibly modified
 * from Bill Lahti’s blog
 * (https://blahti.wordpress.com/2011/10/03/drag-drop-for-android-gridview/)
 * 
 * Interface defining an object where drag operations originate.
 */
public interface DragSource {

    /**
     * This method is called to determine if the DragSource has something to
     * drag.
     * 
     * @return True if there is something to drag
     */

    boolean allowDrag();

    /**
     * This method is used to tell the DragSource which drag controller it is
     * working with.
     * 
     * @param dragger
     *            DragController
     */

    void setDragController(DragController dragger);

    /**
     * This method is called on the completion of the drag operation so the
     * DragSource knows whether it succeeded or failed.
     * 
     * @param target
     *            View - the view that accepted the dragged object
     * @param success
     *            boolean - true means that the object was dropped successfully
     */

    void onDropCompleted(View target, boolean success);
}
