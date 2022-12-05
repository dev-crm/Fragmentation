package me.yokeyword.fragmentation.helper.internal

import android.content.Context
import me.yokeyword.fragmentation.anim.FragmentAnimator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import me.yokeyword.fragmentation.R

/**
 * @Hide Created by YoKeyword on 16/7/26.
 */
class AnimatorHelper(private val context: Context, fragmentAnimator: FragmentAnimator?) {
    var noneAnim: Animation = AnimationUtils.loadAnimation(context, R.anim.no_anim)
    var noneAnimFixed: Animation? = object : Animation() {}
    lateinit var enterAnim: Animation
    lateinit var exitAnim: Animation
    lateinit var popEnterAnim: Animation
    lateinit var popExitAnim: Animation
    private var fragmentAnimator: FragmentAnimator? = null

    init {
        notifyChanged(fragmentAnimator)
    }

    fun notifyChanged(fragmentAnimator: FragmentAnimator?) {
        this.fragmentAnimator = fragmentAnimator
        initEnterAnim()
        initExitAnim()
        initPopEnterAnim()
        initPopExitAnim()
    }

    fun compatChildFragmentExitAnim(fragment: Fragment): Animation? {
        if (fragment.tag != null && fragment.tag!!.startsWith("android:switcher:") && fragment.userVisibleHint ||
            fragment.parentFragment != null && fragment.parentFragment!!.isRemoving && !fragment.isHidden
        ) {
            val animation: Animation = object : Animation() {}
            animation.duration = exitAnim.duration
            return animation
        }
        return null
    }

    private fun initEnterAnim(): Animation {
        enterAnim = if (fragmentAnimator!!.enter == 0) {
            AnimationUtils.loadAnimation(context, R.anim.no_anim)
        } else {
            AnimationUtils.loadAnimation(context, fragmentAnimator!!.enter)
        }
        return enterAnim
    }

    private fun initExitAnim(): Animation {
        exitAnim = if (fragmentAnimator!!.exit == 0) {
            AnimationUtils.loadAnimation(context, R.anim.no_anim)
        } else {
            AnimationUtils.loadAnimation(context, fragmentAnimator!!.exit)
        }
        return exitAnim
    }

    private fun initPopEnterAnim(): Animation {
        popEnterAnim = if (fragmentAnimator!!.popEnter == 0) {
            AnimationUtils.loadAnimation(context, R.anim.no_anim)
        } else {
            AnimationUtils.loadAnimation(context, fragmentAnimator!!.popEnter)
        }
        return popEnterAnim
    }

    private fun initPopExitAnim(): Animation {
        popExitAnim = if (fragmentAnimator!!.popExit == 0) {
            AnimationUtils.loadAnimation(context, R.anim.no_anim)
        } else {
            AnimationUtils.loadAnimation(context, fragmentAnimator!!.popExit)
        }
        return popExitAnim
    }
}