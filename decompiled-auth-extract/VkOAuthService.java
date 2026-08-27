package com.vk.auth.oauth;

import java.util.Iterator;
import xsna.aqo;
import xsna.j8w;
import xsna.zpo;

/* JADX WARN: Failed to restore enum class, 'enum' modifier and super class removed */
/* JADX WARN: Unknown enum class pattern. Please report as an issue! */
/* compiled from: VkOAuthService.kt */
/* loaded from: /home/z/my-project/vk-dex/classes15.dex */
public final class VkOAuthService {
    private static final /* synthetic */ zpo $ENTRIES;
    private static final /* synthetic */ VkOAuthService[] $VALUES;
    public static final VkOAuthService ALFA;
    public static final a Companion;
    public static final VkOAuthService ESIA;
    public static final VkOAuthService GOOGLE;
    public static final String KEY_EXTERNAL_AUTH_START_ARG = "vk_start_arg";
    public static final VkOAuthService MAILRU;
    public static final VkOAuthService OK;
    public static final VkOAuthService PASSKEY;
    public static final String PASSKEY_WEB_AUTH_DATA = "bundle_passkey_web_auth_data";
    public static final VkOAuthService SBER;
    public static final VkOAuthService TINKOFF;
    public static final VkOAuthService VK;
    public static final VkOAuthService VTB;
    public static final VkOAuthService YANDEX;
    private final String serviceName;

    /* compiled from: VkOAuthService.kt */
    public static final class a {
        public static VkOAuthService a(String str) {
            Object obj = null;
            if (str == null) {
                return null;
            }
            Iterator it = VkOAuthService.d().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Object next = it.next();
                if (j8w.f(((VkOAuthService) next).g(), str)) {
                    obj = next;
                    break;
                }
            }
            return (VkOAuthService) obj;
        }
    }

    static {
        VkOAuthService vkOAuthService = new VkOAuthService("MAILRU", 0, "mail_ru");
        MAILRU = vkOAuthService;
        VkOAuthService vkOAuthService2 = new VkOAuthService("GOOGLE", 1, "google_id");
        GOOGLE = vkOAuthService2;
        VkOAuthService vkOAuthService3 = new VkOAuthService("OK", 2, "ok_ru");
        OK = vkOAuthService3;
        VkOAuthService vkOAuthService4 = new VkOAuthService("VK", 3, "oauth_vkid");
        VK = vkOAuthService4;
        VkOAuthService vkOAuthService5 = new VkOAuthService("PASSKEY", 4, null);
        PASSKEY = vkOAuthService5;
        VkOAuthService vkOAuthService6 = new VkOAuthService("ESIA", 5, "esia");
        ESIA = vkOAuthService6;
        VkOAuthService vkOAuthService7 = new VkOAuthService("SBER", 6, "sber_id");
        SBER = vkOAuthService7;
        VkOAuthService vkOAuthService8 = new VkOAuthService("YANDEX", 7, "yandex_id");
        YANDEX = vkOAuthService8;
        VkOAuthService vkOAuthService9 = new VkOAuthService("TINKOFF", 8, "tinkoff_id");
        TINKOFF = vkOAuthService9;
        VkOAuthService vkOAuthService10 = new VkOAuthService("ALFA", 9, "alfa_id");
        ALFA = vkOAuthService10;
        VkOAuthService vkOAuthService11 = new VkOAuthService("VTB", 10, "vtb_id");
        VTB = vkOAuthService11;
        VkOAuthService[] vkOAuthServiceArr = {vkOAuthService, vkOAuthService2, vkOAuthService3, vkOAuthService4, vkOAuthService5, vkOAuthService6, vkOAuthService7, vkOAuthService8, vkOAuthService9, vkOAuthService10, vkOAuthService11};
        $VALUES = vkOAuthServiceArr;
        $ENTRIES = new aqo(vkOAuthServiceArr);
        Companion = new a();
    }

    public VkOAuthService(String str, int i, String str2) {
        this.serviceName = str2;
    }

    public static zpo<VkOAuthService> d() {
        return $ENTRIES;
    }

    public static VkOAuthService valueOf(String str) {
        return (VkOAuthService) Enum.valueOf(VkOAuthService.class, str);
    }

    public static VkOAuthService[] values() {
        return (VkOAuthService[]) $VALUES.clone();
    }

    public final String g() {
        return this.serviceName;
    }
}
