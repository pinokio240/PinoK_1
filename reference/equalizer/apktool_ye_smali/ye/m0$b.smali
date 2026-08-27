.class public final Lye/m0$b;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lye/m0;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "b"
.end annotation


# instance fields
.field public a:Z

.field public b:I

.field public c:F


# virtual methods
.method public final a(F)V
    .locals 6

    iput p1, p0, Lye/m0$b;->c:F

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1c

    if-lt v0, v1, :cond_8

    sget-boolean v0, Lye/m0;->k:Z

    if-nez v0, :cond_8

    :try_start_0
    sget-object v0, Lye/m0;->c:Lye/m0$d;

    iget-boolean v1, v0, Lye/m0$d;->b:Z

    const/4 v2, 0x0

    const/16 v3, 0x14

    if-eqz v1, :cond_0

    iget v0, v0, Lye/m0$d;->d:I

    int-to-float v1, v3

    int-to-float v0, v0

    mul-float/2addr v1, v0

    const/16 v0, 0x2710

    int-to-float v0, v0

    div-float/2addr v1, v0

    goto :goto_0

    :catch_0
    move-exception p1

    goto :goto_4

    :cond_0
    move v1, v2

    :goto_0
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    const/4 v4, 0x0

    if-eqz v0, :cond_1

    invoke-static {v0}, Lye/n0;->a(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v0

    goto :goto_1

    :cond_1
    move-object v0, v4

    :goto_1
    if-eqz v0, :cond_3

    cmpg-float v5, p1, v2

    if-gtz v5, :cond_2

    move v5, v2

    goto :goto_2

    :cond_2
    int-to-float v5, v3

    mul-float/2addr v5, p1

    neg-float v5, v5

    :goto_2
    add-float/2addr v5, v1

    invoke-static {v0, v5}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_3
    sget-object v5, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v5, :cond_4

    invoke-static {v5}, Lio/appmetrica/analytics/impl/jq;->b(Landroid/media/audiofx/DynamicsProcessing;)Landroid/media/audiofx/DynamicsProcessing$Limiter;

    move-result-object v4

    :cond_4
    if-eqz v4, :cond_6

    cmpl-float v5, p1, v2

    if-ltz v5, :cond_5

    goto :goto_3

    :cond_5
    int-to-float v2, v3

    mul-float/2addr v2, p1

    :goto_3
    add-float/2addr v2, v1

    invoke-static {v4, v2}, Lq8/b;->b(Landroid/media/audiofx/DynamicsProcessing$Limiter;F)V

    :cond_6
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_7

    invoke-static {p1, v0}, Lio/appmetrica/analytics/impl/kq;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V

    :cond_7
    sget-object p1, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz p1, :cond_8

    invoke-static {p1, v4}, Lye/o0;->a(Landroid/media/audiofx/DynamicsProcessing;Landroid/media/audiofx/DynamicsProcessing$Limiter;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_4
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_8
    return-void
.end method
