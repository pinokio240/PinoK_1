.class public final synthetic Lye/j;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 36

    move-object/from16 v0, p1

    check-cast v0, Lc8/b;

    const-string v1, "_connection"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v1, "SELECT * FROM custom_preset ORDER BY preset_name ASC"

    invoke-interface {v0, v1}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object v1

    :try_start_0
    const-string v0, "preset_name"

    invoke-static {v1, v0}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v0

    const-string v2, "vir_slider"

    invoke-static {v1, v2}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v2

    const-string v3, "bb_slider"

    invoke-static {v1, v3}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v3

    const-string v4, "loud_slider"

    invoke-static {v1, v4}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v4

    const-string v5, "slider"

    invoke-static {v1, v5}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v5

    const-string v6, "spinner_pos"

    invoke-static {v1, v6}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v6

    const-string v7, "vir_switch"

    invoke-static {v1, v7}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v7

    const-string v8, "bb_switch"

    invoke-static {v1, v8}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v8

    const-string v9, "loud_switch"

    invoke-static {v1, v9}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v9

    const-string v10, "eq_switch"

    invoke-static {v1, v10}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v10

    const-string v11, "is_custom_selected"

    invoke-static {v1, v11}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v11

    const-string v12, "reverb_switch"

    invoke-static {v1, v12}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v12

    const-string v13, "reverb_slider"

    invoke-static {v1, v13}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v13

    const-string v14, "channel_bal_switch"

    invoke-static {v1, v14}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v14

    const-string v15, "channel_bal_slider"

    invoke-static {v1, v15}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v15

    move/from16 p1, v15

    const-string v15, "id"

    invoke-static {v1, v15}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v15

    move/from16 v16, v15

    new-instance v15, Ljava/util/ArrayList;

    invoke-direct {v15}, Ljava/util/ArrayList;-><init>()V

    :goto_0
    invoke-interface {v1}, Lc8/d;->step()Z

    move-result v17

    if-eqz v17, :cond_7

    invoke-interface {v1, v0}, Lc8/d;->l0(I)Ljava/lang/String;

    move-result-object v19

    move/from16 v17, v14

    move-object/from16 v34, v15

    invoke-interface {v1, v2}, Lc8/d;->getLong(I)J

    move-result-wide v14

    long-to-int v14, v14

    move/from16 v20, v14

    invoke-interface {v1, v3}, Lc8/d;->getLong(I)J

    move-result-wide v14

    long-to-int v14, v14

    move v15, v2

    move/from16 v35, v3

    invoke-interface {v1, v4}, Lc8/d;->getDouble(I)D

    move-result-wide v2

    double-to-float v2, v2

    invoke-interface {v1, v5}, Lc8/d;->l0(I)Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Lkf/b;->c(Ljava/lang/String;)Ljava/util/ArrayList;

    move-result-object v23

    move/from16 v22, v2

    invoke-interface {v1, v6}, Lc8/d;->getLong(I)J

    move-result-wide v2

    long-to-int v2, v2

    move/from16 v24, v2

    invoke-interface {v1, v7}, Lc8/d;->getLong(I)J

    move-result-wide v2

    long-to-int v2, v2

    const/16 v18, 0x1

    if-eqz v2, :cond_0

    move/from16 v25, v18

    :goto_1
    move v2, v4

    goto :goto_2

    :cond_0
    const/16 v25, 0x0

    goto :goto_1

    :goto_2
    invoke-interface {v1, v8}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    if-eqz v3, :cond_1

    move/from16 v26, v18

    goto :goto_3

    :cond_1
    const/16 v26, 0x0

    :goto_3
    invoke-interface {v1, v9}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    if-eqz v3, :cond_2

    move/from16 v27, v18

    goto :goto_4

    :cond_2
    const/16 v27, 0x0

    :goto_4
    invoke-interface {v1, v10}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    if-eqz v3, :cond_3

    move/from16 v28, v18

    goto :goto_5

    :cond_3
    const/16 v28, 0x0

    :goto_5
    invoke-interface {v1, v11}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    if-eqz v3, :cond_4

    move/from16 v29, v18

    goto :goto_6

    :cond_4
    const/16 v29, 0x0

    :goto_6
    invoke-interface {v1, v12}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    if-eqz v3, :cond_5

    move/from16 v30, v18

    goto :goto_7

    :cond_5
    const/16 v30, 0x0

    :goto_7
    invoke-interface {v1, v13}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    move/from16 v31, v3

    move/from16 v4, v17

    move/from16 v17, v2

    invoke-interface {v1, v4}, Lc8/d;->getLong(I)J

    move-result-wide v2

    long-to-int v2, v2

    if-eqz v2, :cond_6

    move/from16 v32, v18

    :goto_8
    move/from16 v2, p1

    move/from16 p1, v4

    goto :goto_9

    :cond_6
    const/16 v32, 0x0

    goto :goto_8

    :goto_9
    invoke-interface {v1, v2}, Lc8/d;->getDouble(I)D

    move-result-wide v3

    double-to-float v3, v3

    new-instance v18, Lye/c;

    move/from16 v33, v3

    move/from16 v21, v14

    invoke-direct/range {v18 .. v33}, Lye/c;-><init>(Ljava/lang/String;IIFLjava/util/ArrayList;IZZZZZZIZF)V

    move-object/from16 v3, v18

    move v14, v5

    move/from16 v4, v16

    move/from16 v16, v6

    invoke-interface {v1, v4}, Lc8/d;->getLong(I)J

    move-result-wide v5

    long-to-int v5, v5

    invoke-virtual {v3, v5}, Lye/c;->s(I)V

    move-object/from16 v5, v34

    invoke-virtual {v5, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    move v3, v14

    move/from16 v14, p1

    move/from16 p1, v2

    move v2, v15

    move-object v15, v5

    move v5, v3

    move/from16 v6, v16

    move/from16 v3, v35

    move/from16 v16, v4

    move/from16 v4, v17

    goto/16 :goto_0

    :catchall_0
    move-exception v0

    goto :goto_a

    :cond_7
    move-object v5, v15

    invoke-interface {v1}, Ljava/lang/AutoCloseable;->close()V

    return-object v5

    :goto_a
    invoke-interface {v1}, Ljava/lang/AutoCloseable;->close()V

    throw v0
.end method
