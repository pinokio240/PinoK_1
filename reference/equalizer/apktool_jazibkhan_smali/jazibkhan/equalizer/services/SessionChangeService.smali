.class public final Lcom/jazibkhan/equalizer/services/SessionChangeService;
.super Li3/j;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/jazibkhan/equalizer/services/SessionChangeService$a;
    }
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u000c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0004\u0018\u00002\u00020\u0001:\u0001\u0004B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003\u00a8\u0006\u0005"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/services/SessionChangeService;",
        "Li3/j;",
        "<init>",
        "()V",
        "a",
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


# static fields
.field public static final synthetic f:I


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Li3/j;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(Landroid/content/Intent;)V
    .locals 9
    .annotation build Landroid/annotation/SuppressLint;
        value = {
            "MissingPermission"
        }
    .end annotation

    const-string v0, "intent"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {p0}, Lkf/f;->r(Landroid/content/Context;)V

    invoke-static {}, Lkf/f;->u()Z

    move-result v0

    if-eqz v0, :cond_0

    goto/16 :goto_3

    :cond_0
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_1

    goto/16 :goto_3

    :cond_1
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    const-string v1, "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    const-string v1, "start_with_audio_session"

    const-string v2, "notification"

    const-string v3, "package_name"

    const-string v4, "session_id"

    const-class v5, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    const/4 v6, 0x0

    if-eqz v0, :cond_6

    const-string v0, "android.media.extra.AUDIO_SESSION"

    invoke-virtual {p1, v0, v6}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I

    move-result v0

    if-nez v0, :cond_2

    goto/16 :goto_3

    :cond_2
    const-string v7, "android.media.extra.PACKAGE_NAME"

    invoke-virtual {p1, v7}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-static {v0}, Lkf/f;->D(I)V

    invoke-static {p1}, Lkf/f;->B(Ljava/lang/String;)V

    invoke-static {p0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result v7

    if-nez v7, :cond_5

    invoke-static {}, Lkf/f;->b()Z

    move-result v7

    if-nez v7, :cond_5

    invoke-static {}, Lkf/f;->f()Z

    move-result v7

    if-nez v7, :cond_5

    invoke-static {}, Lkf/f;->i()Z

    move-result v7

    if-nez v7, :cond_5

    invoke-static {}, Lkf/f;->p()Z

    move-result v7

    if-nez v7, :cond_5

    invoke-static {}, Lkf/f;->l()Z

    move-result v7

    if-nez v7, :cond_5

    invoke-static {}, Lkf/f;->d()Z

    move-result v7

    if-eqz v7, :cond_3

    goto/16 :goto_0

    :cond_3
    new-instance v1, Landroid/app/NotificationChannel;

    const-string v5, "music_player_noti"

    const-string v7, "Music player detected notification"

    const/4 v8, 0x2

    invoke-direct {v1, v5, v7, v8}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    const-string v7, "This notification is shown when a music player is detected"

    invoke-virtual {v1, v7}, Landroid/app/NotificationChannel;->setDescription(Ljava/lang/String;)V

    const-class v7, Landroid/app/NotificationManager;

    invoke-virtual {p0, v7}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Landroid/app/NotificationManager;

    invoke-virtual {v7, v1}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    new-instance v1, Landroid/content/Intent;

    const-class v7, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-direct {v1, p0, v7}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v1, v4, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v1, v3, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    const/4 v0, 0x1

    invoke-virtual {v1, v2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    const v2, 0x10008000

    invoke-virtual {v1, v2}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    const/high16 v2, 0x14000000

    invoke-static {p0, v6, v1, v2}, Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v1

    new-instance v2, Li3/q;

    invoke-direct {v2, p0, v5}, Li3/q;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    const v3, 0x7f0800f9

    iget-object v4, v2, Li3/q;->u:Landroid/app/Notification;

    iput v3, v4, Landroid/app/Notification;->icon:I

    const v3, 0x7f130039

    invoke-virtual {p0, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object v3

    iput-object v3, v2, Li3/q;->e:Ljava/lang/CharSequence;

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v3

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v4

    const-string v5, "getApplicationContext(...)"

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v4, p1}, Lkf/a;->c(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    filled-new-array {p1}, [Ljava/lang/Object;

    move-result-object p1

    const v4, 0x7f1302d1

    invoke-virtual {v3, v4, p1}, Landroid/content/Context;->getString(I[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Li3/q;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;

    move-result-object p1

    iput-object p1, v2, Li3/q;->f:Ljava/lang/CharSequence;

    const/4 p1, -0x1

    iput p1, v2, Li3/q;->j:I

    iput-object v1, v2, Li3/q;->g:Landroid/app/PendingIntent;

    const/16 p1, 0x10

    invoke-virtual {v2, p1, v0}, Li3/q;->d(IZ)V

    new-instance p1, Li3/u;

    invoke-direct {p1, p0}, Li3/u;-><init>(Landroid/content/Context;)V

    const-string v0, "android.permission.POST_NOTIFICATIONS"

    invoke-static {p0, v0}, Lmi/i;->a(Landroid/content/Context;Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_4

    goto/16 :goto_3

    :cond_4
    invoke-virtual {v2}, Li3/q;->b()Landroid/app/Notification;

    move-result-object v0

    invoke-virtual {p1, v0}, Li3/u;->a(Landroid/app/Notification;)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-void

    :cond_5
    :goto_0
    new-instance v2, Landroid/content/Intent;

    invoke-direct {v2, p0, v5}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v2, v4, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v2, v3, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v2, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    sget-object p1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object p1

    iget-object p1, p1, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v0, v6, [Landroid/os/Bundle;

    const-string v1, "Start_service_SessionChangeService_1"

    invoke-virtual {p1, v1, v0}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, v2}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    return-void

    :cond_6
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    const-string v7, "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION"

    invoke-static {v0, v7}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_9

    invoke-static {v6}, Lkf/f;->D(I)V

    const-string p1, "Global Mix"

    invoke-static {p1}, Lkf/f;->B(Ljava/lang/String;)V

    invoke-static {p0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {}, Lkf/f;->b()Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {}, Lkf/f;->f()Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {}, Lkf/f;->i()Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {}, Lkf/f;->p()Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {}, Lkf/f;->l()Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {}, Lkf/f;->d()Z

    move-result v0

    if-eqz v0, :cond_7

    goto :goto_2

    :cond_7
    :try_start_0
    invoke-virtual {p0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    const-string v0, "null cannot be cast to non-null type android.app.NotificationManager"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->d(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast p1, Landroid/app/NotificationManager;

    const/16 v0, 0x65

    invoke-virtual {p1, v0}, Landroid/app/NotificationManager;->cancel(I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_1

    :catch_0
    move-exception p1

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v0

    invoke-virtual {v0, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_1
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-void

    :cond_8
    :goto_2
    new-instance v0, Landroid/content/Intent;

    invoke-direct {v0, p0, v5}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v4, v6}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v0, v3, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    sget-object p1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object p1

    iget-object p1, p1, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v6, [Landroid/os/Bundle;

    const-string v2, "Start_service_SessionChangeService_2"

    invoke-virtual {p1, v2, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    return-void

    :cond_9
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object p1

    const-string v0, "android.intent.action.BOOT_COMPLETED"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_b

    invoke-static {}, Lkf/f;->b()Z

    move-result p1

    if-nez p1, :cond_a

    invoke-static {}, Lkf/f;->f()Z

    move-result p1

    if-nez p1, :cond_a

    invoke-static {}, Lkf/f;->i()Z

    move-result p1

    if-nez p1, :cond_a

    invoke-static {}, Lkf/f;->p()Z

    move-result p1

    if-nez p1, :cond_a

    invoke-static {}, Lkf/f;->l()Z

    move-result p1

    if-nez p1, :cond_a

    invoke-static {}, Lkf/f;->d()Z

    move-result p1

    if-eqz p1, :cond_b

    :cond_a
    new-instance p1, Landroid/content/Intent;

    invoke-direct {p1, p0, v5}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    const-string v0, "com.jazibkhan.foregroundservice.action.startforeground"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1f

    if-ge v0, v1, :cond_b

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v6, [Landroid/os/Bundle;

    const-string v2, "Start_service_SessionChangeService_3"

    invoke-virtual {v0, v2, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, p1}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    :cond_b
    :goto_3
    return-void
.end method
