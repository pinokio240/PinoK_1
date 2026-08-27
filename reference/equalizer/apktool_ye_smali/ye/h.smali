.class public final synthetic Lye/h;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# instance fields
.field public final synthetic b:I


# direct methods
.method public synthetic constructor <init>(I)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput p1, p0, Lye/h;->b:I

    return-void
.end method


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 4

    iget v0, p0, Lye/h;->b:I

    check-cast p1, Lc8/b;

    const-string v1, "_connection"

    invoke-static {p1, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v1, "DELETE FROM auto_apply_config WHERE audio_device_id == ?"

    invoke-interface {p1, v1}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object p1

    const/4 v1, 0x1

    int-to-long v2, v0

    :try_start_0
    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-interface {p1}, Lc8/d;->step()Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    invoke-interface {p1}, Ljava/lang/AutoCloseable;->close()V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1

    :catchall_0
    move-exception v0

    invoke-interface {p1}, Ljava/lang/AutoCloseable;->close()V

    throw v0
.end method
