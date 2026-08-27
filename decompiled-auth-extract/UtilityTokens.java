package com.vk.api.sdk.auth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import kotlin.collections.EmptyList;
import org.json.JSONArray;
import org.json.JSONObject;
import xsna.j8w;
import xsna.m63;

/* compiled from: UtilityToken.kt */
/* loaded from: /home/z/my-project/vk-dex/classes.dex */
public final class UtilityTokens implements Parcelable {
    public static final a CREATOR = new a();
    public static final UtilityTokens c = new UtilityTokens((List<UtilityToken>) EmptyList.b);
    public final List<UtilityToken> b;

    /* compiled from: UtilityToken.kt */
    public static final class a implements Parcelable.Creator<UtilityTokens> {
        public static UtilityTokens a(JSONObject jSONObject) {
            JSONArray optJSONArray = jSONObject.optJSONArray("user_session");
            if (optJSONArray == null) {
                optJSONArray = new JSONArray();
            }
            ArrayList arrayList = new ArrayList();
            int length = optJSONArray.length();
            for (int i = 0; i < length; i++) {
                JSONObject optJSONObject = optJSONArray.optJSONObject(i);
                if (optJSONObject != null) {
                    UtilityToken.CREATOR.getClass();
                    arrayList.add(new UtilityToken(optJSONObject.optString("target_key"), optJSONObject.optString("token")));
                }
            }
            return new UtilityTokens(arrayList);
        }

        @Override // android.os.Parcelable.Creator
        public final UtilityTokens createFromParcel(Parcel parcel) {
            return new UtilityTokens(parcel);
        }

        @Override // android.os.Parcelable.Creator
        public final UtilityTokens[] newArray(int i) {
            return new UtilityTokens[i];
        }
    }

    /* JADX WARN: Illegal instructions before constructor call */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public UtilityTokens(android.os.Parcel r3) {
        /*
            r2 = this;
            java.util.ArrayList r0 = new java.util.ArrayList
            r0.<init>()
            com.vk.api.sdk.auth.UtilityToken$a r1 = com.vk.api.sdk.auth.UtilityToken.CREATOR
            r3.readTypedList(r0, r1)
            r2.<init>(r0)
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.vk.api.sdk.auth.UtilityTokens.<init>(android.os.Parcel):void");
    }

    public UtilityTokens(List<UtilityToken> list) {
        this.b = list;
    }

    public final JSONObject c() {
        JSONArray jSONArray = new JSONArray();
        for (UtilityToken utilityToken : this.b) {
            utilityToken.getClass();
            jSONArray.put(new JSONObject().put("target_key", utilityToken.b).put("token", utilityToken.c));
        }
        return new JSONObject().put("user_session", jSONArray);
    }

    @Override // android.os.Parcelable
    public final int describeContents() {
        return 0;
    }

    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return (obj instanceof UtilityTokens) && j8w.f(this.b, ((UtilityTokens) obj).b);
    }

    public final int hashCode() {
        return this.b.hashCode();
    }

    public final String toString() {
        return m63.b("UtilityTokens(list=", ")", this.b);
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        parcel.writeTypedList(this.b);
    }
}
