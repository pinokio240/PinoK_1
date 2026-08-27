.class public final Lye/r;
.super Ljava/lang/Object;

# interfaces
.implements Lye/d;


# instance fields
.field public final a:Lt7/x;

.field public final b:Lye/r$a;

.field public final c:Lye/r$b;

.field public final d:Lye/r$c;

.field public final e:Lye/r$d;


# direct methods
.method public constructor <init>(Lt7/x;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lye/r;->a:Lt7/x;

    new-instance p1, Lye/r$a;

    invoke-direct {p1}, Landroidx/work/x;-><init>()V

    iput-object p1, p0, Lye/r;->b:Lye/r$a;

    new-instance p1, Lye/r$b;

    invoke-direct {p1}, Landroidx/work/x;-><init>()V

    iput-object p1, p0, Lye/r;->c:Lye/r$b;

    new-instance p1, Lye/r$c;

    invoke-direct {p1}, Landroidx/work/x;-><init>()V

    iput-object p1, p0, Lye/r;->d:Lye/r$c;

    new-instance p1, Lye/r$d;

    invoke-direct {p1}, Landroidx/datastore/preferences/protobuf/o;-><init>()V

    iput-object p1, p0, Lye/r;->e:Lye/r$d;

    new-instance p1, Lye/r$e;

    invoke-direct {p1}, Landroidx/datastore/preferences/protobuf/o;-><init>()V

    return-void
.end method


# virtual methods
.method public final a(Lze/c;Ldm/i;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/n;

    invoke-direct {v0, p0, p1}, Lye/n;-><init>(Lye/r;Lze/c;)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final b()Lv7/m;
    .locals 3

    const-string v0, "custom_preset"

    filled-new-array {v0}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lye/m;

    invoke-direct {v1}, Ljava/lang/Object;-><init>()V

    iget-object v2, p0, Lye/r;->a:Lt7/x;

    invoke-static {v2, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object v0

    return-object v0
.end method

.method public final c(Lef/n;)Ljava/lang/Object;
    .locals 4

    new-instance v0, Lj2/a0;

    const/4 v1, 0x1

    invoke-direct {v0, v1}, Lj2/a0;-><init>(I)V

    iget-object v1, p0, Lye/r;->a:Lt7/x;

    const/4 v2, 0x0

    const/4 v3, 0x1

    invoke-static {v1, v2, v3, v0, p1}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object v0, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, v0, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final d(ILff/f;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/h;

    invoke-direct {v0, p1}, Lye/h;-><init>(I)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object p2, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, p2, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final e(Lef/n;)Ljava/lang/Object;
    .locals 4

    new-instance v0, Lh0/i0;

    const/4 v1, 0x1

    invoke-direct {v0, v1}, Lh0/i0;-><init>(I)V

    iget-object v1, p0, Lye/r;->a:Lt7/x;

    const/4 v2, 0x0

    const/4 v3, 0x1

    invoke-static {v1, v2, v3, v0, p1}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object v0, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, v0, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final f()Lv7/m;
    .locals 3

    const-string v0, "audio_devices"

    filled-new-array {v0}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lj2/e0;

    const/4 v2, 0x1

    invoke-direct {v1, v2}, Lj2/e0;-><init>(I)V

    iget-object v2, p0, Lye/r;->a:Lt7/x;

    invoke-static {v2, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object v0

    return-object v0
.end method

.method public final g(I)Lv7/m;
    .locals 2

    const-string v0, "auto_apply_config"

    const-string v1, "audio_devices"

    filled-new-array {v0, v1}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lye/l;

    invoke-direct {v1, p1}, Lye/l;-><init>(I)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    invoke-static {p1, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object p1

    return-object p1
.end method

.method public final h(ILff/f;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/i;

    invoke-direct {v0, p1}, Lye/i;-><init>(I)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object p2, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, p2, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final i(Lye/c;Ldm/i;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/q;

    invoke-direct {v0, p0, p1}, Lye/q;-><init>(Lye/r;Lye/c;)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final j()Lv7/m;
    .locals 3

    const-string v0, "custom_preset"

    filled-new-array {v0}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lye/j;

    invoke-direct {v1}, Ljava/lang/Object;-><init>()V

    iget-object v2, p0, Lye/r;->a:Lt7/x;

    invoke-static {v2, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object v0

    return-object v0
.end method

.method public final k(Lze/a;Lcom/jazibkhan/equalizer/services/MainForegroundService$d;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/f;

    invoke-direct {v0, p0, p1}, Lye/f;-><init>(Lye/r;Lze/a;)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object p2, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, p2, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final l(I)Lv7/m;
    .locals 2

    const-string v0, "custom_preset"

    filled-new-array {v0}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lye/p;

    invoke-direct {v1, p1}, Lye/p;-><init>(I)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    invoke-static {p1, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object p1

    return-object p1
.end method

.method public final m(ILdm/i;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/e;

    invoke-direct {v0, p1}, Lye/e;-><init>(I)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object p2, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, p2, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final n()Lv7/m;
    .locals 3

    const-string v0, "auto_apply_config"

    filled-new-array {v0}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lye/o;

    invoke-direct {v1}, Ljava/lang/Object;-><init>()V

    iget-object v2, p0, Lye/r;->a:Lt7/x;

    invoke-static {v2, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object v0

    return-object v0
.end method

.method public final o(Lye/c;Ldf/u;)Ljava/lang/Object;
    .locals 3

    new-instance v0, Lye/g;

    invoke-direct {v0, p0, p1}, Lye/g;-><init>(Lye/r;Lye/c;)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    const/4 v1, 0x0

    const/4 v2, 0x1

    invoke-static {p1, v1, v2, v0, p2}, Lm4/b;->m(Lt7/x;ZZLmm/l;Ldm/c;)Ljava/lang/Object;

    move-result-object p1

    sget-object p2, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    if-ne p1, p2, :cond_0

    return-object p1

    :cond_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method

.method public final p(I)Lv7/m;
    .locals 3

    const-string v0, "audio_devices"

    const-string v1, "custom_preset"

    const-string v2, "auto_apply_config"

    filled-new-array {v2, v0, v1}, [Ljava/lang/String;

    move-result-object v0

    new-instance v1, Lye/k;

    invoke-direct {v1, p1}, Lye/k;-><init>(I)V

    iget-object p1, p0, Lye/r;->a:Lt7/x;

    invoke-static {p1, v0, v1}, Lg0/f0;->b(Lt7/x;[Ljava/lang/String;Lmm/l;)Lv7/m;

    move-result-object p1

    return-object p1
.end method
