.class public final Lcom/jazibkhan/equalizer/AppDatabase$c;
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
    .locals 4

    const-string v0, "database"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS `audio_devices` (`name` TEXT NOT NULL, `type` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS `auto_apply_config` (`audio_device_id` INTEGER NOT NULL, `custom_preset_id` TEXT NOT NULL, PRIMARY KEY(`audio_device_id`))"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    sget-object v0, Lze/b;->SPEAKER:Lze/b;

    invoke-virtual {v0}, Ljava/lang/Enum;->ordinal()I

    move-result v0

    new-instance v1, Ljava/lang/StringBuilder;

    const-string v2, "INSERT INTO `audio_devices` (`id`, `name`, `type`) VALUES (1, \'Speaker\', "

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, ")"

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-interface {p1, v1}, Ld8/b;->execSQL(Ljava/lang/String;)V

    sget-object v1, Lze/b;->HEADPHONES:Lze/b;

    invoke-virtual {v1}, Ljava/lang/Enum;->ordinal()I

    move-result v1

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "INSERT INTO `audio_devices` (`id`, `name`, `type`) VALUES (2, \'Headphones\', "

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    return-void
.end method
