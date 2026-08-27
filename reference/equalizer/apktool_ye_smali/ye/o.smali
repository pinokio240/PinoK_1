.class public final synthetic Lye/o;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 6

    check-cast p1, Lc8/b;

    const-string v0, "_connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "SELECT * FROM auto_apply_config"

    invoke-interface {p1, v0}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object p1

    :try_start_0
    const-string v0, "audio_device_id"

    invoke-static {p1, v0}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v0

    const-string v1, "custom_preset_id"

    invoke-static {p1, v1}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v1

    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V

    :goto_0
    invoke-interface {p1}, Lc8/d;->step()Z

    move-result v3

    if-eqz v3, :cond_0

    invoke-interface {p1, v0}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    invoke-interface {p1, v1}, Lc8/d;->getLong(I)J

    move-result-wide v4

    long-to-int v4, v4

    new-instance v5, Lze/c;

    invoke-direct {v5, v3, v4}, Lze/c;-><init>(II)V

    invoke-virtual {v2, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    :catchall_0
    move-exception v0

    goto :goto_1

    :cond_0
    invoke-interface {p1}, Ljava/lang/AutoCloseable;->close()V

    return-object v2

    :goto_1
    invoke-interface {p1}, Ljava/lang/AutoCloseable;->close()V

    throw v0
.end method
