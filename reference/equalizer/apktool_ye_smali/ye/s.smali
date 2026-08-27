.class public final Lye/s;
.super Ldm/i;

# interfaces
.implements Lmm/p;


# annotations
.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ldm/i;",
        "Lmm/p<",
        "Lip/h0;",
        "Lbm/e<",
        "-",
        "Lbf/d;",
        ">;",
        "Ljava/lang/Object;",
        ">;"
    }
.end annotation

.annotation runtime Ldm/e;
    c = "com.jazibkhan.equalizer.CustomPresetRepository$createProfileLink$2"
    f = "CustomPresetRepository.kt"
    l = {
        0x5c
    }
    m = "invokeSuspend"
.end annotation


# instance fields
.field public l:I

.field public final synthetic m:Lcom/jazibkhan/equalizer/a;

.field public final synthetic n:Ljava/lang/String;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/a;Ljava/lang/String;Lbm/e;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/jazibkhan/equalizer/a;",
            "Ljava/lang/String;",
            "Lbm/e<",
            "-",
            "Lye/s;",
            ">;)V"
        }
    .end annotation

    iput-object p1, p0, Lye/s;->m:Lcom/jazibkhan/equalizer/a;

    iput-object p2, p0, Lye/s;->n:Ljava/lang/String;

    const/4 p1, 0x2

    invoke-direct {p0, p1, p3}, Ldm/i;-><init>(ILbm/e;)V

    return-void
.end method


# virtual methods
.method public final create(Ljava/lang/Object;Lbm/e;)Lbm/e;
    .locals 2
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

    new-instance p1, Lye/s;

    iget-object v0, p0, Lye/s;->m:Lcom/jazibkhan/equalizer/a;

    iget-object v1, p0, Lye/s;->n:Ljava/lang/String;

    invoke-direct {p1, v0, v1, p2}, Lye/s;-><init>(Lcom/jazibkhan/equalizer/a;Ljava/lang/String;Lbm/e;)V

    return-object p1
.end method

.method public final invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 0

    check-cast p1, Lip/h0;

    check-cast p2, Lbm/e;

    invoke-virtual {p0, p1, p2}, Lye/s;->create(Ljava/lang/Object;Lbm/e;)Lbm/e;

    move-result-object p1

    check-cast p1, Lye/s;

    sget-object p2, Lxl/e0;->a:Lxl/e0;

    invoke-virtual {p1, p2}, Lye/s;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 4

    sget-object v0, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    iget v1, p0, Lye/s;->l:I

    const/4 v2, 0x1

    if-eqz v1, :cond_1

    if-ne v1, v2, :cond_0

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    return-object p1

    :cond_0
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_1
    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    iget-object p1, p0, Lye/s;->m:Lcom/jazibkhan/equalizer/a;

    invoke-static {p1}, Lcom/jazibkhan/equalizer/a;->a(Lcom/jazibkhan/equalizer/a;)Lbf/a;

    move-result-object p1

    new-instance v1, Lbf/c;

    iget-object v3, p0, Lye/s;->n:Ljava/lang/String;

    invoke-direct {v1, v3}, Lbf/c;-><init>(Ljava/lang/String;)V

    iput v2, p0, Lye/s;->l:I

    invoke-interface {p1, v1, p0}, Lbf/a;->b(Lbf/c;Lbm/e;)Ljava/lang/Object;

    move-result-object p1

    if-ne p1, v0, :cond_2

    return-object v0

    :cond_2
    return-object p1
.end method
