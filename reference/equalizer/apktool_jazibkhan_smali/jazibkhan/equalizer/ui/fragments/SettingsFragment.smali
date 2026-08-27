.class public final Lcom/jazibkhan/equalizer/ui/fragments/SettingsFragment;
.super Landroidx/preference/b;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u000c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003\u00a8\u0006\u0004"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/ui/fragments/SettingsFragment;",
        "Landroidx/preference/b;",
        "<init>",
        "()V",
        "flat-equalizer-v6.3.5.7_release"
    }
    k = 0x1
    mv = {
        0x2,
        0x2,
        0x0
    }
    xi = 0x30
.end annotation


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroidx/preference/b;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(Ljava/lang/String;)V
    .locals 9

    iget-object p1, p0, Landroidx/preference/b;->c:Lk7/f;

    if-eqz p1, :cond_1a

    invoke-virtual {p0}, Landroidx/fragment/app/p;->requireContext()Landroid/content/Context;

    move-result-object v0

    iget-object v1, p0, Landroidx/preference/b;->c:Lk7/f;

    iget-object v1, v1, Lk7/f;->g:Landroidx/preference/PreferenceScreen;

    const v2, 0x7f160008

    invoke-virtual {p1, v0, v2, v1}, Lk7/f;->e(Landroid/content/Context;ILandroidx/preference/PreferenceScreen;)Landroidx/preference/PreferenceScreen;

    move-result-object p1

    invoke-virtual {p0, p1}, Landroidx/preference/b;->c(Landroidx/preference/PreferenceScreen;)V

    invoke-virtual {p0}, Landroidx/fragment/app/p;->requireContext()Landroid/content/Context;

    move-result-object p1

    const-string v0, "requireContext(...)"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {p1}, Lkf/f;->r(Landroid/content/Context;)V

    invoke-virtual {p0}, Landroidx/fragment/app/p;->getContext()Landroid/content/Context;

    move-result-object p1

    const-string v0, "hide_show_notifications"

    invoke-virtual {p0, v0}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    if-eqz v0, :cond_0

    new-instance v1, Lj1/r;

    invoke-direct {v1, p1, v0}, Lj1/r;-><init>(Landroid/content/Context;Landroidx/preference/Preference;)V

    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_0
    const-string v0, "disable_battery_optimizations"

    invoke-virtual {p0, v0}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    const/4 v1, 0x1

    if-eqz v0, :cond_1

    invoke-static {p1}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    const-string v2, "power"

    invoke-virtual {p1, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    const-string v3, "null cannot be cast to non-null type android.os.PowerManager"

    invoke-static {v2, v3}, Lkotlin/jvm/internal/l;->d(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v2, Landroid/os/PowerManager;

    invoke-virtual {p1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Landroid/os/PowerManager;->isIgnoringBatteryOptimizations(Ljava/lang/String;)Z

    move-result v2

    xor-int/2addr v2, v1

    invoke-virtual {v0, v2}, Landroidx/preference/Preference;->A(Z)V

    new-instance v2, Lj1/u;

    invoke-direct {v2, p1}, Lj1/u;-><init>(Ljava/lang/Object;)V

    invoke-virtual {v0, v2}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_1
    const-string p1, "is_ten_band"

    invoke-virtual {p0, p1}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object p1

    check-cast p1, Landroidx/preference/CheckBoxPreference;

    const/16 v0, 0x1c

    const/4 v2, 0x0

    if-eqz p1, :cond_3

    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v3, v0, :cond_2

    move v3, v1

    goto :goto_0

    :cond_2
    move v3, v2

    :goto_0
    invoke-virtual {p1, v3}, Landroidx/preference/Preference;->A(Z)V

    :cond_3
    const-string v3, "is_channel_bal_visible"

    invoke-virtual {p0, v3}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v3

    check-cast v3, Landroidx/preference/CheckBoxPreference;

    if-eqz v3, :cond_5

    sget v4, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v4, v0, :cond_4

    move v4, v1

    goto :goto_1

    :cond_4
    move v4, v2

    :goto_1
    invoke-virtual {v3, v4}, Landroidx/preference/Preference;->A(Z)V

    :cond_5
    const-string v4, "is_legacy_mode"

    invoke-virtual {p0, v4}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v4

    check-cast v4, Landroidx/preference/CheckBoxPreference;

    if-eqz v4, :cond_7

    sget v5, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v5, v0, :cond_6

    move v5, v1

    goto :goto_2

    :cond_6
    move v5, v2

    :goto_2
    invoke-virtual {v4, v5}, Landroidx/preference/Preference;->A(Z)V

    :cond_7
    const-string v5, "sticky_service_equalizer"

    invoke-virtual {p0, v5}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v5

    check-cast v5, Landroidx/preference/CheckBoxPreference;

    if-eqz v5, :cond_9

    sget v6, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v7, 0x22

    if-lt v6, v7, :cond_8

    move v6, v1

    goto :goto_3

    :cond_8
    move v6, v2

    :goto_3
    invoke-virtual {v5, v6}, Landroidx/preference/Preference;->A(Z)V

    :cond_9
    if-eqz v5, :cond_a

    new-instance v6, Ljf/c;

    invoke-direct {v6}, Ljava/lang/Object;-><init>()V

    iput-object v6, v5, Landroidx/preference/Preference;->g:Landroidx/preference/Preference$d;

    :cond_a
    const-string v5, "backup_restore_pref"

    invoke-virtual {p0, v5}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v5

    if-eqz v5, :cond_b

    new-instance v6, Lj1/w;

    invoke-direct {v6, p0}, Lj1/w;-><init>(Ljava/lang/Object;)V

    invoke-virtual {v5, v6}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_b
    const-string v5, "saved_bluetooth_devices_pref"

    invoke-virtual {p0, v5}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v5

    if-eqz v5, :cond_c

    new-instance v6, Lcom/applovin/impl/k9;

    invoke-direct {v6, p0}, Lcom/applovin/impl/k9;-><init>(Ljava/lang/Object;)V

    invoke-virtual {v5, v6}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_c
    const-string v5, "theme_pref"

    invoke-virtual {p0, v5}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v5

    if-eqz v5, :cond_d

    new-instance v6, Lj1/x;

    invoke-direct {v6, p0}, Lj1/x;-><init>(Ljava/lang/Object;)V

    invoke-virtual {v5, v6}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_d
    const-string v5, "always_global"

    invoke-virtual {p0, v5}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v5

    check-cast v5, Landroidx/preference/CheckBoxPreference;

    const-string v6, "only_music_player"

    invoke-virtual {p0, v6}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v7

    check-cast v7, Landroidx/preference/CheckBoxPreference;

    if-eqz v5, :cond_e

    new-instance v8, Lj1/y;

    invoke-direct {v8, v7}, Lj1/y;-><init>(Ljava/lang/Object;)V

    iput-object v8, v5, Landroidx/preference/Preference;->f:Landroidx/preference/Preference$c;

    :cond_e
    if-eqz v7, :cond_f

    new-instance v8, Lj1/z;

    invoke-direct {v8, v5}, Lj1/z;-><init>(Ljava/lang/Object;)V

    iput-object v8, v7, Landroidx/preference/Preference;->f:Landroidx/preference/Preference$c;

    :cond_f
    if-eqz v4, :cond_10

    new-instance v8, Ljf/d;

    invoke-direct {v8, p1, v3}, Ljf/d;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V

    iput-object v8, v4, Landroidx/preference/Preference;->f:Landroidx/preference/Preference$c;

    :cond_10
    if-eqz v7, :cond_11

    invoke-static {}, Lkf/f;->u()Z

    move-result v4

    xor-int/2addr v4, v1

    invoke-virtual {v7, v4}, Landroidx/preference/Preference;->v(Z)V

    :cond_11
    if-eqz v5, :cond_13

    sget-object v4, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v4, :cond_12

    invoke-interface {v4, v6, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v4

    xor-int/2addr v4, v1

    invoke-virtual {v5, v4}, Landroidx/preference/Preference;->v(Z)V

    goto :goto_4

    :cond_12
    const-string p1, "mPref"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/4 p1, 0x0

    throw p1

    :cond_13
    :goto_4
    if-eqz p1, :cond_14

    invoke-static {}, Lkf/f;->v()Z

    move-result v4

    xor-int/2addr v4, v1

    invoke-virtual {p1, v4}, Landroidx/preference/Preference;->v(Z)V

    :cond_14
    if-eqz v3, :cond_15

    invoke-static {}, Lkf/f;->v()Z

    move-result p1

    xor-int/2addr p1, v1

    invoke-virtual {v3, p1}, Landroidx/preference/Preference;->v(Z)V

    :cond_15
    const-string p1, "frame_duration_pref"

    invoke-virtual {p0, p1}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object p1

    check-cast p1, Landroidx/preference/EditTextPreference;

    if-eqz p1, :cond_17

    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v3, v0, :cond_16

    goto :goto_5

    :cond_16
    move v1, v2

    :goto_5
    invoke-virtual {p1, v1}, Landroidx/preference/Preference;->A(Z)V

    :cond_17
    if-eqz p1, :cond_18

    invoke-static {}, Lkf/f;->g()I

    move-result v0

    invoke-static {v0}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;

    move-result-object v0

    filled-new-array {v0}, [Ljava/lang/Object;

    move-result-object v0

    const v1, 0x7f1300e9

    invoke-virtual {p0, v1, v0}, Landroidx/fragment/app/p;->getString(I[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Landroidx/preference/Preference;->y(Ljava/lang/CharSequence;)V

    :cond_18
    if-eqz p1, :cond_19

    new-instance v0, Ljf/e;

    invoke-direct {v0, p1, p0}, Ljf/e;-><init>(Landroidx/preference/EditTextPreference;Lcom/jazibkhan/equalizer/ui/fragments/SettingsFragment;)V

    iput-object v0, p1, Landroidx/preference/Preference;->f:Landroidx/preference/Preference$c;

    :cond_19
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/fragments/SettingsFragment;->d()V

    return-void

    :cond_1a
    new-instance p1, Ljava/lang/RuntimeException;

    const-string v0, "This should be called after super.onCreate."

    invoke-direct {p1, v0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p1
.end method

.method public final d()V
    .locals 7

    const-string v0, "bass_boost_freq_equalizer_pro"

    invoke-virtual {p0, v0}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    const-string v1, "bass_boost_freq"

    invoke-virtual {p0, v1}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v1

    check-cast v1, Landroidx/preference/ListPreference;

    sget-object v2, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v3

    iget-object v3, v3, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v3}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v3

    const/4 v4, 0x1

    const/16 v5, 0x1c

    const/4 v6, 0x0

    if-eqz v3, :cond_2

    if-eqz v1, :cond_1

    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v3, v5, :cond_0

    move v3, v4

    goto :goto_0

    :cond_0
    move v3, v6

    :goto_0
    invoke-virtual {v1, v3}, Landroidx/preference/Preference;->A(Z)V

    :cond_1
    if-eqz v0, :cond_6

    invoke-virtual {v0, v6}, Landroidx/preference/Preference;->A(Z)V

    goto :goto_2

    :cond_2
    if-eqz v1, :cond_3

    invoke-virtual {v1, v6}, Landroidx/preference/Preference;->A(Z)V

    :cond_3
    if-eqz v0, :cond_5

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v1, v5, :cond_4

    move v1, v4

    goto :goto_1

    :cond_4
    move v1, v6

    :goto_1
    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->A(Z)V

    :cond_5
    if-eqz v0, :cond_6

    new-instance v1, Ljf/a;

    invoke-direct {v1, p0}, Ljf/a;-><init>(Ljava/lang/Object;)V

    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_6
    :goto_2
    const-string v0, "bass_boost_max_gain_equalizer_pro"

    invoke-virtual {p0, v0}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    const-string v1, "bass_boost_max_gain"

    invoke-virtual {p0, v1}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v1

    check-cast v1, Landroidx/preference/ListPreference;

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v3

    iget-object v3, v3, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v3}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v3

    if-eqz v3, :cond_9

    if-eqz v1, :cond_8

    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v3, v5, :cond_7

    move v3, v4

    goto :goto_3

    :cond_7
    move v3, v6

    :goto_3
    invoke-virtual {v1, v3}, Landroidx/preference/Preference;->A(Z)V

    :cond_8
    if-eqz v0, :cond_d

    invoke-virtual {v0, v6}, Landroidx/preference/Preference;->A(Z)V

    goto :goto_5

    :cond_9
    if-eqz v1, :cond_a

    invoke-virtual {v1, v6}, Landroidx/preference/Preference;->A(Z)V

    :cond_a
    if-eqz v0, :cond_c

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v1, v5, :cond_b

    move v1, v4

    goto :goto_4

    :cond_b
    move v1, v6

    :goto_4
    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->A(Z)V

    :cond_c
    if-eqz v0, :cond_d

    new-instance v1, Lgr/x;

    invoke-direct {v1, p0}, Lgr/x;-><init>(Ljava/lang/Object;)V

    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_d
    :goto_5
    const-string v0, "loudness_max_gain_equalizer_pro"

    invoke-virtual {p0, v0}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v0

    const-string v1, "loudness_max_gain"

    invoke-virtual {p0, v1}, Landroidx/preference/b;->a(Ljava/lang/String;)Landroidx/preference/Preference;

    move-result-object v1

    check-cast v1, Landroidx/preference/ListPreference;

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v2

    iget-object v2, v2, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v2}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v2

    if-eqz v2, :cond_10

    if-eqz v1, :cond_f

    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v2, v5, :cond_e

    goto :goto_6

    :cond_e
    move v4, v6

    :goto_6
    invoke-virtual {v1, v4}, Landroidx/preference/Preference;->A(Z)V

    :cond_f
    if-eqz v0, :cond_14

    invoke-virtual {v0, v6}, Landroidx/preference/Preference;->A(Z)V

    return-void

    :cond_10
    if-eqz v1, :cond_11

    invoke-virtual {v1, v6}, Landroidx/preference/Preference;->A(Z)V

    :cond_11
    if-eqz v0, :cond_13

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v1, v5, :cond_12

    goto :goto_7

    :cond_12
    move v4, v6

    :goto_7
    invoke-virtual {v0, v4}, Landroidx/preference/Preference;->A(Z)V

    :cond_13
    if-eqz v0, :cond_14

    new-instance v1, Ljf/b;

    invoke-direct {v1, p0}, Ljf/b;-><init>(Lcom/jazibkhan/equalizer/ui/fragments/SettingsFragment;)V

    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x(Landroidx/preference/Preference$d;)V

    :cond_14
    return-void
.end method

.method public final onResume()V
    .locals 0

    invoke-super {p0}, Landroidx/fragment/app/p;->onResume()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/fragments/SettingsFragment;->d()V

    return-void
.end method
