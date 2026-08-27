package com.vk.auth.main;

import android.content.Intent;
import com.vk.auth.DefaultAuthActivity;
import com.vk.auth.enterphone.choosecountry.Country;
import com.vk.auth.main.SignUpRouter;
import com.vk.auth.main.a;
import com.vk.auth.main.i;
import xsna.o9l0;

/* compiled from: VkClientAuthActivity.kt */
/* loaded from: /home/z/my-project/vk-dex/classes15.dex */
public class VkClientAuthActivity extends DefaultAuthActivity {
    public Country V;
    public String W;
    public String X;
    public boolean Y;

    /* compiled from: VkClientAuthActivity.kt */
    public static final class OauthActivity extends VkClientAuthActivity {
    }

    /* JADX WARN: Multi-variable type inference failed */
    public final a p1(a.C0027a c0027a) {
        Intent intent = getIntent();
        new g(this, getSupportFragmentManager(), intent != null ? intent.getBooleanExtra("disableEnterPhone", false) : false);
        new i.a();
        o9l0 o9l0Var = f.a;
        throw null;
    }

    public void q1(Intent intent) {
        super.q1(intent);
        this.V = intent != null ? (Country) intent.getParcelableExtra("preFillCountry") : null;
        this.W = intent != null ? intent.getStringExtra("preFillPhoneWithoutCode") : null;
        this.X = intent != null ? intent.getStringExtra("sid") : null;
        boolean z = false;
        if (intent != null && intent.getBooleanExtra("force_sid_saving", false)) {
            z = true;
        }
        this.Y = z;
    }

    public void x1() {
        a aVar = ((DefaultAuthActivity) this).g;
        if (aVar == null) {
            aVar = null;
        }
        e eVar = aVar.c;
        String str = this.X;
        Country country = this.V;
        String str2 = this.W;
        eVar.b.G = this.Y;
        SignUpRouter.a.a(eVar.c, str, country, str2, null, 8);
    }
}
