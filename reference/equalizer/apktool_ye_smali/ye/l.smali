.class public final synthetic Lye/l;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# instance fields
.field public final synthetic b:I


# direct methods
.method public synthetic constructor <init>(I)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput p1, p0, Lye/l;->b:I

    return-void
.end method


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 7

    iget v0, p0, Lye/l;->b:I

    check-cast p1, Lc8/b;

    const-string v1, "_connection"

    invoke-static {p1, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v1, "SELECT audio_devices.* FROM `auto_apply_config` INNER JOIN `audio_devices` ON auto_apply_config.audio_device_id == audio_devices.id WHERE auto_apply_config.custom_preset_id == ?"

    invoke-interface {p1, v1}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object p1

    const/4 v1, 0x1

    int-to-long v2, v0

    :try_start_0
    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    const-string v0, "name"

    invoke-static {p1, v0}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v0

    const-string v1, "type"

    invoke-static {p1, v1}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v1

    const-string v2, "id"

    invoke-static {p1, v2}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v2

    new-instance v3, Ljava/util/ArrayList;

    invoke-direct {v3}, Ljava/util/ArrayList;-><init>()V

    :goto_0
    invoke-interface {p1}, Lc8/d;->step()Z

    move-result v4

    if-eqz v4, :cond_0

    invoke-interface {p1, v0}, Lc8/d;->l0(I)Ljava/lang/String;

    move-result-object v4

    invoke-interface {p1, v1}, Lc8/d;->getLong(I)J

    move-result-wide v5

    long-to-int v5, v5

    invoke-static {}, Lze/b;->values()[Lze/b;

    move-result-object v6

    aget-object v5, v6, v5

    new-instance v6, Lze/a;

    invoke-direct {v6, v4, v5}, Lze/a;-><init>(Ljava/lang/String;Lze/b;)V

    invoke-interface {p1, v2}, Lc8/d;->getLong(I)J

    move-result-wide v4

    long-to-int v4, v4

    iput v4, v6, Lze/a;->c:I

    invoke-virtual {v3, v6}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    :catchall_0
    move-exception v0

    goto :goto_1

    :cond_0
    invoke-interface {p1}, Ljava/lang/AutoCloseable;->close()V

    return-object v3

    :goto_1
    invoke-interface {p1}, Ljava/lang/AutoCloseable;->close()V

    throw v0
.end method
