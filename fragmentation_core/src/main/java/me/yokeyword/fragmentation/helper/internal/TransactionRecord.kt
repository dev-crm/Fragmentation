package me.yokeyword.fragmentation.helper.internal

import java.util.ArrayList
import android.view.View

/**
 * @hide Created by YoKey on 16/11/25.
 */
class TransactionRecord {
    var tag: String? = null
    var targetFragmentEnter = Int.MIN_VALUE
    var currentFragmentPopExit = Int.MIN_VALUE
    var currentFragmentPopEnter = Int.MIN_VALUE
    var targetFragmentExit = Int.MIN_VALUE
    var dontAddToBackStack = false
    var sharedElementList: MutableList<SharedElement> = mutableListOf()

    class SharedElement(var sharedElement: View, var sharedName: String)
}