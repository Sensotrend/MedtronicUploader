package info.nightscout.android.medtronic;

import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.mikepenz.materialdrawer.Drawer;

import java.util.concurrent.atomic.AtomicInteger;

final class DrawerEnabler {
    private DrawerEnabler() {}

    static void make(
            Context context,
            Drawer drawer,
            final View activatingView,
            View rootView,
            final AtomicInteger clickCount) {

        final DrawerLayout drawerLayout = drawer.getDrawerLayout();
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        final GestureDetectorCompat touchListener = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (clickCount.incrementAndGet() == 10) {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                    activatingView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                return true;
            }
        });

        activatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return touchListener.onTouchEvent(event);
            }
        });
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                clickCount.set(0);
                return false;
            }
        });
    }
}
