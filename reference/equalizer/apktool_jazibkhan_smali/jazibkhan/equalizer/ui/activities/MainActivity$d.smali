.class public final Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;
.super Ljava/lang/Object;

# interfaces
.implements Landroid/widget/SeekBar$OnSeekBarChangeListener;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->I()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation


# instance fields
.field public final synthetic b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    return-void
.end method


# virtual methods
.method public final onProgressChanged(Landroid/widget/SeekBar;IZ)V
    .locals 1

    const-string v0, "seekBar"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    if-eqz p3, :cond_0

    :try_start_0
    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    iget-object p1, p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->l:Landroid/media/AudioManager;

    if-eqz p1, :cond_0

    const/4 p3, 0x3

    const/4 v0, 0x0

    invoke-virtual {p1, p3, p2, v0}, Landroid/media/AudioManager;->setStreamVolume(III)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception p1

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object p2

    invoke-virtual {p2, p1}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_0
    return-void
.end method

.method public final onStartTrackingTouch(Landroid/widget/SeekBar;)V
    .locals 1

    const-string v0, "seekBar"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    const/4 v0, 0x1

    iput-boolean v0, p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->o:Z

    return-void
.end method

.method public final onStopTrackingTouch(Landroid/widget/SeekBar;)V
    .locals 1

    const-string v0, "seekBar"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    const/4 v0, 0x0

    iput-boolean v0, p1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->o:Z

    return-void
.end method
