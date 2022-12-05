package me.yokeyword.fragmentation

import me.yokeyword.fragmentation.anim.FragmentAnimator
import me.yokeyword.fragmentation.debug.DebugStackDelegate
import java.lang.RuntimeException
import me.yokeyword.fragmentation.ExtraTransaction.ExtraTransactionImpl
import android.os.Bundle
import me.yokeyword.fragmentation.anim.DefaultVerticalAnimator
import java.lang.Runnable
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import kotlin.jvm.JvmOverloads
import me.yokeyword.fragmentation.ISupportFragment.LaunchMode
import me.yokeyword.fragmentation.anim.DefaultNoAnimator
import me.yokeyword.fragmentation.queue.Action
import java.lang.Class

class SupportActivityDelegate(support: ISupportActivity?) {
    private val mSupport: ISupportActivity
    private val mActivity: FragmentActivity
    internal var mTransactionDelegate: TransactionDelegate
    private var mFragmentAnimator: FragmentAnimator

    var mPopMultipleNoAnim = false
    var mFragmentClickable = true

    /**
     * 当Fragment根布局 没有 设定background属性时,
     * Fragmentation默认使用Theme的android:windowBackground作为Fragment的背景,
     * 可以通过该方法改变Fragment背景。
     */
    var defaultFragmentBackground = 0
    private val mDebugStackDelegate: DebugStackDelegate

    init {
        if (support !is FragmentActivity) throw RuntimeException("Must extends FragmentActivity/AppCompatActivity")
        mSupport = support
        mActivity = support
        mDebugStackDelegate = DebugStackDelegate(mActivity)
        mTransactionDelegate = TransactionDelegate(mSupport)
        mFragmentAnimator = mSupport.onCreateFragmentAnimator() ?: DefaultNoAnimator()
    }

    /**
     * Perform some extra transactions.
     * 额外的事务：自定义Tag，添加SharedElement动画，操作非回退栈Fragment
     */
    fun extraTransaction(): ExtraTransaction {
        return ExtraTransactionImpl(
            mSupport as FragmentActivity,
            topFragment,
            mTransactionDelegate,
            true
        )
    }

    fun onCreate(savedInstanceState: Bundle?) {
        mDebugStackDelegate.onCreate(Fragmentation.default.mode)
    }

    fun onPostCreate(savedInstanceState: Bundle?) {
        mDebugStackDelegate.onPostCreate(Fragmentation.default.mode)
    }

    /**
     * Set all fragments animation.
     * 设置Fragment内的全局动画
     */
    var fragmentAnimator: FragmentAnimator
        get() = mFragmentAnimator.copy()
        set(fragmentAnimator) {
            mFragmentAnimator = fragmentAnimator
            for (fragment in supportFragmentManager.fragments) {
                if (fragment is ISupportFragment) {
                    val iF = fragment as ISupportFragment
                    val delegate = iF.supportDelegate
                    if (delegate.mAnimByActivity) {
                        delegate.mFragmentAnimator = fragmentAnimator.copy()
                        delegate.mAnimHelper.notifyChanged(delegate.mFragmentAnimator)
                    }
                }
            }
        }

    /**
     * Set all fragments animation.
     * 构建Fragment转场动画
     *
     *
     * 如果是在Activity内实现,则构建的是Activity内所有Fragment的转场动画,
     * 如果是在Fragment内实现,则构建的是该Fragment的转场动画,此时优先级 > Activity的onCreateFragmentAnimator()
     *
     * @return FragmentAnimator对象
     */
    fun onCreateFragmentAnimator(): FragmentAnimator {
        return DefaultVerticalAnimator()
    }

    /**
     * 显示栈视图dialog,调试时使用
     */
    fun showFragmentStackHierarchyView() {
        mDebugStackDelegate.showFragmentStackHierarchyView()
    }

    /**
     * 显示栈视图日志,调试时使用
     */
    fun logFragmentStackHierarchy(TAG: String?) {
        mDebugStackDelegate.logFragmentRecords(TAG)
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
     * 不建议复写该方法,请使用 [.onBackPressedSupport] 代替
     */
    fun onBackPressed() {
        mTransactionDelegate.mActionQueue.enqueue(object :
            Action(ACTION_BACK, supportFragmentManager) {
            override fun run() {
                if (!mFragmentClickable) {
                    mFragmentClickable = true
                }

                // 获取activeFragment:即从栈顶开始 状态为show的那个Fragment
                val activeFragment = SupportHelper.getAddedFragment(
                    supportFragmentManager
                )
                if (mTransactionDelegate.dispatchBackPressedEvent(activeFragment)) return
                mSupport.onBackPressedSupport()
            }
        })
    }

    /**
     * 该方法回调时机为,Activity回退栈内Fragment的数量 小于等于1 时,默认finish Activity
     * 请尽量复写该方法,避免复写onBackPress(),以保证SupportFragment内的onBackPressedSupport()回退事件正常执行
     */
    fun onBackPressedSupport() {
        if (supportFragmentManager.backStackEntryCount > 1) {
            pop()
        } else {
            ActivityCompat.finishAfterTransition(mActivity)
        }
    }

    fun onDestroy() = mDebugStackDelegate.onDestroy()

    fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // 防抖动(防止点击速度过快)
        return !mFragmentClickable
    }

    /**
     * 加载根Fragment, 即Activity内的第一个Fragment 或 Fragment内的第一个子Fragment
     */
    @JvmOverloads
    fun loadRootFragment(
        containerId: Int,
        toFragment: ISupportFragment,
        addToBackStack: Boolean = true,
        allowAnimation: Boolean = false
    ) {
        mTransactionDelegate.loadRootTransaction(
            supportFragmentManager,
            containerId,
            toFragment,
            addToBackStack,
            allowAnimation
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
            supportFragmentManager,
            containerId,
            showPosition,
            *toFragments
        )
    }

    /**
     * show一个Fragment,hide其他同栈所有Fragment
     * 使用该方法时，要确保同级栈内无多余的Fragment,(只有通过loadMultipleRootFragment()载入的Fragment)
     *
     *
     * 建议使用更明确的[.showHideFragment]
     *
     * @param showFragment 需要show的Fragment
     */
    @JvmOverloads
    fun showHideFragment(showFragment: ISupportFragment, hideFragment: ISupportFragment? = null) {
        mTransactionDelegate.showHideFragment(supportFragmentManager, showFragment, hideFragment)
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
            supportFragmentManager,
            topFragment,
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
            supportFragmentManager,
            topFragment,
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
        mTransactionDelegate.startWithPop(supportFragmentManager, topFragment, toFragment)
    }

    fun startWithPopTo(
        toFragment: ISupportFragment,
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean
    ) {
        mTransactionDelegate.startWithPopTo(
            supportFragmentManager,
            topFragment,
            toFragment,
            targetFragmentClass.name,
            includeTargetFragment
        )
    }

    fun replaceFragment(toFragment: ISupportFragment, addToBackStack: Boolean) {
        mTransactionDelegate.dispatchStartTransaction(
            supportFragmentManager,
            topFragment,
            toFragment,
            0,
            ISupportFragment.STANDARD,
            if (addToBackStack) TransactionDelegate.TYPE_REPLACE else TransactionDelegate.TYPE_REPLACE_DON_T_BACK
        )
    }

    /**
     * Pop the child fragment.
     */
    fun pop() {
        mTransactionDelegate.pop(supportFragmentManager)
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
        targetFragmentClass: Class<*>,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable? = null,
        popAnim: Int = TransactionDelegate.DEFAULT_POP_TO_ANIM
    ) {
        mTransactionDelegate.popTo(
            targetFragmentClass.name,
            includeTargetFragment,
            afterPopTransactionRunnable,
            supportFragmentManager,
            popAnim
        )
    }

    private val supportFragmentManager: FragmentManager
        get() = mActivity.supportFragmentManager
    private val topFragment: ISupportFragment?
        get() = SupportHelper.getTopFragment(supportFragmentManager)
}