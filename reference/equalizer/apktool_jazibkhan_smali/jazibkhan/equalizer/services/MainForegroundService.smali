.class public final Lcom/jazibkhan/equalizer/services/MainForegroundService;
.super Landroidx/lifecycle/j0;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/jazibkhan/equalizer/services/MainForegroundService$a;,
        Lcom/jazibkhan/equalizer/services/MainForegroundService$b;,
        Lcom/jazibkhan/equalizer/services/MainForegroundService$c;
    }
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u000c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0005\u0018\u00002\u00020\u0001:\u0002\u0004\u0005B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003\u00a8\u0006\u0006"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/services/MainForegroundService;",
        "Landroidx/lifecycle/j0;",
        "<init>",
        "()V",
        "b",
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


# static fields
.field public static final synthetic x:I


# instance fields
.field public final c:Lcom/jazibkhan/equalizer/services/MainForegroundService$a;

.field public final d:Lye/m0$c;

.field public final e:Lye/m0$d;

.field public final f:Lye/m0$a;

.field public final g:Lye/m0$f;

.field public final h:Lye/m0$e;

.field public final i:Lye/m0$b;

.field public j:I

.field public k:Ljava/lang/String;

.field public l:Lj7/w;

.field public m:Landroid/media/AudioManager;

.field public n:Z

.field public o:Lze/a;

.field public p:Lye/c;

.field public final q:Lxl/s;

.field public r:Lip/n2;

.field public final s:Lcom/jazibkhan/equalizer/services/MainForegroundService$e;

.field public t:Ljava/lang/String;

.field public u:Ljava/lang/String;

.field public v:Lze/b;

.field public w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;


# direct methods
.method public constructor <init>()V
    .locals 2

    invoke-direct {p0}, Landroidx/lifecycle/j0;-><init>()V

    new-instance v0, Lcom/jazibkhan/equalizer/services/MainForegroundService$a;

    invoke-direct {v0, p0}, Lcom/jazibkhan/equalizer/services/MainForegroundService$a;-><init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;)V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->c:Lcom/jazibkhan/equalizer/services/MainForegroundService$a;

    sget-object v0, Lye/m0;->a:Lye/m0$c;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->d:Lye/m0$c;

    sget-object v0, Lye/m0;->c:Lye/m0$d;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->e:Lye/m0$d;

    sget-object v0, Lye/m0;->b:Lye/m0$a;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->f:Lye/m0$a;

    sget-object v0, Lye/m0;->e:Lye/m0$f;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->g:Lye/m0$f;

    sget-object v0, Lye/m0;->d:Lye/m0$e;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->h:Lye/m0$e;

    sget-object v0, Lye/m0;->f:Lye/m0$b;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->i:Lye/m0$b;

    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->n:Z

    new-instance v0, Laq/f;

    const/4 v1, 0x1

    invoke-direct {v0, p0, v1}, Laq/f;-><init>(Ljava/lang/Object;I)V

    invoke-static {v0}, Lxl/k;->b(Lmm/a;)Lxl/s;

    move-result-object v0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->q:Lxl/s;

    new-instance v0, Lcom/jazibkhan/equalizer/services/MainForegroundService$e;

    invoke-direct {v0, p0}, Lcom/jazibkhan/equalizer/services/MainForegroundService$e;-><init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;)V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->s:Lcom/jazibkhan/equalizer/services/MainForegroundService$e;

    const-string v0, ""

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->t:Ljava/lang/String;

    return-void
.end method


# virtual methods
.method public final a(Lze/b;)V
    .locals 4

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->v:Lze/b;

    const/4 v1, 0x3

    if-ne v0, p1, :cond_2

    sget-object v0, Lcom/jazibkhan/equalizer/services/MainForegroundService$c;->a:[I

    invoke-virtual {p1}, Ljava/lang/Enum;->ordinal()I

    move-result v2

    aget v0, v0, v2

    const/4 v2, 0x1

    if-eq v0, v2, :cond_1

    const/4 v2, 0x2

    if-eq v0, v2, :cond_1

    if-ne v0, v1, :cond_0

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->u:Ljava/lang/String;

    iget-object v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->t:Ljava/lang/String;

    invoke-static {v0, v2}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    goto :goto_0

    :cond_0
    new-instance p1, Loa/a;

    invoke-direct {p1}, Ljava/lang/RuntimeException;-><init>()V

    throw p1

    :cond_1
    :goto_0
    return-void

    :cond_2
    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->t:Ljava/lang/String;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->u:Ljava/lang/String;

    iput-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->v:Lze/b;

    invoke-static {p0}, Lam/b;->b(Landroidx/lifecycle/g0;)Landroidx/lifecycle/c0;

    move-result-object v0

    new-instance v2, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;

    const/4 v3, 0x0

    invoke-direct {v2, p0, p1, v3}, Lcom/jazibkhan/equalizer/services/MainForegroundService$d;-><init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;Lze/b;Lbm/e;)V

    invoke-static {v0, v3, v3, v2, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void
.end method

.method public final b()V
    .locals 2

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->d:Lye/m0$c;

    invoke-virtual {v0}, Lye/m0$c;->a()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->f:Lye/m0$a;

    invoke-virtual {v0}, Lye/m0$a;->a()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->g:Lye/m0$f;

    invoke-virtual {v0}, Lye/m0$f;->b()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->h:Lye/m0$e;

    invoke-virtual {v0}, Lye/m0$e;->a()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->e:Lye/m0$d;

    invoke-virtual {v0}, Lye/m0$d;->a()V

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1c

    if-lt v0, v1, :cond_0

    invoke-static {}, Lye/m0;->a()V

    :cond_0
    return-void
.end method

.method public final c()V
    .locals 25

    move-object/from16 v1, p0

    invoke-static {}, Lkf/f;->n()I

    move-result v0

    invoke-static {}, Lkf/f;->t()Z

    move-result v2

    invoke-static {}, Lkf/f;->f()Z

    move-result v3

    invoke-static {}, Lkf/f;->b()Z

    move-result v4

    invoke-static {}, Lkf/f;->i()Z

    move-result v5

    invoke-static {}, Lkf/f;->p()Z

    move-result v6

    invoke-static {}, Lkf/f;->l()Z

    move-result v7

    invoke-static {}, Lkf/f;->a()I

    move-result v8

    invoke-static {}, Lkf/f;->o()I

    move-result v9

    invoke-static {}, Lkf/f;->k()I

    move-result v10

    invoke-static {}, Lkf/f;->h()F

    move-result v11

    float-to-int v11, v11

    new-instance v12, Ljava/util/ArrayList;

    invoke-direct {v12}, Ljava/util/ArrayList;-><init>()V

    invoke-static {}, Lkf/f;->j()I

    move-result v13

    invoke-static {}, Lkf/f;->v()Z

    move-result v14

    sget-object v15, Lkf/f;->a:Landroid/content/SharedPreferences;

    const-string v16, "mPref"

    const/16 v17, 0x0

    if-eqz v15, :cond_38

    move/from16 v18, v2

    const-string v2, "only_music_player"

    move/from16 v19, v3

    const/4 v3, 0x0

    invoke-interface {v15, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v2

    invoke-static {}, Lkf/f;->g()I

    move-result v15

    invoke-static {}, Lkf/f;->d()Z

    move-result v3

    move/from16 v20, v2

    invoke-static {}, Lkf/f;->c()F

    move-result v2

    move/from16 v21, v4

    const/4 v4, 0x1

    if-nez v21, :cond_0

    if-nez v19, :cond_0

    if-nez v5, :cond_0

    if-nez v6, :cond_0

    if-nez v7, :cond_0

    if-nez v3, :cond_0

    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->b()V

    invoke-virtual {v1, v4}, Landroid/app/Service;->stopForeground(I)V

    invoke-virtual {v1}, Landroid/app/Service;->stopSelf()V

    return-void

    :cond_0
    if-eqz v18, :cond_1

    const/4 v0, 0x0

    :goto_0
    if-ge v0, v13, :cond_2

    invoke-static {v0}, Lkf/f;->e(I)I

    move-result v18

    invoke-static/range {v18 .. v18}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v4

    invoke-virtual {v12, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    add-int/lit8 v0, v0, 0x1

    const/4 v4, 0x1

    goto :goto_0

    :cond_1
    invoke-static {v13}, Lkf/a;->f(I)Ljava/util/ArrayList;

    move-result-object v4

    invoke-virtual {v4, v0}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lye/u;

    iget-object v0, v0, Lye/u;->b:Ljava/util/List;

    check-cast v0, Ljava/lang/Iterable;

    invoke-interface {v0}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_1
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v4

    if-eqz v4, :cond_2

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Ljava/lang/Number;

    invoke-virtual {v4}, Ljava/lang/Number;->intValue()I

    move-result v4

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v4

    invoke-virtual {v12, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_1

    :cond_2
    sput-boolean v14, Lye/m0;->k:Z

    if-eqz v20, :cond_3

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    if-nez v0, :cond_3

    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->b()V

    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Landroid/app/Service;->stopForeground(I)V

    invoke-virtual {v1}, Landroid/app/Service;->stopSelf()V

    return-void

    :cond_3
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v18, 0x14

    const/16 v4, 0x1c

    if-lt v0, v4, :cond_1c

    if-nez v14, :cond_1c

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    sget-object v14, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v14}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v14

    iget-object v14, v14, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v14}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v14

    const-string v4, "bass_boost_freq"

    const/16 v22, 0x50

    if-eqz v14, :cond_6

    sget-object v14, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v14, :cond_5

    move/from16 v23, v5

    const-string v5, "80"

    invoke-interface {v14, v4, v5}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    if-eqz v4, :cond_4

    invoke-static {v4}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result v22

    :cond_4
    :goto_2
    move/from16 v4, v22

    goto :goto_3

    :cond_5
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_6
    move/from16 v23, v5

    sget-object v5, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v5, :cond_1b

    invoke-interface {v5}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v5

    invoke-static/range {v22 .. v22}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;

    move-result-object v14

    invoke-interface {v5, v4, v14}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v5}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_2

    :goto_3
    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v5

    iget-object v5, v5, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v5}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v5

    const-string v14, "bass_boost_max_gain"

    const/16 v22, 0xf

    if-eqz v5, :cond_9

    sget-object v5, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v5, :cond_8

    move/from16 v24, v7

    const-string v7, "15"

    invoke-interface {v5, v14, v7}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    if-eqz v5, :cond_7

    invoke-static {v5}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result v22

    :cond_7
    :goto_4
    move/from16 v5, v22

    goto :goto_5

    :cond_8
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_9
    move/from16 v24, v7

    sget-object v5, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v5, :cond_1a

    invoke-interface {v5}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v5

    invoke-static/range {v22 .. v22}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;

    move-result-object v7

    invoke-interface {v5, v14, v7}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v5}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_4

    :goto_5
    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v7

    iget-object v7, v7, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v7}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v7

    const-string v14, "loudness_max_gain"

    if-eqz v7, :cond_c

    sget-object v7, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v7, :cond_b

    move/from16 v22, v11

    const-string v11, "20"

    invoke-interface {v7, v14, v11}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    if-eqz v7, :cond_a

    invoke-static {v7}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result v7

    goto :goto_7

    :cond_a
    :goto_6
    move/from16 v7, v18

    goto :goto_7

    :cond_b
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_c
    move/from16 v22, v11

    sget-object v7, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v7, :cond_19

    invoke-interface {v7}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v7

    invoke-static/range {v18 .. v18}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;

    move-result-object v11

    invoke-interface {v7, v14, v11}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v7}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_6

    :goto_7
    sget v11, Lye/m0;->h:I

    if-ne v0, v11, :cond_d

    sget v11, Lye/m0;->g:I

    if-ne v13, v11, :cond_d

    sget v11, Lye/m0;->l:I

    if-ne v4, v11, :cond_d

    sget v11, Lye/m0;->m:I

    if-ne v15, v11, :cond_d

    sget v11, Lye/m0;->n:I

    if-ne v5, v11, :cond_d

    sget v11, Lye/m0;->o:I

    if-ne v7, v11, :cond_d

    sget-boolean v11, Lye/m0;->p:Z

    if-ne v3, v11, :cond_d

    sget-boolean v11, Lye/m0;->q:Z

    if-ne v6, v11, :cond_d

    sget-object v11, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v11, :cond_d

    invoke-static {v11}, Lye/v;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v11

    if-eqz v11, :cond_d

    invoke-static {v11}, Lcom/google/android/material/resources/a;->b(Landroid/media/audiofx/DynamicsProcessing$Eq;)I

    move-result v11

    if-ne v11, v13, :cond_d

    goto/16 :goto_14

    :cond_d
    sput v0, Lye/m0;->h:I

    sput v13, Lye/m0;->g:I

    sput v4, Lye/m0;->l:I

    sput v15, Lye/m0;->m:I

    sput v5, Lye/m0;->n:I

    sput v7, Lye/m0;->o:I

    sput-boolean v3, Lye/m0;->p:Z

    sput-boolean v6, Lye/m0;->q:Z

    invoke-static {}, Lye/m0;->a()V

    sget-object v5, Lye/m0;->e:Lye/m0$f;

    invoke-virtual {v5}, Lye/m0$f;->b()V

    if-eqz v6, :cond_e

    :try_start_0
    invoke-virtual {v5, v0}, Lye/m0$f;->a(I)V

    goto :goto_8

    :catch_0
    move-exception v0

    goto :goto_a

    :cond_e
    :goto_8
    invoke-static {}, Lye/b0;->a()V

    if-eqz v3, :cond_f

    const/4 v5, 0x2

    goto :goto_9

    :cond_f
    const/4 v5, 0x1

    :goto_9
    invoke-static {v5, v13}, Lye/z;->a(II)Landroid/media/audiofx/DynamicsProcessing$Config$Builder;

    move-result-object v5

    sput-object v5, Lye/m0;->j:Landroid/media/audiofx/DynamicsProcessing$Config$Builder;

    int-to-float v7, v15

    invoke-static {v5, v7}, Lye/g0;->a(Landroid/media/audiofx/DynamicsProcessing$Config$Builder;F)V

    sget-object v5, Lye/m0;->j:Landroid/media/audiofx/DynamicsProcessing$Config$Builder;

    if-eqz v5, :cond_10

    invoke-static {v5}, Lye/h0;->a(Landroid/media/audiofx/DynamicsProcessing$Config$Builder;)Landroid/media/audiofx/DynamicsProcessing$Config;

    move-result-object v5

    if-eqz v5, :cond_10

    invoke-static {}, Lye/c0;->a()V

    invoke-static {v0, v5}, Lye/a0;->a(ILandroid/media/audiofx/DynamicsProcessing$Config;)Landroid/media/audiofx/DynamicsProcessing;

    move-result-object v0

    sput-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_b

    :goto_a
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v5

    invoke-virtual {v5, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_10
    :goto_b
    :try_start_1
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_11

    invoke-static {v0}, Lye/v;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v0

    goto :goto_c

    :catch_1
    move-exception v0

    goto :goto_f

    :cond_11
    move-object/from16 v0, v17

    :goto_c
    invoke-static {v13}, Lkf/a;->d(I)Ljava/util/List;

    move-result-object v5

    const/4 v7, 0x0

    :goto_d
    if-ge v7, v13, :cond_14

    if-eqz v0, :cond_13

    invoke-static {v0, v7}, Lye/i0;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;I)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v11

    if-eqz v11, :cond_13

    invoke-interface {v5, v7}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v14

    check-cast v14, Ljava/lang/Number;

    invoke-virtual {v14}, Ljava/lang/Number;->intValue()I

    move-result v14

    int-to-float v14, v14

    const v15, 0x47f42400    # 125000.0f

    cmpg-float v14, v14, v15

    if-nez v14, :cond_12

    const v14, 0x480d9a00    # 145000.0f

    goto :goto_e

    :cond_12
    invoke-interface {v5, v7}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v14

    check-cast v14, Ljava/lang/Number;

    invoke-virtual {v14}, Ljava/lang/Number;->intValue()I

    move-result v14

    int-to-float v14, v14

    :goto_e
    const/high16 v15, 0x447a0000    # 1000.0f

    div-float/2addr v14, v15

    invoke-static {v11, v14}, Lye/d0;->a(Landroid/media/audiofx/DynamicsProcessing$EqBand;F)V

    :cond_13
    add-int/lit8 v7, v7, 0x1

    goto :goto_d

    :cond_14
    sget-object v5, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v5, :cond_15

    invoke-static {v5, v0}, Lrg/g;->b(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Eq;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_10

    :goto_f
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v5

    invoke-virtual {v5, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_15
    :goto_10
    :try_start_2
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_16

    invoke-static {v0}, Lye/j0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v0

    goto :goto_11

    :catch_2
    move-exception v0

    goto :goto_12

    :cond_16
    move-object/from16 v0, v17

    :goto_11
    if-eqz v0, :cond_17

    invoke-static {v0}, Lye/w;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v5

    if-eqz v5, :cond_17

    int-to-float v4, v4

    invoke-static {v5, v4}, Lye/d0;->a(Landroid/media/audiofx/DynamicsProcessing$EqBand;F)V

    :cond_17
    sget-object v4, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v4, :cond_18

    invoke-static {v4, v0}, Lye/e0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Eq;)V
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_2

    goto :goto_13

    :goto_12
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v4

    invoke-virtual {v4, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_18
    :goto_13
    :try_start_3
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_1d

    invoke-static {v0}, Lye/f0;->a(Landroid/media/audiofx/DynamicsProcessing;)V
    :try_end_3
    .catch Ljava/lang/Exception; {:try_start_3 .. :try_end_3} :catch_3

    goto :goto_14

    :catch_3
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v4

    invoke-virtual {v4, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    goto :goto_14

    :cond_19
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_1a
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_1b
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_1c
    move/from16 v23, v5

    move/from16 v24, v7

    move/from16 v22, v11

    :cond_1d
    :goto_14
    iget-object v4, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->d:Lye/m0$c;

    const v5, 0x7fffffff

    if-eqz v19, :cond_21

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    iget-boolean v7, v4, Lye/m0$c;->c:Z

    if-eqz v7, :cond_1e

    iget v7, v4, Lye/m0$c;->b:I

    if-ne v7, v0, :cond_1e

    goto :goto_18

    :cond_1e
    iput v0, v4, Lye/m0$c;->b:I

    sget v7, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v11, 0x1c

    if-lt v7, v11, :cond_20

    sget-boolean v7, Lye/m0;->k:Z

    if-eqz v7, :cond_1f

    goto :goto_16

    :cond_1f
    :goto_15
    const/4 v11, 0x1

    goto :goto_17

    :cond_20
    :goto_16
    invoke-virtual {v4}, Lye/m0$c;->a()V

    :try_start_4
    new-instance v7, Landroid/media/audiofx/Equalizer;

    invoke-direct {v7, v5, v0}, Landroid/media/audiofx/Equalizer;-><init>(II)V

    iput-object v7, v4, Lye/m0$c;->a:Landroid/media/audiofx/Equalizer;

    const/4 v11, 0x1

    invoke-virtual {v7, v11}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I
    :try_end_4
    .catch Ljava/lang/Exception; {:try_start_4 .. :try_end_4} :catch_4

    goto :goto_15

    :catch_4
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v7

    invoke-virtual {v7, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    goto :goto_15

    :goto_17
    iput-boolean v11, v4, Lye/m0$c;->c:Z

    :goto_18
    const/4 v0, 0x0

    :goto_19
    if-ge v0, v13, :cond_22

    invoke-virtual {v12, v0}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/Number;

    invoke-virtual {v7}, Ljava/lang/Number;->intValue()I

    move-result v7

    invoke-virtual {v4, v0, v7}, Lye/m0$c;->b(II)V

    add-int/lit8 v0, v0, 0x1

    goto :goto_19

    :cond_21
    invoke-virtual {v4}, Lye/m0$c;->a()V

    :cond_22
    iget-object v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->g:Lye/m0$f;

    if-eqz v6, :cond_23

    iget v4, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    invoke-virtual {v0, v4}, Lye/m0$f;->a(I)V

    invoke-virtual {v0, v9}, Lye/m0$f;->c(I)V

    goto :goto_1a

    :cond_23
    invoke-virtual {v0}, Lye/m0$f;->b()V

    :goto_1a
    iget-object v4, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->h:Lye/m0$e;

    if-eqz v24, :cond_25

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    iget-boolean v6, v4, Lye/m0$e;->b:Z

    if-eqz v6, :cond_24

    iget v6, v4, Lye/m0$e;->c:I

    if-ne v6, v0, :cond_24

    goto :goto_1b

    :cond_24
    invoke-virtual {v4}, Lye/m0$e;->a()V

    iput v0, v4, Lye/m0$e;->c:I

    const/4 v11, 0x1

    iput-boolean v11, v4, Lye/m0$e;->b:Z

    :try_start_5
    new-instance v6, Landroid/media/audiofx/PresetReverb;

    invoke-direct {v6, v5, v0}, Landroid/media/audiofx/PresetReverb;-><init>(II)V

    iput-object v6, v4, Lye/m0$e;->a:Landroid/media/audiofx/PresetReverb;

    invoke-virtual {v6, v11}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I
    :try_end_5
    .catch Ljava/lang/RuntimeException; {:try_start_5 .. :try_end_5} :catch_5

    goto :goto_1b

    :catch_5
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v6

    invoke-virtual {v6, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_1b
    invoke-virtual {v4, v10}, Lye/m0$e;->b(I)V

    goto :goto_1c

    :cond_25
    invoke-virtual {v4}, Lye/m0$e;->a()V

    :goto_1c
    iget-object v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->i:Lye/m0$b;

    if-eqz v3, :cond_27

    iget v3, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    iget-boolean v4, v0, Lye/m0$b;->a:Z

    if-eqz v4, :cond_26

    iget v4, v0, Lye/m0$b;->b:I

    if-ne v4, v3, :cond_26

    goto :goto_1d

    :cond_26
    iput v3, v0, Lye/m0$b;->b:I

    const/4 v11, 0x1

    iput-boolean v11, v0, Lye/m0$b;->a:Z

    :goto_1d
    invoke-virtual {v0, v2}, Lye/m0$b;->a(F)V

    goto :goto_21

    :cond_27
    iget-boolean v2, v0, Lye/m0$b;->a:Z

    if-nez v2, :cond_28

    goto :goto_21

    :cond_28
    const/4 v2, 0x0

    iput-boolean v2, v0, Lye/m0$b;->a:Z

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v11, 0x1c

    if-lt v0, v11, :cond_2f

    sget-boolean v0, Lye/m0;->k:Z

    if-nez v0, :cond_2f

    :try_start_6
    sget-object v0, Lye/m0;->c:Lye/m0$d;

    iget-boolean v2, v0, Lye/m0$d;->b:Z

    if-eqz v2, :cond_29

    iget v0, v0, Lye/m0$d;->d:I

    move/from16 v2, v18

    int-to-float v2, v2

    int-to-float v0, v0

    mul-float/2addr v2, v0

    const/16 v0, 0x2710

    int-to-float v0, v0

    div-float/2addr v2, v0

    goto :goto_1e

    :catch_6
    move-exception v0

    goto :goto_20

    :cond_29
    const/4 v2, 0x0

    :goto_1e
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_2a

    invoke-static {v0}, Lye/n0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v0

    goto :goto_1f

    :cond_2a
    move-object/from16 v0, v17

    :goto_1f
    if-eqz v0, :cond_2b

    invoke-static {v0, v2}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_2b
    sget-object v3, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v3, :cond_2c

    invoke-static {v3}, Lio/appmetrica/analytics/impl/jq;->b(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v17

    :cond_2c
    move-object/from16 v3, v17

    if-eqz v3, :cond_2d

    invoke-static {v3, v2}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_2d
    sget-object v2, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v2, :cond_2e

    invoke-static {v2, v0}, Lio/appmetrica/analytics/impl/kq;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    :cond_2e
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_2f

    invoke-static {v0, v3}, Lye/o0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V
    :try_end_6
    .catch Ljava/lang/Exception; {:try_start_6 .. :try_end_6} :catch_6

    goto :goto_21

    :goto_20
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v2

    invoke-virtual {v2, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_2f
    :goto_21
    iget-object v2, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->f:Lye/m0$a;

    if-eqz v21, :cond_33

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    iget-boolean v3, v2, Lye/m0$a;->b:Z

    if-eqz v3, :cond_30

    iget v3, v2, Lye/m0$a;->c:I

    if-ne v3, v0, :cond_30

    goto :goto_25

    :cond_30
    iput v0, v2, Lye/m0$a;->c:I

    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v11, 0x1c

    if-lt v3, v11, :cond_32

    sget-boolean v3, Lye/m0;->k:Z

    if-eqz v3, :cond_31

    goto :goto_23

    :cond_31
    :goto_22
    const/4 v11, 0x1

    goto :goto_24

    :cond_32
    :goto_23
    invoke-virtual {v2}, Lye/m0$a;->a()V

    :try_start_7
    new-instance v3, Landroid/media/audiofx/BassBoost;

    invoke-direct {v3, v5, v0}, Landroid/media/audiofx/BassBoost;-><init>(II)V

    iput-object v3, v2, Lye/m0$a;->a:Landroid/media/audiofx/BassBoost;

    const/4 v11, 0x1

    invoke-virtual {v3, v11}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I
    :try_end_7
    .catch Ljava/lang/Exception; {:try_start_7 .. :try_end_7} :catch_7

    goto :goto_22

    :catch_7
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v3

    invoke-virtual {v3, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    goto :goto_22

    :goto_24
    iput-boolean v11, v2, Lye/m0$a;->b:Z

    :goto_25
    invoke-virtual {v2, v8}, Lye/m0$a;->b(I)V

    goto :goto_26

    :cond_33
    invoke-virtual {v2}, Lye/m0$a;->a()V

    :goto_26
    iget-object v2, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->e:Lye/m0$d;

    if-eqz v23, :cond_37

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    iget-boolean v3, v2, Lye/m0$d;->b:Z

    if-eqz v3, :cond_34

    iget v3, v2, Lye/m0$d;->c:I

    if-ne v3, v0, :cond_34

    :goto_27
    move/from16 v3, v22

    goto :goto_2b

    :cond_34
    iput v0, v2, Lye/m0$d;->c:I

    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v11, 0x1c

    if-lt v3, v11, :cond_36

    sget-boolean v3, Lye/m0;->k:Z

    if-eqz v3, :cond_35

    goto :goto_29

    :cond_35
    :goto_28
    const/4 v11, 0x1

    goto :goto_2a

    :cond_36
    :goto_29
    invoke-virtual {v2}, Lye/m0$d;->a()V

    :try_start_8
    new-instance v3, Landroid/media/audiofx/LoudnessEnhancer;

    invoke-direct {v3, v0}, Landroid/media/audiofx/LoudnessEnhancer;-><init>(I)V

    iput-object v3, v2, Lye/m0$d;->a:Landroid/media/audiofx/LoudnessEnhancer;

    const/4 v11, 0x1

    invoke-virtual {v3, v11}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I
    :try_end_8
    .catch Ljava/lang/RuntimeException; {:try_start_8 .. :try_end_8} :catch_8

    goto :goto_28

    :catch_8
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v3

    invoke-virtual {v3, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    goto :goto_28

    :goto_2a
    iput-boolean v11, v2, Lye/m0$d;->b:Z

    goto :goto_27

    :goto_2b
    invoke-virtual {v2, v3}, Lye/m0$d;->b(I)V

    goto :goto_2c

    :cond_37
    invoke-virtual {v2}, Lye/m0$d;->a()V

    :goto_2c
    return-void

    :cond_38
    invoke-static/range {v16 .. v16}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17
.end method

.method public final d()V
    .locals 4

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->r:Lip/n2;

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    invoke-virtual {v0, v1}, Lip/x1;->e(Ljava/util/concurrent/CancellationException;)V

    :cond_0
    invoke-static {p0}, Lam/b;->b(Landroidx/lifecycle/g0;)Landroidx/lifecycle/c0;

    move-result-object v0

    new-instance v2, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;

    invoke-direct {v2, p0, v1}, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;-><init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;Lbm/e;)V

    const/4 v3, 0x3

    invoke-static {v0, v1, v1, v2, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    move-result-object v0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->r:Lip/n2;

    return-void
.end method

.method public final onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1

    const-string v0, "intent"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-super {p0, p1}, Landroidx/lifecycle/j0;->onBind(Landroid/content/Intent;)Landroid/os/IBinder;

    iget-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->c:Lcom/jazibkhan/equalizer/services/MainForegroundService$a;

    return-object p1
.end method

.method public final onCreate()V
    .locals 4

    invoke-super {p0}, Landroidx/lifecycle/j0;->onCreate()V

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-nez v0, :cond_0

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0}, Lk7/f;->a(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    const-string v1, "getDefaultSharedPreferences(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    :cond_0
    new-instance v0, Landroid/app/NotificationChannel;

    const-string v1, "myChannel"

    const-string v2, "Equalizer persistent notification"

    const/4 v3, 0x2

    invoke-direct {v0, v1, v2, v3}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    const-string v1, "This notification is shown when the equalizer is enabled"

    invoke-virtual {v0, v1}, Landroid/app/NotificationChannel;->setDescription(Ljava/lang/String;)V

    const-class v1, Landroid/app/NotificationManager;

    invoke-virtual {p0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Landroid/app/NotificationManager;

    invoke-virtual {v1, v0}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    const/4 v0, 0x0

    :try_start_0
    const-string v1, "audio"

    invoke-virtual {p0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v1

    instance-of v2, v1, Landroid/media/AudioManager;

    if-eqz v2, :cond_1

    check-cast v1, Landroid/media/AudioManager;

    goto :goto_0

    :catch_0
    move-exception v1

    goto :goto_1

    :cond_1
    move-object v1, v0

    :goto_0
    iput-object v1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->m:Landroid/media/AudioManager;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_2

    :goto_1
    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->m:Landroid/media/AudioManager;

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v2

    invoke-virtual {v2, v1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_2
    :try_start_1
    const-string v1, "android.media.intent.category.LIVE_AUDIO"

    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V

    invoke-virtual {v2, v1}, Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z

    move-result v3

    if-nez v3, :cond_2

    invoke-virtual {v2, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    :cond_2
    new-instance v1, Landroid/os/Bundle;

    invoke-direct {v1}, Landroid/os/Bundle;-><init>()V

    const-string v3, "controlCategories"

    invoke-virtual {v1, v3, v2}, Landroid/os/Bundle;->putStringArrayList(Ljava/lang/String;Ljava/util/ArrayList;)V

    new-instance v3, Lj7/v;

    invoke-direct {v3, v1, v2}, Lj7/v;-><init>(Landroid/os/Bundle;Ljava/util/ArrayList;)V

    invoke-static {p0}, Lj7/w;->d(Lcom/jazibkhan/equalizer/services/MainForegroundService;)Lj7/w;

    move-result-object v1

    iput-object v1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->l:Lj7/w;

    iget-object v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->s:Lcom/jazibkhan/equalizer/services/MainForegroundService$e;

    invoke-virtual {v1, v3, v2}, Lj7/w;->a(Lj7/v;Lj7/w$a;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->d()V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_3

    :catch_1
    move-exception v1

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->l:Lj7/w;

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, v1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_3
    return-void
.end method

.method public final onDestroy()V
    .locals 5

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->b()V

    :try_start_0
    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->l:Lj7/w;

    if-eqz v0, :cond_3

    iget-object v0, v0, Lj7/w;->b:Ljava/util/ArrayList;

    iget-object v1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->s:Lcom/jazibkhan/equalizer/services/MainForegroundService$e;

    if-eqz v1, :cond_2

    invoke-static {}, Lj7/w;->b()V

    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I

    move-result v2

    const/4 v3, 0x0

    :goto_0
    if-ge v3, v2, :cond_1

    invoke-virtual {v0, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lj7/w$b;

    iget-object v4, v4, Lj7/w$b;->b:Lj7/w$a;

    if-ne v4, v1, :cond_0

    goto :goto_1

    :cond_0
    add-int/lit8 v3, v3, 0x1

    goto :goto_0

    :cond_1
    const/4 v3, -0x1

    :goto_1
    if-ltz v3, :cond_3

    invoke-virtual {v0, v3}, Ljava/util/ArrayList;->remove(I)Ljava/lang/Object;

    invoke-static {}, Lj7/w;->c()Lj7/b;

    move-result-object v0

    invoke-virtual {v0}, Lj7/b;->i()V

    goto :goto_2

    :cond_2
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v1, "callback must not be null"

    invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_3
    :goto_2
    const/4 v0, 0x0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->l:Lj7/w;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->m:Landroid/media/AudioManager;

    invoke-super {p0}, Landroidx/lifecycle/j0;->onDestroy()V

    return-void
.end method

.method public final onStartCommand(Landroid/content/Intent;II)I
    .locals 17

    move-object/from16 v1, p0

    move-object/from16 v0, p1

    const-string v2, "notification"

    invoke-super/range {p0 .. p3}, Landroid/app/Service;->onStartCommand(Landroid/content/Intent;II)I

    const-string v3, "sticky_service_equalizer"

    const-string v4, "Global Mix"

    const/4 v5, 0x0

    const-string v6, "package_name"

    const-string v7, "session_id"

    const-string v8, "com.jazibkhan.foregroundservice.action.startforeground"

    const-class v9, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    const-string v10, "mPref"

    const/16 v11, 0x22

    const/4 v12, 0x2

    const/4 v13, 0x1

    const/4 v14, 0x0

    if-nez v0, :cond_6

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v2, v14, [Landroid/os/Bundle;

    const-string v15, "onStartCommand_intent_null_recovery"

    invoke-virtual {v0, v15, v2}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_5

    invoke-interface {v0, v7, v14}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v0

    iput v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_4

    invoke-interface {v0, v6, v4}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    iput-object v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    invoke-static {}, Lkf/f;->f()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {}, Lkf/f;->b()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {}, Lkf/f;->i()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {}, Lkf/f;->p()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {}, Lkf/f;->l()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-static {}, Lkf/f;->d()Z

    move-result v0

    if-eqz v0, :cond_0

    goto :goto_0

    :cond_0
    invoke-virtual {v1, v13}, Landroid/app/Service;->stopForeground(I)V

    invoke-virtual {v1}, Landroid/app/Service;->stopSelf()V

    return v12

    :cond_1
    :goto_0
    new-instance v0, Landroid/content/Intent;

    invoke-direct {v0, v1, v9}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v8}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    iget v2, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    invoke-virtual {v0, v7, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    iget-object v2, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    invoke-virtual {v0, v6, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    move/from16 v2, p2

    move/from16 v4, p3

    invoke-virtual {v1, v0, v2, v4}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->onStartCommand(Landroid/content/Intent;II)I

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v0, v11, :cond_3

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_2

    invoke-interface {v0, v3, v14}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-nez v0, :cond_3

    return v12

    :cond_2
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v5

    :cond_3
    return v13

    :cond_4
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v5

    :cond_5
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v5

    :cond_6
    invoke-virtual {v0}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v15

    move-object/from16 v16, v5

    const-string v5, "stopped_via_notification_button"

    const-string v11, "com.jazibkhan.foregroundservice.action.stopforeground"

    if-eqz v15, :cond_7

    invoke-virtual {v0}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v15

    invoke-static {v15, v8}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v8

    if-nez v8, :cond_8

    invoke-virtual {v0}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v8

    const-string v15, "start_with_audio_session"

    invoke-static {v8, v15}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v8

    if-eqz v8, :cond_7

    goto :goto_1

    :cond_7
    move v6, v12

    goto/16 :goto_7

    :cond_8
    :goto_1
    const/16 v8, 0x65

    :try_start_0
    invoke-virtual {v1, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    const-string v15, "null cannot be cast to non-null type android.app.NotificationManager"

    invoke-static {v0, v15}, Lkotlin/jvm/internal/l;->d(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v0, Landroid/app/NotificationManager;

    invoke-virtual {v0, v8}, Landroid/app/NotificationManager;->cancel(I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_2

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v15

    invoke-virtual {v15, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_2
    new-instance v0, Landroid/content/Intent;

    const-class v15, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-direct {v0, v1, v15}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    const-string v15, "com.jazibkhan.equalizer.action.main"

    invoke-virtual {v0, v15}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    const v15, 0x10008000

    invoke-virtual {v0, v15}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    invoke-virtual {v0, v2, v13}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    const/high16 v15, 0x4000000

    invoke-static {v1, v14, v0, v15}, Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v0

    new-instance v15, Landroid/widget/RemoteViews;

    invoke-virtual {v1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v8

    const v12, 0x7f0d00e4

    invoke-direct {v15, v8, v12}, Landroid/widget/RemoteViews;-><init>(Ljava/lang/String;I)V

    new-instance v8, Landroid/content/Intent;

    invoke-direct {v8, v1, v9}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v8, v11}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v8, v5, v13}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    const/high16 v5, 0x14000000

    invoke-static {v1, v14, v8, v5}, Landroid/app/PendingIntent;->getService(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v5

    invoke-static {v5}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    const v8, 0x7f0a0305

    invoke-virtual {v15, v8, v5}, Landroid/widget/RemoteViews;->setOnClickPendingIntent(ILandroid/app/PendingIntent;)V

    sget-object v8, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v8, :cond_11

    invoke-interface {v8, v7, v14}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v7

    iput v7, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    sget-object v7, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v7, :cond_10

    invoke-interface {v7, v6, v4}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    iput-object v4, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    const/16 v4, 0x1f

    const v6, 0x7f0800f9

    const-string v7, "myChannel"

    const/4 v8, -0x1

    if-lt v2, v4, :cond_b

    new-instance v4, Li3/q;

    invoke-direct {v4, v1, v7}, Li3/q;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    iget-object v7, v4, Li3/q;->u:Landroid/app/Notification;

    iput v6, v7, Landroid/app/Notification;->icon:I

    iput-boolean v14, v4, Li3/q;->k:Z

    iput-object v0, v4, Li3/q;->g:Landroid/app/PendingIntent;

    iput v8, v4, Li3/q;->j:I

    iput v8, v4, Li3/q;->p:I

    const v0, 0x7f1300b4

    invoke-virtual {v1, v0}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object v0

    iput-object v0, v4, Li3/q;->e:Ljava/lang/CharSequence;

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_a

    const-string v6, "only_music_player"

    invoke-interface {v0, v6, v14}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-eqz v0, :cond_9

    iget v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    if-nez v0, :cond_9

    const v0, 0x7f1301fa

    invoke-virtual {v1, v0}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v0

    goto :goto_3

    :cond_9
    iget-object v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    invoke-static {v1, v0}, Lkf/a;->c(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    filled-new-array {v0}, [Ljava/lang/Object;

    move-result-object v0

    const v6, 0x7f13004e

    invoke-virtual {v1, v6, v0}, Landroid/content/Context;->getString(I[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    :goto_3
    invoke-static {v0}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object v0

    iput-object v0, v4, Li3/q;->f:Ljava/lang/CharSequence;

    const v0, 0x7f1302c4

    invoke-virtual {v1, v0}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v0

    const v6, 0x108001d

    invoke-virtual {v4, v6, v0, v5}, Li3/q;->a(ILjava/lang/String;Landroid/app/PendingIntent;)V

    iput v13, v4, Li3/q;->s:I

    invoke-virtual {v4}, Li3/q;->b()Landroid/app/Notification;

    move-result-object v0

    const/4 v6, 0x2

    goto :goto_4

    :cond_a
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v16

    :cond_b
    new-instance v4, Li3/q;

    invoke-direct {v4, v1, v7}, Li3/q;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    iget-object v5, v4, Li3/q;->u:Landroid/app/Notification;

    iput v6, v5, Landroid/app/Notification;->icon:I

    iput-object v15, v4, Li3/q;->q:Landroid/widget/RemoteViews;

    iput-boolean v14, v4, Li3/q;->k:Z

    iput-object v0, v4, Li3/q;->g:Landroid/app/PendingIntent;

    iput v8, v4, Li3/q;->j:I

    iput v8, v4, Li3/q;->p:I

    const/4 v6, 0x2

    invoke-virtual {v4, v6, v13}, Li3/q;->d(IZ)V

    iput v13, v4, Li3/q;->s:I

    invoke-virtual {v4}, Li3/q;->b()Landroid/app/Notification;

    move-result-object v0

    :goto_4
    invoke-static {v0}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    const/16 v4, 0x22

    if-lt v2, v4, :cond_e

    sget-object v5, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v5}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v5

    iget-object v5, v5, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v7, v14, [Landroid/os/Bundle;

    const-string v8, "onStartCommand_serviceType_1073741824"

    invoke-virtual {v5, v8, v7}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    if-lt v2, v4, :cond_c

    :try_start_1
    invoke-static {v1, v0}, Li3/b0;->a(Lcom/jazibkhan/equalizer/services/MainForegroundService;Landroid/app/Notification;)V

    goto :goto_6

    :catch_1
    move-exception v0

    goto :goto_5

    :cond_c
    const/16 v4, 0x1d

    if-lt v2, v4, :cond_d

    invoke-static {v1, v0}, Li3/a0;->a(Lcom/jazibkhan/equalizer/services/MainForegroundService;Landroid/app/Notification;)V

    goto :goto_6

    :cond_d
    const/16 v2, 0x65

    invoke-virtual {v1, v2, v0}, Landroid/app/Service;->startForeground(ILandroid/app/Notification;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_6

    :goto_5
    sget-object v2, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v2

    iget-object v2, v2, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v4, v14, [Landroid/os/Bundle;

    const-string v5, "startForeground_exception"

    invoke-virtual {v2, v5, v4}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {v0}, Ljava/lang/Throwable;->printStackTrace()V

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v2

    invoke-virtual {v2, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    invoke-virtual {v1, v13}, Landroid/app/Service;->stopForeground(I)V

    invoke-virtual {v1}, Landroid/app/Service;->stopSelf()V

    goto :goto_6

    :cond_e
    const/16 v2, 0x65

    invoke-virtual {v1, v2, v0}, Landroid/app/Service;->startForeground(ILandroid/app/Notification;)V

    :goto_6
    iget-object v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;

    if-eqz v0, :cond_f

    iget-object v2, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->k:Ljava/lang/String;

    invoke-interface {v0, v2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$b;->c(Ljava/lang/String;)V

    :cond_f
    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->c()V

    goto :goto_9

    :cond_10
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v16

    :cond_11
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v16

    :goto_7
    invoke-virtual {v0}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v2

    if-eqz v2, :cond_14

    invoke-virtual {v0}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v2

    invoke-static {v2, v11}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_14

    invoke-virtual {v0, v5, v14}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z

    move-result v0

    if-eqz v0, :cond_13

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_12

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v2, "eqswitch"

    invoke-interface {v0, v2, v14}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {v14}, Lkf/f;->w(Z)V

    invoke-static {v14}, Lkf/f;->C(Z)V

    invoke-static {v14}, Lkf/f;->A(Z)V

    invoke-static {v14}, Lkf/f;->F(Z)V

    invoke-static {v14}, Lkf/f;->x(Z)V

    iget-object v0, v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;

    if-eqz v0, :cond_13

    invoke-interface {v0}, Lcom/jazibkhan/equalizer/services/MainForegroundService$b;->d()V

    goto :goto_8

    :cond_12
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v16

    :cond_13
    :goto_8
    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->b()V

    invoke-virtual {v1, v13}, Landroid/app/Service;->stopForeground(I)V

    invoke-virtual {v1}, Landroid/app/Service;->stopSelf()V

    :cond_14
    :goto_9
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v4, 0x22

    if-lt v0, v4, :cond_16

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_15

    invoke-interface {v0, v3, v14}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-nez v0, :cond_16

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v2, v14, [Landroid/os/Bundle;

    const-string v3, "START_NOT_STICKY"

    invoke-virtual {v0, v3, v2}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    move v12, v6

    goto :goto_a

    :cond_15
    invoke-static {v10}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v16

    :cond_16
    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v2, v14, [Landroid/os/Bundle;

    const-string v3, "START_STICKY"

    invoke-virtual {v0, v3, v2}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    move v12, v13

    :goto_a
    return v12
.end method
