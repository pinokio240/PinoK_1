.class public final Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;
.super Ljava/lang/Object;

# interfaces
.implements Landroid/content/ServiceConnection;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/ui/activities/MainActivity;-><init>()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation


# instance fields
.field public final synthetic b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    return-void
.end method


# virtual methods
.method public final onServiceConnected(Landroid/content/ComponentName;Landroid/os/IBinder;)V
    .locals 6

    const-string v0, "className"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string p1, "service"

    invoke-static {p2, p1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    sget-object p1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    const/4 v1, 0x0

    new-array v2, v1, [Landroid/os/Bundle;

    const-string v3, "onServiceConnected"

    invoke-virtual {v0, v3, v2}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    sget v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->t:I

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-virtual {v0, v1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->v(Z)V

    check-cast p2, Lcom/jazibkhan/equalizer/services/MainForegroundService$a;

    iget-object p2, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService$a;->b:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iput-object p2, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-static {v0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result p2

    const/4 v2, 0x0

    if-nez p2, :cond_3

    iget-object p2, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    const-string v3, "mPref"

    if-eqz p2, :cond_1

    sget-object v4, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v4, :cond_0

    const-string v5, "session_id"

    invoke-interface {v4, v5, v1}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v4

    iput v4, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    goto :goto_0

    :cond_0
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_1
    :goto_0
    iget-object p2, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz p2, :cond_3

    sget-object v4, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v4, :cond_2

    const-string v3, "package_name"

    const-string v5, "Global Mix"

    invoke-interface {v4, v3, v5}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    iput-object v3, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    goto :goto_1

    :cond_2
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_3
    :goto_1
    iget-object p2, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz p2, :cond_4

    iget v3, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    goto :goto_2

    :cond_4
    move v3, v1

    :goto_2
    iput v3, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->j:I

    if-eqz p2, :cond_5

    iget-object v3, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    goto :goto_3

    :cond_5
    move-object v3, v2

    :goto_3
    iput-object v3, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->k:Ljava/lang/String;

    if-eqz p2, :cond_6

    iput-object v0, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;

    iget-boolean v3, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->n:Z

    if-nez v3, :cond_6

    const/4 v3, 0x1

    iput-boolean v3, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->n:Z

    iget-object v3, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->p:Lye/c;

    if-eqz v3, :cond_6

    iget-object p2, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->o:Lze/a;

    if-eqz p2, :cond_6

    invoke-virtual {v0, v3, p2}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->i(Lye/c;Lze/a;)V

    :cond_6
    iget-object p2, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz p2, :cond_7

    iget-object p2, p2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    goto :goto_4

    :cond_7
    move-object p2, v2

    :goto_4
    invoke-virtual {v0, p2}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c(Ljava/lang/String;)V

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object p1

    iget-object p1, p1, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array p2, v1, [Landroid/os/Bundle;

    const-string v1, "fadeInAll"

    invoke-virtual {p1, v1, p2}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const-string p2, "binding"

    if-eqz p1, :cond_14

    iget-object p1, p1, Laf/a;->e:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_13

    iget-object p1, p1, Laf/a;->c:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_12

    iget-object p1, p1, Laf/a;->f:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_11

    iget-object p1, p1, Laf/a;->h:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_10

    iget-object p1, p1, Laf/a;->g:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_f

    iget-object p1, p1, Laf/a;->d:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_e

    iget-object p1, p1, Laf/a;->i:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-static {p1}, Lkf/a;->a(Landroid/view/View;)V

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->B()V

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iget-boolean p1, p1, Ldf/b;->y:Z

    if-nez p1, :cond_8

    goto :goto_5

    :cond_8
    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz p1, :cond_9

    iget-object p1, p1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->f:Lye/m0$a;

    if-eqz p1, :cond_9

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p2

    iget p2, p2, Ldf/b;->q:I

    invoke-virtual {p1, p2}, Lye/m0$a;->b(I)V

    :cond_9
    :goto_5
    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iget-boolean p1, p1, Ldf/b;->z:Z

    if-nez p1, :cond_a

    goto :goto_6

    :cond_a
    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz p1, :cond_b

    iget-object p1, p1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->e:Lye/m0$d;

    if-eqz p1, :cond_b

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p2

    iget p2, p2, Ldf/b;->t:F

    float-to-int p2, p2

    invoke-virtual {p1, p2}, Lye/m0$d;->b(I)V

    :cond_b
    :goto_6
    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iget-boolean p1, p1, Ldf/b;->A:Z

    if-nez p1, :cond_c

    goto :goto_7

    :cond_c
    iget-object p1, v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz p1, :cond_d

    iget-object p1, p1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->g:Lye/m0$f;

    if-eqz p1, :cond_d

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p2

    iget p2, p2, Ldf/b;->p:I

    invoke-virtual {p1, p2}, Lye/m0$f;->c(I)V

    :cond_d
    :goto_7
    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->E()V

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->z()V

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/n0;

    invoke-direct {v0, p1, v2}, Ldf/n0;-><init>(Ldf/b;Lbm/e;)V

    const/4 p1, 0x3

    invoke-static {p2, v2, v2, v0, p1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_e
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_f
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_10
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_11
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_12
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_13
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_14
    invoke-static {p2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2
.end method

.method public final onServiceDisconnected(Landroid/content/ComponentName;)V
    .locals 2

    const-string v0, "arg0"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    sget-object p1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object p1

    iget-object p1, p1, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    const/4 v0, 0x0

    new-array v0, v0, [Landroid/os/Bundle;

    const-string v1, "onServiceDisconnected"

    invoke-virtual {p1, v1, v0}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    iget-object v0, p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    iput-object v1, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;

    :cond_0
    iput-object v1, p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    return-void
.end method
