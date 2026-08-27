.class public final Lye/m0$c;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lye/m0;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "c"
.end annotation


# instance fields
.field public a:Landroid/media/audiofx/Equalizer;

.field public b:I

.field public c:Z


# virtual methods
.method public final a()V
    .locals 4

    iget-boolean v0, p0, Lye/m0$c;->c:Z

    if-nez v0, :cond_0

    goto :goto_5

    :cond_0
    const/4 v0, 0x0

    iput-boolean v0, p0, Lye/m0$c;->c:Z

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x1c

    const/4 v3, 0x0

    if-lt v1, v2, :cond_3

    sget-boolean v1, Lye/m0;->k:Z

    if-nez v1, :cond_3

    :try_start_0
    sget-object v1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v1, :cond_1

    invoke-static {v1}, Lye/v;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v3

    goto :goto_0

    :catch_0
    move-exception v0

    goto :goto_2

    :cond_1
    :goto_0
    if-eqz v3, :cond_2

    invoke-static {v3}, Lcom/google/android/material/resources/a;->b(Landroid/media/audiofx/DynamicsProcessing$Eq;)I

    move-result v1

    :goto_1
    if-ge v0, v1, :cond_2

    invoke-static {v3, v0}, Lye/i0;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;I)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v2

    invoke-static {v2}, Lye/k0;->a(Landroid/media/audiofx/DynamicsProcessing$EqBand;)V

    add-int/lit8 v0, v0, 0x1

    goto :goto_1

    :cond_2
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_6

    invoke-static {v0, v3}, Lrg/g;->b(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Eq;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_2
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object v0, Lxl/e0;->a:Lxl/e0;

    goto :goto_5

    :cond_3
    :try_start_1
    iget-object v1, p0, Lye/m0$c;->a:Landroid/media/audiofx/Equalizer;

    if-eqz v1, :cond_4

    invoke-virtual {v1, v0}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I

    goto :goto_3

    :catch_1
    move-exception v0

    goto :goto_4

    :cond_4
    :goto_3
    iget-object v0, p0, Lye/m0$c;->a:Landroid/media/audiofx/Equalizer;

    if-eqz v0, :cond_5

    invoke-virtual {v0}, Landroid/media/audiofx/AudioEffect;->release()V

    :cond_5
    iput-object v3, p0, Lye/m0$c;->a:Landroid/media/audiofx/Equalizer;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    return-void

    :goto_4
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_6
    :goto_5
    return-void
.end method

.method public final b(II)V
    .locals 3

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1c

    if-lt v0, v1, :cond_4

    sget-boolean v0, Lye/m0;->k:Z

    if-nez v0, :cond_4

    :try_start_0
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    invoke-static {v0}, Lye/v;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Eq;

    move-result-object v0

    goto :goto_0

    :catch_0
    move-exception p1

    goto :goto_2

    :cond_0
    move-object v0, v1

    :goto_0
    if-eqz v0, :cond_1

    invoke-static {v0, p1}, Lye/i0;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;I)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object v2

    if-eqz v2, :cond_1

    invoke-static {v2}, Lye/l0;->a(Landroid/media/audiofx/DynamicsProcessing$EqBand;)F

    move-result v1

    invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object v1

    :cond_1
    int-to-float p2, p2

    const/16 v2, 0x64

    int-to-float v2, v2

    div-float/2addr p2, v2

    if-eqz v1, :cond_2

    invoke-virtual {v1}, Ljava/lang/Float;->floatValue()F

    move-result v1

    cmpl-float v1, v1, p2

    if-nez v1, :cond_2

    const/4 v1, 0x1

    goto :goto_1

    :cond_2
    const/4 v1, 0x0

    :goto_1
    if-nez v1, :cond_5

    if-eqz v0, :cond_3

    invoke-static {v0, p1}, Lye/i0;->a(Landroid/media/audiofx/DynamicsProcessing$Eq;I)Landroid/media/audiofx/DynamicsProcessing$EqBand;

    move-result-object p1

    if-eqz p1, :cond_3

    invoke-static {p1, p2}, Landroidx/core/view/p;->b(Landroid/media/audiofx/DynamicsProcessing$EqBand;F)V

    :cond_3
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_5

    invoke-static {p1, v0}, Lrg/g;->b(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Eq;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_2
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object p2

    invoke-virtual {p2, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    goto :goto_3

    :cond_4
    :try_start_1
    iget-object v0, p0, Lye/m0$c;->a:Landroid/media/audiofx/Equalizer;

    if-eqz v0, :cond_5

    int-to-short p1, p1

    int-to-short p2, p2

    invoke-virtual {v0, p1, p2}, Landroid/media/audiofx/Equalizer;->setBandLevel(SS)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    return-void

    :catch_1
    move-exception p1

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object p2

    invoke-virtual {p2, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    :cond_5
    :goto_3
    return-void
.end method
