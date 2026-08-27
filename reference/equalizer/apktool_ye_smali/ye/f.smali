.class public final synthetic Lye/f;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# instance fields
.field public final synthetic b:Lye/r;

.field public final synthetic c:Lze/a;


# direct methods
.method public synthetic constructor <init>(Lye/r;Lze/a;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lye/f;->b:Lye/r;

    iput-object p2, p0, Lye/f;->c:Lze/a;

    return-void
.end method


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 3

    iget-object v0, p0, Lye/f;->c:Lze/a;

    check-cast p1, Lc8/b;

    const-string v1, "_connection"

    invoke-static {p1, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v1, p0, Lye/f;->b:Lye/r;

    iget-object v1, v1, Lye/r;->c:Lye/r$b;

    invoke-virtual {v1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    const-string v2, "INSERT OR IGNORE INTO `audio_devices` (`name`,`type`,`id`) VALUES (?,?,nullif(?, 0))"

    invoke-interface {p1, v2}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object p1

    :try_start_0
    invoke-virtual {v1, p1, v0}, Lye/r$b;->G(Lc8/d;Ljava/lang/Object;)V

    invoke-interface {p1}, Lc8/d;->step()Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    const/4 v0, 0x0

    invoke-static {p1, v0}, Lkm/a;->c(Ljava/lang/AutoCloseable;Ljava/lang/Throwable;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1

    :catchall_0
    move-exception v0

    :try_start_1
    throw v0
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_1

    :catchall_1
    move-exception v1

    invoke-static {p1, v0}, Lkm/a;->c(Ljava/lang/AutoCloseable;Ljava/lang/Throwable;)V

    throw v1
.end method
