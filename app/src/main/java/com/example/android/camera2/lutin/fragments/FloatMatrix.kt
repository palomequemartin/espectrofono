package com.example.android.camera2.lutin.fragments

import android.os.Parcel
import android.os.Parcelable

data class FloatMatrix(
    val matrix: MutableList<MutableList<Float>>
) : Parcelable {
    constructor(parcel: Parcel) : this(
        mutableListOf<MutableList<Float>>().apply {
            val rows = parcel.readInt()
            repeat(rows) {
                val cols = parcel.readInt()
                val row = mutableListOf<Float>()
                parcel.readFloatArray(FloatArray(cols).also { row.addAll(it.toList()) })
                add(row)
            }
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(matrix.size)
        matrix.forEach { row ->
            parcel.writeInt(row.size)
            parcel.writeFloatArray(row.toFloatArray())
        }
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FloatMatrix> {
        override fun createFromParcel(parcel: Parcel): FloatMatrix = FloatMatrix(parcel)
        override fun newArray(size: Int): Array<FloatMatrix?> = arrayOfNulls(size)
    }
}