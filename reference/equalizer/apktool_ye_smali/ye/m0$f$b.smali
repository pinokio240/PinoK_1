.class public final Lye/m0$f$b;
.super Ldm/i;

# interfaces
.implements Lmm/p;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lye/m0$f;->a(I)V
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
    c = "com.jazibkhan.equalizer.JEffectsContainer$JVirtualizer$create$2"
    f = "JEffects.kt"
    l = {
        0x20c
    }
    m = "invokeSuspend"
.end annotation


# instance fields
.field public l:I

.field public final synthetic m:Lye/m0$f;


# direct methods
.method public constructor <init>(Lye/m0$f;Lbm/e;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lye/m0$f;",
            "Lbm/e<",
            "-",
            "Lye/m0$f$b;",
            ">;)V"
        }
    .end annotation

    iput-object p1, p0, Lye/m0$f$b;->m:Lye/m0$f;

    const/4 p1, 0x2

    invoke-direct {p0, p1, p2}, Ldm/i;-><init>(ILbm/e;)V

    return-void
.end method


# virtual methods
.method public final create(Ljava/lang/Object;Lbm/e;)Lbm/e;
    .locals 1
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

    new-instance p1, Lye/m0$f$b;

    iget-object v0, p0, Lye/m0$f$b;->m:Lye/m0$f;

    invoke-direct {p1, v0, p2}, Lye/m0$f$b;-><init>(Lye/m0$f;Lbm/e;)V

    return-object p1
.end method

.method public final invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 0

    check-cast p1, Lip/h0;

    check-cast p2, Lbm/e;

    invoke-virtual {p0, p1, p2}, Lye/m0$f$b;->create(Ljava/lang/Object;Lbm/e;)Lbm/e;

    move-result-object p1

    check-cast p1, Lye/m0$f$b;

    sget-object p2, Lxl/e0;->a:Lxl/e0;

    invoke-virtual {p1, p2}, Lye/m0$f$b;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 3

    sget-object v0, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    iget v1, p0, Lye/m0$f$b;->l:I

    const/4 v2, 0x1

    if-eqz v1, :cond_1

    if-ne v1, v2, :cond_0

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    goto :goto_0

    :cond_0
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_1
    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    iput v2, p0, Lye/m0$f$b;->l:I

    const-wide/16 v1, 0x190

    invoke-static {v1, v2, p0}, Lip/r0;->a(JLbm/e;)Ljava/lang/Object;

    move-result-object p1

    if-ne p1, v0, :cond_2

    return-object v0

    :cond_2
    :goto_0
    iget-object p1, p0, Lye/m0$f$b;->m:Lye/m0$f;

    iget-object p1, p1, Lye/m0$f;->a:Landroid/media/audiofx/Virtualizer;

    if-eqz p1, :cond_3

    const/4 v0, 0x2

    invoke-virtual {p1, v0}, Landroid/media/audiofx/Virtualizer;->forceVirtualizationMode(I)Z

    :cond_3
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method
