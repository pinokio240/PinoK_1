package com.vk.api.sdk.auth;

import android.os.Parcel;
import android.os.Parcelable;
import xsna.j8w;
import xsna.ze9;

/* compiled from: UtilityToken.kt */
/* loaded from: /home/z/my-project/vk-dex/classes.dex */
public final class UtilityToken implements Parcelable {
    public static final a CREATOR = new a();
    public final String b;
    public final String c;

    /* compiled from: UtilityToken.kt */
    public static final class a implements Parcelable.Creator<UtilityToken> {
        @Override // android.os.Parcelable.Creator
        public final UtilityToken createFromParcel(Parcel parcel) {
            return new UtilityToken(parcel);
        }

        @Override // android.os.Parcelable.Creator
        public final UtilityToken[] newArray(int i) {
            return new UtilityToken[i];
        }
    }

    /* JADX WARN: Illegal instructions before constructor call */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public UtilityToken(android.os.Parcel r3) {
        /*
            r2 = this;
            java.lang.String r0 = r3.readString()
            java.lang.String r1 = ""
            if (r0 != 0) goto L9
            r0 = r1
        L9:
            java.lang.String r3 = r3.readString()
            if (r3 != 0) goto L10
            goto L11
        L10:
            r1 = r3
        L11:
            r2.<init>(r0, r1)
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.vk.api.sdk.auth.UtilityToken.<init>(android.os.Parcel):void");
    }

    public UtilityToken(String str, String str2) {
        this.b = str;
        this.c = str2;
    }

    @Override // android.os.Parcelable
    public final int describeContents() {
        return 0;
    }

    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UtilityToken)) {
            return false;
        }
        UtilityToken utilityToken = (UtilityToken) obj;
        return j8w.f(this.b, utilityToken.b) && j8w.f(this.c, utilityToken.c);
    }

    public final int hashCode() {
        return this.c.hashCode() + (this.b.hashCode() * 31);
    }

    public final String toString() {
        return ze9.a("UtilityToken(targetKey=", this.b, ", token=", this.c, ")");
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.b);
        parcel.writeString(this.c);
    }
}
