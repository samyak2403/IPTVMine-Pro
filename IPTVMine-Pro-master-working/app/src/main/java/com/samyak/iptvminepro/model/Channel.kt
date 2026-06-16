package com.samyak.iptvminepro.model

import android.os.Parcel
import android.os.Parcelable

data class Channel(
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val streamType: String = "HTTP",
    val category: String = "Uncategorized",
    val team1: String = "",
    val team2: String = "",
    val status: String = "UNKNOWN",
    val startTime: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "HTTP",
        parcel.readString() ?: "Uncategorized",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "UNKNOWN",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(logoUrl)
        parcel.writeString(streamUrl)
        parcel.writeString(streamType)
        parcel.writeString(category)
        parcel.writeString(team1)
        parcel.writeString(team2)
        parcel.writeString(status)
        parcel.writeString(startTime)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Channel> {
        override fun createFromParcel(parcel: Parcel): Channel {
            return Channel(parcel)
        }

        override fun newArray(size: Int): Array<Channel?> {
            return arrayOfNulls(size)
        }
    }
}

