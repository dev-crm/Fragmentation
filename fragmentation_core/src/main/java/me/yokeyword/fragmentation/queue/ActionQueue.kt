package me.yokeyword.fragmentation.queue

import android.os.Handler
import me.yokeyword.fragmentation.SupportHelper.getBackStackTopFragment
import java.util.Queue
import java.util.LinkedList
import android.os.Looper

/**
 * The queue of perform action.
 *
 * Created by YoKey on 17/12/29.
 */
class ActionQueue(private val mMainHandler: Handler) {
    private val mQueue: Queue<Action> = LinkedList()
    fun enqueue(action: Action) {
        if (isThrottleBACK(action)) return
        if (action.action == Action.ACTION_LOAD && mQueue.isEmpty()
            && Thread.currentThread() === Looper.getMainLooper().thread
        ) {
            action.run()
            return
        }
        mMainHandler.post { enqueueAction(action) }
    }

    private fun enqueueAction(action: Action) {
        mQueue.add(action)
        //第一次进来的时候，执行完上局，队列只有一个，一旦进入handleAction，就会一直执行，直到队列为空
        if (mQueue.size == 1) {
            handleAction()
        }
    }

    private fun handleAction() {
        if (mQueue.isEmpty()) return
        val action = mQueue.peek()
        if (action == null || action.fragmentManager!!.isStateSaved) {
            mQueue.clear()
            return
        }
        action.run()
        executeNextAction(action)
    }

    private fun executeNextAction(action: Action) {
        if (action.action == Action.ACTION_POP) {
            val top = getBackStackTopFragment(action.fragmentManager!!)
            action.duration = top?.supportDelegate?.exitAnimDuration ?: Action.DEFAULT_POP_TIME
        }
        mMainHandler.postDelayed({
            mQueue.poll()
            handleAction()
        }, action.duration)
    }

    private fun isThrottleBACK(action: Action): Boolean {
        if (action.action == Action.ACTION_BACK) {
            val head = mQueue.peek()
            if (head != null && head.action == Action.ACTION_POP) {
                return true
            }
        }
        return false
    }
}