package me.yokeyword.fragmentation.anim

import android.os.Parcelable
import me.yokeyword.fragmentation.R

/**
 * Created by YoKeyword on 16/2/5.
 */
class DefaultVerticalAnimator : FragmentAnimator(), Parcelable {
    init {
        enter = R.anim.v_fragment_enter
        exit = R.anim.v_fragment_exit
        popEnter = R.anim.v_fragment_pop_enter
        popExit = R.anim.v_fragment_pop_exit
    }

}