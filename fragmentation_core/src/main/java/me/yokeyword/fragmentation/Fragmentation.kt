package me.yokeyword.fragmentation

import androidx.annotation.IntDef
import me.yokeyword.fragmentation.helper.ExceptionHandler
import java.lang.Exception

/**
 * Created by YoKey on 17/2/5.
 */
class Fragmentation internal constructor(builder: FragmentationBuilder) {
    private var isDebug: Boolean
    var mode = BUBBLE
    var handler: ExceptionHandler

    @IntDef(NONE, SHAKE, BUBBLE)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class StackViewMode

    init {
        isDebug = builder.debug
        mode = if (isDebug) {
            builder.mode
        } else {
            NONE
        }
        handler = builder.handler
    }

    class FragmentationBuilder {
        var debug = false
        var mode = 0
        var handler: ExceptionHandler = object : ExceptionHandler{
            override fun onException(e: Exception) {

            }
        }

        /**
         * @param debug Suppressed Exception("Can not perform this action after onSaveInstanceState!") when debug=false
         */
        fun debug(debug: Boolean): FragmentationBuilder {
            this.debug = debug
            return this
        }

        /**
         * Sets the mode to display the stack view
         *
         * None if debug(false).
         *
         * Default:NONE
         */
        fun stackViewMode(@StackViewMode mode: Int): FragmentationBuilder {
            this.mode = mode
            return this
        }

        /**
         * @param handler Handled Exception("Can not perform this action after onSaveInstanceState!") when debug=false.
         */
        fun handleException(handler: ExceptionHandler): FragmentationBuilder {
            this.handler = handler
            return this
        }

        fun install(): Fragmentation? {
            INSTANCE = Fragmentation(this)
            return INSTANCE
        }
    }

    companion object {
        /**
         * Dont display stack view.
         */
        const val NONE = 0

        /**
         * Shake it to display stack view.
         */
        const val SHAKE = 1

        /**
         * As a bubble display stack view.
         */
        const val BUBBLE = 2

        @Volatile
        var INSTANCE: Fragmentation? = null
        val default: Fragmentation
            get() {
                if (INSTANCE == null) {
                    synchronized(Fragmentation::class.java) {
                        if (INSTANCE == null) {
                            INSTANCE = Fragmentation(FragmentationBuilder())
                        }
                    }
                }
                return INSTANCE!!
            }

        @JvmStatic
        fun builder(): FragmentationBuilder {
            return FragmentationBuilder()
        }
    }
}