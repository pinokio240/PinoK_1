.class public final Lcom/jazibkhan/equalizer/AppDatabase$b;
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

    const-string v0, "CREATE TABLE IF NOT EXISTS `custom_preset_temp` (`preset_name` TEXT NOT NULL, `vir_slider` INTEGER NOT NULL, `bb_slider` INTEGER NOT NULL, `loud_slider` REAL NOT NULL, `slider` TEXT NOT NULL, `spinner_pos` INTEGER NOT NULL, `vir_switch` INTEGER NOT NULL, `bb_switch` INTEGER NOT NULL, `loud_switch` INTEGER NOT NULL, `eq_switch` INTEGER NOT NULL, `is_custom_selected` INTEGER NOT NULL, `reverb_switch` INTEGER NOT NULL, `reverb_slider` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "INSERT INTO `custom_preset_temp` (`preset_name`, `vir_slider`, `bb_slider`, `loud_slider`, `slider`, `spinner_pos`, `vir_switch`, `bb_switch`, `loud_switch`, `eq_switch`, `is_custom_selected`, `reverb_switch`, `reverb_slider`, `id`) SELECT `preset_name`, `vir_slider`, `bb_slider`, `loud_slider`, `slider`, `spinner_pos`, `vir_switch`, `bb_switch`, `loud_switch`, `eq_switch`, `is_custom_selected`, `reverb_switch`, `reverb_slider`, `id` FROM `custom_preset` ORDER BY preset_name ASC"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "DROP TABLE `custom_preset`"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    const-string v0, "ALTER TABLE `custom_preset_temp` RENAME TO `custom_preset`"

    invoke-interface {p1, v0}, Ld8/b;->execSQL(Ljava/lang/String;)V

    return-void
.end method
