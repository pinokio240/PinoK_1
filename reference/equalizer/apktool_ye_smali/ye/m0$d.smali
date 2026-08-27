.class public final Lye/m0$d;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lye/m0;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "d"
.end annotation


# instance fields
.field public a:Landroid/media/audiofx/LoudnessEnhancer;

.field public b:Z

.field public c:I

.field public d:I


# virtual methods
.method public final a()V
    .locals 6

    iget-boolean v0, p0, Lye/m0$d;->b:Z

    if-nez v0, :cond_0

    goto/16 :goto_7

    :cond_0
    const/4 v0, 0x0

    iput-boolean v0, p0, Lye/m0$d;->b:Z

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x1c

    const/4 v3, 0x0

    if-lt v1, v2, :cond_d

    sget-boolean v1, Lye/m0;->k:Z

    if-nez v1, :cond_d

    :try_start_0
    sget-boolean v0, Lye/m0;->p:Z

    if-eqz v0, :cond_a

    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_1

    invoke-static {v0}, Lye/n0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v0

    goto :goto_0

    :catch_0
    move-exception v0

    goto :goto_4

    :cond_1
    move-object v0, v3

    :goto_0
    const/16 v1, 0x14

    const/4 v2, 0x0

    if-eqz v0, :cond_4

    sget-object v4, Lye/m0;->f:Lye/m0$b;

    iget-boolean v5, v4, Lye/m0$b;->a:Z

    if-eqz v5, :cond_3

    iget v4, v4, Lye/m0$b;->c:F

    cmpg-float v5, v4, v2

    if-gtz v5, :cond_2

    goto :goto_1

    :cond_2
    int-to-float v5, v1

    mul-float/2addr v5, v4

    neg-float v4, v5

    goto :goto_2

    :cond_3
    :goto_1
    move v4, v2

    :goto_2
    invoke-static {v0, v4}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_4
    sget-object v4, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v4, :cond_5

    invoke-static {v4}, Lio/appmetrica/analytics/impl/jq;->b(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v3

    :cond_5
    if-eqz v3, :cond_8

    sget-object v4, Lye/m0;->f:Lye/m0$b;

    iget-boolean v5, v4, Lye/m0$b;->a:Z

    if-eqz v5, :cond_7

    iget v4, v4, Lye/m0$b;->c:F

    cmpl-float v5, v4, v2

    if-ltz v5, :cond_6

    goto :goto_3

    :cond_6
    int-to-float v1, v1

    mul-float v2, v1, v4

    :cond_7
    :goto_3
    invoke-static {v3, v2}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_8
    sget-object v1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v1, :cond_9

    invoke-static {v1, v0}, Lio/appmetrica/analytics/impl/kq;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    :cond_9
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_10

    invoke-static {v0, v3}, Lye/o0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;

    return-void

    :cond_a
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_b

    invoke-static {v0}, Lye/n0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v3

    :cond_b
    if-eqz v3, :cond_c

    invoke-static {v3}, Lye/p0;->a(Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    :cond_c
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_10

    invoke-static {v0, v3}, Lye/q0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_4
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;

    goto :goto_7

    :cond_d
    :try_start_1
    iget-object v1, p0, Lye/m0$d;->a:Landroid/media/audiofx/LoudnessEnhancer;

    if-eqz v1, :cond_e

    invoke-virtual {v1, v0}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I

    goto :goto_5

    :catch_1
    move-exception v0

    goto :goto_6

    :cond_e
    :goto_5
    iget-object v0, p0, Lye/m0$d;->a:Landroid/media/audiofx/LoudnessEnhancer;

    if-eqz v0, :cond_f

    invoke-virtual {v0}, Landroid/media/audiofx/AudioEffect;->release()V

    :cond_f
    iput-object v3, p0, Lye/m0$d;->a:Landroid/media/audiofx/LoudnessEnhancer;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    return-void

    :goto_6
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_10
    :goto_7
    return-void
.end method

.method public final b(I)V
    .locals 7

    iput p1, p0, Lye/m0$d;->d:I

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1c

    if-lt v0, v1, :cond_c

    sget-boolean v0, Lye/m0;->k:Z

    if-nez v0, :cond_c

    :try_start_0
    sget v0, Lye/m0;->o:I

    int-to-float v0, v0

    int-to-float p1, p1

    mul-float/2addr v0, p1

    const/16 p1, 0x2710

    int-to-float p1, p1

    div-float/2addr v0, p1

    sget-boolean p1, Lye/m0;->p:Z

    const/4 v1, 0x0

    if-eqz p1, :cond_9

    sget-object p1, Lye/m0;->f:Lye/m0$b;

    iget-boolean v2, p1, Lye/m0$b;->a:Z

    const/16 v3, 0x14

    const/4 v4, 0x0

    if-eqz v2, :cond_1

    iget v5, p1, Lye/m0$b;->c:F

    cmpg-float v6, v5, v4

    if-gtz v6, :cond_0

    goto :goto_0

    :cond_0
    int-to-float v6, v3

    mul-float/2addr v6, v5

    neg-float v5, v6

    goto :goto_1

    :catch_0
    move-exception p1

    goto :goto_4

    :cond_1
    :goto_0
    move v5, v4

    :goto_1
    if-eqz v2, :cond_3

    iget p1, p1, Lye/m0$b;->c:F

    cmpl-float v2, p1, v4

    if-ltz v2, :cond_2

    goto :goto_2

    :cond_2
    int-to-float v2, v3

    mul-float v4, v2, p1

    :cond_3
    :goto_2
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_4

    invoke-static {p1}, Lye/n0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object p1

    goto :goto_3

    :cond_4
    move-object p1, v1

    :goto_3
    if-eqz p1, :cond_5

    add-float/2addr v5, v0

    invoke-static {p1, v5}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_5
    sget-object v2, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v2, :cond_6

    invoke-static {v2}, Lio/appmetrica/analytics/impl/jq;->b(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v1

    :cond_6
    if-eqz v1, :cond_7

    add-float/2addr v0, v4

    invoke-static {v1, v0}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_7
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_8

    invoke-static {v0, p1}, Lio/appmetrica/analytics/impl/kq;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    :cond_8
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_d

    invoke-static {p1, v1}, Lye/o0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-void

    :cond_9
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_a

    invoke-static {p1}, Lye/n0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v1

    :cond_a
    if-eqz v1, :cond_b

    invoke-static {v1, v0}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_b
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_d

    invoke-static {p1, v1}, Lye/q0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_4
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    goto :goto_5

    :cond_c
    :try_start_1
    iget-object v0, p0, Lye/m0$d;->a:Landroid/media/audiofx/LoudnessEnhancer;

    if-eqz v0, :cond_d

    invoke-virtual {v0, p1}, Landroid/media/audiofx/LoudnessEnhancer;->setTargetGain(I)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    return-void

    :catch_1
    move-exception p1

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    :cond_d
    :goto_5
    return-void
.end method
