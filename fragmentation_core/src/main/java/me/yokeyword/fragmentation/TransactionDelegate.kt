package me.yokeyword.fragmentation

import me.yokeyword.fragmentation.SupportHelper.getTopFragment
import me.yokeyword.fragmentation.SupportHelper.getWillPopFragments
import me.yokeyword.fragmentation.SupportHelper.getPreFragment
import me.yokeyword.fragmentation.SupportHelper.getBackStackTopFragment
import me.yokeyword.fragmentation.SupportHelper.findBackStackFragment
import me.yokeyword.fragmentation.queue.ActionQueue
import android.os.Looper
import java.lang.Runnable
import android.os.Bundle
import android.os.Handler
import me.yokeyword.fragmentation.helper.internal.TransactionRecord.SharedElement
import java.lang.NullPointerException
import java.lang.Exception
import android.util.Log
import android.view.ViewGroup
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import me.yokeyword.fragmentation.SupportFragmentDelegate.EnterAnimListener
import java.lang.IllegalStateException
import me.yokeyword.fragmentation.helper.internal.ResultRecord
import me.yokeyword.fragmentation.exception.AfterSaveStateTransactionWarning
import me.yokeyword.fragmentation.queue.Action

/**
 * Controller
 * Created by YoKeyword on 16/1/22.
 */
class TransactionDelegate(private val mSupport: ISupportActivity) {
    private val mActivity: FragmentActivity = mSupport as FragmentActivity
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    var mActionQueue: ActionQueue = ActionQueue(mHandler)

    fun post(runnable: Runnable?) {
        runnable?.also {
            mActionQueue.enqueue(object : Action(mActivity.supportFragmentManager) {
                override fun run() {
                    it.run()
                }
            })
        }
    }

    fun loadRootTransaction(
        fm: FragmentManager,
        containerId: Int,
        to: ISupportFragment,
        addToBackStack: Boolean,
        allowAnimation: Boolean
    ) {
        enqueue(fm, object : Action(ACTION_LOAD, fm) {
            override fun run() {
                bindContainerId(containerId, to)
                var toFragmentTag = to.javaClass.name
                val transactionRecord = to.supportDelegate.mTransactionRecord
                if (transactionRecord != null) {
                    if (transactionRecord.tag != null) {
                        toFragmentTag = transactionRecord.tag!!
                    }
                }
                start(
                    fm,
                    null,
                    to,
                    toFragmentTag,
                    !addToBackStack,
                    mutableListOf(),
                    allowAnimation,
                    TYPE_REPLACE
                )
            }
        })
    }

    fun loadMultipleRootTransaction(
        fm: FragmentManager,
        containerId: Int,
        showPosition: Int,
        vararg tos: ISupportFragment
    ) {
        enqueue(fm, object : Action(ACTION_LOAD, fm) {
            override fun run() {
                val ft = fm.beginTransaction()
                for (i in tos.indices) {
                    val to = tos[i] as Fragment
                    val args = getArguments(to)
                    args.putInt(
                        FRAGMENTATION_ARG_ROOT_STATUS,
                        SupportFragmentDelegate.STATUS_ROOT_ANIM_DISABLE
                    )
                    bindContainerId(containerId, tos[i])
                    val toName = to.javaClass.name
                    ft.add(containerId, to, toName)
                    if (i != showPosition) {
                        ft.hide(to)
                    }
                }
                supportCommit(fm, ft)
            }
        })
    }

    private fun start(
        fm: FragmentManager,
        from: ISupportFragment?,
        to: ISupportFragment,
        toFragmentTag: String,
        dontAddToBackStack: Boolean,
        sharedElementList: MutableList<SharedElement>,
        allowRootFragmentAnim: Boolean,
        type: Int
    ) {
        val ft = fm.beginTransaction()
        val addMode =
            type == TYPE_ADD || type == TYPE_ADD_RESULT || type == TYPE_ADD_WITHOUT_HIDE || type == TYPE_ADD_RESULT_WITHOUT_HIDE
        val fromF = from as Fragment?
        val toF = to as Fragment
        val args = getArguments(toF)
        args.putBoolean(FRAGMENTATION_ARG_REPLACE, !addMode)
        if (sharedElementList.isNotEmpty()) {
            if (addMode) { // Replace mode forbidden animation, the replace animations exist overlapping Bug on support-v4.
                val record = to.supportDelegate.mTransactionRecord
                if (record != null && record.targetFragmentEnter != Int.MIN_VALUE) {
                    ft.setCustomAnimations(
                        record.targetFragmentEnter,
                        record.currentFragmentPopExit,
                        record.currentFragmentPopEnter,
                        record.targetFragmentExit
                    )
                    args.putInt(FRAGMENTATION_ARG_CUSTOM_ENTER_ANIM, record.targetFragmentEnter)
                    args.putInt(FRAGMENTATION_ARG_CUSTOM_EXIT_ANIM, record.targetFragmentExit)
                    args.putInt(
                        FRAGMENTATION_ARG_CUSTOM_POP_EXIT_ANIM,
                        record.currentFragmentPopExit
                    )
                } else {
                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                }
            } else {
                args.putInt(
                    FRAGMENTATION_ARG_ROOT_STATUS,
                    SupportFragmentDelegate.STATUS_ROOT_ANIM_DISABLE
                )
            }
        } else {
            args.putBoolean(FRAGMENTATION_ARG_IS_SHARED_ELEMENT, true)
            for (item in sharedElementList) {
                ft.addSharedElement(item.sharedElement, item.sharedName)
            }
        }
        if (from == null) {
            ft.replace(args.getInt(FRAGMENTATION_ARG_CONTAINER), toF, toFragmentTag)
            if (!addMode) {
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                args.putInt(
                    FRAGMENTATION_ARG_ROOT_STATUS,
                    if (allowRootFragmentAnim) SupportFragmentDelegate.STATUS_ROOT_ANIM_ENABLE else SupportFragmentDelegate.STATUS_ROOT_ANIM_DISABLE
                )
            }
        } else {
            if (addMode) {
                ft.add(from.supportDelegate.mContainerId, toF, toFragmentTag)
                if (type != TYPE_ADD_WITHOUT_HIDE && type != TYPE_ADD_RESULT_WITHOUT_HIDE) {
                    ft.hide(fromF!!)
                }
            } else {
                ft.replace(from.supportDelegate.mContainerId, toF, toFragmentTag)
            }
        }
        if (!dontAddToBackStack && type != TYPE_REPLACE_DON_T_BACK) {
            ft.addToBackStack(toFragmentTag)
        }
        supportCommit(fm, ft)
    }

    /**
     * Start the target Fragment and pop itself
     */
    fun startWithPop(fm: FragmentManager, from: ISupportFragment?, to: ISupportFragment) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                val top = getTopFragmentForStart(from, fm)
                    ?: throw NullPointerException("There is no Fragment in the FragmentManager, maybe you need to call loadRootFragment() first!")
                val containerId = top.supportDelegate.mContainerId
                bindContainerId(containerId, to)
                top.supportDelegate.mLockAnim = true
                if (!fm.isStateSaved) {
                    mockStartWithPopAnim(
                        getTopFragment(fm),
                        to,
                        top.supportDelegate.mAnimHelper.popExitAnim
                    )
                }
                handleAfterSaveInStateTransactionException(fm, "startWithPop()")
                removeTopFragment(fm)
                fm.popBackStackImmediate()
            }
        })
        dispatchStartTransaction(fm, from, to, 0, ISupportFragment.STANDARD, TYPE_ADD)
    }

    fun startWithPopTo(
        fm: FragmentManager,
        from: ISupportFragment?,
        to: ISupportFragment,
        fragmentTag: String?,
        includeTargetFragment: Boolean
    ) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                var flag = 0
                if (includeTargetFragment) {
                    flag = FragmentManager.POP_BACK_STACK_INCLUSIVE
                }
                val willPopFragments = getWillPopFragments(fm, fragmentTag, includeTargetFragment)
                if (willPopFragments.isEmpty()) return
                val top = getTopFragmentForStart(from, fm)
                    ?: throw NullPointerException("There is no Fragment in the FragmentManager, maybe you need to call loadRootFragment() first!")
                val containerId = top.supportDelegate.mContainerId
                bindContainerId(containerId, to)
                if (!fm.isStateSaved) {
                    mockStartWithPopAnim(
                        getTopFragment(fm),
                        to,
                        top.supportDelegate.mAnimHelper.popExitAnim
                    )
                }
                handleAfterSaveInStateTransactionException(fm, "startWithPopTo()")
                safePopTo(fragmentTag, fm, flag, willPopFragments)
            }
        })
        dispatchStartTransaction(fm, from, to, 0, ISupportFragment.STANDARD, TYPE_ADD)
    }

    /**
     * Show showFragment then hide hideFragment
     */
    fun showHideFragment(
        fm: FragmentManager,
        showFragment: ISupportFragment,
        hideFragment: ISupportFragment?
    ) {
        enqueue(fm, object : Action(fm) {
            override fun run() {
                doShowHideFragment(fm, showFragment, hideFragment)
            }
        })
    }

    /**
     * Remove
     * Only allowed in interfaces  [ExtraTransaction.remove]
     */
    fun remove(fm: FragmentManager, fragment: Fragment?, showPreFragment: Boolean) {
        enqueue(fm, object : Action(ACTION_POP, fm) {
            override fun run() {
                val ft = fm.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .remove(fragment!!)
                if (showPreFragment) {
                    val preFragment = getPreFragment(fragment)
                    if (preFragment is Fragment) {
                        ft.show((preFragment as Fragment?)!!)
                    }
                }
                supportCommit(fm, ft)
            }
        })
    }

    /**
     * Pop
     */
    fun pop(fm: FragmentManager) {
        enqueue(fm, object : Action(ACTION_POP, fm) {
            override fun run() {
                handleAfterSaveInStateTransactionException(fm, "pop()")
                removeTopFragment(fm)
                fm.popBackStackImmediate()
            }
        })
    }

    fun popQuiet(fm: FragmentManager, fragment: Fragment) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                mSupport.supportDelegate.mPopMultipleNoAnim = true
                removeTopFragment(fm)
                fm.popBackStackImmediate(fragment.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                mSupport.supportDelegate.mPopMultipleNoAnim = false
            }
        })
    }

    private fun removeTopFragment(fm: FragmentManager) {
        try { // Safe popBackStack()
            val top = getBackStackTopFragment(fm)
            if (top != null) {
                fm.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .remove((top as Fragment?)!!)
                    .commitAllowingStateLoss()
            }
        } catch (ignored: Exception) {
        }
    }

    /**
     * Pop the last fragment transition from the manager's fragment pop stack.
     *
     * @param targetFragmentTag     Tag
     * @param includeTargetFragment Whether it includes targetFragment
     */
    fun popTo(
        targetFragmentTag: String,
        includeTargetFragment: Boolean,
        afterPopTransactionRunnable: Runnable?,
        fm: FragmentManager,
        popAnim: Int
    ) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                doPopTo(targetFragmentTag, includeTargetFragment, fm, popAnim)
                afterPopTransactionRunnable?.run()
            }
        })
    }

    /**
     * Dispatch the start transaction.
     */
    fun dispatchStartTransaction(
        fm: FragmentManager,
        from: ISupportFragment?,
        to: ISupportFragment,
        requestCode: Int,
        launchMode: Int,
        type: Int
    ) {
        enqueue(
            fm,
            object : Action(
                if (launchMode == ISupportFragment.SINGLE_TASK) ACTION_POP_MOCK else ACTION_NORMAL,
                fm
            ) {
                override fun run() {
                    doDispatchStartTransaction(fm, from, to, requestCode, launchMode, type)
                }
            })
    }

    private fun doDispatchStartTransaction(
        fm: FragmentManager,
        from: ISupportFragment?,
        to: ISupportFragment,
        requestCode: Int,
        launchMode: Int,
        type: Int
    ) {
        var fromFragment = from
        if ((type == TYPE_ADD_RESULT || type == TYPE_ADD_RESULT_WITHOUT_HIDE) && fromFragment != null) {
            if (!(fromFragment as Fragment).isAdded) {
                Log.w(
                    TAG,
                    (fromFragment as Fragment).javaClass.simpleName + " has not been attached yet! startForResult() converted to start()"
                )
            } else {
                saveRequestCode(fm, fromFragment as Fragment, to as Fragment, requestCode)
            }
        }
        fromFragment = getTopFragmentForStart(fromFragment, fm)
        val containerId = getArguments(to as Fragment).getInt(FRAGMENTATION_ARG_CONTAINER, 0)
        if (fromFragment == null && containerId == 0) {
            Log.e(
                TAG,
                "There is no Fragment in the FragmentManager, maybe you need to call loadRootFragment()!"
            )
            return
        }
        if (fromFragment != null && containerId == 0) {
            bindContainerId(fromFragment.supportDelegate.mContainerId, to)
        }

        // process ExtraTransaction
        var toFragmentTag = to.javaClass.name
        var dontAddToBackStack = false
        var sharedElementList: MutableList<SharedElement> = mutableListOf()
        val transactionRecord = to.supportDelegate.mTransactionRecord
        if (transactionRecord != null) {
            if (transactionRecord.tag != null) {
                toFragmentTag = transactionRecord.tag!!
            }
            dontAddToBackStack = transactionRecord.dontAddToBackStack
            sharedElementList = transactionRecord.sharedElementList
        }
        if (handleLaunchMode(fm, fromFragment, to, toFragmentTag, launchMode)) return
        start(fm, fromFragment, to, toFragmentTag, dontAddToBackStack, sharedElementList, false, type)
    }

    private fun doShowHideFragment(
        fm: FragmentManager,
        showFragment: ISupportFragment,
        hideFragment: ISupportFragment?
    ) {
        if (showFragment === hideFragment) return
        val ft = fm.beginTransaction().show((showFragment as Fragment))
        if (hideFragment == null) {
            val fragmentList = fm.fragments
            for (fragment in fragmentList) {
                if (fragment != null && fragment !== showFragment) {
                    ft.hide(fragment)
                }
            }
        } else {
            ft.hide((hideFragment as Fragment?)!!)
        }
        supportCommit(fm, ft)
    }

    private fun doPopTo(
        targetFragmentTag: String,
        includeTargetFragment: Boolean,
        fm: FragmentManager,
        popAnim: Int
    ) {
        handleAfterSaveInStateTransactionException(fm, "popTo()")
        val targetFragment = fm.findFragmentByTag(targetFragmentTag)
        if (targetFragment == null) {
            Log.e(
                TAG,
                "Pop failure! Can't find FragmentTag:$targetFragmentTag in the FragmentManager's Stack."
            )
            return
        }
        var flag = 0
        if (includeTargetFragment) {
            flag = FragmentManager.POP_BACK_STACK_INCLUSIVE
        }
        val willPopFragments = getWillPopFragments(fm, targetFragmentTag, includeTargetFragment)
        if (willPopFragments.isEmpty()) return
        val top = willPopFragments[0]
        mockPopToAnim(top, targetFragmentTag, fm, flag, willPopFragments, popAnim)
    }

    private fun mockPopToAnim(
        from: Fragment,
        targetFragmentTag: String,
        fm: FragmentManager,
        flag: Int,
        willPopFragments: List<Fragment>,
        popAnim: Int
    ) {
        if (from !is ISupportFragment) {
            safePopTo(targetFragmentTag, fm, flag, willPopFragments)
            return
        }
        val fromSupport = from as ISupportFragment
        val container = findContainerById(from, fromSupport.supportDelegate.mContainerId) ?: return
        val fromView = from.view ?: return
        if (fromView.animation != null) {
            fromView.clearAnimation()
        }
        container.removeViewInLayout(fromView)
        val mock = addMockView(fromView, container)
        safePopTo(targetFragmentTag, fm, flag, willPopFragments)
        var animation: Animation?
        if (popAnim == DEFAULT_POP_TO_ANIM) {
            animation = fromSupport.supportDelegate.exitAnim
            if (animation == null) {
                animation = object : Animation() {}
            }
        } else if (popAnim == 0) {
            animation = object : Animation() {}
        } else {
            animation = AnimationUtils.loadAnimation(mActivity, popAnim)
        }
        mock.startAnimation(animation)
        mHandler.postDelayed({
            try {
                mock.clearAnimation()
                mock.removeViewInLayout(fromView)
                container.removeViewInLayout(mock)
            } catch (ignored: Exception) {
            }
        }, animation!!.duration)
    }

    private fun safePopTo(
        fragmentTag: String?,
        fm: FragmentManager,
        flag: Int,
        willPopFragments: List<Fragment>
    ) {
        mSupport.supportDelegate.mPopMultipleNoAnim = true

        // 批量删除fragment ，static final int OP_REMOVE = 3;
        val transaction = fm.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
        for (fragment in willPopFragments) {
            transaction.remove(fragment)
        }
        transaction.commitAllowingStateLoss()

        // 弹栈到指定fragment，从数据上来看，和上面的效果完全一样，把栈中所有的backStackRecord 包含多个记录，每个记录包含多个操作 ，
        // 在每个记录中，对操作索引 按照从大到小的顺序，逐个进行反操作。
        // 除了第一个记录，其余每个记录都有两个操作，一个是添加OP_ADD = 1;(反操作是remove)  一个是OP_HIDE = 4;（反操作是show）（这是在start中设定的）
        // 之所以有上面的批量删除，在执行动画的时候，发现f.mView == null  就不去执行show动画。
        fm.popBackStackImmediate(fragmentTag, flag)
        mSupport.supportDelegate.mPopMultipleNoAnim = false
    }

    private fun mockStartWithPopAnim(
        from: ISupportFragment?,
        to: ISupportFragment,
        exitAnim: Animation
    ) {
        val fromF = from as Fragment?
        val container = findContainerById(fromF, from!!.supportDelegate.mContainerId) ?: return
        val fromView = fromF!!.view ?: return
        if (fromView.animation != null) {
            fromView.clearAnimation()
        }
        container.removeViewInLayout(fromView)
        val mock = addMockView(fromView, container)


        // 下面这个动画，是在新的fragment（to） 执行的时候，用mock去模拟上一个fragment（from）的退出动画
        to.supportDelegate.mEnterAnimListener = object : EnterAnimListener {
            override fun onEnterAnimStart() {
                mock.startAnimation(exitAnim)
                mHandler.postDelayed({
                    try {
                        mock.clearAnimation()
                        mock.removeViewInLayout(fromView)
                        container.removeViewInLayout(mock)
                    } catch (ignored: Exception) {
                    }
                }, exitAnim.duration)
            }
        }
    }

    private fun addMockView(fromView: View, container: ViewGroup): ViewGroup {
        val mock: ViewGroup = object : ViewGroup(mActivity) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        }
        mock.addView(fromView)
        container.addView(mock)
        return mock
    }

    private fun getTopFragmentForStart(
        from: ISupportFragment?,
        fm: FragmentManager
    ): ISupportFragment? {
        val top: ISupportFragment? = if (from == null) {
            getTopFragment(fm)
        } else {
            if (from.supportDelegate.mContainerId == 0) {
                val fromF = from as Fragment
                check(!(fromF.tag != null && !fromF.tag!!.startsWith("android:switcher:"))) { "Can't find container, please call loadRootFragment() first!" }
            }
            getTopFragment(fm, from.supportDelegate.mContainerId)
        }
        return top
    }

    /**
     * Dispatch the pop-event. Priority of the top of the stack of Fragment
     */
    fun dispatchBackPressedEvent(activeFragment: ISupportFragment?): Boolean {
        if (activeFragment != null) {
            val result = activeFragment.onBackPressedSupport()
            if (result) {
                return true
            }
            val parentFragment = (activeFragment as Fragment).parentFragment
            if (dispatchBackPressedEvent(parentFragment as ISupportFragment?)) {
                return true
            }
        }
        return false
    }

    fun handleResultRecord(from: Fragment) {
        try {
            val args = from.arguments ?: return
            val resultRecord = args.getParcelable<ResultRecord>(FRAGMENTATION_ARG_RESULT_RECORD)
                ?: return
            val targetFragment = from.fragmentManager!!
                .getFragment(from.arguments!!, FRAGMENTATION_STATE_SAVE_RESULT) as ISupportFragment?
            targetFragment!!.onFragmentResult(
                resultRecord.requestCode,
                resultRecord.resultCode,
                resultRecord.resultBundle
            )
        } catch (ignored: IllegalStateException) {
            // Fragment no longer exists
        }
    }

    private fun enqueue(fm: FragmentManager?, action: Action) {
        if (fm == null) {
            Log.w(TAG, "FragmentManager is null, skip the action!")
            return
        }
        mActionQueue.enqueue(action)
    }

    private fun bindContainerId(containerId: Int, to: ISupportFragment) {
        val args = getArguments(to as Fragment)
        args.putInt(FRAGMENTATION_ARG_CONTAINER, containerId)
    }

    private fun getArguments(fragment: Fragment): Bundle {
        var bundle = fragment.arguments
        if (bundle == null) {
            bundle = Bundle()
            fragment.arguments = bundle
        }
        return bundle
    }

    private fun supportCommit(fm: FragmentManager, transaction: FragmentTransaction) {
        handleAfterSaveInStateTransactionException(fm, "commit()")
        transaction.commitAllowingStateLoss()
    }

    private fun handleLaunchMode(
        fm: FragmentManager,
        topFragment: ISupportFragment?,
        to: ISupportFragment,
        toFragmentTag: String,
        launchMode: Int
    ): Boolean {
        if (topFragment == null) return false
        val stackToFragment = findBackStackFragment(to.javaClass, toFragmentTag, fm)
            ?: return false
        if (launchMode == ISupportFragment.SINGLE_TOP) {
            if (to === topFragment || to.javaClass.name == topFragment.javaClass.name) {
                handleNewBundle(to, stackToFragment)
                return true
            }
        } else if (launchMode == ISupportFragment.SINGLE_TASK) {
            doPopTo(toFragmentTag, false, fm, DEFAULT_POP_TO_ANIM)
            mHandler.post { handleNewBundle(to, stackToFragment) }
            return true
        }
        return false
    }

    private fun handleNewBundle(toFragment: ISupportFragment, stackToFragment: ISupportFragment) {
        val argsNewBundle = toFragment.supportDelegate.mNewBundle
        val args = getArguments(toFragment as Fragment)
        if (args.containsKey(FRAGMENTATION_ARG_CONTAINER)) {
            args.remove(FRAGMENTATION_ARG_CONTAINER)
        }
        if (argsNewBundle != null) {
            args.putAll(argsNewBundle)
        }
        stackToFragment.onNewBundle(args)
    }

    /**
     * save requestCode
     */
    private fun saveRequestCode(
        fm: FragmentManager,
        from: Fragment,
        to: Fragment,
        requestCode: Int
    ) {
        val bundle = getArguments(to)
        val resultRecord = ResultRecord()
        resultRecord.requestCode = requestCode
        bundle.putParcelable(FRAGMENTATION_ARG_RESULT_RECORD, resultRecord)
        fm.putFragment(bundle, FRAGMENTATION_STATE_SAVE_RESULT, from)
    }

    private fun findContainerById(fragment: Fragment?, containerId: Int): ViewGroup? {
        if (fragment!!.view == null) return null
        val container: View?
        val parentFragment = fragment.parentFragment
        container = if (parentFragment != null) {
            if (parentFragment.view != null) {
                parentFragment.view!!.findViewById<View>(containerId)
            } else {
                findContainerById(parentFragment, containerId)
            }
        } else {
            mActivity.findViewById(containerId)
        }
        return if (container is ViewGroup) {
            container
        } else null
    }

    private fun handleAfterSaveInStateTransactionException(fm: FragmentManager, action: String) {
        val stateSaved = fm.isStateSaved
        if (stateSaved) {
            val e = AfterSaveStateTransactionWarning(action)
            Fragmentation.default.handler.onException(e)
        }
    }

    companion object {
        const val DEFAULT_POP_TO_ANIM = Int.MAX_VALUE
        private const val TAG = "Fragmentation"
        const val FRAGMENTATION_ARG_RESULT_RECORD = "fragment_arg_result_record"
        const val FRAGMENTATION_ARG_ROOT_STATUS = "fragmentation_arg_root_status"
        const val FRAGMENTATION_ARG_IS_SHARED_ELEMENT = "fragmentation_arg_is_shared_element"
        const val FRAGMENTATION_ARG_CONTAINER = "fragmentation_arg_container"
        const val FRAGMENTATION_ARG_REPLACE = "fragmentation_arg_replace"
        const val FRAGMENTATION_ARG_CUSTOM_ENTER_ANIM = "fragmentation_arg_custom_enter_anim"
        const val FRAGMENTATION_ARG_CUSTOM_EXIT_ANIM = "fragmentation_arg_custom_exit_anim"
        const val FRAGMENTATION_ARG_CUSTOM_POP_EXIT_ANIM = "fragmentation_arg_custom_pop_exit_anim"
        const val FRAGMENTATION_STATE_SAVE_ANIMATOR = "fragmentation_state_save_animator"
        const val FRAGMENTATION_STATE_SAVE_IS_HIDDEN = "fragmentation_state_save_status"
        private const val FRAGMENTATION_STATE_SAVE_RESULT = "fragmentation_state_save_result"
        const val TYPE_ADD = 0
        const val TYPE_ADD_RESULT = 1
        const val TYPE_ADD_WITHOUT_HIDE = 2
        const val TYPE_ADD_RESULT_WITHOUT_HIDE = 3
        const val TYPE_REPLACE = 10
        const val TYPE_REPLACE_DON_T_BACK = 11
    }
}