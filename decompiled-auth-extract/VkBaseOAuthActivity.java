package com.vk.auth.oauth;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import java.util.LinkedHashMap;
import java.util.UUID;
import xsna.bdn0;
import xsna.p0l;
import xsna.q0l;
import xsna.t0g0;

/* compiled from: VkBaseOAuthActivity.kt */
/* loaded from: /home/z/my-project/vk-dex/classes15.dex */
public abstract class VkBaseOAuthActivity extends FragmentActivity {
    public static final /* synthetic */ int k = 0;
    public p0l f;
    public String g;
    public boolean h;
    public boolean i;
    public boolean j;

    /* JADX WARN: Multi-variable type inference failed */
    public final void finish() {
        super/*android.app.Activity*/.finish();
        overridePendingTransition(0, 0);
    }

    public abstract p0l o1();

    public final void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        this.h = false;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void onCreate(Bundle bundle) {
        overridePendingTransition(0, 0);
        super.onCreate(bundle);
        this.j = bundle != null ? bundle.getBoolean("vk_base_oauth_activity.key_is_changing_config", false) : false;
        String string = bundle != null ? bundle.getString("vk_base_oauth_activity.key_storage_key") : null;
        if (string == null) {
            p0l o1 = o1();
            this.f = o1;
            LinkedHashMap linkedHashMap = q0l.a;
            String uuid = UUID.randomUUID().toString();
            q0l.a.put(uuid, o1);
            this.g = uuid;
        } else {
            LinkedHashMap linkedHashMap2 = q0l.a;
            p0l p0lVar = (p0l) linkedHashMap2.get(string);
            if (p0lVar == null) {
                p0l o12 = o1();
                this.f = o12;
                String uuid2 = UUID.randomUUID().toString();
                linkedHashMap2.put(uuid2, o12);
                this.g = uuid2;
            } else {
                this.f = p0lVar;
                this.g = string;
            }
        }
        if (bundle != null) {
            this.h = bundle.getBoolean("vk_base_oauth_activity.key_awaiting_result", false);
        } else {
            if (!getIntent().getBooleanExtra("vk_base_oauth_activity.key_start_auth", false)) {
                finish();
                return;
            }
            this.h = true;
            this.i = true;
            bdn0.f(new t0g0(this, 25));
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void onDestroy() {
        if (isFinishing()) {
            p0l p0lVar = this.f;
            if (p0lVar == null) {
                p0lVar = null;
            }
            p0lVar.onDestroy();
            LinkedHashMap linkedHashMap = q0l.a;
            String str = this.g;
            q0l.a.remove(str != null ? str : null);
        }
        super.onDestroy();
    }

    public void onNewIntent(Intent intent) {
        super/*androidx.activity.ComponentActivity*/.onNewIntent(intent);
        this.h = false;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public final void onResume() {
        super.onResume();
        if (this.h && !this.i && !this.j) {
            setResult(0);
            finish();
        }
        this.i = false;
        this.j = false;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public final void onSaveInstanceState(Bundle bundle) {
        super/*androidx.activity.ComponentActivity*/.onSaveInstanceState(bundle);
        bundle.putBoolean("vk_base_oauth_activity.key_awaiting_result", this.h);
        String str = this.g;
        if (str == null) {
            str = null;
        }
        bundle.putString("vk_base_oauth_activity.key_storage_key", str);
        bundle.putBoolean("vk_base_oauth_activity.key_is_changing_config", isChangingConfigurations());
    }

    /* JADX WARN: Multi-variable type inference failed */
    public final void p1(String str) {
        setResult(2, str != null ? new Intent().putExtra("error_message", str) : null);
        finish();
    }

    /* JADX WARN: Multi-variable type inference failed */
    public final void q1(Intent intent) {
        setResult(-1, intent);
        finish();
    }

    public abstract void r1();
}
