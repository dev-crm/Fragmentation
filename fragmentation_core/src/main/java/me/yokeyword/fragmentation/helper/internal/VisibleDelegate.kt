package me.yokeyword.fragmentation.helper.internal

import me.yokeyword.fragmentation.ISupportFragment
import android.os.MessageQueue.IdleHandler
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment

/**
 * Created by YoKey on 17/4/4.
 * Modify by JantHsuesh on 20/06/02
 */
class VisibleDelegate(private val mSupportF: ISupportFragment) {
    // SupportVisible相关
    var isSupportVisible = false
        private set
    private var mNeedDispatch = true
    private var mVisibleWhenLeave = true

    //true = 曾经可见，也就是onLazyInitView 执行过一次
    private var mIsOnceVisible = false
    private var mFirstCreateViewCompatReplace = true
    private var mAbortInitVisible = false
    private var mIdleDispatchSupportVisible: IdleHandler? = null
    private var mSaveInstanceState: Bundle? = null
    private val mFragment: Fragment = mSupportF as Fragment

    fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            mSaveInstanceState = savedInstanceState
            // setUserVisibleHint() may be called before onCreate()
            mVisibleWhenLeave = savedInstanceState.getBoolean(
                FRAGMENTATION_STATE_SAVE_IS_INVISIBLE_WHEN_LEAVE
            )
            mFirstCreateViewCompatReplace = savedInstanceState.getBoolean(
                FRAGMENTATION_STATE_SAVE_COMPAT_REPLACE
            )
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(FRAGMENTATION_STATE_SAVE_IS_INVISIBLE_WHEN_LEAVE, mVisibleWhenLeave)
        outState.putBoolean(FRAGMENTATION_STATE_SAVE_COMPAT_REPLACE, mFirstCreateViewCompatReplace)
    }

    fun onActivityCreated(savedInstanceState: Bundle?) {
        if (!mFirstCreateViewCompatReplace && mFragment.tag != null && mFragment.tag!!.startsWith("android:switcher:")) {
            return
        }
        if (mFirstCreateViewCompatReplace) {
            mFirstCreateViewCompatReplace = false
        }
        initVisible()
    }

    private fun initVisible() {
        if (mVisibleWhenLeave && isFragmentVisible(mFragment)) {
            if (mFragment.parentFragment == null || isFragmentVisible(mFragment.parentFragment)) {
                mNeedDispatch = false
                enqueueDispatchVisible()
            }
        }
    }

    fun onResume() {
        if (mIsOnceVisible) {
            if (!isSupportVisible && mVisibleWhenLeave && isFragmentVisible(mFragment)) {
                mNeedDispatch = false
                enqueueDispatchVisible()
            }
        } else {
            if (mAbortInitVisible) {
                mAbortInitVisible = false
                initVisible()
            }
        }
    }

    fun onPause() {
        //界面还没有执行到initVisible 发出的任务taskDispatchSupportVisible，界面就已经pause。
        //为了让下次resume 时候，能正常的执行需要设置mAbortInitVisible ，来确保在resume的时候，可以执行完整initVisible
        if (mIdleDispatchSupportVisible != null) {
            Looper.myQueue().removeIdleHandler(mIdleDispatchSupportVisible!!)
            mAbortInitVisible = true
            return
        }
        if (isSupportVisible && isFragmentVisible(mFragment)) {
            mNeedDispatch = false
            mVisibleWhenLeave = true
            dispatchSupportVisible(false)
        } else {
            mVisibleWhenLeave = false
        }
    }

    fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && !mFragment.isResumed) {
            //Activity 不是resumed 状态，不用显示其下的fragment，只需设置标志位，待OnResume时 显示出来
            //if fragment is shown but not resumed, ignore...
            onFragmentShownWhenNotResumed()
            return
        }
        if (hidden) {
            dispatchSupportVisible(false)
        } else {
            safeDispatchUserVisibleHint(true)
        }
    }

    private fun onFragmentShownWhenNotResumed() {
        //fragment 需要显示，但是Activity状态不是resumed，下次resumed的时候 fragment 需要显示， 所以可以认为离开的时候可见
        mVisibleWhenLeave = true
        mAbortInitVisible = true
        dispatchChildOnFragmentShownWhenNotResumed()
    }

    private fun dispatchChildOnFragmentShownWhenNotResumed() {
        val fragmentManager = mFragment.childFragmentManager
        val childFragments = fragmentManager.fragments
        for (child in childFragments) {
            if (child is ISupportFragment && !child.isHidden && child.userVisibleHint) {
                (child as ISupportFragment).supportDelegate.mVisibleDelegate
                    .onFragmentShownWhenNotResumed()
            }
        }
    }

    fun onDestroyView() {
        mIsOnceVisible = false
    }

    fun setUserVisibleHint(isVisibleToUser: Boolean) {
        if (mFragment.isResumed || !mFragment.isAdded && isVisibleToUser) {
            if (!isSupportVisible && isVisibleToUser) {
                safeDispatchUserVisibleHint(true)
            } else if (isSupportVisible && !isVisibleToUser) {
                dispatchSupportVisible(false)
            }
        }
    }

    private fun safeDispatchUserVisibleHint(visible: Boolean) {
        if (visible) {
            enqueueDispatchVisible()
        } else {
            if (mIsOnceVisible) {
                dispatchSupportVisible(false)
            }
        }
    }

    private fun enqueueDispatchVisible() {
        mIdleDispatchSupportVisible = IdleHandler {
            dispatchSupportVisible(true)
            mIdleDispatchSupportVisible = null
            false
        }
        mIdleDispatchSupportVisible?.apply {
            Looper.myQueue().addIdleHandler(this)
        }
    }

    private fun dispatchSupportVisible(visible: Boolean) {
        if (visible && isParentInvisible) return
        if (isSupportVisible == visible) {
            mNeedDispatch = true
            return
        }
        isSupportVisible = visible
        if (visible) {
            if (checkAddState()) return
            mSupportF.onSupportVisible()
            if (!mIsOnceVisible) {
                mIsOnceVisible = true
                mSupportF.onLazyInitView(mSaveInstanceState)
            }
            dispatchChild(true)
        } else {
            dispatchChild(false)
            mSupportF.onSupportInvisible()
        }
    }

    private fun dispatchChild(visible: Boolean) {
        if (mNeedDispatch) {
            if (checkAddState()) return
            val fragmentManager = mFragment.childFragmentManager
            val childFragments = fragmentManager.fragments
            for (child in childFragments) {
                if (child is ISupportFragment && !child.isHidden && child.userVisibleHint) {
                    (child as ISupportFragment).supportDelegate.mVisibleDelegate
                        .dispatchSupportVisible(visible)
                }
            }
        } else {
            mNeedDispatch = true
        }
    }

    private val isParentInvisible: Boolean
        get() {
            val parentFragment = mFragment.parentFragment
            return if (parentFragment is ISupportFragment) {
                !(parentFragment as ISupportFragment).isSupportVisible
            } else parentFragment != null && !parentFragment.isVisible
        }

    private fun checkAddState(): Boolean {
        if (!mFragment.isAdded) {
            isSupportVisible = !isSupportVisible
            return true
        }
        return false
    }

    private fun isFragmentVisible(fragment: Fragment?): Boolean {
        return !fragment!!.isHidden && fragment.userVisibleHint
    }

    companion object {
        private const val FRAGMENTATION_STATE_SAVE_IS_INVISIBLE_WHEN_LEAVE =
            "fragmentation_invisible_when_leave"
        private const val FRAGMENTATION_STATE_SAVE_COMPAT_REPLACE = "fragmentation_compat_replace"
    }
}