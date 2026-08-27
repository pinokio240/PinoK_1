package com.vk.superapp.api.internal.oauthrequests;

import com.vk.api.external.exceptions.VKWebAuthException;
import com.vk.api.sdk.VKApiConfig;
import com.vk.api.sdk.auth.UtilityTokens;
import com.vk.api.sdk.exceptions.ApiErrorViewType;
import com.vk.api.sdk.exceptions.VKApiExecutionException;
import com.vk.auth.api.models.AuthResult;
import com.vk.core.serialize.Serializer;
import com.vk.dto.common.id.UserId;
import com.vk.movika.sdk.base.logic.interactor.f;
import com.vk.superapp.api.analytics.RegistrationStatParamsFactory;
import com.vk.superapp.api.exceptions.AuthException$BannedUserException;
import com.vk.superapp.api.exceptions.AuthException$DeactivatedUserException;
import com.vk.superapp.api.exceptions.AuthException$ExchangeTokenException;
import com.vk.superapp.api.exceptions.AuthException$NeedSilentAuthException;
import com.vk.superapp.api.exceptions.AuthException$UnknownException;
import com.vk.superapp.api.states.VkAuthState;
import com.vk.superapp.core.api.models.BanInfo;
import com.vk.superapp.core.api.models.SendOtpInfo;
import com.vk.superapp.core.api.models.SignUpIncompleteFieldsModel;
import com.vk.superapp.core.api.models.ValidateInfo;
import com.vk.superapp.core.api.models.ValidationType;
import com.vk.superapp.core.api.models.a;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import kotlin.Pair;
import okhttp3.m;
import okhttp3.t;
import org.json.JSONObject;
import xsna.bq2;
import xsna.dau0;
import xsna.ev4;
import xsna.geo0;
import xsna.klc0;
import xsna.lcu0;
import xsna.mau;
import xsna.mq2;
import xsna.ncu0;
import xsna.nn;
import xsna.o9l0;
import xsna.u0l0;
import xsna.xbp;
import xsna.yr2;
import xsna.z400;
import xsna.z8;
import xsna.zck0;

/* compiled from: AuthByExchangeToken.kt */
/* loaded from: /home/z/my-project/vk-dex/classes5.dex */
public final class AuthByExchangeToken extends mq2<AuthResult> {
    public final UserId b;
    public final Initiator c;
    public final boolean d;
    public final String f;
    public final LinkedHashMap e = new LinkedHashMap();
    public final o9l0 g = new o9l0(new f(2));

    public AuthByExchangeToken(String str, UserId userId, String str2, int i, Initiator initiator, boolean z, String str3, boolean z2) {
        this.b = userId;
        this.c = initiator;
        this.d = z;
        this.f = z400.a("https://", str, "/auth_by_exchange_token");
        e("client_id", String.valueOf(i));
        e("exchange_token", str2);
        e("scope", "all");
        e("initiator", initiator.d());
        e("validate_session", str3);
        if (z2) {
            e("silent_auth_by_login", "1");
        }
    }

    public final Object d(geo0 geo0Var) {
        VKApiConfig vKApiConfig = geo0Var.a;
        e("device_id", (String) vKApiConfig.f.getValue());
        Iterator it = RegistrationStatParamsFactory.a().iterator();
        while (it.hasNext()) {
            Pair pair = (Pair) it.next();
            e((String) pair.d(), (String) pair.g());
        }
        String a = klc0.a(klc0.a, this.e, vKApiConfig.g, (String) null, vKApiConfig.b, (Map) null, (Collection) null, 244);
        long j = u0l0.a().i;
        int i = u0l0.a().j;
        t.a aVar = t.Companion;
        Pattern pattern = m.e;
        m a2 = m.a.a("application/x-www-form-urlencoded; charset=utf-8");
        aVar.getClass();
        mau mauVar = new mau(this.f, j, i, 0, t.a.a(a, a2), (List) null, 40);
        ncu0 ncu0Var = new ncu0(geo0Var, mauVar, "access_token");
        try {
            Initiator initiator = Initiator.EXPIRED_TOKEN;
            Initiator initiator2 = this.c;
            lcu0 lcu0Var = (lcu0) xbp.a(geo0Var, mauVar, ncu0Var, initiator2 != initiator);
            if (this.d && !zck0.O(lcu0Var.a) && initiator2 != initiator) {
                bq2 d = nn.d(g());
                ((dau0) d).n = lcu0Var.a;
                ((dau0) d).o = null;
                d.d(geo0Var);
            }
            AuthResult a3 = ev4.a(lcu0Var);
            if (a3 != null) {
                return a3;
            }
            throw new AuthException$UnknownException(null, null);
        } catch (VKWebAuthException e) {
            return f(e);
        } catch (AuthException$NeedSilentAuthException e2) {
            throw e2;
        } catch (IOException e3) {
            throw e3;
        } catch (InterruptedException e4) {
            throw e4;
        } catch (Throwable th) {
            Throwable cause = th.getCause();
            if (th instanceof VKApiExecutionException) {
                JSONObject D = th.D();
                if (D != null) {
                    Serializer.c cVar = BanInfo.CREATOR;
                    throw new AuthException$BannedUserException(BanInfo.a.a(D));
                }
            } else if (cause instanceof VKWebAuthException) {
                return f((VKWebAuthException) cause);
            }
            throw new AuthException$UnknownException(null, th);
        }
    }

    public final void e(String str, String str2) {
        if (str2 != null) {
            this.e.put(str, str2);
        }
    }

    public final AuthResult f(VKWebAuthException vKWebAuthException) throws AuthException$ExchangeTokenException, AuthException$DeactivatedUserException, AuthException$UnknownException {
        UtilityTokens utilityTokens;
        if (vKWebAuthException.o()) {
            throw new AuthException$ExchangeTokenException(new com.vk.superapp.core.api.models.a((String) null, (String) null, this.b, 0, false, (String) null, (UtilityTokens) null, (String) null, (String) null, 0, (List) null, (List) null, (String) null, (ValidationType) null, (ValidationType) null, (String) null, (String) null, (String) null, (String) null, (String) null, (String) null, (String) null, (String) null, 0, 0L, (String) null, (String) null, (a.a) null, (a.b) null, (BanInfo) null, 0L, (String) null, false, (String) null, (String) null, 0, 0, (ArrayList) null, (ArrayList) null, (SignUpIncompleteFieldsModel) null, false, (String) null, (ApiErrorViewType) null, (String) null, (String) null, (ValidateInfo) null, (SendOtpInfo) null, -5, 65535));
        }
        if (!vKWebAuthException.m()) {
            JSONObject i = vKWebAuthException.i();
            if (i == null) {
                throw new AuthException$UnknownException(null, vKWebAuthException);
            }
            com.vk.superapp.core.api.models.a aVar = new com.vk.superapp.core.api.models.a(i);
            Serializer.c cVar = VkAuthState.CREATOR;
            return ev4.b(aVar, VkAuthState.a.c(), new z8(vKWebAuthException, 7), 4);
        }
        String string = vKWebAuthException.j().getString("access_token");
        JSONObject j = vKWebAuthException.j();
        if (j != null) {
            UtilityTokens.CREATOR.getClass();
            utilityTokens = UtilityTokens.a.a(j);
        } else {
            UtilityTokens.CREATOR.getClass();
            utilityTokens = UtilityTokens.c;
        }
        throw new AuthException$DeactivatedUserException(string, null, utilityTokens);
    }

    public final yr2<? extends Object> g() {
        return (yr2) this.g.getValue();
    }
}
