.class public final synthetic Lye/g;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# instance fields
.field public final synthetic b:Lye/r;

.field public final synthetic c:Lye/c;


# direct methods
.method public synthetic constructor <init>(Lye/r;Lye/c;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lye/g;->b:Lye/r;

    iput-object p2, p0, Lye/g;->c:Lye/c;

    return-void
.end method


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 4

    check-cast p1, Lc8/b;

    const-string v0, "_connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v0, p0, Lye/g;->b:Lye/r;

    iget-object v0, v0, Lye/r;->e:Lye/r$d;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    iget-object v0, p0, Lye/g;->c:Lye/c;

    if-nez v0, :cond_0

    goto :goto_0

    :cond_0
    const-string v1, "DELETE FROM `custom_preset` WHERE `id` = ?"

    invoke-interface {p1, v1}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object v1

    :try_start_0
    const-string v2, "statement"

    invoke-static {v1, v2}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {v0}, Lye/c;->g()I

    move-result v0

    int-to-long v2, v0

    const/4 v0, 0x1

    invoke-interface {v1, v0, v2, v3}, Lc8/d;->f(IJ)V

    invoke-interface {v1}, Lc8/d;->step()Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    const/4 v0, 0x0

    invoke-static {v1, v0}, Lkm/a;->c(Ljava/lang/AutoCloseable;Ljava/lang/Throwable;)V

    invoke-static {p1}, Ly7/i;->a(Lc8/b;)I

    :goto_0
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1

    :catchall_0
    move-exception p1

    :try_start_1
    throw p1
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_1

    :catchall_1
    move-exception v0

    invoke-static {v1, p1}, Lkm/a;->c(Ljava/lang/AutoCloseable;Ljava/lang/Throwable;)V

    throw v0
.end method
