.class public final Lcom/jazibkhan/equalizer/AppDatabase$f;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/jazibkhan/equalizer/AppDatabase;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "f"
.end annotation


# virtual methods
.method public final a(Landroid/content/Context;)Lcom/jazibkhan/equalizer/AppDatabase;
    .locals 3

    const-string v0, "context"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->m:Lcom/jazibkhan/equalizer/AppDatabase;

    if-eqz v0, :cond_0

    sget-object p1, Lcom/jazibkhan/equalizer/AppDatabase;->m:Lcom/jazibkhan/equalizer/AppDatabase;

    invoke-static {p1}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    return-object p1

    :cond_0
    monitor-enter p0

    :try_start_0
    invoke-virtual {p1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p1

    const-string v0, "getApplicationContext(...)"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    const-class v0, Lcom/jazibkhan/equalizer/AppDatabase;

    const-string v1, "custom_preset"

    invoke-static {p1, v0, v1}, Lt7/r;->a(Landroid/content/Context;Ljava/lang/Class;Ljava/lang/String;)Lt7/x$a;

    move-result-object p1

    const/4 v0, 0x5

    new-array v0, v0, [Lw7/a;

    sget-object v1, Lcom/jazibkhan/equalizer/AppDatabase;->n:Lcom/jazibkhan/equalizer/AppDatabase$a;

    const/4 v2, 0x0

    aput-object v1, v0, v2

    sget-object v1, Lcom/jazibkhan/equalizer/AppDatabase;->o:Lcom/jazibkhan/equalizer/AppDatabase$b;

    const/4 v2, 0x1

    aput-object v1, v0, v2

    sget-object v1, Lcom/jazibkhan/equalizer/AppDatabase;->p:Lcom/jazibkhan/equalizer/AppDatabase$c;

    const/4 v2, 0x2

    aput-object v1, v0, v2

    sget-object v1, Lcom/jazibkhan/equalizer/AppDatabase;->q:Lcom/jazibkhan/equalizer/AppDatabase$d;

    const/4 v2, 0x3

    aput-object v1, v0, v2

    sget-object v1, Lcom/jazibkhan/equalizer/AppDatabase;->r:Lcom/jazibkhan/equalizer/AppDatabase$e;

    const/4 v2, 0x4

    aput-object v1, v0, v2

    invoke-virtual {p1, v0}, Lt7/x$a;->a([Lw7/a;)V

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$f$a;

    invoke-direct {v0}, Lt7/x$b;-><init>()V

    iget-object v1, p1, Lt7/x$a;->d:Ljava/util/ArrayList;

    invoke-virtual {v1, v0}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-virtual {p1}, Lt7/x$a;->b()Lt7/x;

    move-result-object p1

    check-cast p1, Lcom/jazibkhan/equalizer/AppDatabase;

    sput-object p1, Lcom/jazibkhan/equalizer/AppDatabase;->m:Lcom/jazibkhan/equalizer/AppDatabase;

    sget-object p1, Lcom/jazibkhan/equalizer/AppDatabase;->m:Lcom/jazibkhan/equalizer/AppDatabase;

    invoke-static {p1}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return-object p1

    :catchall_0
    move-exception p1

    monitor-exit p0

    throw p1
.end method
