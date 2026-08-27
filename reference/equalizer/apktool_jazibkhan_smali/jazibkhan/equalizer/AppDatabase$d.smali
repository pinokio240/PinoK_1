.class public final Lcom/jazibkhan/equalizer/AppDatabase$d;
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

    const-string v0, "DROP TABLE IF EXISTS `auto_apply_config`"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS `auto_apply_config` (`audio_device_id` INTEGER NOT NULL, `custom_preset_id` INTEGER NOT NULL, PRIMARY KEY(`audio_device_id`))"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_devices_name_type` ON `audio_devices` (`name`, `type`)"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    return-void
.end method
