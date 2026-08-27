.class public final Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;
.super Ldm/i;

# interfaces
.implements Lmm/p;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->C()V
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
    c = "com.jazibkhan.equalizer.ui.activities.MainActivity$setupEqView$1"
    f = "MainActivity.kt"
    l = {}
    m = "invokeSuspend"
.end annotation


# instance fields
.field public final synthetic l:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

.field public final synthetic m:I

.field public final synthetic n:I


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;IILbm/e;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/jazibkhan/equalizer/ui/activities/MainActivity;",
            "II",
            "Lbm/e<",
            "-",
            "Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;",
            ">;)V"
        }
    .end annotation

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->l:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    iput p2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->m:I

    iput p3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->n:I

    const/4 p1, 0x2

    invoke-direct {p0, p1, p4}, Ldm/i;-><init>(ILbm/e;)V

    return-void
.end method


# virtual methods
.method public final create(Ljava/lang/Object;Lbm/e;)Lbm/e;
    .locals 3
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

    new-instance p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;

    iget v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->m:I

    iget v1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->n:I

    iget-object v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->l:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-direct {p1, v2, v0, v1, p2}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;IILbm/e;)V

    return-object p1
.end method

.method public final invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 0

    check-cast p1, Lip/h0;

    check-cast p2, Lbm/e;

    invoke-virtual {p0, p1, p2}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->create(Ljava/lang/Object;Lbm/e;)Lbm/e;

    move-result-object p1

    check-cast p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;

    sget-object p2, Lxl/e0;->a:Lxl/e0;

    invoke-virtual {p1, p2}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    return-object p1
.end method

.method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 2

    sget-object v0, Lcm/a;->COROUTINE_SUSPENDED:Lcm/a;

    invoke-static {p1}, Lxl/q;->b(Ljava/lang/Object;)V

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->l:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    iget-object p1, p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    iget v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->m:I

    invoke-virtual {p1, v0}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Landroid/widget/SeekBar;

    iget v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;->n:I

    const/4 v1, 0x1

    invoke-virtual {p1, v0, v1}, Landroid/widget/ProgressBar;->setProgress(IZ)V

    sget-object p1, Lxl/e0;->a:Lxl/e0;

    return-object p1
.end method
