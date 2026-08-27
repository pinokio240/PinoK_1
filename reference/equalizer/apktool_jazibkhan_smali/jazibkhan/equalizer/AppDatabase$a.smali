.class public final Lcom/jazibkhan/equalizer/AppDatabase$a;
.super Lw7/a;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/jazibkhan/equalizer/AppDatabase;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation


# virtual methods
.method public final a(Ld8/b;)V
    .locals 1

    const-string v0, "database"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "ALTER TABLE custom_preset  ADD COLUMN reverb_switch INTEGER DEFAULT 0"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "ALTER TABLE custom_preset  ADD COLUMN reverb_slider INTEGER DEFAULT 0 NOT NULL"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    return-void
.end method
