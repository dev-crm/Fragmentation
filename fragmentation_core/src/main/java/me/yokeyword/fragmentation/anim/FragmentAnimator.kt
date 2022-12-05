package me.yokeyword.fragmentation.anim

import android.os.Parcelable
import androidx.annotation.AnimRes
import android.os.Parcel
import android.os.Parcelable.Creator

/**
 * Fragment动画实体类
 * Created by YoKeyword on 16/2/4.
 */
open class FragmentAnimator : Parcelable {
    @AnimRes
    var enter = 0
        protected set

    @AnimRes
    var exit = 0
        protected set

    @AnimRes
    var popEnter = 0
        protected set

    @AnimRes
    var popExit = 0
        protected set

    constructor() {}
    constructor(enter: Int, exit: Int) {
        this.enter = enter
        this.exit = exit
    }

    constructor(enter: Int, exit: Int, popEnter: Int, popExit: Int) {
        this.enter = enter
        this.exit = exit
        this.popEnter = popEnter
        this.popExit = popExit
    }

    fun copy(): FragmentAnimator {
        return FragmentAnimator(enter, exit, popEnter, popExit)
    }

    protected constructor(`in`: Parcel) {
        enter = `in`.readInt()
        exit = `in`.readInt()
        popEnter = `in`.readInt()
        popExit = `in`.readInt()
    }

    fun setEnter(enter: Int): FragmentAnimator {
        this.enter = enter
        return this
    }

    /**
     * enter animation
     */
    fun setExit(exit: Int): FragmentAnimator {
        this.exit = exit
        return this
    }

    fun setPopEnter(popEnter: Int): FragmentAnimator {
        this.popEnter = popEnter
        return this
    }

    fun setPopExit(popExit: Int): FragmentAnimator {
        this.popExit = popExit
        return this
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(enter)
        dest.writeInt(exit)
        dest.writeInt(popEnter)
        dest.writeInt(popExit)
    }

    companion object CREATOR : Creator<FragmentAnimator> {
        override fun createFromParcel(parcel: Parcel): FragmentAnimator {
            return FragmentAnimator(parcel)
        }

        override fun newArray(size: Int): Array<FragmentAnimator?> {
            return arrayOfNulls(size)
        }
    }
}