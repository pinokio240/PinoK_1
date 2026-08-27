.class public final Lye/m0$f;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lye/m0;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "f"
.end annotation


# instance fields
.field public a:Landroid/media/audiofx/Virtualizer;

.field public b:Z

.field public c:I

.field public final d:Lnp/d;


# direct methods
.method public constructor <init>()V
    .locals 1

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    invoke-static {}, Lip/i0;->b()Lnp/d;

    move-result-object v0

    iput-object v0, p0, Lye/m0$f;->d:Lnp/d;

    return-void
.end method


# virtual methods
.method public final a(I)V
    .locals 4

    iget-boolean v0, p0, Lye/m0$f;->b:Z

    if-eqz v0, :cond_0

    iget v0, p0, Lye/m0$f;->c:I

    if-eq v0, p1, :cond_1

    :cond_0
    if-nez p1, :cond_2

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x22

    if-lt v0, v1, :cond_2

    :cond_1
    return-void

    :cond_2
    invoke-virtual {p0}, Lye/m0$f;->b()V

    iput p1, p0, Lye/m0$f;->c:I

    const/4 v0, 0x1

    iput-boolean v0, p0, Lye/m0$f;->b:Z

    :try_start_0
    new-instance v1, Landroid/media/audiofx/Virtualizer;

    const v2, 0x7fffffff

    invoke-direct {v1, v2, p1}, Landroid/media/audiofx/Virtualizer;-><init>(II)V

    iput-object v1, p0, Lye/m0$f;->a:Landroid/media/audiofx/Virtualizer;

    invoke-virtual {v1, v0}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I

    iget-object p1, p0, Lye/m0$f;->d:Lnp/d;

    sget-object v0, Lip/e0$a;->b:Lip/e0$a;

    new-instance v1, Lye/m0$f$a;

    invoke-direct {v1, v0}, Lbm/a;-><init>(Lbm/h$b;)V

    new-instance v0, Lye/m0$f$b;

    const/4 v2, 0x0

    invoke-direct {v0, p0, v2}, Lye/m0$f$b;-><init>(Lye/m0$f;Lbm/e;)V

    const/4 v3, 0x2

    invoke-static {p1, v1, v2, v0, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception p1

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method

.method public final b()V
    .locals 2

    const/4 v0, 0x0

    iput-boolean v0, p0, Lye/m0$f;->b:Z

    iget-object v1, p0, Lye/m0$f;->d:Lnp/d;

    iget-object v1, v1, Lnp/d;->b:Lbm/h;

    invoke-static {v1}, Lcq/e2;->c(Lbm/h;)V

    :try_start_0
    iget-object v1, p0, Lye/m0$f;->a:Landroid/media/audiofx/Virtualizer;

    if-eqz v1, :cond_0

    invoke-virtual {v1, v0}, Landroid/media/audiofx/AudioEffect;->setEnabled(Z)I

    goto :goto_0

    :catch_0
    move-exception v0

    goto :goto_1

    :cond_0
    :goto_0
    iget-object v0, p0, Lye/m0$f;->a:Landroid/media/audiofx/Virtualizer;

    if-eqz v0, :cond_1

    invoke-virtual {v0}, Landroid/media/audiofx/AudioEffect;->release()V

    :cond_1
    const/4 v0, 0x0

    iput-object v0, p0, Lye/m0$f;->a:Landroid/media/audiofx/Virtualizer;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :goto_1
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method

.method public final c(I)V
    .locals 1

    :try_start_0
    iget-object v0, p0, Lye/m0$f;->a:Landroid/media/audiofx/Virtualizer;

    if-eqz v0, :cond_0

    int-to-short p1, p1

    invoke-virtual {v0, p1}, Landroid/media/audiofx/Virtualizer;->setStrength(S)V
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
