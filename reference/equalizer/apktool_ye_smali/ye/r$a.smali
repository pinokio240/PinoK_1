.class public final Lye/r$a;
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

    check-cast p2, Lye/c;

    const-string v0, "statement"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "entity"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const/4 v0, 0x1

    invoke-virtual {p2}, Lye/c;->j()Ljava/lang/String;

    move-result-object v1

    invoke-interface {p1, v0, v1}, Lc8/d;->t(ILjava/lang/String;)V

    invoke-virtual {p2}, Lye/c;->o()I

    move-result v0

    int-to-long v0, v0

    const/4 v2, 0x2

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->a()I

    move-result v0

    int-to-long v0, v0

    const/4 v2, 0x3

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->h()F

    move-result v0

    float-to-double v0, v0

    const/4 v2, 0x4

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->a(ID)V

    invoke-virtual {p2}, Lye/c;->m()Ljava/util/List;

    move-result-object v0

    const-string v1, "value"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v1, Lorg/json/JSONArray;

    check-cast v0, Ljava/util/Collection;

    invoke-direct {v1, v0}, Lorg/json/JSONArray;-><init>(Ljava/util/Collection;)V

    invoke-virtual {v1}, Lorg/json/JSONArray;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "toString(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    const/4 v1, 0x5

    invoke-interface {p1, v1, v0}, Lc8/d;->t(ILjava/lang/String;)V

    invoke-virtual {p2}, Lye/c;->n()I

    move-result v0

    int-to-long v0, v0

    const/4 v2, 0x6

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->p()Z

    move-result v0

    const/4 v1, 0x7

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->b()Z

    move-result v0

    const/16 v1, 0x8

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->i()Z

    move-result v0

    const/16 v1, 0x9

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->f()Z

    move-result v0

    const/16 v1, 0xa

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->e()Z

    move-result v0

    const/16 v1, 0xb

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->l()Z

    move-result v0

    const/16 v1, 0xc

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->k()I

    move-result v0

    int-to-long v0, v0

    const/16 v2, 0xd

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->d()Z

    move-result v0

    const/16 v1, 0xe

    int-to-long v2, v0

    invoke-interface {p1, v1, v2, v3}, Lc8/d;->f(IJ)V

    invoke-virtual {p2}, Lye/c;->c()F

    move-result v0

    float-to-double v0, v0

    const/16 v2, 0xf

    invoke-interface {p1, v2, v0, v1}, Lc8/d;->a(ID)V

    invoke-virtual {p2}, Lye/c;->g()I

    move-result p2

    int-to-long v0, p2

    const/16 p2, 0x10

    invoke-interface {p1, p2, v0, v1}, Lc8/d;->f(IJ)V

    return-void
.end method

.method public final I()Ljava/lang/String;
    .locals 1

    const-string v0, "INSERT OR REPLACE INTO `custom_preset` (`preset_name`,`vir_slider`,`bb_slider`,`loud_slider`,`slider`,`spinner_pos`,`vir_switch`,`bb_switch`,`loud_switch`,`eq_switch`,`is_custom_selected`,`reverb_switch`,`reverb_slider`,`channel_bal_switch`,`channel_bal_slider`,`id`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,nullif(?, 0))"

    return-object v0
.end method
