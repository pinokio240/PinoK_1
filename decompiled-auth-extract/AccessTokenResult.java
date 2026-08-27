package com.vk.auth.oauth.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

/* compiled from: AccessTokenResult.kt */
/* loaded from: /home/z/my-project/vk-dex/classes15.dex */
public final class AccessTokenResult implements Parcelable {
    public static final Parcelable.Creator<AccessTokenResult> CREATOR = new a();
    public final String b;
    public final String c;

    /* compiled from: AccessTokenResult.kt */
    public static final class a implements Parcelable.Creator<AccessTokenResult> {
        @Override // android.os.Parcelable.Creator
        public final AccessTokenResult createFromParcel(Parcel parcel) {
            return new AccessTokenResult(parcel.readString(), parcel.readString());
        }

        @Override // android.os.Parcelable.Creator
        public final AccessTokenResult[] newArray(int i) {
            return new AccessTokenResult[i];
        }
    }

    public AccessTokenResult(String str, String str2) {
        this.b = str;
        this.c = str2;
    }

    @Override // android.os.Parcelable
    public final int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.b);
        parcel.writeString(this.c);
    }
}
