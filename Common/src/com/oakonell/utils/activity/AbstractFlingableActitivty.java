package com.oakonell.utils.activity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;

import com.oakonell.utils.R;

public abstract class AbstractFlingableActitivty extends Activity {
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    private static final int MIN_SDK_VERSION_FOR_OVERRIDE_TRANSITION = 5;

    private final Method overridePendingTransitionMethod;
    private GestureDetector gestureDetector;

    public AbstractFlingableActitivty() {
        super();
        Method method = null;
        if (android.os.Build.VERSION.SDK_INT < MIN_SDK_VERSION_FOR_OVERRIDE_TRANSITION) {
            method = null;
        } else {
            try {
                method = getClass().getMethod("overridePendingTransition",
                        Integer.TYPE,
                        Integer.TYPE);
            } catch (SecurityException e) {
                throw new RuntimeException("Unable to get overridePendingTransition method", e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Unable to get overridePendingTransition method", e);
            }
        }
        overridePendingTransitionMethod = method;
    }

    public boolean swipeRightToLeft() {
        return false;
    }

    public boolean swipeLeftToRight() {
        return false;
    }

    protected final void installFlingHandler(View mainview) {
        gestureDetector = new GestureDetector(new MyGestureDetector());

        // Set the touch listener for the main view to be our custom gesture
        // listener
        mainview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                return false;
            }
        });
    }

    void backwardsCompatibleOverridePendingTransition(int resLeft, int resRight) {
        if (overridePendingTransitionMethod == null) {
            return;
        }

        // call the method via reflection
        try {
            overridePendingTransitionMethod.invoke(this, resLeft, resRight);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to invoke overridePendingTransition method", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Unable to invoke overridePendingTransition method", e);
        }
    }

    final class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                return false;
            }

            // right to left swipe
            if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                if (swipeRightToLeft()) {
                    transitionToLeft();
                }
                // left to right swipe
            } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                if (swipeLeftToRight()) {
                    transitionToRight();
                }
            }

            return false;
        }

        // It is necessary to return true from onDown for the onFling event to
        // register
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }

    protected final void transitionToRight() {
        backwardsCompatibleOverridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right);
    }

    protected final void transitionToLeft() {
        backwardsCompatibleOverridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left);
    }

}
