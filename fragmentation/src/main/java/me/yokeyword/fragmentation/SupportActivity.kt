package me.yokeyword.fragmentation

import me.yokeyword.fragmentation.anim.FragmentAnimator
import java.lang.Runnable
import me.yokeyword.fragmentation.ISupportFragment.LaunchMode
import java.lang.Class
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import me.yokeyword.fragmentation.anim.DefaultNoAnimator

/**
 * activities基类
 * [ISupportActivity] and
 * [AppCompatActivity] APIs.
 *
 * Created by YoKey on 17/6/20.
 */
open class SupportActivity : AppCompatActivity, ISupportActivity {
    private val mDelegate = SupportActivityDelegate(this)

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override val supportDelegate: SupportActivityDelegate
        get() = mDelegate

    override fun extraTransaction(): ExtraTransaction {
        return mDelegate.extraTransaction()
    }

    override var fragmentAnimator: FragmentAnimator = DefaultNoAnimator()
        get() = mDelegate.fragmentAnimator
        set(value) {
            mDelegate.fragmentAnimator = value
            field = value
        }

    override fun onCreateFragmentAnimator(): FragmentAnimator = mDelegate.onCreateFragmentAnimator()

    override fun post(runnable: Runnable?) = mDelegate.post(runnable)

    override fun onBackPressedSupport() = mDelegate.onBackPressedSupport()

    /**
     * 加载根Fragment, 即Activity内的第一个Fragment 或 Fragment内的第一个子Fragment
     *
     * @param containerId 容器id
     * @param toFragment  目标Fragment
     */
    fun loadRootFragment(
        containerId: Int, toFragment: ISupportFragment, addToBackStack: Boolean = true,
        allowAnimation: Boolean = false
    ) = mDelegate.loadRootFragment(containerId, toFragment, addToBackStack, allowAnimation)

    /**
     * 加载多个同级根Fragment,类似Wechat, QQ主页的场景
     */
    fun loadMultipleRootFragment(
        containerId: Int,
        showPosition: Int,
        vararg toFragments: ISupportFragment
    ) =  mDelegate.loadMultipleRootFragment(containerId, showPosition, *toFragments)

    /**
     * show一个Fragment,hide其他同栈所有Fragment
     * 使用该方法时，要确保同级栈内无多余的Fragment,(只有通过loadMultipleRootFragment()载入的Fragment)
     *
     * 建议使用更明确的[.showHideFragment]
     *
     * @param showFragment 需要show的Fragment
     */
    fun showHideFragment(showFragment: ISupportFragment, hideFragment: ISupportFragment? = null) =
        mDelegate.showHideFragment(showFragment, hideFragment)

    /**
     * 推荐使用[SupportFragment.start]
     */
    fun start(
        toFragment: ISupportFragment?,
        @LaunchMode launchMode: Int = ISupportFragment.STANDARD
    ) = toFragment?.also {
        mDelegate.start(it, launchMode)
    }

    /**
     * 推荐使用SupportFragment.startForResult 。启动一个片段，当它弹出时你想要一个结果。
     */
    fun startForResult(toFragment: ISupportFragment, requestCode: Int) =
        mDelegate.startForResult(toFragment, requestCode)

    /**
     * 推荐使用[SupportFragment.startWithPop]。启动目标 Fragment 并弹出自身
     */
    fun startWithPop(toFragment: ISupportFragment) = mDelegate.startWithPop(toFragment)

    /**
     * 推荐使用[SupportFragment.startWithPopTo]
     *
     * @see .popTo
     * @see .start
     */
    fun startWithPopTo(
        toFragment: ISupportFragment,
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean
    ) = mDelegate.startWithPopTo(toFragment, targetFragmentClass, includeTargetFragment)

    /**
     * 推荐使用 [SupportFragment.replaceFragment]。
     */
    fun replaceFragment(toFragment: ISupportFragment, addToBackStack: Boolean) =
        mDelegate.replaceFragment(toFragment, addToBackStack)

    /**
     * 弹出片段。
     */
    fun pop() = mDelegate.pop()

    /**
     * 出栈到目标fragment
     *
     * 如果你想在出栈后, 立刻进行FragmentTransaction操作，请使用该方法
     *
     * @param targetFragmentClass   目标fragment
     * @param includeTargetFragment 是否包含该fragment
     */
    fun popTo(
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable? = null,
        popAnim: Int = TransactionDelegate.DEFAULT_POP_TO_ANIM
    ) = mDelegate.popTo(
        targetFragmentClass,
        includeTargetFragment,
        afterPopTransactionRunnable,
        popAnim
    )

    /**
     * 当Fragment根布局 没有 设定background属性时,
     * Fragmentation默认使用Theme的android:windowBackground作为Fragment的背景,
     * 可以通过该方法改变其内所有Fragment的默认背景。
     */
    fun setDefaultFragmentBackground(@DrawableRes backgroundRes: Int) {
        mDelegate.defaultFragmentBackground = backgroundRes
    }

    /**
     * 得到位于栈顶Fragment
     */
    fun getTopFragment(): ISupportFragment? = SupportHelper.getTopFragment(supportFragmentManager)

    /**
     * 获取栈内的fragment对象
     */
    fun <T : ISupportFragment?> findFragment(fragmentClass: Class<T>?): T? = SupportHelper.findFragment(supportFragmentManager, fragmentClass)
}