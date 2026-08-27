.class public final Lye/r$c;
.super Landroidx/work/x;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lye/r;-><init>(Lt7/x;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Landroidx/work/x;"
    }
.end annotation


# virtual methods
.method public final G(Lc8/d;Ljava/lang/Object;)V
    .locals 3

    check-cast p2, Lze/c;

    const-string v0, "statement"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "entity"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget v0, p2, Lze/c;->a:I

    int-to-long v0, v0

    const/4 v2, 0x1

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->f(IJ)V

    iget p2, p2, Lze/c;->b:I

    int-to-long v0, p2

    const/4 p2, 0x2

    invoke-interface {p1, p2, v0, v1}, Lc8/d;->f(IJ)V

    return-void
.end method

.method public final I()Ljava/lang/String;
    .locals 1

    const-string v0, "INSERT OR REPLACE INTO `auto_apply_config` (`audio_device_id`,`custom_preset_id`) VALUES (?,?)"

    return-object v0
.end method
