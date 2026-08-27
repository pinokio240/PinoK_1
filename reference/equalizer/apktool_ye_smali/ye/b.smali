.class public final Lye/b;
.super Lt7/e0;


# instance fields
.field public final synthetic d:Lcom/jazibkhan/equalizer/AppDatabase_Impl;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/AppDatabase_Impl;)V
    .locals 2

    iput-object p1, p0, Lye/b;->d:Lcom/jazibkhan/equalizer/AppDatabase_Impl;

    const-string p1, "803d759d2a9e85942b17d002bb169dc7"

    const-string v0, "83850cbfecf42cac34c93c524029d38c"

    const/4 v1, 0x6

    invoke-direct {p0, v1, p1, v0}, Lt7/e0;-><init>(ILjava/lang/String;Ljava/lang/String;)V

    return-void
.end method


# virtual methods
.method public final a(Lc8/b;)V
    .locals 1

    const-string v0, "connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS `custom_preset` (`preset_name` TEXT NOT NULL, `vir_slider` INTEGER NOT NULL, `bb_slider` INTEGER NOT NULL, `loud_slider` REAL NOT NULL, `slider` TEXT NOT NULL, `spinner_pos` INTEGER NOT NULL, `vir_switch` INTEGER NOT NULL, `bb_switch` INTEGER NOT NULL, `loud_switch` INTEGER NOT NULL, `eq_switch` INTEGER NOT NULL, `is_custom_selected` INTEGER NOT NULL, `reverb_switch` INTEGER NOT NULL, `reverb_slider` INTEGER NOT NULL, `channel_bal_switch` INTEGER NOT NULL, `channel_bal_slider` REAL NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS `audio_devices` (`name` TEXT NOT NULL, `type` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_devices_name_type` ON `audio_devices` (`name`, `type`)"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS `auto_apply_config` (`audio_device_id` INTEGER NOT NULL, `custom_preset_id` INTEGER NOT NULL, PRIMARY KEY(`audio_device_id`))"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, \'803d759d2a9e85942b17d002bb169dc7\')"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    return-void
.end method

.method public final b(Lc8/b;)V
    .locals 1

    const-string v0, "connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "DROP TABLE IF EXISTS `custom_preset`"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "DROP TABLE IF EXISTS `audio_devices`"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    const-string v0, "DROP TABLE IF EXISTS `auto_apply_config`"

    invoke-static {p1, v0}, Lc8/a;->a(Lc8/b;Ljava/lang/String;)V

    return-void
.end method

.method public final c(Lc8/b;)V
    .locals 1

    const-string v0, "connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    return-void
.end method

.method public final d(Lc8/b;)V
    .locals 1

    const-string v0, "connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v0, p0, Lye/b;->d:Lcom/jazibkhan/equalizer/AppDatabase_Impl;

    invoke-virtual {v0, p1}, Lt7/x;->r(Lc8/b;)V

    return-void
.end method

.method public final e(Lc8/b;)V
    .locals 1

    const-string v0, "connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    return-void
.end method

.method public final f(Lc8/b;)V
    .locals 1

    const-string v0, "connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {p1}, Lm4/b;->j(Lc8/b;)V

    return-void
.end method

.method public final g(Lc8/b;)Lt7/e0$a;
    .locals 19

    move-object/from16 v0, p1

    const-string v1, "connection"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v1, Ljava/util/LinkedHashMap;

    invoke-direct {v1}, Ljava/util/LinkedHashMap;-><init>()V

    new-instance v2, Ly7/l$a;

    const/4 v6, 0x0

    const/4 v8, 0x1

    const/4 v3, 0x0

    const-string v4, "preset_name"

    const-string v5, "TEXT"

    const/4 v7, 0x1

    invoke-direct/range {v2 .. v8}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v3, "preset_name"

    invoke-interface {v1, v3, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v4, Ly7/l$a;

    const/4 v8, 0x0

    const/4 v10, 0x1

    const/4 v5, 0x0

    const-string v6, "vir_slider"

    const-string v7, "INTEGER"

    const/4 v9, 0x1

    invoke-direct/range {v4 .. v10}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "vir_slider"

    invoke-interface {v1, v2, v4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v5, Ly7/l$a;

    const/4 v9, 0x0

    const/4 v11, 0x1

    const/4 v6, 0x0

    const-string v7, "bb_slider"

    const-string v8, "INTEGER"

    invoke-direct/range {v5 .. v11}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "bb_slider"

    invoke-interface {v1, v2, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v6, Ly7/l$a;

    const/4 v10, 0x0

    const/4 v12, 0x1

    const/4 v7, 0x0

    const-string v8, "loud_slider"

    const-string v9, "REAL"

    invoke-direct/range {v6 .. v12}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "loud_slider"

    invoke-interface {v1, v2, v6}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v7, Ly7/l$a;

    const/4 v11, 0x0

    const/4 v13, 0x1

    const/4 v8, 0x0

    const-string v9, "slider"

    const-string v10, "TEXT"

    invoke-direct/range {v7 .. v13}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "slider"

    invoke-interface {v1, v2, v7}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v8, Ly7/l$a;

    const/4 v12, 0x0

    const/4 v14, 0x1

    const/4 v9, 0x0

    const-string v10, "spinner_pos"

    const-string v11, "INTEGER"

    invoke-direct/range {v8 .. v14}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "spinner_pos"

    invoke-interface {v1, v2, v8}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v9, Ly7/l$a;

    const/4 v13, 0x0

    const/4 v15, 0x1

    const/4 v10, 0x0

    const-string v11, "vir_switch"

    const-string v12, "INTEGER"

    invoke-direct/range {v9 .. v15}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "vir_switch"

    invoke-interface {v1, v2, v9}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v10, Ly7/l$a;

    const/4 v14, 0x0

    const/16 v16, 0x1

    const/4 v11, 0x0

    const-string v12, "bb_switch"

    const-string v13, "INTEGER"

    invoke-direct/range {v10 .. v16}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "bb_switch"

    invoke-interface {v1, v2, v10}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v3, Ly7/l$a;

    const/4 v7, 0x0

    const/4 v9, 0x1

    const/4 v4, 0x0

    const-string v5, "loud_switch"

    const-string v6, "INTEGER"

    const/4 v8, 0x1

    invoke-direct/range {v3 .. v9}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "loud_switch"

    invoke-interface {v1, v2, v3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v4, Ly7/l$a;

    const/4 v8, 0x0

    const/4 v10, 0x1

    const/4 v5, 0x0

    const-string v6, "eq_switch"

    const-string v7, "INTEGER"

    invoke-direct/range {v4 .. v10}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "eq_switch"

    invoke-interface {v1, v2, v4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v5, Ly7/l$a;

    const/4 v9, 0x0

    const/4 v11, 0x1

    const/4 v6, 0x0

    const-string v7, "is_custom_selected"

    const-string v8, "INTEGER"

    invoke-direct/range {v5 .. v11}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "is_custom_selected"

    invoke-interface {v1, v2, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v6, Ly7/l$a;

    const/4 v10, 0x0

    const/4 v12, 0x1

    const/4 v7, 0x0

    const-string v8, "reverb_switch"

    const-string v9, "INTEGER"

    invoke-direct/range {v6 .. v12}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "reverb_switch"

    invoke-interface {v1, v2, v6}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v7, Ly7/l$a;

    const/4 v11, 0x0

    const/4 v13, 0x1

    const/4 v8, 0x0

    const-string v9, "reverb_slider"

    const-string v10, "INTEGER"

    invoke-direct/range {v7 .. v13}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "reverb_slider"

    invoke-interface {v1, v2, v7}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v8, Ly7/l$a;

    const/4 v12, 0x0

    const/4 v14, 0x1

    const/4 v9, 0x0

    const-string v10, "channel_bal_switch"

    const-string v11, "INTEGER"

    invoke-direct/range {v8 .. v14}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "channel_bal_switch"

    invoke-interface {v1, v2, v8}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v9, Ly7/l$a;

    const/4 v13, 0x0

    const/4 v10, 0x0

    const-string v11, "channel_bal_slider"

    const-string v12, "REAL"

    invoke-direct/range {v9 .. v15}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "channel_bal_slider"

    invoke-interface {v1, v2, v9}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v10, Ly7/l$a;

    const/4 v14, 0x0

    const/4 v11, 0x1

    const-string v12, "id"

    const-string v13, "INTEGER"

    invoke-direct/range {v10 .. v16}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "id"

    invoke-interface {v1, v2, v10}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v3, Ljava/util/LinkedHashSet;

    invoke-direct {v3}, Ljava/util/LinkedHashSet;-><init>()V

    new-instance v4, Ljava/util/LinkedHashSet;

    invoke-direct {v4}, Ljava/util/LinkedHashSet;-><init>()V

    new-instance v5, Ly7/l;

    const-string v6, "custom_preset"

    invoke-direct {v5, v6, v1, v3, v4}, Ly7/l;-><init>(Ljava/lang/String;Ljava/util/Map;Ljava/util/AbstractSet;Ljava/util/AbstractSet;)V

    invoke-static {v0, v6}, Ly7/l$b;->a(Lc8/b;Ljava/lang/String;)Ly7/l;

    move-result-object v1

    invoke-virtual {v5, v1}, Ly7/l;->equals(Ljava/lang/Object;)Z

    move-result v3

    const-string v4, "\n Found:\n"

    const/4 v6, 0x0

    if-nez v3, :cond_0

    new-instance v0, Lt7/e0$a;

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "custom_preset(com.jazibkhan.equalizer.CustomPreset).\n Expected:\n"

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v6, v1}, Lt7/e0$a;-><init>(ZLjava/lang/String;)V

    return-object v0

    :cond_0
    new-instance v1, Ljava/util/LinkedHashMap;

    invoke-direct {v1}, Ljava/util/LinkedHashMap;-><init>()V

    new-instance v7, Ly7/l$a;

    const/4 v11, 0x0

    const/4 v13, 0x1

    const/4 v8, 0x0

    const-string v9, "name"

    const-string v10, "TEXT"

    const/4 v12, 0x1

    invoke-direct/range {v7 .. v13}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v3, "name"

    invoke-interface {v1, v3, v7}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v8, Ly7/l$a;

    const/4 v12, 0x0

    const/4 v14, 0x1

    const/4 v9, 0x0

    const-string v10, "type"

    const-string v11, "INTEGER"

    invoke-direct/range {v8 .. v14}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v5, "type"

    invoke-interface {v1, v5, v8}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v9, Ly7/l$a;

    const/4 v13, 0x0

    const/4 v15, 0x1

    const/4 v10, 0x1

    const-string v11, "id"

    const-string v12, "INTEGER"

    invoke-direct/range {v9 .. v15}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    invoke-interface {v1, v2, v9}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v2, Ljava/util/LinkedHashSet;

    invoke-direct {v2}, Ljava/util/LinkedHashSet;-><init>()V

    new-instance v7, Ljava/util/LinkedHashSet;

    invoke-direct {v7}, Ljava/util/LinkedHashSet;-><init>()V

    new-instance v8, Ly7/l$d;

    filled-new-array {v3, v5}, [Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Lip/w0;->i([Ljava/lang/Object;)Ljava/util/List;

    move-result-object v3

    const-string v5, "ASC"

    filled-new-array {v5, v5}, [Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lip/w0;->i([Ljava/lang/Object;)Ljava/util/List;

    move-result-object v5

    const-string v9, "index_audio_devices_name_type"

    invoke-direct {v8, v9, v10, v3, v5}, Ly7/l$d;-><init>(Ljava/lang/String;ZLjava/util/List;Ljava/util/List;)V

    invoke-interface {v7, v8}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    new-instance v3, Ly7/l;

    const-string v5, "audio_devices"

    invoke-direct {v3, v5, v1, v2, v7}, Ly7/l;-><init>(Ljava/lang/String;Ljava/util/Map;Ljava/util/AbstractSet;Ljava/util/AbstractSet;)V

    invoke-static {v0, v5}, Ly7/l$b;->a(Lc8/b;Ljava/lang/String;)Ly7/l;

    move-result-object v1

    invoke-virtual {v3, v1}, Ly7/l;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_1

    new-instance v0, Lt7/e0$a;

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v5, "audio_devices(com.jazibkhan.equalizer.data.AudioDevice).\n Expected:\n"

    invoke-direct {v2, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v6, v1}, Lt7/e0$a;-><init>(ZLjava/lang/String;)V

    return-object v0

    :cond_1
    new-instance v1, Ljava/util/LinkedHashMap;

    invoke-direct {v1}, Ljava/util/LinkedHashMap;-><init>()V

    new-instance v11, Ly7/l$a;

    const/4 v15, 0x0

    const/16 v17, 0x1

    const/4 v12, 0x1

    const-string v13, "audio_device_id"

    const-string v14, "INTEGER"

    const/16 v16, 0x1

    invoke-direct/range {v11 .. v17}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "audio_device_id"

    invoke-interface {v1, v2, v11}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v12, Ly7/l$a;

    const/16 v16, 0x0

    const/16 v18, 0x1

    const/4 v13, 0x0

    const-string v14, "custom_preset_id"

    const-string v15, "INTEGER"

    invoke-direct/range {v12 .. v18}, Ly7/l$a;-><init>(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V

    const-string v2, "custom_preset_id"

    invoke-interface {v1, v2, v12}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    new-instance v2, Ljava/util/LinkedHashSet;

    invoke-direct {v2}, Ljava/util/LinkedHashSet;-><init>()V

    new-instance v3, Ljava/util/LinkedHashSet;

    invoke-direct {v3}, Ljava/util/LinkedHashSet;-><init>()V

    new-instance v5, Ly7/l;

    const-string v7, "auto_apply_config"

    invoke-direct {v5, v7, v1, v2, v3}, Ly7/l;-><init>(Ljava/lang/String;Ljava/util/Map;Ljava/util/AbstractSet;Ljava/util/AbstractSet;)V

    invoke-static {v0, v7}, Ly7/l$b;->a(Lc8/b;Ljava/lang/String;)Ly7/l;

    move-result-object v0

    invoke-virtual {v5, v0}, Ly7/l;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2

    new-instance v1, Lt7/e0$a;

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "auto_apply_config(com.jazibkhan.equalizer.data.AutoApplyConfig).\n Expected:\n"

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-direct {v1, v6, v0}, Lt7/e0$a;-><init>(ZLjava/lang/String;)V

    return-object v1

    :cond_2
    new-instance v0, Lt7/e0$a;

    const/4 v1, 0x0

    invoke-direct {v0, v10, v1}, Lt7/e0$a;-><init>(ZLjava/lang/String;)V

    return-object v0
.end method
