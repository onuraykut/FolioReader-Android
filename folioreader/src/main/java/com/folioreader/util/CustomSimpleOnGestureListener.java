package com.folioreader.util;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

public class CustomSimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener {

    private final CustomScrollGestureListener listener;

    public CustomSimpleOnGestureListener(CustomScrollGestureListener listener) {
        this.listener = listener;

    }

    @Override
    public boolean onScroll(@Nullable MotionEvent e1, @Nullable MotionEvent e2, float distanceX, float distanceY) {
        return listener.onScroll(distanceX, distanceY);
    }

    @Override
    public boolean onFling(@Nullable MotionEvent e1, @Nullable MotionEvent e2, float distanceX, float distanceY) {
        return listener.onFling(distanceX, distanceY);
    }
}