.class public final Lye/m0$a;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lye/m0;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "a"
.end annotation


# instance fields
.field public a:Landroid/media/audiofx/BassBoost;

.field public b:Z

.field public c:I


# virtual methods
.method public final a()V
    .locals 4

    iget-boolean v0, p0, Lye/m0$a;->b:Z

    if-nez v0, :cond_0

    goto :goto_4

    :cond_0
    const/4 v0, 0x0

    iput-boolean v0, p0, Lye/m0$a;->b:Z

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x1c

    const/4 v3, 0x0

    if-lt v1, v2, :cond_3

    sget-boolean v1, Lye/m0;->k:Z

    if-nez v1, :cond_3

    :try_start_0
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_1

    invoke-static {v0}, Lye/j0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v3

    goto :goto_0

    :catch_0
    move-exception v0

    goto :goto_1

    :cond_1
    :goto_0
    if-eqz v3, :cond_2

    invoke-static {v3}, Lye/w;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v0

    if-eqz v0, :cond_2

    invoke-static {v0}, Lye/k0;->a(Landroid/media/audiofx/DynamicsProcessing$EqBand;)V

    :cond_2
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_6

    invoke-static {v0, v3}, Lye/e0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Eq;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_1
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;

    goto :goto_4

    :cond_3
    :try_start_1
    iget-object v1, p0, Lye/m0$a;->a:Landroid/media/audiofx/BassBoost;

    if-eqz v1, :cond_4

    invoke-virtual {v1, v0}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I

    goto :goto_2

    :catch_1
    move-exception v0

    goto :goto_3

    :cond_4
    :goto_2
    iget-object v0, p0, Lye/m0$a;->a:Landroid/media/audiofx/BassBoost;

    if-eqz v0, :cond_5

    invoke-virtual {v0}, Landroid/media/audiofx/AudioEffect;->release()V

    :cond_5
    iput-object v3, p0, Lye/m0$a;->a:Landroid/media/audiofx/BassBoost;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    return-void

    :goto_3
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_6
    :goto_4
    return-void
.end method

.method public final b(I)V
    .locals 4

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1c

    if-lt v0, v1, :cond_4

    sget-boolean v0, Lye/m0;->k:Z

    if-nez v0, :cond_4

    :try_start_0
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    invoke-static {v0}, Lye/j0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v0

    goto :goto_0

    :catch_0
    move-exception p1

    goto :goto_2

    :cond_0
    move-object v0, v1

    :goto_0
    if-eqz v0, :cond_1

    invoke-static {v0}, Lye/w;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v2

    if-eqz v2, :cond_1

    invoke-static {v2}, Lye/l0;->a(Landroid/media/audiofx/DynamicsProcessing$EqBand;)F

    move-result v1

    invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object v1

    :cond_1
    sget v2, Lye/m0;->n:I

    int-to-float v2, v2

    int-to-float p1, p1

    mul-float/2addr v2, p1

    const/16 v3, 0x3e8

    int-to-float v3, v3

    div-float/2addr v2, v3

    if-eqz v1, :cond_2

    invoke-virtual {v1}, Ljava/lang/Float;->floatValue()F

    move-result v1

    cmpl-float v1, v1, v2

    if-nez v1, :cond_2

    const/4 v1, 0x1

    goto :goto_1

    :cond_2
    const/4 v1, 0x0

    :goto_1
    if-nez v1, :cond_5

    if-eqz v0, :cond_3

    invoke-static {v0}, Lye/w;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v1

    if-eqz v1, :cond_3

    sget v2, Lye/m0;->n:I

    int-to-float v2, v2

    mul-float/2addr v2, p1

    div-float/2addr v2, v3

    invoke-static {v1, v2}, Landroidx/core/view/p;->b(Landroid/media/audiofx/DynamicsProcessing$EqBand;F)V

    :cond_3
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_5

    invoke-static {p1, v0}, Lye/e0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Eq;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_2
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    goto :goto_3

    :cond_4
    :try_start_1
    iget-object v0, p0, Lye/m0$a;->a:Landroid/media/audiofx/BassBoost;

    if-eqz v0, :cond_5

    int-to-short p1, p1

    invoke-virtual {v0, p1}, Landroid/media/audiofx/BassBoost;->setStrength(S)V

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

    :cond_5
    :goto_3
    return-void
.end method
