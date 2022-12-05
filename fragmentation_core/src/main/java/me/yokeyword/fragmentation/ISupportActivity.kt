package me.yokeyword.fragmentation

import me.yokeyword.fragmentation.anim.FragmentAnimator
import java.lang.Runnable
import android.view.MotionEvent

/**
 * Created by YoKey on 17/6/13.
 */
interface ISupportActivity {
    val supportDelegate: SupportActivityDelegate
    fun extraTransaction(): ExtraTransaction?
    var fragmentAnimator: FragmentAnimator
    fun onCreateFragmentAnimator(): FragmentAnimator?
    fun post(runnable: Runnable?)
    fun onBackPressed()
    fun onBackPressedSupport()
    fun dispatchTouchEvent(ev: MotionEvent?): Boolean
}