.class public final Lcom/jazibkhan/equalizer/services/SessionChangeWorker;
.super Landroidx/work/CoroutineWorker;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0004\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u0012\u0006\u0010\u0005\u001a\u00020\u0004\u00a2\u0006\u0004\u0008\u0006\u0010\u0007\u00a8\u0006\u0008"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/services/SessionChangeWorker;",
        "Landroidx/work/CoroutineWorker;",
        "Landroid/content/Context;",
        "context",
        "Landroidx/work/WorkerParameters;",
        "workerParams",
        "<init>",
        "(Landroid/content/Context;Landroidx/work/WorkerParameters;)V",
        "flat-equalizer-v6.3.5.7_release"
    }
    k = 0x1
    mv = {
        0x2,
        0x2,
        0x0
    }
    xi = 0x30
.end annotation


# direct methods
.method public constructor <init>(Landroid/content/Context;Landroidx/work/WorkerParameters;)V
    .locals 1

    const-string v0, "context"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "workerParams"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-direct {p0, p1, p2}, Landroidx/work/CoroutineWorker;-><init>(Landroid/content/Context;Landroidx/work/WorkerParameters;)V

    return-void
.end method


# virtual methods
.method public final doWork(Lbm/e;)Ljava/lang/Object;
    .locals 16
    .annotation build Landroid/annotation/SuppressLint;
        value = {
            "MissingPermission"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lbm/e<",
            "-",
            "Landroidx/work/p$a;",
            ">;)",
            "Ljava/lang/Object;"
        }
    .end annotation

    const-string v0, "SessionChangeWorker_doWork"

    invoke-static {v0}, Lkf/e;->b(Ljava/lang/String;)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    const-string v1, "getApplicationContext(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v0}, Lkf/f;->r(Landroid/content/Context;)V

    invoke-static {}, Lkf/f;->u()Z

    move-result v0

    if-eqz v0, :cond_0

    new-instance v0, Landroidx/work/p$a$c;

    invoke-direct {v0}, Landroidx/work/p$a$c;-><init>()V

    return-object v0

    :cond_0
    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getInputData()Landroidx/work/f;

    move-result-object v0

    const-string v2, "action_type"

    invoke-virtual {v0, v2}, Landroidx/work/f;->h(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getInputData()Landroidx/work/f;

    move-result-object v2

    iget-object v2, v2, Landroidx/work/f;->a:Ljava/util/HashMap;

    const-string v3, "session_id"

    invoke-virtual {v2, v3}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    instance-of v4, v2, Ljava/lang/Integer;

    if-eqz v4, :cond_1

    check-cast v2, Ljava/lang/Integer;

    invoke-virtual {v2}, Ljava/lang/Integer;->intValue()I

    move-result v2

    goto :goto_0

    :cond_1
    const/4 v2, 0x0

    :goto_0
    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getInputData()Landroidx/work/f;

    move-result-object v4

    const-string v6, "package_name"

    invoke-virtual {v4, v6}, Landroidx/work/f;->h(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    if-eqz v0, :cond_11

    invoke-virtual {v0}, Ljava/lang/String;->hashCode()I

    move-result v7

    const v8, -0x73b66a4b

    const-string v9, "start_with_audio_session"

    const-string v10, "android.permission.POST_NOTIFICATIONS"

    const v15, 0x10008000

    const-class v11, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    const-class v12, Landroid/app/NotificationManager;

    const-string v13, "notification"

    const/4 v5, 0x1

    const-class v14, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eq v7, v8, :cond_b

    const v2, -0x2608d5db

    if-eq v7, v2, :cond_8

    const v2, 0x2f94f923

    if-eq v7, v2, :cond_2

    goto/16 :goto_3

    :cond_2
    const-string v2, "android.intent.action.BOOT_COMPLETED"

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_3

    goto/16 :goto_3

    :cond_3
    invoke-static {}, Lkf/f;->b()Z

    move-result v0

    if-nez v0, :cond_4

    invoke-static {}, Lkf/f;->f()Z

    move-result v0

    if-nez v0, :cond_4

    invoke-static {}, Lkf/f;->i()Z

    move-result v0

    if-nez v0, :cond_4

    invoke-static {}, Lkf/f;->p()Z

    move-result v0

    if-nez v0, :cond_4

    invoke-static {}, Lkf/f;->l()Z

    move-result v0

    if-nez v0, :cond_4

    invoke-static {}, Lkf/f;->d()Z

    move-result v0

    if-eqz v0, :cond_11

    :cond_4
    new-instance v0, Landroid/content/Intent;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v2

    invoke-direct {v0, v2, v14}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    const-string v2, "com.jazibkhan.foregroundservice.action.startforeground"

    invoke-virtual {v0, v2}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v3, 0x1f

    if-ge v2, v3, :cond_5

    const-string v1, "Start service SessionChangeWorker 3"

    invoke-static {v1}, Lkf/e;->b(Ljava/lang/String;)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-virtual {v1, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    goto/16 :goto_3

    :cond_5
    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v3, Landroid/app/NotificationChannel;

    const-string v4, "boot_complete_noti"

    const-string v6, "Boot completed notification"

    const/4 v7, 0x4

    invoke-direct {v3, v4, v6, v7}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    const-string v6, "This notification is shown when the device is restarted"

    invoke-virtual {v3, v6}, Landroid/app/NotificationChannel;->setDescription(Ljava/lang/String;)V

    invoke-virtual {v0, v12}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/NotificationManager;

    invoke-virtual {v0, v3}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    new-instance v0, Landroid/content/Intent;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v3

    invoke-direct {v0, v3, v11}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v13, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    invoke-virtual {v0, v15}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v3

    const/high16 v6, 0x14000000

    const/4 v7, 0x0

    invoke-static {v3, v7, v0, v6}, Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v0

    new-instance v3, Li3/q;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v6

    invoke-direct {v3, v6, v4}, Li3/q;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    iget-object v4, v3, Li3/q;->u:Landroid/app/Notification;

    const v6, 0x7f0800f9

    iput v6, v4, Landroid/app/Notification;->icon:I

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v4

    const v6, 0x7f1300a1

    invoke-virtual {v4, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object v4

    iput-object v4, v3, Li3/q;->e:Ljava/lang/CharSequence;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v4

    const v6, 0x7f1302d2

    invoke-virtual {v4, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object v4

    iput-object v4, v3, Li3/q;->f:Ljava/lang/CharSequence;

    iput v5, v3, Li3/q;->j:I

    iput-object v0, v3, Li3/q;->g:Landroid/app/PendingIntent;

    const/16 v0, 0x10

    invoke-virtual {v3, v0, v5}, Li3/q;->d(IZ)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    new-instance v4, Li3/u;

    invoke-direct {v4, v0}, Li3/u;-><init>(Landroid/content/Context;)V

    const/16 v0, 0x21

    if-lt v2, v0, :cond_6

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v0, v10}, Lmi/i;->a(Landroid/content/Context;Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_7

    :cond_6
    invoke-virtual {v3}, Li3/q;->b()Landroid/app/Notification;

    move-result-object v0

    invoke-virtual {v4, v0}, Li3/u;->a(Landroid/app/Notification;)V

    :cond_7
    sget-object v0, Lxl/e0;->a:Lxl/e0;

    goto/16 :goto_3

    :cond_8
    const-string v2, "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION"

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_9

    goto/16 :goto_3

    :cond_9
    const/4 v7, 0x0

    invoke-static {v7}, Lkf/f;->D(I)V

    const-string v0, "Global Mix"

    invoke-static {v0}, Lkf/f;->B(Ljava/lang/String;)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v2

    invoke-static {v2, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v2}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result v1

    if-eqz v1, :cond_a

    new-instance v1, Landroid/content/Intent;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v2

    invoke-direct {v1, v2, v14}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v1, v3, v7}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v1, v6, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v1, v9}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    const-string v0, "Start_service_SessionChangeWorker_2"

    invoke-static {v0}, Lkf/e;->b(Ljava/lang/String;)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-virtual {v0, v1}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    goto/16 :goto_3

    :cond_a
    :try_start_0
    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-virtual {v0, v13}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    const-string v1, "null cannot be cast to non-null type android.app.NotificationManager"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->d(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v0, Landroid/app/NotificationManager;

    const/16 v1, 0x65

    invoke-virtual {v0, v1}, Landroid/app/NotificationManager;->cancel(I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_1

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_1
    sget-object v0, Lxl/e0;->a:Lxl/e0;

    goto/16 :goto_3

    :cond_b
    const-string v7, "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION"

    invoke-virtual {v0, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_11

    if-nez v2, :cond_c

    new-instance v0, Landroidx/work/p$a$c;

    invoke-direct {v0}, Landroidx/work/p$a$c;-><init>()V

    return-object v0

    :cond_c
    invoke-static {v2}, Lkf/f;->D(I)V

    invoke-static {v4}, Lkf/f;->B(Ljava/lang/String;)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_10

    invoke-static {}, Lkf/f;->b()Z

    move-result v0

    if-nez v0, :cond_10

    invoke-static {}, Lkf/f;->f()Z

    move-result v0

    if-nez v0, :cond_10

    invoke-static {}, Lkf/f;->i()Z

    move-result v0

    if-nez v0, :cond_10

    invoke-static {}, Lkf/f;->p()Z

    move-result v0

    if-nez v0, :cond_10

    invoke-static {}, Lkf/f;->l()Z

    move-result v0

    if-nez v0, :cond_10

    invoke-static {}, Lkf/f;->d()Z

    move-result v0

    if-eqz v0, :cond_d

    goto/16 :goto_2

    :cond_d
    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v7, Landroid/app/NotificationChannel;

    const-string v8, "music_player_noti"

    const-string v9, "Music player detected notification"

    const/4 v14, 0x2

    invoke-direct {v7, v8, v9, v14}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    const-string v9, "This notification is shown when a music player is detected"

    invoke-virtual {v7, v9}, Landroid/app/NotificationChannel;->setDescription(Ljava/lang/String;)V

    invoke-virtual {v0, v12}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/NotificationManager;

    invoke-virtual {v0, v7}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    new-instance v0, Landroid/content/Intent;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v7

    invoke-direct {v0, v7, v11}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v3, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v0, v6, v4}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v0, v13, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    invoke-virtual {v0, v15}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v3

    const/high16 v6, 0x14000000

    const/4 v7, 0x0

    invoke-static {v3, v7, v0, v6}, Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v0

    new-instance v3, Li3/q;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v6

    invoke-direct {v3, v6, v8}, Li3/q;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    iget-object v6, v3, Li3/q;->u:Landroid/app/Notification;

    const v7, 0x7f0800f9

    iput v7, v6, Landroid/app/Notification;->icon:I

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v6

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v7

    invoke-static {v7, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v7, v4}, Lkf/a;->c(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    filled-new-array {v4}, [Ljava/lang/Object;

    move-result-object v4

    const v7, 0x7f1302d1

    invoke-virtual {v6, v7, v4}, Landroid/content/Context;->getString(I[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object v4

    iput-object v4, v3, Li3/q;->f:Ljava/lang/CharSequence;

    const/4 v4, -0x1

    iput v4, v3, Li3/q;->j:I

    iput v4, v3, Li3/q;->p:I

    iput-object v0, v3, Li3/q;->g:Landroid/app/PendingIntent;

    const/16 v0, 0x10

    invoke-virtual {v3, v0, v5}, Li3/q;->d(IZ)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    new-instance v4, Li3/u;

    invoke-direct {v4, v0}, Li3/u;-><init>(Landroid/content/Context;)V

    const/16 v0, 0x21

    if-lt v2, v0, :cond_e

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v0, v10}, Lmi/i;->a(Landroid/content/Context;Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_f

    :cond_e
    invoke-virtual {v3}, Li3/q;->b()Landroid/app/Notification;

    move-result-object v0

    invoke-virtual {v4, v0}, Li3/u;->a(Landroid/app/Notification;)V

    :cond_f
    sget-object v0, Lxl/e0;->a:Lxl/e0;

    goto :goto_3

    :cond_10
    :goto_2
    new-instance v0, Landroid/content/Intent;

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-direct {v0, v1, v14}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v3, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v0, v6, v4}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v0, v9}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    const-string v1, "Start_service_SessionChangeWorker_1"

    invoke-static {v1}, Lkf/e;->b(Ljava/lang/String;)V

    invoke-virtual/range {p0 .. p0}, Landroidx/work/p;->getApplicationContext()Landroid/content/Context;

    move-result-object v1

    invoke-virtual {v1, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    :cond_11
    :goto_3
    new-instance v0, Landroidx/work/p$a$c;

    invoke-direct {v0}, Landroidx/work/p$a$c;-><init>()V

    return-object v0
.end method
