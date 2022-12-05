package me.yokeyword.fragmentation

import android.content.Context
import android.os.Bundle
import android.view.animation.Animation
import java.lang.Runnable
import me.yokeyword.fragmentation.anim.FragmentAnimator
import android.view.View
import androidx.fragment.app.Fragment
import me.yokeyword.fragmentation.ISupportFragment.LaunchMode
import java.lang.Class

/**
 * Base class for activities that use the support-based
 * [ISupportFragment] and
 * [Fragment] APIs.
 * Created by YoKey on 17/6/22.
 */
open class SupportFragment : Fragment(), ISupportFragment {
    override val supportDelegate = SupportFragmentDelegate(this)

    @JvmField
    protected var mActivity: SupportActivity? = null

    /**
     * Perform some extra transactions.
     * 额外的事务：自定义Tag，添加SharedElement动画，操作非回退栈Fragment
     */
    override fun extraTransaction(): ExtraTransaction? {
        return supportDelegate.extraTransaction()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        supportDelegate.onAttach()
        mActivity = supportDelegate.activity as SupportActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportDelegate.onCreate(savedInstanceState)
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        return supportDelegate.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        supportDelegate.onActivityCreated(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        supportDelegate.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        supportDelegate.onResume()
        supportDelegate.setUserVisibleHint(true)
    }

    override fun onPause() {
        super.onPause()
        supportDelegate.onPause()
        supportDelegate.setUserVisibleHint(false)
    }

    override fun onDestroyView() {
        supportDelegate.onDestroyView()
        super.onDestroyView()
    }

    override fun onDestroy() {
        supportDelegate.onDestroy()
        super.onDestroy()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        supportDelegate.onHiddenChanged(hidden)
    }

    /**
     * 前面的事务全部执行后 执行该Action
     */
    override fun post(runnable: Runnable?) {
        supportDelegate.post(runnable)
    }

    /**
     * 入栈动画 结束时,回调
     */
    override fun onEnterAnimationEnd(savedInstanceState: Bundle?) {
        supportDelegate.onEnterAnimationEnd(savedInstanceState)
    }

    /**
     * 同级下的 懒加载 ＋ ViewPager下的懒加载  的结合回调方法
     */
    override fun onLazyInitView(savedInstanceState: Bundle?) {
        supportDelegate.onLazyInitView(savedInstanceState)
    }

    /**
     * 当Fragment对用户可见时回调
     *
     * Is the combination of  [onHiddenChanged() + onResume()/onPause() + setUserVisibleHint()]
     */
    override fun onSupportVisible() {
        supportDelegate.onSupportVisible()
    }

    /**
     * Called when the fragment is invisible.
     *
     * 当Fragment不可见时调用。
     *
     * Is the combination of  [onHiddenChanged() + onResume()/onPause() + setUserVisibleHint()]
     */
    override fun onSupportInvisible() {
        supportDelegate.onSupportInvisible()
    }

    /**
     * Return true if the fragment has been supportVisible.
     *
     * 如果片段已支持可见，则返回 true。
     */
    override val isSupportVisible: Boolean
        get() = supportDelegate.isSupportVisible

    /**
     * Set fragment animation with a higher priority than the ISupportActivity
     * 设定当前Fragment动画,优先级比在SupportActivity里高
     */
    override fun onCreateFragmentAnimator(): FragmentAnimator {
        return supportDelegate.onCreateFragmentAnimator()
    }

    /**
     * 设置Fragment内的全局动画
     */
    override var fragmentAnimator: FragmentAnimator?
        get() = supportDelegate.fragmentAnimator
        set(fragmentAnimator) {
            supportDelegate.fragmentAnimator = fragmentAnimator
        }

    /**
     * 按返回键触发,前提是SupportActivity的onBackPressed()方法能被调用
     *
     * @return false则继续向上传递, true则消费掉该事件
     */
    override fun onBackPressedSupport(): Boolean = supportDelegate.onBackPressedSupport()

    /**
     * 类似 [androidx.appcompat.app.AppCompatActivity.setResult]
     *
     * Similar to [androidx.appcompat.app.AppCompatActivity.setResult]
     *
     * @see .startForResult
     */
    override fun setFragmentResult(resultCode: Int, bundle: Bundle?) =
        supportDelegate.setFragmentResult(resultCode, bundle)

    /**
     * 类似  [androidx.appcompat.app.AppCompatActivity.onActivityResult]
     *
     * Similar to [androidx.appcompat.app.AppCompatActivity.onActivityResult]
     *
     * @see .startForResult
     */
    override fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) =
        supportDelegate.onFragmentResult(requestCode, resultCode, data)

    /**
     * 在start(TargetFragment,LaunchMode)时,启动模式为SingleTask/SingleTop, 回调TargetFragment的该方法
     * 类似 [androidx.appcompat.app.AppCompatActivity.onNewIntent]
     *
     * Similar to [androidx.appcompat.app.AppCompatActivity.onNewIntent]
     *
     * @param args putNewBundle(Bundle newBundle)
     * @see .start
     */
    override fun onNewBundle(args: Bundle?) = supportDelegate.onNewBundle(args)

    /**
     * 添加NewBundle,用于启动模式为SingleTask/SingleTop时
     *
     * @see .start
     */
    override fun putNewBundle(newBundle: Bundle?) = supportDelegate.putNewBundle(newBundle)

    /**
     * 隐藏软键盘
     */
    protected fun hideSoftInput() = supportDelegate.hideSoftInput()

    /**
     * 显示软键盘,调用该方法后,会在onPause时自动隐藏软键盘
     */
    protected fun showSoftInput(view: View?) = view?.apply { supportDelegate.showSoftInput(this) }

    /**
     * 加载根Fragment, 即Activity内的第一个Fragment 或 Fragment内的第一个子Fragment
     *
     * @param containerId 容器id
     * @param toFragment  目标Fragment
     */
    fun loadRootFragment(
        containerId: Int, toFragment: ISupportFragment, addToBackStack: Boolean = true,
        allowAnim: Boolean = false
    ) = supportDelegate.loadRootFragment(containerId, toFragment, addToBackStack, allowAnim)

    /**
     * 加载多个同级根Fragment,类似Wechat, QQ主页的场景
     */
    fun loadMultipleRootFragment(
        containerId: Int,
        showPosition: Int,
        vararg toFragments: ISupportFragment
    ) = supportDelegate.loadMultipleRootFragment(containerId, showPosition, *toFragments)

    /**
     * show一个Fragment,hide其他同栈所有Fragment
     * 使用该方法时，要确保同级栈内无多余的Fragment,(只有通过loadMultipleRootFragment()载入的Fragment)
     *
     * show一个Fragment,hide一个Fragment ; 主要用于类似微信主页那种 切换tab的情况
     *
     * @param showFragment 需要show的Fragment
     */
    fun showHideFragment(showFragment: ISupportFragment, hideFragment: ISupportFragment? = null) =
        supportDelegate.showHideFragment(showFragment, hideFragment)

    fun start(
        toFragment: ISupportFragment,
        @LaunchMode launchMode: Int = ISupportFragment.STANDARD
    ) = supportDelegate.start(toFragment, launchMode)

    /**
     * Launch an fragment for which you would like a result when it popped.
     */
    fun startForResult(toFragment: ISupportFragment, requestCode: Int) =
        supportDelegate.startForResult(toFragment, requestCode)

    /**
     * Start the target Fragment and pop itself
     */
    fun startWithPop(toFragment: ISupportFragment) = supportDelegate.startWithPop(toFragment)

    /**
     * @see .popTo
     * @see .start
     */
    fun startWithPopTo(
        toFragment: ISupportFragment,
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean
    ) = supportDelegate.startWithPopTo(toFragment, targetFragmentClass, includeTargetFragment)

    fun replaceFragment(toFragment: ISupportFragment, addToBackStack: Boolean) =
        supportDelegate.replaceFragment(toFragment, addToBackStack)

    fun pop() = supportDelegate.pop()

    /**
     * Pop the child fragment.
     */
    fun popChild() = supportDelegate.popChild()

    /**
     * If you want to begin another FragmentTransaction immediately after popTo(), use this method.
     * 如果你想在出栈后, 立刻进行FragmentTransaction操作，请使用该方法
     */
    fun popTo(
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable? = null,
        popAnim: Int = TransactionDelegate.DEFAULT_POP_TO_ANIM
    ) = supportDelegate.popTo(
        targetFragmentClass,
        includeTargetFragment,
        afterPopTransactionRunnable,
        popAnim
    )

    fun popToChild(
        targetFragmentClass: Class<Fragment>,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable? = null,
        popAnim: Int = TransactionDelegate.DEFAULT_POP_TO_ANIM
    ) = supportDelegate.popToChild(
        targetFragmentClass,
        includeTargetFragment,
        afterPopTransactionRunnable,
        popAnim
    )

    /**
     * 得到位于栈顶Fragment
     */
    val topFragment: ISupportFragment?
        get() = SupportHelper.getTopFragment(parentFragmentManager)
    val topChildFragment: ISupportFragment?
        get() = SupportHelper.getTopFragment(childFragmentManager)

    /**
     * @return 位于当前Fragment的前一个Fragment
     */
    val preFragment: ISupportFragment?
        get() = SupportHelper.getPreFragment(this)

    /**
     * 获取栈内的fragment对象
     */
    fun <T : ISupportFragment?> findFragment(fragmentClass: Class<T>?): T? =
        SupportHelper.findFragment(parentFragmentManager, fragmentClass)

    /**
     * 获取栈内的fragment对象
     */
    fun <T : ISupportFragment?> findChildFragment(fragmentClass: Class<T>?): T? =
        SupportHelper.findFragment(childFragmentManager, fragmentClass)
}