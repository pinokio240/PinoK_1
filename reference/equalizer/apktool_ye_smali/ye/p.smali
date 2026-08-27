.class public final synthetic Lye/p;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# instance fields
.field public final synthetic b:I


# direct methods
.method public synthetic constructor <init>(I)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput p1, p0, Lye/p;->b:I

    return-void
.end method


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 34

    move-object/from16 v1, p0

    iget v0, v1, Lye/p;->b:I

    move-object/from16 v2, p1

    check-cast v2, Lc8/b;

    const-string v3, "_connection"

    invoke-static {v2, v3}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v3, "SELECT * FROM custom_preset WHERE id == ?"

    invoke-interface {v2, v3}, Lc8/b;->w0(Ljava/lang/String;)Lc8/d;

    move-result-object v2

    int-to-long v3, v0

    const/4 v0, 0x1

    :try_start_0
    invoke-interface {v2, v0, v3, v4}, Lc8/d;->f(IJ)V

    const-string v3, "preset_name"

    invoke-static {v2, v3}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v3

    const-string v4, "vir_slider"

    invoke-static {v2, v4}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v4

    const-string v5, "bb_slider"

    invoke-static {v2, v5}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v5

    const-string v6, "loud_slider"

    invoke-static {v2, v6}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v6

    const-string v7, "slider"

    invoke-static {v2, v7}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v7

    const-string v8, "spinner_pos"

    invoke-static {v2, v8}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v8

    const-string v9, "vir_switch"

    invoke-static {v2, v9}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v9

    const-string v10, "bb_switch"

    invoke-static {v2, v10}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v10

    const-string v11, "loud_switch"

    invoke-static {v2, v11}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v11

    const-string v12, "eq_switch"

    invoke-static {v2, v12}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v12

    const-string v13, "is_custom_selected"

    invoke-static {v2, v13}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v13

    const-string v14, "reverb_switch"

    invoke-static {v2, v14}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v14

    const-string v15, "reverb_slider"

    invoke-static {v2, v15}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v15

    const-string v0, "channel_bal_switch"

    invoke-static {v2, v0}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v0

    const-string v1, "channel_bal_slider"

    invoke-static {v2, v1}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v1

    move/from16 v16, v1

    const-string v1, "id"

    invoke-static {v2, v1}, Landroidx/fragment/app/z0;->d(Lc8/d;Ljava/lang/String;)I

    move-result v1

    invoke-interface {v2}, Lc8/d;->step()Z

    move-result v17

    if-eqz v17, :cond_7

    invoke-interface {v2, v3}, Lc8/d;->l0(I)Ljava/lang/String;

    move-result-object v19

    invoke-interface {v2, v4}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v3, v3

    invoke-interface {v2, v5}, Lc8/d;->getLong(I)J

    move-result-wide v4

    long-to-int v4, v4

    invoke-interface {v2, v6}, Lc8/d;->getDouble(I)D

    move-result-wide v5

    double-to-float v5, v5

    invoke-interface {v2, v7}, Lc8/d;->l0(I)Ljava/lang/String;

    move-result-object v6

    invoke-static {v6}, Lkf/b;->c(Ljava/lang/String;)Ljava/util/ArrayList;

    move-result-object v23

    invoke-interface {v2, v8}, Lc8/d;->getLong(I)J

    move-result-wide v6

    long-to-int v6, v6

    invoke-interface {v2, v9}, Lc8/d;->getLong(I)J

    move-result-wide v7

    long-to-int v7, v7

    const/4 v8, 0x0

    if-eqz v7, :cond_0

    const/16 v25, 0x1

    goto :goto_0

    :cond_0
    move/from16 v25, v8

    :goto_0
    invoke-interface {v2, v10}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v7, v9

    if-eqz v7, :cond_1

    const/16 v26, 0x1

    goto :goto_1

    :cond_1
    move/from16 v26, v8

    :goto_1
    invoke-interface {v2, v11}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v7, v9

    if-eqz v7, :cond_2

    const/16 v27, 0x1

    goto :goto_2

    :cond_2
    move/from16 v27, v8

    :goto_2
    invoke-interface {v2, v12}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v7, v9

    if-eqz v7, :cond_3

    const/16 v28, 0x1

    goto :goto_3

    :cond_3
    move/from16 v28, v8

    :goto_3
    invoke-interface {v2, v13}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v7, v9

    if-eqz v7, :cond_4

    const/16 v29, 0x1

    goto :goto_4

    :cond_4
    move/from16 v29, v8

    :goto_4
    invoke-interface {v2, v14}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v7, v9

    if-eqz v7, :cond_5

    const/16 v30, 0x1

    goto :goto_5

    :cond_5
    move/from16 v30, v8

    :goto_5
    invoke-interface {v2, v15}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v7, v9

    invoke-interface {v2, v0}, Lc8/d;->getLong(I)J

    move-result-wide v9

    long-to-int v0, v9

    if-eqz v0, :cond_6

    const/16 v32, 0x1

    :goto_6
    move/from16 v0, v16

    goto :goto_7

    :cond_6
    move/from16 v32, v8

    goto :goto_6

    :goto_7
    invoke-interface {v2, v0}, Lc8/d;->getDouble(I)D

    move-result-wide v8

    double-to-float v0, v8

    new-instance v18, Lye/c;

    move/from16 v33, v0

    move/from16 v20, v3

    move/from16 v21, v4

    move/from16 v22, v5

    move/from16 v24, v6

    move/from16 v31, v7

    invoke-direct/range {v18 .. v33}, Lye/c;-><init>(Ljava/lang/String;IIFLjava/util/ArrayList;IZZZZZZIZF)V

    move-object/from16 v0, v18

    invoke-interface {v2, v1}, Lc8/d;->getLong(I)J

    move-result-wide v3

    long-to-int v1, v3

    invoke-virtual {v0, v1}, Lye/c;->s(I)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    move-object/from16 v18, v0

    goto :goto_8

    :catchall_0
    move-exception v0

    goto :goto_9

    :cond_7
    const/16 v18, 0x0

    :goto_8
    invoke-interface {v2}, Ljava/lang/AutoCloseable;->close()V

    return-object v18

    :goto_9
    invoke-interface {v2}, Ljava/lang/AutoCloseable;->close()V

    throw v0
.end method
