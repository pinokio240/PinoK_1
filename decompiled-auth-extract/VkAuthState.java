package com.vk.superapp.api.states;

import com.vk.core.serialize.Serializer;
import com.vk.superapp.api.dto.auth.VkAuthCredentials;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kotlin.collections.EmptyList;
import xsna.feo;
import xsna.j8w;
import xsna.nck;
import xsna.uho0;
import xsna.zck0;

/* compiled from: VkAuthState.kt */
/* loaded from: /home/z/my-project/vk-dex/classes10.dex */
public final class VkAuthState extends Serializer.StreamParcelableAdapter {
    public static final Serializer.c<VkAuthState> CREATOR = new b();
    public String b;
    public String c;
    public LinkedHashMap d;
    public ArrayList e;

    /* compiled from: VkAuthState.kt */
    public static final class a {
        public static VkAuthState a(String str, String str2, String str3, String str4, String str5, String str6) {
            VkAuthState vkAuthState = new VkAuthState(null);
            vkAuthState.d.put("grant_type", "vk_external_auth");
            vkAuthState.d.put("vk_service", str);
            vkAuthState.d.put("vk_external_code", str2);
            vkAuthState.d.put("vk_external_client_id", str3);
            vkAuthState.d.put("vk_external_redirect_uri", str4);
            if (str5 != null) {
                vkAuthState.d.put("code_verifier", str5);
            }
            if (str6 != null) {
                vkAuthState.d.put("nonce", str6);
            }
            vkAuthState.d.put("2fa_supported", "1");
            return vkAuthState;
        }

        public static VkAuthState b(String str, String str2, String str3, boolean z) {
            VkAuthState vkAuthState = new VkAuthState(null);
            if (str3 != null) {
                vkAuthState.d.put("sid", str3);
                if (z) {
                    vkAuthState.d.put("grant_type", "phone_confirmation_sid");
                } else {
                    vkAuthState.d.put("grant_type", "password");
                }
            } else {
                vkAuthState.d.put("grant_type", "password");
            }
            vkAuthState.d.put("username", str);
            vkAuthState.d.put("password", str2);
            vkAuthState.d.put("2fa_supported", "1");
            vkAuthState.ob("supported_ways", "push");
            vkAuthState.ob("supported_ways", "email");
            return vkAuthState;
        }

        public static VkAuthState c() {
            Serializer.c<VkAuthState> cVar = VkAuthState.CREATOR;
            EmptyList emptyList = EmptyList.b;
            VkAuthState vkAuthState = new VkAuthState(null);
            vkAuthState.e.addAll(emptyList);
            return vkAuthState;
        }

        public static VkAuthState d(String str, String str2, boolean z, boolean z2) {
            VkAuthState vkAuthState = new VkAuthState(null);
            if (z) {
                vkAuthState.d.put("grant_type", "without_password");
                vkAuthState.d.put("password", "");
            } else {
                vkAuthState.d.put("grant_type", "phone_confirmation_sid");
            }
            if (str != null) {
                vkAuthState.d.put("sid", str);
            } else {
                uho0.a.getClass();
                uho0.e("Sid is null on Auth, but it shouldn't be empty");
            }
            vkAuthState.d.put("username", str2);
            if (z2) {
                vkAuthState.d.put("additional_sign_up_agreement_showed", "1");
            }
            vkAuthState.ob("supported_ways", "push");
            vkAuthState.ob("supported_ways", "email");
            return vkAuthState;
        }

        public static VkAuthState e(String str, String str2) {
            VkAuthState vkAuthState = new VkAuthState(null);
            vkAuthState.d.put("grant_type", "trusted_hash");
            vkAuthState.d.put("password", "");
            vkAuthState.d.put("username", str2);
            vkAuthState.d.put("sid", str);
            return vkAuthState;
        }
    }

    /* compiled from: Serializer.kt */
    public static final class b extends Serializer.c<VkAuthState> {
        @Override // com.vk.core.serialize.Serializer.c
        public final VkAuthState a(Serializer serializer) {
            LinkedHashMap linkedHashMap;
            VkAuthState vkAuthState = new VkAuthState(null);
            vkAuthState.b = serializer.H();
            vkAuthState.c = serializer.H();
            HashMap<ClassLoader, HashMap<String, Serializer.c<?>>> hashMap = Serializer.a;
            try {
                int u = serializer.u();
                if (u >= 0) {
                    linkedHashMap = new LinkedHashMap();
                    for (int i = 0; i < u; i++) {
                        String H = serializer.H();
                        String H2 = serializer.H();
                        if (H != null && H2 != null) {
                            linkedHashMap.put(H, H2);
                        }
                    }
                } else {
                    linkedHashMap = feo.b;
                }
                vkAuthState.d = new LinkedHashMap(linkedHashMap);
                vkAuthState.e = serializer.D();
                return vkAuthState;
            } finally {
            }
        }

        @Override // android.os.Parcelable.Creator
        public final Object[] newArray(int i) {
            return new VkAuthState[i];
        }
    }

    public VkAuthState() {
        this.d = new LinkedHashMap();
        this.e = new ArrayList();
    }

    public /* synthetic */ VkAuthState(nck nckVar) {
        this();
    }

    @Override // com.vk.core.serialize.Serializer.StreamParcelable
    public final void J7(Serializer serializer) {
        serializer.j0(this.b);
        serializer.j0(this.c);
        LinkedHashMap linkedHashMap = this.d;
        if (linkedHashMap == null) {
            serializer.S(-1);
        } else {
            serializer.S(linkedHashMap.size());
            for (Map.Entry entry : linkedHashMap.entrySet()) {
                serializer.j0((String) entry.getKey());
                serializer.j0((String) entry.getValue());
            }
        }
        serializer.h0(this.e);
    }

    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!VkAuthState.class.equals(obj != null ? obj.getClass() : null)) {
            return false;
        }
        VkAuthState vkAuthState = (VkAuthState) obj;
        return j8w.f(this.b, vkAuthState.b) && j8w.f(this.c, vkAuthState.c) && j8w.f(this.d, vkAuthState.d) && j8w.f(this.e, vkAuthState.e);
    }

    public final int hashCode() {
        return Objects.hash(this.b, this.c, this.d, this.e);
    }

    public final void ob(String str, String str2) {
        String str3 = (String) this.d.get(str);
        if (str3 == null || !zck0.D(str3, str2, false)) {
            StringBuilder sb = new StringBuilder(str3 == null ? "" : str3);
            if (str3 != null && str3.length() != 0) {
                sb.append(",");
            }
            sb.append(str2);
            this.d.put(str, sb.toString());
        }
    }

    public final VkAuthCredentials pb() {
        String str = (String) this.d.get("username");
        String str2 = (String) this.d.get("password");
        if (str == null || str.length() == 0) {
            return null;
        }
        return new VkAuthCredentials(str, str2);
    }
}
