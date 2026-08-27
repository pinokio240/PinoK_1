package com.vk.superapp.api.internal.oauthrequests;

import xsna.aqo;
import xsna.zpo;

/* JADX WARN: Failed to restore enum class, 'enum' modifier and super class removed */
/* JADX WARN: Unknown enum class pattern. Please report as an issue! */
/* compiled from: AuthByExchangeToken.kt */
/* loaded from: /home/z/my-project/vk-dex/classes10.dex */
public final class AuthByExchangeToken$Initiator {
    private static final /* synthetic */ zpo $ENTRIES;
    private static final /* synthetic */ AuthByExchangeToken$Initiator[] $VALUES;
    public static final AuthByExchangeToken$Initiator ADD_EDU_PROFILE;
    public static final AuthByExchangeToken$Initiator AUTHORIZATION;
    public static final AuthByExchangeToken$Initiator EXPIRED_TOKEN;
    public static final AuthByExchangeToken$Initiator NO_INITIATOR;
    public static final AuthByExchangeToken$Initiator SILENT_AUTHORIZATION;
    public static final AuthByExchangeToken$Initiator WEB_HANDLER_AUTHORIZATION;
    private final String value;

    static {
        AuthByExchangeToken$Initiator authByExchangeToken$Initiator = new AuthByExchangeToken$Initiator("NO_INITIATOR", 0, null);
        NO_INITIATOR = authByExchangeToken$Initiator;
        AuthByExchangeToken$Initiator authByExchangeToken$Initiator2 = new AuthByExchangeToken$Initiator("EXPIRED_TOKEN", 1, "expired_token");
        EXPIRED_TOKEN = authByExchangeToken$Initiator2;
        AuthByExchangeToken$Initiator authByExchangeToken$Initiator3 = new AuthByExchangeToken$Initiator("ADD_EDU_PROFILE", 2, "add_edu_profile");
        ADD_EDU_PROFILE = authByExchangeToken$Initiator3;
        AuthByExchangeToken$Initiator authByExchangeToken$Initiator4 = new AuthByExchangeToken$Initiator("AUTHORIZATION", 3, "authorization");
        AUTHORIZATION = authByExchangeToken$Initiator4;
        AuthByExchangeToken$Initiator authByExchangeToken$Initiator5 = new AuthByExchangeToken$Initiator("SILENT_AUTHORIZATION", 4, "silent_authorization");
        SILENT_AUTHORIZATION = authByExchangeToken$Initiator5;
        AuthByExchangeToken$Initiator authByExchangeToken$Initiator6 = new AuthByExchangeToken$Initiator("WEB_HANDLER_AUTHORIZATION", 5, "web_handler_authorization");
        WEB_HANDLER_AUTHORIZATION = authByExchangeToken$Initiator6;
        AuthByExchangeToken$Initiator[] authByExchangeToken$InitiatorArr = {authByExchangeToken$Initiator, authByExchangeToken$Initiator2, authByExchangeToken$Initiator3, authByExchangeToken$Initiator4, authByExchangeToken$Initiator5, authByExchangeToken$Initiator6};
        $VALUES = authByExchangeToken$InitiatorArr;
        $ENTRIES = new aqo(authByExchangeToken$InitiatorArr);
    }

    public AuthByExchangeToken$Initiator(String str, int i, String str2) {
        this.value = str2;
    }

    public static AuthByExchangeToken$Initiator valueOf(String str) {
        return (AuthByExchangeToken$Initiator) Enum.valueOf(AuthByExchangeToken$Initiator.class, str);
    }

    public static AuthByExchangeToken$Initiator[] values() {
        return (AuthByExchangeToken$Initiator[]) $VALUES.clone();
    }

    public final String d() {
        return this.value;
    }
}
