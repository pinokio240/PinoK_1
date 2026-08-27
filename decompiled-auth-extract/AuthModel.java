package com.vk.auth.main;

import android.net.Uri;
import com.vk.auth.api.models.AuthResult;
import com.vk.auth.enterphone.choosecountry.Country;
import io.reactivex.rxjava3.internal.operators.observable.b0;
import io.reactivex.rxjava3.internal.operators.observable.l2;
import java.util.regex.Pattern;
import xsna.aqo;
import xsna.qp0;
import xsna.zpo;

/* compiled from: AuthModel.kt */
/* loaded from: /home/z/my-project/vk-dex/classes.dex */
public interface AuthModel {

    /* JADX WARN: Failed to restore enum class, 'enum' modifier and super class removed */
    /* JADX WARN: Unknown enum class pattern. Please report as an issue! */
    /* compiled from: AuthModel.kt */
    public static final class EmailAdsAcceptance {
        private static final /* synthetic */ zpo $ENTRIES;
        private static final /* synthetic */ EmailAdsAcceptance[] $VALUES;
        public static final EmailAdsAcceptance ACCEPTED;
        public static final EmailAdsAcceptance NOT_ACCEPTED;
        public static final EmailAdsAcceptance UNKNOWN;

        static {
            EmailAdsAcceptance emailAdsAcceptance = new EmailAdsAcceptance("UNKNOWN", 0);
            UNKNOWN = emailAdsAcceptance;
            EmailAdsAcceptance emailAdsAcceptance2 = new EmailAdsAcceptance("ACCEPTED", 1);
            ACCEPTED = emailAdsAcceptance2;
            EmailAdsAcceptance emailAdsAcceptance3 = new EmailAdsAcceptance("NOT_ACCEPTED", 2);
            NOT_ACCEPTED = emailAdsAcceptance3;
            EmailAdsAcceptance[] emailAdsAcceptanceArr = {emailAdsAcceptance, emailAdsAcceptance2, emailAdsAcceptance3};
            $VALUES = emailAdsAcceptanceArr;
            $ENTRIES = new aqo(emailAdsAcceptanceArr);
        }

        public EmailAdsAcceptance() {
            throw null;
        }

        public static EmailAdsAcceptance valueOf(String str) {
            return (EmailAdsAcceptance) Enum.valueOf(EmailAdsAcceptance.class, str);
        }

        public static EmailAdsAcceptance[] values() {
            return (EmailAdsAcceptance[]) $VALUES.clone();
        }
    }

    /* compiled from: AuthModel.kt */
    public static final class a {
    }

    Country a();

    int b();

    String c(String str);

    l2 d(AuthResult authResult);

    EmailAdsAcceptance e();

    String f();

    Pattern g();

    void h(AuthResult authResult, Uri uri);

    b0 i();

    int j();

    qp0 k();

    String l(String str);

    Pattern m();

    VkClientLibverifyInfo n();
}
