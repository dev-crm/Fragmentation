package me.yokeyword.fragmentation.helper.internal

import android.os.Parcelable
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable.Creator

/**
 * @Hide
 * Result 记录
 * Created by YoKeyword on 16/6/2.
 */
class ResultRecord : Parcelable {
    var requestCode = 0
    var resultCode = 0
    var resultBundle: Bundle? = null

    constructor()
    constructor(`in`: Parcel) {
        requestCode = `in`.readInt()
        resultCode = `in`.readInt()
        resultBundle = `in`.readBundle(javaClass.classLoader)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(requestCode)
        dest.writeInt(resultCode)
        dest.writeBundle(resultBundle)
    }

    companion object {
        @JvmField
        val CREATOR: Creator<ResultRecord?> = object : Creator<ResultRecord?> {
            override fun createFromParcel(`in`: Parcel): ResultRecord? {
                return ResultRecord(`in`)
            }

            override fun newArray(size: Int): Array<ResultRecord?> {
                return arrayOfNulls(size)
            }
        }
    }
}