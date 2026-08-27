package com.vk.auth.api.models;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.vk.api.sdk.auth.UtilityTokens;
import com.vk.dto.common.id.UserId;
import com.vk.superapp.api.dto.auth.AuthPayload;
import com.vk.superapp.api.dto.auth.AuthTarget;
import com.vk.superapp.api.dto.auth.PersonalData;
import com.vk.superapp.api.dto.auth.VkAuthCredentials;
import java.io.Serializable;
import java.util.ArrayList;
import xsna.h7c0;
import xsna.j8w;
import xsna.lff;
import xsna.ny4;
import xsna.ph;
import xsna.q5t;
import xsna.sn9;

/* compiled from: AuthResult.kt */
/* loaded from: /home/z/my-project/vk-dex/classes.dex */
public final class AuthResult implements Parcelable {
    public static final Parcelable.Creator<AuthResult> CREATOR = new a();
    public final String b;
    public final String c;
    public final UserId d;
    public final boolean e;
    public final int f;
    public final String g;
    public final VkAuthCredentials h;
    public final String i;
    public final String j;
    public final int k;
    public final ArrayList<String> l;
    public final int m;
    public final AuthPayload n;
    public final AuthTarget o;
    public final PersonalData p;
    public final long q;
    public final Bundle r;
    public final UtilityTokens s;
    public final String t;
    public final String u;
    public final String v;
    public final String w;

    /* compiled from: AuthResult.kt */
    public static final class a implements Parcelable.Creator<AuthResult> {
        @Override // android.os.Parcelable.Creator
        public final AuthResult createFromParcel(Parcel parcel) {
            String readString = parcel.readString();
            String readString2 = parcel.readString();
            UserId readParcelable = parcel.readParcelable(UserId.class.getClassLoader());
            boolean z = parcel.readInt() != 0;
            int readInt = parcel.readInt();
            boolean z2 = z;
            String readString3 = parcel.readString();
            VkAuthCredentials readParcelable2 = parcel.readParcelable(VkAuthCredentials.class.getClassLoader());
            String readString4 = parcel.readString();
            String readString5 = parcel.readString();
            int readInt2 = parcel.readInt();
            Serializable readSerializable = parcel.readSerializable();
            ArrayList arrayList = readSerializable instanceof ArrayList ? (ArrayList) readSerializable : null;
            int readInt3 = parcel.readInt();
            AuthPayload readParcelable3 = parcel.readParcelable(AuthPayload.class.getClassLoader());
            AuthTarget readParcelable4 = parcel.readParcelable(AuthTarget.class.getClassLoader());
            PersonalData readParcelable5 = parcel.readParcelable(PersonalData.class.getClassLoader());
            ArrayList arrayList2 = arrayList;
            long readLong = parcel.readLong();
            Bundle bundle = (Bundle) parcel.readParcelable(Bundle.class.getClassLoader());
            UtilityTokens utilityTokens = (UtilityTokens) parcel.readParcelable(UtilityTokens.class.getClassLoader());
            if (utilityTokens == null) {
                UtilityTokens.CREATOR.getClass();
                utilityTokens = UtilityTokens.c;
            }
            return new AuthResult(readString, readString2, readParcelable, z2, readInt, readString3, readParcelable2, readString4, readString5, readInt2, arrayList2, readInt3, readParcelable3, readParcelable4, readParcelable5, readLong, bundle, utilityTokens, parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString());
        }

        @Override // android.os.Parcelable.Creator
        public final AuthResult[] newArray(int i) {
            return new AuthResult[i];
        }
    }

    public AuthResult(String str, String str2, UserId userId, boolean z, int i, String str3, VkAuthCredentials vkAuthCredentials, String str4, String str5, int i2, ArrayList<String> arrayList, int i3, AuthPayload authPayload, AuthTarget authTarget, PersonalData personalData, long j, Bundle bundle, UtilityTokens utilityTokens, String str6, String str7, String str8, String str9) {
        this.b = str;
        this.c = str2;
        this.d = userId;
        this.e = z;
        this.f = i;
        this.g = str3;
        this.h = vkAuthCredentials;
        this.i = str4;
        this.j = str5;
        this.k = i2;
        this.l = arrayList;
        this.m = i3;
        this.n = authPayload;
        this.o = authTarget;
        this.p = personalData;
        this.q = j;
        this.r = bundle;
        this.s = utilityTokens;
        this.t = str6;
        this.u = str7;
        this.v = str8;
        this.w = str9;
    }

    /*  JADX ERROR: NullPointerException in pass: InitCodeVariables
        java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.SSAVar.getPhiList()" because "resultVar" is null
        	at jadx.core.dex.visitors.InitCodeVariables.collectConnectedVars(InitCodeVariables.java:119)
        	at jadx.core.dex.visitors.InitCodeVariables.setCodeVar(InitCodeVariables.java:82)
        	at jadx.core.dex.visitors.InitCodeVariables.initCodeVar(InitCodeVariables.java:74)
        	at jadx.core.dex.visitors.InitCodeVariables.initCodeVars(InitCodeVariables.java:48)
        	at jadx.core.dex.visitors.InitCodeVariables.visit(InitCodeVariables.java:29)
        */
    public AuthResult(java.lang.String r27, java.lang.String r28, com.vk.dto.common.id.UserId r29, boolean r30, int r31, java.lang.String r32, com.vk.superapp.api.dto.auth.VkAuthCredentials r33, java.lang.String r34, java.lang.String r35, int r36, java.util.ArrayList r37, int r38, com.vk.superapp.api.dto.auth.AuthPayload r39, com.vk.superapp.api.dto.auth.AuthTarget r40, com.vk.superapp.api.dto.auth.PersonalData r41, long r42, android.os.Bundle r44, com.vk.api.sdk.auth.UtilityTokens r45, java.lang.String r46, java.lang.String r47, java.lang.String r48, java.lang.String r49, int r50, xsna.nck r51) {
        /*
            Method dump skipped, instructions count: 240
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.vk.auth.api.models.AuthResult.<init>(java.lang.String, java.lang.String, com.vk.dto.common.id.UserId, boolean, int, java.lang.String, com.vk.superapp.api.dto.auth.VkAuthCredentials, java.lang.String, java.lang.String, int, java.util.ArrayList, int, com.vk.superapp.api.dto.auth.AuthPayload, com.vk.superapp.api.dto.auth.AuthTarget, com.vk.superapp.api.dto.auth.PersonalData, long, android.os.Bundle, com.vk.api.sdk.auth.UtilityTokens, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, xsna.nck):void");
    }

    public static AuthResult a(AuthResult authResult, String str, VkAuthCredentials vkAuthCredentials, AuthPayload authPayload, AuthTarget authTarget, long j, Bundle bundle, String str2, int i) {
        String str3;
        long j2;
        String str4 = authResult.b;
        String str5 = authResult.c;
        UserId userId = authResult.d;
        boolean z = authResult.e;
        int i2 = authResult.f;
        String str6 = (i & 32) != 0 ? authResult.g : str;
        VkAuthCredentials vkAuthCredentials2 = (i & 64) != 0 ? authResult.h : vkAuthCredentials;
        String str7 = authResult.i;
        String str8 = str6;
        VkAuthCredentials vkAuthCredentials3 = vkAuthCredentials2;
        String str9 = authResult.j;
        int i3 = authResult.k;
        ArrayList<String> arrayList = authResult.l;
        int i4 = authResult.m;
        AuthPayload authPayload2 = (i & 4096) != 0 ? authResult.n : authPayload;
        AuthTarget authTarget2 = (i & 8192) != 0 ? authResult.o : authTarget;
        PersonalData personalData = authResult.p;
        if ((i & 32768) != 0) {
            str3 = str5;
            j2 = authResult.q;
        } else {
            str3 = str5;
            j2 = j;
        }
        long j3 = j2;
        Bundle bundle2 = (i & 65536) != 0 ? authResult.r : bundle;
        UtilityTokens utilityTokens = authResult.s;
        String str10 = authResult.t;
        String str11 = authResult.u;
        String str12 = authResult.v;
        String str13 = (i & 2097152) != 0 ? authResult.w : str2;
        authResult.getClass();
        return new AuthResult(str4, str3, userId, z, i2, str8, vkAuthCredentials3, str7, str9, i3, arrayList, i4, authPayload2, authTarget2, personalData, j3, bundle2, utilityTokens, str10, str11, str12, str13);
    }

    @Override // android.os.Parcelable
    public final int describeContents() {
        return 0;
    }

    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AuthResult)) {
            return false;
        }
        AuthResult authResult = (AuthResult) obj;
        return j8w.f(this.b, authResult.b) && j8w.f(this.c, authResult.c) && j8w.f(this.d, authResult.d) && this.e == authResult.e && this.f == authResult.f && j8w.f(this.g, authResult.g) && j8w.f(this.h, authResult.h) && j8w.f(this.i, authResult.i) && j8w.f(this.j, authResult.j) && this.k == authResult.k && j8w.f(this.l, authResult.l) && this.m == authResult.m && j8w.f(this.n, authResult.n) && j8w.f(this.o, authResult.o) && j8w.f(this.p, authResult.p) && this.q == authResult.q && j8w.f(this.r, authResult.r) && j8w.f(this.s, authResult.s) && j8w.f(this.t, authResult.t) && j8w.f(this.u, authResult.u) && j8w.f(this.v, authResult.v) && j8w.f(this.w, authResult.w);
    }

    public final int hashCode() {
        int hashCode = this.b.hashCode() * 31;
        String str = this.c;
        int a2 = q5t.a(this.f, sn9.b(ph.a((hashCode + (str == null ? 0 : str.hashCode())) * 31, 31, this.d.b), 31, this.e), 31);
        String str2 = this.g;
        int hashCode2 = (a2 + (str2 == null ? 0 : str2.hashCode())) * 31;
        VkAuthCredentials vkAuthCredentials = this.h;
        int a3 = q5t.a(this.k, lff.c(lff.c((hashCode2 + (vkAuthCredentials == null ? 0 : vkAuthCredentials.hashCode())) * 31, 31, this.i), 31, this.j), 31);
        ArrayList<String> arrayList = this.l;
        int a4 = q5t.a(this.m, (a3 + (arrayList == null ? 0 : arrayList.hashCode())) * 31, 31);
        AuthPayload authPayload = this.n;
        int hashCode3 = (this.o.hashCode() + ((a4 + (authPayload == null ? 0 : authPayload.hashCode())) * 31)) * 31;
        PersonalData personalData = this.p;
        int a5 = ph.a((hashCode3 + (personalData == null ? 0 : personalData.hashCode())) * 31, 31, this.q);
        Bundle bundle = this.r;
        int a6 = h7c0.a((a5 + (bundle == null ? 0 : bundle.hashCode())) * 31, 31, this.s.b);
        String str3 = this.t;
        int hashCode4 = (a6 + (str3 == null ? 0 : str3.hashCode())) * 31;
        String str4 = this.u;
        int hashCode5 = (hashCode4 + (str4 == null ? 0 : str4.hashCode())) * 31;
        String str5 = this.v;
        int hashCode6 = (hashCode5 + (str5 == null ? 0 : str5.hashCode())) * 31;
        String str6 = this.w;
        return hashCode6 + (str6 != null ? str6.hashCode() : 0);
    }

    public final String toString() {
        StringBuilder sb = new StringBuilder("AuthResult(accessToken=");
        sb.append(this.b);
        sb.append(", secret=");
        sb.append(this.c);
        sb.append(", uid=");
        sb.append(this.d);
        sb.append(", httpsRequired=");
        sb.append(this.e);
        sb.append(", expiresIn=");
        sb.append(this.f);
        sb.append(", trustedHash=");
        sb.append(this.g);
        sb.append(", authCredentials=");
        sb.append(this.h);
        sb.append(", webviewAccessToken=");
        sb.append(this.i);
        sb.append(", webviewRefreshToken=");
        sb.append(this.j);
        sb.append(", webviewExpired=");
        sb.append(this.k);
        sb.append(", authCookies=");
        sb.append(this.l);
        sb.append(", webviewRefreshTokenExpired=");
        sb.append(this.m);
        sb.append(", authPayload=");
        sb.append(this.n);
        sb.append(", authTarget=");
        sb.append(this.o);
        sb.append(", personalData=");
        sb.append(this.p);
        sb.append(", createdMs=");
        sb.append(this.q);
        sb.append(", metadata=");
        sb.append(this.r);
        sb.append(", utilityTokens=");
        sb.append(this.s);
        sb.append(", phoneToActualize=");
        sb.append(this.t);
        sb.append(", email=");
        sb.append(this.u);
        sb.append(", silentToken=");
        sb.append(this.v);
        sb.append(", silentTokenUuid=");
        return ny4.a(sb, this.w, ')');
    }

    @Override // android.os.Parcelable
    public final void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.b);
        parcel.writeString(this.c);
        parcel.writeParcelable(this.d, 0);
        parcel.writeInt(this.e ? 1 : 0);
        parcel.writeInt(this.f);
        parcel.writeString(this.g);
        parcel.writeParcelable(this.h, 0);
        parcel.writeString(this.i);
        parcel.writeString(this.j);
        parcel.writeInt(this.k);
        parcel.writeSerializable(this.l);
        parcel.writeInt(this.m);
        parcel.writeParcelable(this.n, 0);
        parcel.writeParcelable(this.o, 0);
        parcel.writeParcelable(this.p, 0);
        parcel.writeLong(this.q);
        parcel.writeParcelable(this.r, 0);
        parcel.writeParcelable(this.s, 0);
        parcel.writeString(this.t);
        parcel.writeString(this.u);
        parcel.writeString(this.v);
        parcel.writeString(this.w);
    }
}
