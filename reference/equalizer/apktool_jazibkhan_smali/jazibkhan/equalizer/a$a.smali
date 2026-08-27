.class public final Lcom/jazibkhan/equalizer/a$a;
.super Ldm/i;

# interfaces
.implements Lmm/q;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/a;-><init>(Landroid/app/Application;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ldm/i;",
        "Lmm/q<",
        "Ljava/util/List<",
        "+",
        "Lye/c;",
        ">;",
        "Ljava/util/List<",
        "+",
        "Lze/c;",
        ">;",
        "Lbm/e<",
        "-",
        "Ljava/util/List<",
        "+",
        "Lye/c;",
        ">;>;",
        "Ljava/lang/Object;",
        ">;"
    }
.end annotation

.annotation runtime Ldm/e;
    c = "com.jazibkhan.equalizer.CustomPresetRepository$customPresetWithAutoApply$1"
    f = "CustomPresetRepository.kt"
    l = {}
    m = "invokeSuspend"
.end annotation


# instance fields
.field public synthetic l:Ljava/util/List;

.field public synthetic m:Ljava/util/List;


# virtual methods
.method public final invoke(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 2

    check-cast p1, Ljava/util/List;

    check-cast p2, Ljava/util/List;

    check-cast p3, Lbm/e;

    new-instance v0, Lcom/jazibkhan/equalizer/a$a;

    const/4 v1, 0x3

    invoke-direct {v0, v1, p3}, Ldm/i;-><init>(ILbm/e;)V

    check-cast p1, Ljava/util/List;

    iput-object p1, v0, Lcom/jazibkhan/equalizer/a$a;->l:Ljava/util/List;

    check-cast p2, Ljava/util/List;

    iput-object p2, v0, Lcom/jazibkhan/equalizer/a$a;->m:Ljava/util/List;

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    invoke-virtual {v0, p1}, Lcom/jazibkhan/equalizer/a$a;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 7

    iget-object v0, p0, Lcom/jazibkhan/equalizer/a$a;->l:Ljava/util/List;

    check-cast v0, Ljava/util/List;

    iget-object v1, p0, Lcom/jazibkhan/equalizer/a$a;->m:Ljava/util/List;

    check-cast v1, Ljava/util/List;

    sget-object v2, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    check-cast v0, Ljava/lang/Iterable;

    new-instance p1, Ljava/util/ArrayList;

    const/16 v2, 0xa

    invoke-static {v0, v2}, Lyl/o;->p(Ljava/lang/Iterable;I)I

    move-result v2

    invoke-direct {p1, v2}, Ljava/util/ArrayList;-><init>(I)V

    invoke-interface {v0}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_3

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lye/c;

    move-object v3, v1

    check-cast v3, Ljava/lang/Iterable;

    invoke-interface {v3}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object v3

    :cond_0
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    move-result v4

    if-eqz v4, :cond_1

    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v4

    move-object v5, v4

    check-cast v5, Lze/c;

    iget v5, v5, Lze/c;->b:I

    invoke-virtual {v2}, Lye/c;->g()I

    move-result v6

    if-ne v5, v6, :cond_0

    goto :goto_1

    :cond_1
    const/4 v4, 0x0

    :goto_1
    check-cast v4, Lze/c;

    if-eqz v4, :cond_2

    const/4 v3, 0x1

    goto :goto_2

    :cond_2
    const/4 v3, 0x0

    :goto_2
    invoke-virtual {v2, v3}, Lye/c;->r(Z)V

    invoke-virtual {p1, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_0

    :cond_3
    return-object p1
.end method
