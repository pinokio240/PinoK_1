.class public final Lcom/jazibkhan/equalizer/services/MainForegroundService$f;
.super Ldm/i;

# interfaces
.implements Lmm/p;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/services/MainForegroundService;->d()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ldm/i;",
        "Lmm/p<",
        "Lip/h0;",
        "Lbm/e<",
        "-",
        "Lxl/e0;",
        ">;",
        "Ljava/lang/Object;",
        ">;"
    }
.end annotation

.annotation runtime Ldm/e;
    c = "com.jazibkhan.equalizer.services.MainForegroundService$startNotifyRouteJob$1"
    f = "MainForegroundService.kt"
    l = {
        0x6a
    }
    m = "invokeSuspend"
.end annotation


# instance fields
.field public l:I

.field public final synthetic m:Lcom/jazibkhan/equalizer/services/MainForegroundService;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;Lbm/e;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/jazibkhan/equalizer/services/MainForegroundService;",
            "Lbm/e<",
            "-",
            "Lcom/jazibkhan/equalizer/services/MainForegroundService$f;",
            ">;)V"
        }
    .end annotation

    iput-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->m:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    const/4 p1, 0x2

    invoke-direct {p0, p1, p2}, Ldm/i;-><init>(ILbm/e;)V

    return-void
.end method


# virtual methods
.method public final create(Ljava/lang/Object;Lbm/e;)Lbm/e;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/Object;",
            "Lbm/e<",
            "*>;)",
            "Lbm/e<",
            "Lxl/e0;",
            ">;"
        }
    .end annotation

    new-instance p1, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;

    iget-object v0, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->m:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-direct {p1, v0, p2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;-><init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;Lbm/e;)V

    return-object p1
.end method

.method public final invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 0

    check-cast p1, Lip/h0;

    check-cast p2, Lbm/e;

    invoke-virtual {p0, p1, p2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->create(Ljava/lang/Object;Lbm/e;)Lbm/e;

    move-result-object p1

    check-cast p1, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;

    sget-object p2, Lxl/e0;->a:Lxl/e0;

    invoke-virtual {p1, p2}, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 7

    sget-object v0, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    iget v1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->l:I

    const/4 v2, 0x1

    if-eqz v1, :cond_1

    if-ne v1, v2, :cond_0

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    goto :goto_0

    :cond_0
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_1
    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    iput v2, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->l:I

    const-wide/16 v1, 0x3e8

    invoke-static {v1, v2, p0}, Lip/r0;->a(JLbm/e;)Ljava/lang/Object;

    move-result-object p1

    if-ne p1, v0, :cond_2

    return-object v0

    :cond_2
    :goto_0
    iget-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$f;->m:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    iget-object v0, p1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->l:Lj7/w;

    const/4 v1, 0x0

    if-eqz v0, :cond_4

    invoke-static {}, Lj7/w;->b()V

    invoke-static {}, Lj7/w;->c()Lj7/b;

    move-result-object v0

    iget-object v0, v0, Lj7/b;->c:Lj7/w$g;

    if-eqz v0, :cond_3

    goto :goto_1

    :cond_3
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "There is no currently selected route.  The media router has not yet been fully initialized."

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_4
    move-object v0, v1

    :goto_1
    if-eqz v0, :cond_5

    iget-object v2, v0, Lj7/w$g;->d:Ljava/lang/String;

    goto :goto_2

    :cond_5
    move-object v2, v1

    :goto_2
    invoke-static {v2}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v2

    iput-object v2, p1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->t:Ljava/lang/String;

    if-eqz v0, :cond_6

    invoke-static {}, Lj7/w;->b()V

    invoke-static {}, Lj7/w;->c()Lj7/b;

    move-result-object v2

    iget-object v2, v2, Lj7/b;->t:Lj7/w$g;

    if-ne v2, v0, :cond_6

    sget-object v0, Lze/b;->BLUETOOTH:Lze/b;

    invoke-virtual {p1, v0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->a(Lze/b;)V

    goto/16 :goto_6

    :cond_6
    if-eqz v0, :cond_8

    invoke-static {}, Landroid/content/res/Resources;->getSystem()Landroid/content/res/Resources;

    move-result-object v2

    const-string v3, "string"

    const-string v4, "android"

    const-string v5, "default_audio_route_name"

    invoke-virtual {v2, v5, v3, v4}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v2

    invoke-static {}, Lj7/w;->b()V

    invoke-static {}, Lj7/w;->c()Lj7/b;

    move-result-object v3

    iget-object v3, v3, Lj7/b;->s:Lj7/w$g;

    if-eqz v3, :cond_7

    if-ne v3, v0, :cond_8

    invoke-static {}, Landroid/content/res/Resources;->getSystem()Landroid/content/res/Resources;

    move-result-object v3

    invoke-virtual {v3, v2}, Landroid/content/res/Resources;->getText(I)Ljava/lang/CharSequence;

    move-result-object v2

    iget-object v0, v0, Lj7/w$g;->d:Ljava/lang/String;

    invoke-static {v2, v0}, Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z

    move-result v0

    if-eqz v0, :cond_8

    sget-object v0, Lze/b;->SPEAKER:Lze/b;

    invoke-virtual {p1, v0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->a(Lze/b;)V

    goto :goto_6

    :cond_7
    new-instance p1, Ljava/lang/IllegalStateException;

    const-string v0, "There is no default route.  The media router has not yet been fully initialized."

    invoke-direct {p1, v0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_8
    iget-object v0, p1, Lcom/jazibkhan/equalizer/services/MainForegroundService;->m:Landroid/media/AudioManager;

    if-eqz v0, :cond_9

    const/4 v2, 0x2

    invoke-virtual {v0, v2}, Landroid/media/AudioManager;->getDevices(I)[Landroid/media/AudioDeviceInfo;

    move-result-object v0

    goto :goto_3

    :cond_9
    move-object v0, v1

    :goto_3
    if-eqz v0, :cond_c

    array-length v2, v0

    const/4 v3, 0x0

    :goto_4
    if-ge v3, v2, :cond_c

    aget-object v4, v0, v3

    invoke-static {v4}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    invoke-virtual {v4}, Landroid/media/AudioDeviceInfo;->getType()I

    move-result v5

    const/4 v6, 0x4

    if-eq v5, v6, :cond_b

    invoke-virtual {v4}, Landroid/media/AudioDeviceInfo;->getType()I

    move-result v5

    const/4 v6, 0x3

    if-eq v5, v6, :cond_b

    invoke-virtual {v4}, Landroid/media/AudioDeviceInfo;->getType()I

    move-result v5

    const/4 v6, 0x5

    if-eq v5, v6, :cond_b

    invoke-virtual {v4}, Landroid/media/AudioDeviceInfo;->getType()I

    move-result v5

    const/16 v6, 0x16

    if-eq v5, v6, :cond_b

    invoke-virtual {v4}, Landroid/media/AudioDeviceInfo;->getType()I

    move-result v5

    const/16 v6, 0xb

    if-ne v5, v6, :cond_a

    goto :goto_5

    :cond_a
    add-int/lit8 v3, v3, 0x1

    goto :goto_4

    :cond_b
    :goto_5
    move-object v1, v4

    :cond_c
    if-eqz v1, :cond_d

    sget-object v0, Lze/b;->HEADPHONES:Lze/b;

    invoke-virtual {p1, v0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->a(Lze/b;)V

    goto :goto_6

    :cond_d
    sget-object v0, Lze/b;->SPEAKER:Lze/b;

    invoke-virtual {p1, v0}, Lcom/jazibkhan/equalizer/services/MainForegroundService;->a(Lze/b;)V

    :goto_6
    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method
