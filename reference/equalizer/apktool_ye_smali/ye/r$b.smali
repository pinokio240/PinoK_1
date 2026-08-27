.class public final Lye/r$b;
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
    .locals 4

    check-cast p2, Lze/a;

    const-string v0, "statement"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "entity"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const/4 v0, 0x1

    iget-object v1, p2, Lze/a;->a:Ljava/lang/String;

    invoke-interface {p1, v0, v1}, Lc8/d;->t(ILjava/lang/String;)V

    iget-object v0, p2, Lze/a;->b:Lze/b;

    const-string v1, "value"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {v0}, Ljava/lang/Enum;->ordinal()I

    move-result v0

    const/4 v1, 0x2

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    iget p2, p2, Lze/a;->c:I

    int-to-long v0, p2

    const/4 p2, 0x3

    invoke-interface {p1, p2, v0, v1}, Lc8/d;->f(IJ)V

    return-void
.end method

.method public final I()Ljava/lang/String;
    .locals 1

    const-string v0, "INSERT OR IGNORE INTO `audio_devices` (`name`,`type`,`id`) VALUES (?,?,nullif(?, 0))"

    return-object v0
.end method
