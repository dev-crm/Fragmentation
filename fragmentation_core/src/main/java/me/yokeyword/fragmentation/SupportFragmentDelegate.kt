package me.yokeyword.fragmentation

//noinspection SuspiciousImport
import android.R
import me.yokeyword.fragmentation.anim.FragmentAnimator
import me.yokeyword.fragmentation.helper.internal.AnimatorHelper
import me.yokeyword.fragmentation.helper.internal.TransactionRecord
import me.yokeyword.fragmentation.helper.internal.VisibleDelegate
import android.os.Bundle
import java.lang.RuntimeException
import me.yokeyword.fragmentation.ExtraTransaction.ExtraTransactionImpl
import android.view.animation.Animation
import java.lang.Runnable
import android.view.View
import me.yokeyword.fragmentation.helper.internal.ResultRecord
import android.app.Activity
import kotlin.jvm.JvmOverloads
import me.yokeyword.fragmentation.ISupportFragment.LaunchMode
import java.lang.Class
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import java.lang.Exception

class SupportFragmentDelegate(support: ISupportFragment?) {

    private var mRootStatus = STATUS_UN_ROOT
    private var mIsSharedElement = false
    var mFragmentAnimator: FragmentAnimator? = null
    var mAnimHelper: AnimatorHelper

    @JvmField
    var mLockAnim = false
    private var mCustomEnterAnim = Int.MIN_VALUE
    private var mCustomExitAnim = Int.MIN_VALUE
    private var mCustomPopExitAnim = Int.MIN_VALUE

    private var mFirstCreateView = true
    private var mReplaceMode = false
    private var mIsHidden = true

    @JvmField
    var mContainerId = 0
    private var mTransactionDelegate: TransactionDelegate

    @JvmField
    var mTransactionRecord: TransactionRecord? = null

    // SupportVisible
    var mVisibleDelegate: VisibleDelegate

    @JvmField
    var mNewBundle: Bundle? = null
    private var mSaveInstanceState: Bundle? = null
    private val mSupportF: ISupportFragment
    private val mFragment: Fragment
    var activity: FragmentActivity

    private var mSupportA: ISupportActivity
    var mAnimByActivity = true

    @JvmField
    var mEnterAnimListener: EnterAnimListener? = null
    private var mRootViewClickable = false

    private var mHandler: Handler

    init {
        if (support !is Fragment) throw RuntimeException("Must extends Fragment")
        mSupportF = support
        mFragment = support
        activity = mFragment.requireActivity()
        mAnimHelper = AnimatorHelper(activity.applicationContext, mFragmentAnimator)

        if (activity !is ISupportActivity) throw RuntimeException(activity.javaClass.simpleName + " must impl ISupportActivity!")
        mSupportA = activity as ISupportActivity
        mTransactionDelegate = mSupportA.supportDelegate.mTransactionDelegate

        mHandler = Handler(Looper.getMainLooper())

        mVisibleDelegate = VisibleDelegate(mSupportF)
    }

    /**
     * Perform some extra transactions.
     * 额外的事务：自定义Tag，添加SharedElement动画，操作非回退栈Fragment
     */
    fun extraTransaction(): ExtraTransaction {
        return ExtraTransactionImpl(
            mSupportA as FragmentActivity,
            mSupportF,
            mTransactionDelegate,
            false
        )
    }

    fun onAttach() {

    }

    fun onCreate(savedInstanceState: Bundle?) {
        mVisibleDelegate.onCreate(savedInstanceState)
        mFragment.requireArguments().apply {
            mRootStatus =
                getInt(TransactionDelegate.FRAGMENTATION_ARG_ROOT_STATUS, STATUS_UN_ROOT)
            mIsSharedElement =
                getBoolean(TransactionDelegate.FRAGMENTATION_ARG_IS_SHARED_ELEMENT, false)
            mContainerId = getInt(TransactionDelegate.FRAGMENTATION_ARG_CONTAINER)
            mReplaceMode = getBoolean(TransactionDelegate.FRAGMENTATION_ARG_REPLACE, false)
            mCustomEnterAnim = getInt(
                TransactionDelegate.FRAGMENTATION_ARG_CUSTOM_ENTER_ANIM,
                Int.MIN_VALUE
            )
            mCustomExitAnim =
                getInt(TransactionDelegate.FRAGMENTATION_ARG_CUSTOM_EXIT_ANIM, Int.MIN_VALUE)
            mCustomPopExitAnim = getInt(
                TransactionDelegate.FRAGMENTATION_ARG_CUSTOM_POP_EXIT_ANIM,
                Int.MIN_VALUE
            )
        }

        if (savedInstanceState == null) {
            fragmentAnimator
        } else {
            savedInstanceState.classLoader = javaClass.classLoader
            mSaveInstanceState = savedInstanceState
            mFragmentAnimator =
                savedInstanceState.getParcelable(TransactionDelegate.FRAGMENTATION_STATE_SAVE_ANIMATOR)
            mIsHidden =
                savedInstanceState.getBoolean(TransactionDelegate.FRAGMENTATION_STATE_SAVE_IS_HIDDEN)
            mContainerId =
                savedInstanceState.getInt(TransactionDelegate.FRAGMENTATION_ARG_CONTAINER)
        }

        val enter = enterAnim ?: return
        enterAnim?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                mSupportA.supportDelegate.mFragmentClickable = false
                // 开启防抖动
                mHandler.postDelayed(
                    { mSupportA.supportDelegate.mFragmentClickable = true },
                    enter.duration
                )
            }

            override fun onAnimationEnd(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (mSupportA.supportDelegate.mPopMultipleNoAnim || mLockAnim) {
            return if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE && enter) {
                mAnimHelper.noneAnimFixed
            } else mAnimHelper.noneAnim
        }
        return if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            if (enter) {
                val enterAnim: Animation
                if (mRootStatus == STATUS_ROOT_ANIM_DISABLE) {
                    enterAnim = mAnimHelper.noneAnim
                } else {
                    enterAnim = mAnimHelper.enterAnim
                    fixAnimationListener(enterAnim)
                }
                enterAnim
            } else {
                mAnimHelper.popExitAnim
            }
        } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE) {
            if (enter) mAnimHelper.popEnterAnim else mAnimHelper.exitAnim
        } else {
            if (mIsSharedElement && enter) {
                compatSharedElements()
            }
            if (!enter) {
                mAnimHelper.compatChildFragmentExitAnim(mFragment)
            } else null
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        mVisibleDelegate.onSaveInstanceState(outState)
        outState.putParcelable(
            TransactionDelegate.FRAGMENTATION_STATE_SAVE_ANIMATOR,
            mFragmentAnimator
        )
        outState.putBoolean(
            TransactionDelegate.FRAGMENTATION_STATE_SAVE_IS_HIDDEN,
            mFragment.isHidden
        )
        outState.putInt(TransactionDelegate.FRAGMENTATION_ARG_CONTAINER, mContainerId)
    }

    fun onActivityCreated(savedInstanceState: Bundle?) {
        mVisibleDelegate.onActivityCreated(savedInstanceState)
        val view = mFragment.view
        if (view != null) {
            mRootViewClickable = view.isClickable
            view.isClickable = true
            setBackground(view)
        }
        if (savedInstanceState != null || mRootStatus == STATUS_ROOT_ANIM_DISABLE || mFragment.tag != null && mFragment.tag!!.startsWith(
                "android:switcher:"
            )
            || mReplaceMode && !mFirstCreateView
        ) {
            notifyEnterAnimEnd()
        } else if (mCustomEnterAnim != Int.MIN_VALUE) {
            fixAnimationListener(
                if (mCustomEnterAnim == 0) mAnimHelper.noneAnim else AnimationUtils.loadAnimation(
                    activity, mCustomEnterAnim
                )
            )
        }
        if (mFirstCreateView) {
            mFirstCreateView = false
        }
    }

    fun onResume() {
        mVisibleDelegate.onResume()
    }

    fun onPause() {
        mVisibleDelegate.onPause()
    }

    fun onDestroyView() {
        mSupportA.supportDelegate.mFragmentClickable = true
        mVisibleDelegate.onDestroyView()
        mHandler.removeCallbacks(mNotifyEnterAnimEndRunnable)
    }

    fun onDestroy() {
        mTransactionDelegate.handleResultRecord(mFragment)
    }

    fun onHiddenChanged(hidden: Boolean) {
        mVisibleDelegate.onHiddenChanged(hidden)
    }

    fun setUserVisibleHint(isVisibleToUser: Boolean) {
        mVisibleDelegate.setUserVisibleHint(isVisibleToUser)
    }

    /**
     * Causes the Runnable r to be added to the action queue.
     *
     *
     * The runnable will be run after all the previous action has been run.
     *
     *
     * 前面的事务全部执行后 执行该Action
     */
    fun post(runnable: Runnable?) {
        mTransactionDelegate.post(runnable)
    }

    /**
     * Called when the enter-animation end.
     * 入栈动画 结束时,回调
     */
    fun onEnterAnimationEnd(savedInstanceState: Bundle?) {}

    /**
     * Lazy initial，Called when fragment is first visible.
     *
     *
     * 同级下的 懒加载 ＋ ViewPager下的懒加载  的结合回调方法
     */
    fun onLazyInitView(savedInstanceState: Bundle?) {}

    /**
     * Called when the fragment is visible.
     *
     *
     * 当Fragment对用户可见时回调
     *
     *
     * Is the combination of  [onHiddenChanged() + onResume()/onPause() + setUserVisibleHint()]
     */
    fun onSupportVisible() {}

    /**
     * Called when the fragment is invivible.
     *
     *
     * Is the combination of  [onHiddenChanged() + onResume()/onPause() + setUserVisibleHint()]
     */
    fun onSupportInvisible() {}

    /**
     * Return true if the fragment has been supportVisible.
     */
    val isSupportVisible: Boolean = mVisibleDelegate.isSupportVisible

    /**
     * Set fragment animation with a higher priority than the ISupportActivity
     * 设定当前Fragment动画,优先级比在ISupportActivity里高
     */
    fun onCreateFragmentAnimator(): FragmentAnimator {
        return mSupportA.fragmentAnimator
    }

    /**
     * Set the fragment animation.
     */
    var fragmentAnimator: FragmentAnimator?
        get() {
            if (mFragmentAnimator == null) {
                mFragmentAnimator = mSupportF.onCreateFragmentAnimator()
                if (mFragmentAnimator == null) {
                    mFragmentAnimator = mSupportA.fragmentAnimator
                }
            }
            return mFragmentAnimator
        }
        set(fragmentAnimator) {
            mFragmentAnimator = fragmentAnimator
            mAnimHelper.notifyChanged(fragmentAnimator)
            mAnimByActivity = false
        }

    /**
     * 类似 [Activity.setResult]
     *
     *
     * Similar to [Activity.setResult]
     *
     * @see .startForResult
     */
    fun setFragmentResult(resultCode: Int, bundle: Bundle?) {
        val args = mFragment.requireArguments()
        if (!args.containsKey(TransactionDelegate.FRAGMENTATION_ARG_RESULT_RECORD)) {
            return
        }
        val resultRecord =
            args.getParcelable<ResultRecord>(TransactionDelegate.FRAGMENTATION_ARG_RESULT_RECORD)
        if (resultRecord != null) {
            resultRecord.resultCode = resultCode
            resultRecord.resultBundle = bundle
        }
    }

    /**
     * 类似  [Activity.onActivityResult]
     *
     *
     * Similar to [Activity.onActivityResult]
     *
     * @see .startForResult
     */
    fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {}

    /**
     * 在start(TargetFragment,LaunchMode)时,启动模式为SingleTask/SingleTop, 回调TargetFragment的该方法
     * 类似 [Activity.onNewIntent]
     *
     *
     * Similar to [Activity.onNewIntent]
     *
     * @param args putNewBundle(Bundle newBundle)
     * @see .start
     */
    fun onNewBundle(args: Bundle?) {}

    /**
     * 添加NewBundle,用于启动模式为SingleTask/SingleTop时
     *
     * @see .start
     */
    fun putNewBundle(newBundle: Bundle?) {
        mNewBundle = newBundle
    }

    /**
     * Back Event
     *
     * @return false则继续向上传递, true则消费掉该事件
     */
    fun onBackPressedSupport(): Boolean {
        return false
    }
    /** */
    /**
     * 隐藏软键盘
     */
    fun hideSoftInput() {
        val activity = mFragment.activity ?: return
        val view = activity.window.decorView
        SupportHelper.hideSoftInput(view)
    }

    /**
     * 显示软键盘,调用该方法后,会在onPause时自动隐藏软键盘
     */
    fun showSoftInput(view: View?) {
        SupportHelper.showSoftInput(view)
    }

    /**
     * 加载根Fragment, 即Activity内的第一个Fragment 或 Fragment内的第一个子Fragment
     */
    @JvmOverloads
    fun loadRootFragment(
        containerId: Int,
        toFragment: ISupportFragment,
        addToBackStack: Boolean = true,
        allowAnim: Boolean = false
    ) {
        mTransactionDelegate.loadRootTransaction(
            childFragmentManager,
            containerId,
            toFragment,
            addToBackStack,
            allowAnim
        )
    }

    /**
     * 加载多个同级根Fragment,类似Wechat, QQ主页的场景
     */
    fun loadMultipleRootFragment(
        containerId: Int,
        showPosition: Int,
        vararg toFragments: ISupportFragment
    ) {
        mTransactionDelegate.loadMultipleRootTransaction(
            childFragmentManager,
            containerId,
            showPosition,
            *toFragments
        )
    }
    /**
     * show一个Fragment,hide一个Fragment ; 主要用于类似微信主页那种 切换tab的情况
     */
    /**
     * show一个Fragment,hide其他同栈所有Fragment
     * 使用该方法时，要确保同级栈内无多余的Fragment,(只有通过loadMultipleRootFragment()载入的Fragment)
     *
     *
     * 建议使用更明确的[.showHideFragment]
     */
    @JvmOverloads
    fun showHideFragment(showFragment: ISupportFragment, hideFragment: ISupportFragment? = null) {
        mTransactionDelegate.showHideFragment(childFragmentManager, showFragment, hideFragment)
    }

    /**
     * @param launchMode Similar to Activity's LaunchMode.
     */
    @JvmOverloads
    fun start(
        toFragment: ISupportFragment,
        @LaunchMode launchMode: Int = ISupportFragment.STANDARD
    ) {
        mTransactionDelegate.dispatchStartTransaction(
            mFragment.requireFragmentManager(),
            mSupportF,
            toFragment,
            0,
            launchMode,
            TransactionDelegate.TYPE_ADD
        )
    }

    /**
     * Launch an fragment for which you would like a result when it poped.
     */
    fun startForResult(toFragment: ISupportFragment, requestCode: Int) {
        mTransactionDelegate.dispatchStartTransaction(
            mFragment.requireFragmentManager(),
            mSupportF,
            toFragment,
            requestCode,
            ISupportFragment.STANDARD,
            TransactionDelegate.TYPE_ADD_RESULT
        )
    }

    /**
     * Start the target Fragment and pop itself
     */
    fun startWithPop(toFragment: ISupportFragment) {
        mTransactionDelegate.startWithPop(mFragment.requireFragmentManager(), mSupportF, toFragment)
    }

    fun startWithPopTo(
        toFragment: ISupportFragment,
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean
    ) {
        mTransactionDelegate.startWithPopTo(
            mFragment.requireFragmentManager(),
            mSupportF,
            toFragment,
            targetFragmentClass.name,
            includeTargetFragment
        )
    }

    fun replaceFragment(toFragment: ISupportFragment, addToBackStack: Boolean) {
        mTransactionDelegate.dispatchStartTransaction(
            mFragment.requireFragmentManager(),
            mSupportF,
            toFragment,
            0,
            ISupportFragment.STANDARD,
            if (addToBackStack) TransactionDelegate.TYPE_REPLACE else TransactionDelegate.TYPE_REPLACE_DON_T_BACK
        )
    }

    @JvmOverloads
    fun startChild(
        toFragment: ISupportFragment,
        @LaunchMode launchMode: Int = ISupportFragment.STANDARD
    ) {
        mTransactionDelegate.dispatchStartTransaction(
            childFragmentManager,
            topFragment,
            toFragment,
            0,
            launchMode,
            TransactionDelegate.TYPE_ADD
        )
    }

    fun startChildForResult(toFragment: ISupportFragment, requestCode: Int) {
        mTransactionDelegate.dispatchStartTransaction(
            childFragmentManager,
            topFragment,
            toFragment,
            requestCode,
            ISupportFragment.STANDARD,
            TransactionDelegate.TYPE_ADD_RESULT
        )
    }

    fun startChildWithPop(toFragment: ISupportFragment) {
        mTransactionDelegate.startWithPop(childFragmentManager, topFragment, toFragment)
    }

    fun replaceChildFragment(toFragment: ISupportFragment, addToBackStack: Boolean) {
        mTransactionDelegate.dispatchStartTransaction(
            childFragmentManager,
            topFragment,
            toFragment,
            0,
            ISupportFragment.STANDARD,
            if (addToBackStack) TransactionDelegate.TYPE_REPLACE else TransactionDelegate.TYPE_REPLACE_DON_T_BACK
        )
    }

    fun pop() {
        mTransactionDelegate.pop(mFragment.requireFragmentManager())
    }

    /**
     * Pop the child fragment.
     */
    fun popChild() {
        mTransactionDelegate.pop(childFragmentManager)
    }

    /**
     * Pop the last fragment transition from the manager's fragment
     * back stack.
     *
     *
     * 出栈到目标fragment
     *
     * @param targetFragmentClass   目标fragment
     * @param includeTargetFragment 是否包含该fragment
     */
    @JvmOverloads
    fun popTo(
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable? = null,
        popAnim: Int = TransactionDelegate.DEFAULT_POP_TO_ANIM
    ) {
        mTransactionDelegate.popTo(
            targetFragmentClass.name,
            includeTargetFragment,
            afterPopTransactionRunnable,
            mFragment.requireFragmentManager(),
            popAnim
        )
    }

    @JvmOverloads
    fun popToChild(
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable? = null,
        popAnim: Int = TransactionDelegate.DEFAULT_POP_TO_ANIM
    ) {
        mTransactionDelegate.popTo(
            targetFragmentClass.name,
            includeTargetFragment,
            afterPopTransactionRunnable,
            childFragmentManager,
            popAnim
        )
    }

    fun popQuiet() {
        mTransactionDelegate.popQuiet(mFragment.requireFragmentManager(), mFragment)
    }

    private val childFragmentManager: FragmentManager = mFragment.childFragmentManager

    private val topFragment: ISupportFragment?
        get() = SupportHelper.getTopFragment(childFragmentManager)

    private fun fixAnimationListener(enterAnim: Animation) {
        // AnimationListener is not reliable.
        mHandler.postDelayed(mNotifyEnterAnimEndRunnable, enterAnim.duration)
        mSupportA.supportDelegate.mFragmentClickable = true
        if (mEnterAnimListener != null) {
            mHandler.post {
                mEnterAnimListener!!.onEnterAnimStart()
                mEnterAnimListener = null
            }
        }
    }

    private val mNotifyEnterAnimEndRunnable = Runnable {
        mSupportF.onEnterAnimationEnd(mSaveInstanceState)
        if (mRootViewClickable) return@Runnable
        val view = mFragment.view ?: return@Runnable
        val preFragment = SupportHelper.getPreFragment(mFragment) ?: return@Runnable
        val prePopExitDuration = preFragment.supportDelegate.popExitAnimDuration
        val enterDuration = enterAnimDuration
        mHandler.postDelayed({ view.isClickable = false }, prePopExitDuration - enterDuration)
    }


    private fun compatSharedElements() {
        notifyEnterAnimEnd()
    }

    fun setBackground(view: View) {
        if (mFragment.tag != null && mFragment.tag!!.startsWith("android:switcher:") || mRootStatus != STATUS_UN_ROOT || view.background != null) {
            return
        }
        val defaultBg = mSupportA.supportDelegate.defaultFragmentBackground
        if (defaultBg == 0) {
            val background = windowBackground
            view.setBackgroundResource(background)
        } else {
            view.setBackgroundResource(defaultBg)
        }
    }

    private val windowBackground: Int
        get() {
            val a = activity.theme.obtainStyledAttributes(
                intArrayOf(
                    R.attr.windowBackground
                )
            )
            val background = a.getResourceId(0, 0)
            a.recycle()
            return background
        }

    private fun notifyEnterAnimEnd() {
        mHandler.post(mNotifyEnterAnimEndRunnable)
        mSupportA.supportDelegate.mFragmentClickable = true
    }


    private val enterAnim: Animation?
        get() {
            if (mCustomEnterAnim == Int.MIN_VALUE) {
                return mAnimHelper.enterAnim
            } else {
                try {
                    return AnimationUtils.loadAnimation(activity, mCustomEnterAnim)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return null
        }
    private val enterAnimDuration: Long
        get() {
            return enterAnim?.duration ?: NOT_FOUND_ANIM_TIME
        }
    val exitAnimDuration: Long
        get() {
            if (mCustomExitAnim == Int.MIN_VALUE) {
                return mAnimHelper.exitAnim.duration
            } else {
                try {
                    return AnimationUtils.loadAnimation(activity, mCustomExitAnim).duration
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return NOT_FOUND_ANIM_TIME
        }
    private val popExitAnimDuration: Long
        get() {
            if (mCustomPopExitAnim == Int.MIN_VALUE) {
                return mAnimHelper.popExitAnim.duration
            } else {
                try {
                    return AnimationUtils.loadAnimation(activity, mCustomPopExitAnim).duration
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return NOT_FOUND_ANIM_TIME
        }
    val exitAnim: Animation?
        get() {
            if (mCustomExitAnim == Int.MIN_VALUE) {
                return mAnimHelper.exitAnim
            } else {
                try {
                    return AnimationUtils.loadAnimation(activity, mCustomExitAnim)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return null
        }

    interface EnterAnimListener {
        fun onEnterAnimStart()
    }

    companion object {
        private const val NOT_FOUND_ANIM_TIME = 300L
        const val STATUS_UN_ROOT = 0
        const val STATUS_ROOT_ANIM_DISABLE = 1
        const val STATUS_ROOT_ANIM_ENABLE = 2
    }
}