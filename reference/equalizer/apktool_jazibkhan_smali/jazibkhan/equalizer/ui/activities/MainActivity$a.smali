.class public final Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;
.super Landroid/content/BroadcastReceiver;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/ui/activities/MainActivity;-><init>()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation


# instance fields
.field public final synthetic a:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V
    .locals 0

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;->a:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V

    return-void
.end method


# virtual methods
.method public final onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 2

    const-string p1, "intent"

    invoke-static {p2, p1}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object p1

    const-string p2, "main_activity_broadcast"

    invoke-static {p1, p2}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_0

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;->a:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-virtual {p1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/l;

    const/4 v1, 0x0

    invoke-direct {v0, p1, v1}, Ldf/l;-><init>(Ldf/b;Lbm/e;)V

    const/4 p1, 0x3

    invoke-static {p2, v1, v1, v0, p1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    :cond_0
    return-void
.end method
