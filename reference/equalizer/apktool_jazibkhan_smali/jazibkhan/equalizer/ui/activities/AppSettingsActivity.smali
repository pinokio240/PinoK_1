.class public final Lcom/jazibkhan/equalizer/ui/activities/AppSettingsActivity;
.super Ldf/a;

# interfaces
.implements Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\u0018\u00002\u00020\u00012\u00020\u0002B\u0007\u00a2\u0006\u0004\u0008\u0003\u0010\u0004\u00a8\u0006\u0005"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/ui/activities/AppSettingsActivity;",
        "Ldf/a;",
        "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;",
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

    invoke-direct {p0}, Ldf/a;-><init>()V

    return-void
.end method


# virtual methods
.method public final onCreate(Landroid/os/Bundle;)V
    .locals 1

    invoke-super {p0, p1}, Ldf/a;->onCreate(Landroid/os/Bundle;)V

    const p1, 0x7f0d001c

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->setContentView(I)V

    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-nez p1, :cond_0

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p1

    invoke-static {p1}, Lk7/f;->a(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object p1

    const-string v0, "getDefaultSharedPreferences(...)"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    :cond_0
    invoke-static {p0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object p1

    invoke-interface {p1, p0}, Landroid/content/SharedPreferences;->registerOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    const p1, 0x7f0a0408

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;

    move-result-object p1

    check-cast p1, Lcom/google/android/material/appbar/MaterialToolbar;

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->setSupportActionBar(Landroidx/appcompat/widget/Toolbar;)V

    invoke-virtual {p0}, Landroidx/appcompat/app/AppCompatActivity;->getSupportActionBar()Lk/a;

    move-result-object p1

    if-eqz p1, :cond_1

    const/4 v0, 0x1

    invoke-virtual {p1, v0}, Lk/a;->m(Z)V

    :cond_1
    return-void
.end method

.method public final onDestroy()V
    .locals 1

    invoke-super {p0}, Landroidx/appcompat/app/AppCompatActivity;->onDestroy()V

    invoke-static {p0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0, p0}, Landroid/content/SharedPreferences;->unregisterOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    return-void
.end method

.method public final onSharedPreferenceChanged(Landroid/content/SharedPreferences;Ljava/lang/String;)V
    .locals 2

    if-eqz p2, :cond_d

    invoke-virtual {p2}, Ljava/lang/String;->hashCode()I

    move-result p1

    const v0, -0x23dea296

    const/4 v1, 0x0

    if-eq p1, v0, :cond_9

    const v0, 0x47a53e52

    if-eq p1, v0, :cond_7

    const v0, 0x54591b26

    if-eq p1, v0, :cond_0

    goto/16 :goto_2

    :cond_0
    const-string p1, "in_app_lang"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_1

    goto/16 :goto_2

    :cond_1
    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_6

    const-string p2, "in_app_lang"

    invoke-static {}, Ljava/util/Locale;->getDefault()Ljava/util/Locale;

    move-result-object v0

    invoke-virtual {v0}, Ljava/util/Locale;->getLanguage()Ljava/lang/String;

    move-result-object v0

    const-string v1, "getLanguage(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    const/4 v1, 0x2

    invoke-static {v1, v0}, Lfp/z;->i0(ILjava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-interface {p1, p2, v0}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    if-nez p1, :cond_2

    const-string p1, "en"

    :cond_2
    invoke-static {p1}, Lq3/h;->a(Ljava/lang/String;)Lq3/h;

    move-result-object p1

    const-string p2, "forLanguageTags(...)"

    invoke-static {p1, p2}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    sget-object p2, Lk/e;->b:Lk/e$c;

    sget p2, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v0, 0x21

    if-lt p2, v0, :cond_3

    invoke-static {}, Lk/e;->j()Ljava/lang/Object;

    move-result-object p2

    if-eqz p2, :cond_d

    iget-object p1, p1, Lq3/h;->a:Lq3/j;

    iget-object p1, p1, Lq3/j;->a:Landroid/os/LocaleList;

    invoke-virtual {p1}, Landroid/os/LocaleList;->toLanguageTags()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Lk/e$a;->a(Ljava/lang/String;)Landroid/os/LocaleList;

    move-result-object p1

    invoke-static {p2, p1}, Lk/e$b;->b(Ljava/lang/Object;Landroid/os/LocaleList;)V

    return-void

    :cond_3
    sget-object p2, Lk/e;->d:Lq3/h;

    invoke-virtual {p1, p2}, Lq3/h;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-nez p2, :cond_d

    sget-object p2, Lk/e;->i:Ljava/lang/Object;

    monitor-enter p2

    :try_start_0
    sput-object p1, Lk/e;->d:Lq3/h;

    sget-object p1, Lk/e;->h:Lu/b;

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    new-instance v0, Lu/b$a;

    invoke-direct {v0, p1}, Lu/b$a;-><init>(Lu/b;)V

    :cond_4
    :goto_0
    invoke-virtual {v0}, Lu/e;->hasNext()Z

    move-result p1

    if-eqz p1, :cond_5

    invoke-virtual {v0}, Lu/e;->next()Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/lang/ref/WeakReference;

    invoke-virtual {p1}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lk/e;

    if-eqz p1, :cond_4

    invoke-virtual {p1}, Lk/e;->b()V

    goto :goto_0

    :cond_5
    monitor-exit p2

    return-void

    :catchall_0
    move-exception p1

    monitor-exit p2
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw p1

    :cond_6
    const-string p1, "mPref"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_7
    const-string p1, "alf5sdj4lw5j30234j2l423"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_8

    goto :goto_2

    :cond_8
    invoke-virtual {p0}, Landroid/app/Activity;->recreate()V

    return-void

    :cond_9
    const-string p1, "night_mode"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_a

    goto :goto_2

    :cond_a
    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_c

    const-string p2, "night_mode"

    const-string v0, "-1"

    invoke-interface {p1, p2, v0}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    if-eqz p1, :cond_b

    invoke-static {p1}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result p1

    goto :goto_1

    :cond_b
    const/4 p1, -0x1

    :goto_1
    invoke-static {p1}, Lk/e;->B(I)V

    return-void

    :cond_c
    const-string p1, "mPref"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_d
    :goto_2
    return-void
.end method
