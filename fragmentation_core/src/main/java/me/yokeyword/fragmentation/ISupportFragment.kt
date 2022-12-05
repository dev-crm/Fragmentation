package me.yokeyword.fragmentation

import android.os.Bundle
import androidx.annotation.IntDef
import me.yokeyword.fragmentation.anim.FragmentAnimator

/**
 * Created by YoKey on 17/6/23.
 */
interface ISupportFragment {

    @IntDef(STANDARD, SINGLE_TOP, SINGLE_TASK)
    @Retention(AnnotationRetention.SOURCE)
    annotation class LaunchMode

    val supportDelegate: SupportFragmentDelegate
    fun extraTransaction(): ExtraTransaction?
    fun post(runnable: Runnable?)
    fun onEnterAnimationEnd(savedInstanceState: Bundle?)
    fun onLazyInitView(savedInstanceState: Bundle?)
    fun onSupportVisible()
    fun onSupportInvisible()
    val isSupportVisible: Boolean
    fun onCreateFragmentAnimator(): FragmentAnimator
    var fragmentAnimator: FragmentAnimator?
    fun setFragmentResult(resultCode: Int, bundle: Bundle?)
    fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?)
    fun onNewBundle(args: Bundle?)
    fun putNewBundle(newBundle: Bundle?)
    fun onBackPressedSupport(): Boolean

    companion object {
        // LaunchMode
        const val STANDARD = 0
        const val SINGLE_TOP = 1
        const val SINGLE_TASK = 2

        // ResultCode
        const val RESULT_CANCELED = 0
        const val RESULT_OK = -1
    }
}