.class public final Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;
.super Ldf/a;

# interfaces
.implements Lyh/j;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;
    }
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0004\u0018\u00002\u00020\u00012\u00020\u0002:\u0001\u0005B\u0007\u00a2\u0006\u0004\u0008\u0003\u0010\u0004\u00a8\u0006\u0006"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;",
        "Ldf/a;",
        "Lyh/j;",
        "<init>",
        "()V",
        "a",
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


# instance fields
.field public c:Landroidx/viewpager/widget/ViewPager;

.field public d:Lcom/google/android/material/tabs/TabLayout;

.field public e:Lcom/google/android/material/button/MaterialButton;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ldf/a;-><init>()V

    return-void
.end method


# virtual methods
.method public final onCreate(Landroid/os/Bundle;)V
    .locals 3

    invoke-super {p0, p1}, Ldf/a;->onCreate(Landroid/os/Bundle;)V

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
    const p1, 0x7f0d0026

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->setContentView(I)V

    const p1, 0x7f0a046c

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;

    move-result-object p1

    check-cast p1, Landroidx/viewpager/widget/ViewPager;

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->c:Landroidx/viewpager/widget/ViewPager;

    const p1, 0x7f0a046b

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;

    move-result-object p1

    check-cast p1, Lcom/google/android/material/tabs/TabLayout;

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->d:Lcom/google/android/material/tabs/TabLayout;

    const p1, 0x7f0a046a

    invoke-virtual {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;

    move-result-object p1

    check-cast p1, Lcom/google/android/material/button/MaterialButton;

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->e:Lcom/google/android/material/button/MaterialButton;

    new-instance p1, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;

    invoke-virtual {p0}, Landroidx/fragment/app/u;->getSupportFragmentManager()Landroidx/fragment/app/h0;

    move-result-object v0

    const-string v1, "getSupportFragmentManager(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-direct {p1, v0}, Landroidx/fragment/app/n0;-><init>(Landroidx/fragment/app/h0;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->c:Landroidx/viewpager/widget/ViewPager;

    if-eqz v0, :cond_1

    invoke-virtual {v0, p1}, Landroidx/viewpager/widget/ViewPager;->setAdapter(Ll8/a;)V

    :cond_1
    :try_start_0
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->d:Lcom/google/android/material/tabs/TabLayout;

    if-eqz v0, :cond_2

    iget-object v1, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->c:Landroidx/viewpager/widget/ViewPager;

    invoke-virtual {v0, v1}, Lcom/google/android/material/tabs/TabLayout;->setupWithViewPager(Landroidx/viewpager/widget/ViewPager;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_5

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "launch_count"

    const/4 v2, 0x1

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    const-string v1, "intro_complete"

    sget-object v2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;

    invoke-virtual {v0, v1, v2}, Lcom/zipoapps/premiumhelper/c;->o(Ljava/lang/String;Ljava/lang/Object;)V

    new-instance v0, Landroid/content/Intent;

    const-class v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p0}, Landroid/app/Activity;->finish()V

    const/4 v1, 0x0

    invoke-virtual {p0, v1, v1}, Landroid/app/Activity;->overridePendingTransition(II)V

    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    :cond_2
    :goto_0
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->e:Lcom/google/android/material/button/MaterialButton;

    if-eqz v0, :cond_3

    new-instance v1, Ldf/d2;

    const/4 v2, 0x0

    invoke-direct {v1, v2, p0, p1}, Ldf/d2;-><init>(ILandroid/view/KeyEvent$Callback;Ljava/lang/Object;)V

    invoke-virtual {v0, v1}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    :cond_3
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->c:Landroidx/viewpager/widget/ViewPager;

    if-eqz v0, :cond_4

    new-instance v1, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;

    invoke-direct {v1, p1, p0}, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;-><init>(Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;)V

    invoke-virtual {v0, v1}, Landroidx/viewpager/widget/ViewPager;->addOnPageChangeListener(Landroidx/viewpager/widget/ViewPager$j;)V

    :cond_4
    return-void

    :cond_5
    const-string p1, "mPref"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/4 p1, 0x0

    throw p1
.end method
