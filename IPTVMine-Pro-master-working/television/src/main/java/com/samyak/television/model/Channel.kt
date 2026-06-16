package com.samyak.television.model

import android.os.Parcel
import android.os.Parcelable

data class Channel(
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val streamType: String = "HTTP",
    val category: String = "Uncategorized"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "HTTP",
        parcel.readString() ?: "Uncategorized"
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(logoUrl)
        parcel.writeString(streamUrl)
        parcel.writeString(streamType)
        parcel.writeString(category)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Channel> {
        override fun createFromParcel(parcel: Parcel): Channel = Channel(parcel)
        override fun newArray(size: Int): Array<Channel?> = arrayOfNulls(size)
    }
}
