.class public final Lcom/jazibkhan/equalizer/services/MainForegroundService$d;
.super Ldm/i;

# interfaces
.implements Lmm/p;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/services/MainForegroundService;->a(Lze/b;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ldm/i;",
        "Lmm/p<",
        "Lip/h0;",
        "Lbm/e<",
        "-",
        "Lxl/e0;",
        ">;",
        "Ljava/lang/Object;",
        ">;"
    }
.end annotation

.annotation runtime Ldm/e;
    c = "com.jazibkhan.equalizer.services.MainForegroundService$checkAndApplyPreset$1"
    f = "MainForegroundService.kt"
    l = {
        0xc1,
        0xcd,
        0xd2
    }
    m = "invokeSuspend"
.end annotation


# instance fields
.field public l:Lcom/jazibkhan/equalizer/services/MainForegroundService;

.field public m:Lze/a;

.field public n:I

.field public synthetic o:Ljava/lang/Object;

.field public final synthetic p:Lcom/jazibkhan/equalizer/services/MainForegroundService;

.field public final synthetic q:Lze/b;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;Lze/b;Lbm/e;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/jazibkhan/equalizer/services/MainForegroundService;",
            "Lze/b;",
            "Lbm/e<",
            "-",
            "Lcom/jazibkhan/equalizer/services/MainForegroundService$d;",
            ">;)V"
        }
    .end annotation

    iput-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->p:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iput-object p2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->q:Lze/b;

    const/4 p1, 0x2

    invoke-direct {p0, p1, p3}, Ldm/i;-><init>(ILbm/e;)V

    return-void
.end method


# virtual methods
.method public final create(Ljava/lang/Object;Lbm/e;)Lbm/e;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Object;",
            "Lbm/e<",
            "*>;)",
            "Lbm/e<",
            "Lxl/e0;",
            ">;"
        }
    .end annotation

    new-instance v0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;

    iget-object v1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->p:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iget-object v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->q:Lze/b;

    invoke-direct {v0, v1, v2, p2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;-><init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;Lze/b;Lbm/e;)V

    iput-object p1, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->o:Ljava/lang/Object;

    return-object v0
.end method

.method public final invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 0

    check-cast p1, Lip/h0;

    check-cast p2, Lbm/e;

    invoke-virtual {p0, p1, p2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->create(Ljava/lang/Object;Lbm/e;)Lbm/e;

    move-result-object p1

    check-cast p1, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;

    sget-object p2, Lxl/e0;->a:Lxl/e0;

    invoke-virtual {p1, p2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 14

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->p:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iget-object v1, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->q:Lxl/s;

    iget-object v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->o:Ljava/lang/Object;

    check-cast v2, Lip/h0;

    sget-object v3, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    iget v4, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->n:I

    const/4 v5, 0x3

    const/4 v6, 0x2

    const/4 v7, 0x1

    const/4 v8, 0x0

    if-eqz v4, :cond_3

    if-eq v4, v7, :cond_2

    if-eq v4, v6, :cond_1

    if-ne v4, v5, :cond_0

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->l:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    check-cast v0, Lip/h0;

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    goto/16 :goto_7

    :cond_0
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_1
    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->m:Lze/a;

    iget-object v1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->l:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    move-object v13, v1

    move-object v1, v0

    move-object v0, v13

    goto/16 :goto_2

    :cond_2
    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    goto :goto_0

    :cond_3
    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    invoke-virtual {v1}, Lxl/s;->getValue()Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/jazibkhan/equalizer/AppDatabase;

    invoke-virtual {p1}, Lcom/jazibkhan/equalizer/AppDatabase;->w()Lye/d;

    move-result-object p1

    invoke-interface {p1}, Lye/d;->f()Lv7/m;

    move-result-object p1

    iput-object v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->o:Ljava/lang/Object;

    iput v7, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->n:I

    invoke-static {p1, p0}, Llp/h;->h(Llp/f;Lbm/e;)Ljava/lang/Object;

    move-result-object p1

    if-ne p1, v3, :cond_4

    goto/16 :goto_6

    :cond_4
    :goto_0
    check-cast p1, Ljava/util/List;

    iget-object v4, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->q:Lze/b;

    if-eqz p1, :cond_16

    check-cast p1, Ljava/lang/Iterable;

    invoke-interface {p1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_5
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v9

    if-eqz v9, :cond_7

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v9

    move-object v10, v9

    check-cast v10, Lze/a;

    iget-object v11, v10, Lze/a;->b:Lze/b;

    sget-object v12, Lze/b;->BLUETOOTH:Lze/b;

    if-ne v11, v12, :cond_6

    iget-object v11, v10, Lze/a;->a:Ljava/lang/String;

    iget-object v12, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->t:Ljava/lang/String;

    invoke-static {v11, v12}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v11

    if-eqz v11, :cond_5

    iget-object v10, v10, Lze/a;->b:Lze/b;

    if-ne v10, v4, :cond_5

    goto :goto_1

    :cond_6
    if-ne v11, v4, :cond_5

    goto :goto_1

    :cond_7
    move-object v9, v8

    :goto_1
    move-object p1, v9

    check-cast p1, Lze/a;

    if-eqz p1, :cond_16

    invoke-virtual {v1}, Lxl/s;->getValue()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/jazibkhan/equalizer/AppDatabase;

    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/AppDatabase;->w()Lye/d;

    move-result-object v1

    iget v4, p1, Lze/a;->c:I

    invoke-interface {v1, v4}, Lye/d;->p(I)Lv7/m;

    move-result-object v1

    iput-object v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->o:Ljava/lang/Object;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->l:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iput-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->m:Lze/a;

    iput v6, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->n:I

    invoke-static {v1, p0}, Llp/h;->h(Llp/f;Lbm/e;)Ljava/lang/Object;

    move-result-object v1

    if-ne v1, v3, :cond_8

    goto/16 :goto_6

    :cond_8
    move-object v13, v1

    move-object v1, p1

    move-object p1, v13

    :goto_2
    check-cast p1, Lye/c;

    sget v2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->x:I

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    if-nez p1, :cond_9

    goto/16 :goto_7

    :cond_9
    invoke-virtual {p1}, Lye/c;->p()Z

    move-result v2

    invoke-static {v2}, Lkf/f;->F(Z)V

    invoke-virtual {p1}, Lye/c;->o()I

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    const-string v4, "mPref"

    if-eqz v3, :cond_15

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v5, "virslider"

    invoke-interface {v3, v5, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p1}, Lye/c;->b()Z

    move-result v2

    invoke-static {v2}, Lkf/f;->w(Z)V

    invoke-virtual {p1}, Lye/c;->a()I

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_14

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v5, "bbslider"

    invoke-interface {v3, v5, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p1}, Lye/c;->i()Z

    move-result v2

    invoke-static {v2}, Lkf/f;->A(Z)V

    invoke-virtual {p1}, Lye/c;->h()F

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_13

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v5, "loudslider"

    invoke-interface {v3, v5, v2}, Landroid/content/SharedPreferences$Editor;->putFloat(Ljava/lang/String;F)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p1}, Lye/c;->f()Z

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_12

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v5, "eqswitch"

    invoke-interface {v3, v5, v2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p1}, Lye/c;->m()Ljava/util/List;

    move-result-object v2

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    invoke-static {}, Lkf/f;->j()I

    move-result v3

    const/4 v5, 0x0

    if-ne v2, v3, :cond_a

    invoke-static {}, Lkf/f;->j()I

    move-result v2

    move v3, v5

    :goto_3
    if-ge v3, v2, :cond_c

    invoke-virtual {p1}, Lye/c;->m()Ljava/util/List;

    move-result-object v6

    invoke-interface {v6, v3}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/Number;

    invoke-virtual {v6}, Ljava/lang/Number;->intValue()I

    move-result v6

    invoke-static {v6, v3}, Lkf/f;->z(II)V

    add-int/lit8 v3, v3, 0x1

    goto :goto_3

    :cond_a
    invoke-static {}, Lkf/f;->j()I

    move-result v2

    const/16 v3, 0xa

    const/4 v6, 0x5

    if-ne v2, v6, :cond_b

    invoke-virtual {p1}, Lye/c;->m()Ljava/util/List;

    move-result-object v2

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    if-ne v2, v3, :cond_b

    invoke-virtual {p1}, Lye/c;->m()Ljava/util/List;

    move-result-object v2

    invoke-static {v2}, Lkf/a;->i(Ljava/util/List;)Ljava/util/List;

    move-result-object v2

    invoke-static {}, Lkf/f;->j()I

    move-result v3

    move v6, v5

    :goto_4
    if-ge v6, v3, :cond_c

    invoke-interface {v2, v6}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Ljava/lang/Number;

    invoke-virtual {v9}, Ljava/lang/Number;->intValue()I

    move-result v9

    invoke-static {v9, v6}, Lkf/f;->z(II)V

    add-int/lit8 v6, v6, 0x1

    goto :goto_4

    :cond_b
    invoke-static {}, Lkf/f;->j()I

    move-result v2

    if-ne v2, v3, :cond_c

    invoke-virtual {p1}, Lye/c;->m()Ljava/util/List;

    move-result-object v2

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    if-ne v2, v6, :cond_c

    invoke-virtual {p1}, Lye/c;->m()Ljava/util/List;

    move-result-object v2

    invoke-static {v2}, Lkf/a;->b(Ljava/util/List;)Ljava/util/List;

    move-result-object v2

    invoke-static {}, Lkf/f;->j()I

    move-result v3

    move v6, v5

    :goto_5
    if-ge v6, v3, :cond_c

    invoke-interface {v2, v6}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Ljava/lang/Number;

    invoke-virtual {v9}, Ljava/lang/Number;->intValue()I

    move-result v9

    invoke-static {v9, v6}, Lkf/f;->z(II)V

    add-int/lit8 v6, v6, 0x1

    goto :goto_5

    :cond_c
    invoke-virtual {p1}, Lye/c;->n()I

    move-result v2

    invoke-static {v2}, Lkf/f;->E(I)V

    invoke-virtual {p1}, Lye/c;->e()Z

    move-result v2

    invoke-static {v2}, Lkf/f;->y(Z)V

    invoke-virtual {p1}, Lye/c;->l()Z

    move-result v2

    invoke-static {v2}, Lkf/f;->C(Z)V

    invoke-virtual {p1}, Lye/c;->k()I

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_11

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v6, "reverbslider"

    invoke-interface {v3, v6, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p1}, Lye/c;->d()Z

    move-result v2

    invoke-static {v2}, Lkf/f;->x(Z)V

    invoke-virtual {p1}, Lye/c;->c()F

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_10

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v6, "channel_bal_slider"

    invoke-interface {v3, v6, v2}, Landroid/content/SharedPreferences$Editor;->putFloat(Ljava/lang/String;F)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_f

    invoke-interface {v2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v2

    const-string v3, "alfjl4kj53lkjsfl"

    invoke-interface {v2, v3, v5}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v2}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p1}, Lye/c;->g()I

    move-result v2

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_e

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    const-string v4, "a23jlk324j2lk5j34k5"

    invoke-interface {v3, v4, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->c()V

    iput-object v1, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->o:Lze/a;

    iput-object p1, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->p:Lye/c;

    iget-object v2, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;

    if-nez v2, :cond_d

    iput-boolean v5, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->n:Z

    goto :goto_7

    :cond_d
    iput-boolean v7, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->n:Z

    invoke-interface {v2, p1, v1}, Lcom/jazibkhan/equalizer/services/MainForegroundService$b;->i(Lye/c;Lze/a;)V

    goto :goto_7

    :cond_e
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_f
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_10
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_11
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_12
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_13
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_14
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_15
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v8

    :cond_16
    sget-object p1, Lze/b;->BLUETOOTH:Lze/b;

    if-ne v4, p1, :cond_18

    invoke-virtual {v1}, Lxl/s;->getValue()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/jazibkhan/equalizer/AppDatabase;

    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/AppDatabase;->w()Lye/d;

    move-result-object v1

    iget-object v0, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->t:Ljava/lang/String;

    invoke-static {v0}, Lfp/y;->K(Ljava/lang/CharSequence;)Z

    move-result v2

    if-eqz v2, :cond_17

    const-string v0, "Bluetooth"

    :cond_17
    new-instance v2, Lze/a;

    invoke-direct {v2, v0, p1}, Lze/a;-><init>(Ljava/lang/String;Lze/b;)V

    iput-object v8, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->o:Ljava/lang/Object;

    iput-object v8, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->l:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iput-object v8, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->m:Lze/a;

    iput v5, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;->n:I

    invoke-interface {v1, v2, p0}, Lye/d;->k(Lze/a;Lcom/jazibkhan/equalizer/services/MainForegroundService$d;)Ljava/lang/Object;

    move-result-object p1

    if-ne p1, v3, :cond_18

    :goto_6
    return-object v3

    :cond_18
    :goto_7
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method
