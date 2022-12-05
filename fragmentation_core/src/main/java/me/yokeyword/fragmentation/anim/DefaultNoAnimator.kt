package me.yokeyword.fragmentation.anim

import android.os.Parcelable

/**
 * Created by YoKeyword on 16/2/15.
 */
class DefaultNoAnimator : FragmentAnimator(), Parcelable {
    init {
        enter = 0
        exit = 0
        popEnter = 0
        popExit = 0
    }
}