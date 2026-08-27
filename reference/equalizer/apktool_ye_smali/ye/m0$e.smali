.class public final Lye/m0$e;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lye/m0;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "e"
.end annotation


# instance fields
.field public a:Landroid/media/audiofx/PresetReverb;

.field public b:Z

.field public c:I


# virtual methods
.method public final a()V
    .locals 2

    const/4 v0, 0x0

    iput-boolean v0, p0, Lye/m0$e;->b:Z

    :try_start_0
    iget-object v1, p0, Lye/m0$e;->a:Landroid/media/audiofx/PresetReverb;

    if-eqz v1, :cond_0

    invoke-virtual {v1, v0}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I

    goto :goto_0

    :catch_0
    move-exception v0

    goto :goto_1

    :cond_0
    :goto_0
    iget-object v0, p0, Lye/m0$e;->a:Landroid/media/audiofx/PresetReverb;

    if-eqz v0, :cond_1

    invoke-virtual {v0}, Landroid/media/audiofx/AudioEffect;->release()V

    :cond_1
    const/4 v0, 0x0

    iput-object v0, p0, Lye/m0$e;->a:Landroid/media/audiofx/PresetReverb;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_1
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method

.method public final b(I)V
    .locals 1

    :try_start_0
    iget-object v0, p0, Lye/m0$e;->a:Landroid/media/audiofx/PresetReverb;

    if-eqz v0, :cond_0

    int-to-short p1, p1

    invoke-virtual {v0, p1}, Landroid/media/audiofx/PresetReverb;->setPreset(S)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception p1

    goto :goto_0

    :cond_0
    return-void

    :goto_0
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method
