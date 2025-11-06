package com.folioreader.util

import android.view.MotionEvent

interface CustomScrollGestureListener {
    fun onScroll(distanceX: Float, distanceY: Float): Boolean
    fun onFling(distanceX: Float, distanceY: Float): Boolean
    fun onDown(event: MotionEvent): Boolean

}
