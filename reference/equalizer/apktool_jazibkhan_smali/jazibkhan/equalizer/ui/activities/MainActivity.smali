.class public final Lcom/jazibkhan/equalizer/ui/activities/MainActivity;
.super Ldf/a;

# interfaces
.implements Landroid/widget/SeekBar$OnSeekBarChangeListener;
.implements Landroid/widget/CompoundButton$OnCheckedChangeListener;
.implements Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;
.implements Landroid/view/View$OnTouchListener;
.implements Lmf/c;
.implements Lcom/jazibkhan/equalizer/services/MainForegroundService$b;
.implements Lhf/w$b;
.implements Lhf/o;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\u0018\u00002\u00020\u00012\u00020\u00022\u00020\u00032\u00020\u00042\u00020\u00052\u00020\u00062\u00020\u00072\u00020\u00082\u00020\tB\u0007\u00a2\u0006\u0004\u0008\n\u0010\u000b\u00a8\u0006\u000c"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/ui/activities/MainActivity;",
        "Ldf/a;",
        "Landroid/widget/SeekBar$OnSeekBarChangeListener;",
        "Landroid/widget/CompoundButton$OnCheckedChangeListener;",
        "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;",
        "Landroid/view/View$OnTouchListener;",
        "Lmf/c;",
        "Lcom/jazibkhan/equalizer/services/MainForegroundService$b;",
        "Lhf/w$b;",
        "Lhf/o;",
        "<init>",
        "()V",
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
.field public static final synthetic t:I


# instance fields
.field public c:Laf/a;

.field public final d:Landroidx/lifecycle/q1;

.field public final e:Ljava/util/ArrayList;

.field public final f:Ljava/util/ArrayList;

.field public final g:Ljava/util/ArrayList;

.field public h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

.field public i:Z

.field public j:I

.field public k:Ljava/lang/String;

.field public l:Landroid/media/AudioManager;

.field public m:Lkf/g;

.field public n:Landroid/os/HandlerThread;

.field public o:Z

.field public p:Lmi/d;

.field public q:Z

.field public final r:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;

.field public final s:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;


# direct methods
.method public constructor <init>()V
    .locals 5

    invoke-direct {p0}, Ldf/a;-><init>()V

    new-instance v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$e;

    invoke-direct {v0, p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$e;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    new-instance v1, Landroidx/lifecycle/q1;

    const-class v2, Ldf/b;

    sget-object v3, Lkotlin/jvm/internal/e0;->a:Lkotlin/jvm/internal/f0;

    invoke-virtual {v3, v2}, Lkotlin/jvm/internal/f0;->b(Ljava/lang/Class;)Ltm/d;

    move-result-object v2

    new-instance v3, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$f;

    invoke-direct {v3, p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$f;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    new-instance v4, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$g;

    invoke-direct {v4, p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$g;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-direct {v1, v2, v3, v0, v4}, Landroidx/lifecycle/q1;-><init>(Ltm/d;Lmm/a;Lmm/a;Lmm/a;)V

    iput-object v1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->d:Landroidx/lifecycle/q1;

    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->f:Ljava/util/ArrayList;

    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->g:Ljava/util/ArrayList;

    new-instance v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;

    invoke-direct {v0, p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->r:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;

    new-instance v0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;

    invoke-direct {v0, p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->s:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;

    return-void
.end method


# virtual methods
.method public final A()V
    .locals 9

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_3

    iget-object v1, v0, Laf/a;->b:Landroid/widget/TextView;

    iget-object v2, v0, Laf/a;->O:Landroid/widget/TextView;

    iget-object v3, v0, Laf/a;->J:Landroid/widget/TextView;

    iget-object v4, v0, Laf/a;->t:Lcom/jazibkhan/equalizer/views/MidSeekBar;

    iget-object v5, v0, Laf/a;->A:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v6

    iget-boolean v6, v6, Ldf/b;->C:Z

    invoke-virtual {v5, v6}, Lcom/jazibkhan/equalizer/views/JSwitch;->setCheckedSilently(Z)V

    iget-object v5, v0, Laf/a;->k:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v6

    iget-boolean v6, v6, Ldf/b;->F:Z

    const/16 v7, 0x8

    const/4 v8, 0x0

    if-eqz v6, :cond_0

    move v6, v8

    goto :goto_0

    :cond_0
    move v6, v7

    :goto_0
    invoke-virtual {v5, v6}, Landroid/view/View;->setVisibility(I)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    iget v5, v5, Ldf/b;->s:F

    invoke-virtual {v4, v5}, Lcom/jazibkhan/equalizer/views/MidSeekBar;->setProgress(F)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    invoke-virtual {v5}, Ldf/b;->l()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v3, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    invoke-virtual {v5}, Ldf/b;->m()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v2, v5}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    iget-boolean v5, v5, Ldf/b;->C:Z

    invoke-virtual {v4, v5}, Landroid/view/View;->setEnabled(Z)V

    iget-object v4, v0, Laf/a;->I:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    iget-boolean v5, v5, Ldf/b;->C:Z

    invoke-virtual {v4, v5}, Landroid/widget/TextView;->setEnabled(Z)V

    iget-object v0, v0, Laf/a;->N:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget-boolean v4, v4, Ldf/b;->C:Z

    invoke-virtual {v0, v4}, Landroid/widget/TextView;->setEnabled(Z)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->C:Z

    invoke-virtual {v3, v0}, Landroid/widget/TextView;->setEnabled(Z)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->C:Z

    invoke-virtual {v2, v0}, Landroid/widget/TextView;->setEnabled(Z)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->C:Z

    invoke-virtual {v1, v0}, Landroid/widget/TextView;->setEnabled(Z)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget v2, v0, Ldf/b;->s:F

    const/4 v3, 0x0

    cmpg-float v2, v2, v3

    if-nez v2, :cond_1

    goto :goto_1

    :cond_1
    iget-boolean v0, v0, Ldf/b;->C:Z

    if-eqz v0, :cond_2

    move v7, v8

    :cond_2
    :goto_1
    invoke-virtual {v1, v7}, Landroid/view/View;->setVisibility(I)V

    return-void

    :cond_3
    const-string v0, "binding"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/4 v0, 0x0

    throw v0
.end method

.method public final B()V
    .locals 5

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->x:Z

    if-nez v0, :cond_0

    goto :goto_2

    :cond_0
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->D:Z

    const/4 v1, 0x0

    if-eqz v0, :cond_2

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget v0, v0, Ldf/b;->k:I

    :goto_0
    if-ge v1, v0, :cond_4

    iget-object v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz v2, :cond_1

    iget-object v2, v2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->d:Lye/m0$c;

    if-eqz v2, :cond_1

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-object v3, v3, Ldf/b;->v:Ljava/util/ArrayList;

    invoke-interface {v3, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/Number;

    invoke-virtual {v3}, Ljava/lang/Number;->intValue()I

    move-result v3

    invoke-virtual {v2, v1, v3}, Lye/m0$c;->b(II)V

    :cond_1
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_2
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget v0, v0, Ldf/b;->k:I

    :goto_1
    if-ge v1, v0, :cond_4

    iget-object v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz v2, :cond_3

    iget-object v2, v2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->d:Lye/m0$c;

    if-eqz v2, :cond_3

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-object v3, v3, Ldf/b;->m:Ljava/util/ArrayList;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget v4, v4, Ldf/b;->w:I

    invoke-virtual {v3, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lye/u;

    iget-object v3, v3, Lye/u;->b:Ljava/util/List;

    invoke-interface {v3, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/Number;

    invoke-virtual {v3}, Ljava/lang/Number;->intValue()I

    move-result v3

    invoke-virtual {v2, v1, v3}, Lye/m0$c;->b(II)V

    :cond_3
    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    :cond_4
    :goto_2
    return-void
.end method

.method public final C()V
    .locals 9

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const-string v1, "binding"

    const/4 v2, 0x0

    if-eqz v0, :cond_b

    iget-object v0, v0, Laf/a;->B:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->x:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/JSwitch;->setCheckedSilently(Z)V

    new-instance v0, Landroid/widget/ArrayAdapter;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-object v3, v3, Ldf/b;->m:Ljava/util/ArrayList;

    const v4, 0x7f0d0113

    invoke-direct {v0, p0, v4, v3}, Landroid/widget/ArrayAdapter;-><init>(Landroid/content/Context;ILjava/util/List;)V

    invoke-virtual {v0}, Landroid/widget/ArrayAdapter;->notifyDataSetChanged()V

    const v3, 0x1090009

    invoke-virtual {v0, v3}, Landroid/widget/ArrayAdapter;->setDropDownViewResource(I)V

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_a

    iget-object v3, v3, Laf/a;->y:Landroidx/appcompat/widget/AppCompatSpinner;

    invoke-virtual {v3, v0}, Landroidx/appcompat/widget/AppCompatSpinner;->setAdapter(Landroid/widget/SpinnerAdapter;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->D:Z

    const/4 v3, 0x0

    if-eqz v0, :cond_1

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_0

    iget-object v0, v0, Laf/a;->y:Landroidx/appcompat/widget/AppCompatSpinner;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget-object v4, v4, Ldf/b;->m:Ljava/util/ArrayList;

    invoke-virtual {v4}, Ljava/util/ArrayList;->size()I

    move-result v4

    add-int/lit8 v4, v4, -0x1

    invoke-virtual {v0, v4, v3}, Landroid/widget/AbsSpinner;->setSelection(IZ)V

    goto :goto_0

    :cond_0
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_1
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_9

    iget-object v0, v0, Laf/a;->y:Landroidx/appcompat/widget/AppCompatSpinner;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget v4, v4, Ldf/b;->w:I

    invoke-virtual {v0, v4, v3}, Landroid/widget/AbsSpinner;->setSelection(IZ)V

    :goto_0
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget v0, v0, Ldf/b;->k:I

    move v4, v3

    :goto_1
    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->g:Ljava/util/ArrayList;

    iget-object v6, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->f:Ljava/util/ArrayList;

    iget-object v7, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    if-ge v4, v0, :cond_3

    invoke-virtual {v7, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Landroid/widget/SeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v8

    iget v8, v8, Ldf/b;->e:I

    invoke-virtual {v7, v8}, Landroid/widget/ProgressBar;->setMax(I)V

    invoke-virtual {v6, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v7

    iget-object v7, v7, Ldf/b;->n:Ljava/util/ArrayList;

    invoke-virtual {v7, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/CharSequence;

    invoke-virtual {v6, v7}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {v5, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v6

    invoke-virtual {v6}, Ldf/b;->k()Ljava/util/ArrayList;

    move-result-object v6

    invoke-virtual {v6, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/CharSequence;

    invoke-virtual {v5, v6}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    iget-boolean v5, v5, Ldf/b;->D:Z

    if-eqz v5, :cond_2

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    iget v5, v5, Ldf/b;->i:I

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v6

    iget v6, v6, Ldf/b;->j:I

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v7

    iget-object v7, v7, Ldf/b;->v:Ljava/util/ArrayList;

    invoke-interface {v7, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/Number;

    invoke-virtual {v7}, Ljava/lang/Number;->intValue()I

    move-result v7

    sub-int/2addr v7, v5

    mul-int/lit16 v7, v7, 0xbb8

    sub-int/2addr v6, v5

    div-int/2addr v7, v6

    goto :goto_2

    :cond_2
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v5

    iget v5, v5, Ldf/b;->i:I

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v6

    iget v6, v6, Ldf/b;->j:I

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v7

    iget-object v7, v7, Ldf/b;->m:Ljava/util/ArrayList;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v8

    iget v8, v8, Ldf/b;->w:I

    invoke-virtual {v7, v8}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Lye/u;

    iget-object v7, v7, Lye/u;->b:Ljava/util/List;

    invoke-interface {v7, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/Number;

    invoke-virtual {v7}, Ljava/lang/Number;->intValue()I

    move-result v7

    sub-int/2addr v7, v5

    mul-int/lit16 v7, v7, 0xbb8

    sub-int/2addr v6, v5

    div-int/2addr v7, v6

    :goto_2
    invoke-static {p0}, Lam/b;->b(Landroidx/lifecycle/g0;)Landroidx/lifecycle/c0;

    move-result-object v5

    sget-object v6, Lip/y0;->a:Lqp/c;

    sget-object v6, Lnp/s;->a:Lip/e2;

    new-instance v8, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;

    invoke-direct {v8, p0, v4, v7, v2}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$c;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;IILbm/e;)V

    const/4 v7, 0x2

    invoke-static {v5, v6, v2, v8, v7}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    add-int/lit8 v4, v4, 0x1

    goto/16 :goto_1

    :cond_3
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_8

    iget-object v0, v0, Laf/a;->j:Lcom/jazibkhan/equalizer/views/Curve;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget-object v4, v4, Ldf/b;->l:[F

    invoke-virtual {v0, v4}, Lcom/jazibkhan/equalizer/views/Curve;->b([F)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_7

    iget-object v0, v0, Laf/a;->y:Landroidx/appcompat/widget/AppCompatSpinner;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget-boolean v4, v4, Ldf/b;->x:Z

    invoke-virtual {v0, v4}, Landroid/view/View;->setEnabled(Z)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget v0, v0, Ldf/b;->k:I

    :goto_3
    if-ge v3, v0, :cond_4

    invoke-virtual {v7, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/widget/SeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v8

    iget-boolean v8, v8, Ldf/b;->x:Z

    invoke-virtual {v4, v8}, Landroid/view/View;->setEnabled(Z)V

    invoke-virtual {v6, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v8

    iget-boolean v8, v8, Ldf/b;->x:Z

    invoke-virtual {v4, v8}, Landroid/widget/TextView;->setEnabled(Z)V

    invoke-virtual {v5, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v8

    iget-boolean v8, v8, Ldf/b;->x:Z

    invoke-virtual {v4, v8}, Landroid/widget/TextView;->setEnabled(Z)V

    add-int/lit8 v3, v3, 0x1

    goto :goto_3

    :cond_4
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_6

    iget-object v0, v0, Laf/a;->j:Lcom/jazibkhan/equalizer/views/Curve;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->x:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/Curve;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_5

    iget-object v0, v0, Laf/a;->L:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget-boolean v1, v1, Ldf/b;->x:Z

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setEnabled(Z)V

    return-void

    :cond_5
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_6
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_7
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_8
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_9
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_a
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_b
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2
.end method

.method public final D()V
    .locals 4

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const/4 v1, 0x0

    const-string v2, "binding"

    if-eqz v0, :cond_5

    iget-object v0, v0, Laf/a;->C:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->z:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/JSwitch;->setCheckedSilently(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_4

    iget-object v0, v0, Laf/a;->u:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->h:I

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setMaxProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_3

    iget-object v0, v0, Laf/a;->u:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->t:F

    float-to-int v3, v3

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_2

    iget-object v0, v0, Laf/a;->u:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->z:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1

    iget-object v0, v0, Laf/a;->K:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->z:Z

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_0

    iget-object v0, v0, Laf/a;->K:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget v1, v1, Ldf/b;->t:F

    float-to-int v1, v1

    int-to-float v1, v1

    const v2, 0x461c4000    # 10000.0f

    div-float/2addr v1, v2

    const/16 v2, 0x64

    int-to-float v2, v2

    mul-float/2addr v1, v2

    invoke-static {v1}, Lom/a;->b(F)I

    move-result v1

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, "%"

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void

    :cond_0
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_1
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_3
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_4
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_5
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final E()V
    .locals 2

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->B:Z

    if-nez v0, :cond_0

    goto :goto_0

    :cond_0
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz v0, :cond_1

    iget-object v0, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->h:Lye/m0$e;

    if-eqz v0, :cond_1

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget v1, v1, Ldf/b;->r:I

    invoke-virtual {v0, v1}, Lye/m0$e;->b(I)V

    :cond_1
    :goto_0
    return-void
.end method

.method public final F()V
    .locals 5

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const/4 v1, 0x0

    const-string v2, "binding"

    if-eqz v0, :cond_6

    iget-object v0, v0, Laf/a;->D:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->B:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/JSwitch;->setCheckedSilently(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_5

    iget-object v0, v0, Laf/a;->l:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->E:Z

    if-eqz v3, :cond_0

    const/4 v3, 0x0

    goto :goto_0

    :cond_0
    const/16 v3, 0x8

    :goto_0
    invoke-virtual {v0, v3}, Landroid/view/View;->setVisibility(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_4

    iget-object v0, v0, Laf/a;->v:Landroidx/appcompat/widget/AppCompatSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->r:I

    invoke-virtual {v0, v3}, Landroid/widget/ProgressBar;->setProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_3

    iget-object v0, v0, Laf/a;->P:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-object v3, v3, Ldf/b;->d:Ljava/util/List;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v4

    iget v4, v4, Ldf/b;->r:I

    invoke-interface {v3, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/CharSequence;

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_2

    iget-object v0, v0, Laf/a;->v:Landroidx/appcompat/widget/AppCompatSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->B:Z

    invoke-virtual {v0, v3}, Landroid/view/View;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1

    iget-object v0, v0, Laf/a;->P:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget-boolean v1, v1, Ldf/b;->B:Z

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setEnabled(Z)V

    return-void

    :cond_1
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_3
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_4
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_5
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_6
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final G()V
    .locals 6

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->C()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->y()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->H()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->D()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->F()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->A()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->I()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_0

    iget-object v1, v0, Laf/a;->G:Landroid/widget/TextView;

    const v2, 0x7f060050

    invoke-static {p0, v2}, Lj3/a;->getColor(Landroid/content/Context;I)I

    move-result v3

    invoke-virtual {p0}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v4

    const v5, 0x7f07046d

    invoke-virtual {v4, v5}, Landroid/content/res/Resources;->getDimension(I)F

    move-result v4

    invoke-static {v1, v3, v4}, Lkf/a;->h(Landroid/view/View;IF)V

    iget-object v0, v0, Laf/a;->M:Landroid/widget/TextView;

    invoke-static {p0, v2}, Lj3/a;->getColor(Landroid/content/Context;I)I

    move-result v1

    invoke-virtual {p0}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v2

    invoke-virtual {v2, v5}, Landroid/content/res/Resources;->getDimension(I)F

    move-result v2

    invoke-static {v0, v1, v2}, Lkf/a;->h(Landroid/view/View;IF)V

    return-void

    :cond_0
    const-string v0, "binding"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/4 v0, 0x0

    throw v0
.end method

.method public final H()V
    .locals 4

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const/4 v1, 0x0

    const-string v2, "binding"

    if-eqz v0, :cond_5

    iget-object v0, v0, Laf/a;->E:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->A:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/JSwitch;->setCheckedSilently(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_4

    iget-object v0, v0, Laf/a;->w:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->g:I

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setMaxProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_3

    iget-object v0, v0, Laf/a;->w:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->p:I

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_2

    iget-object v0, v0, Laf/a;->w:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->A:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1

    iget-object v0, v0, Laf/a;->Q:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->A:Z

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_0

    iget-object v0, v0, Laf/a;->Q:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget v1, v1, Ldf/b;->p:I

    int-to-float v1, v1

    const/high16 v2, 0x447a0000    # 1000.0f

    div-float/2addr v1, v2

    const/16 v2, 0x64

    int-to-float v2, v2

    mul-float/2addr v1, v2

    invoke-static {v1}, Lom/a;->b(F)I

    move-result v1

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, "%"

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void

    :cond_0
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_1
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_3
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_4
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_5
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final I()V
    .locals 9

    const-string v0, "audio"

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget-boolean v1, v1, Ldf/b;->G:Z

    const/16 v2, 0x8

    const-string v3, "binding"

    const/4 v4, 0x0

    if-eqz v1, :cond_b

    iget-object v1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v1, :cond_a

    iget-object v1, v1, Laf/a;->m:Lcom/google/android/material/card/MaterialCardView;

    const/4 v5, 0x0

    invoke-virtual {v1, v5}, Landroid/view/View;->setVisibility(I)V

    const/4 v1, 0x3

    :try_start_0
    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    if-eqz v5, :cond_2

    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    if-eqz v5, :cond_0

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v6

    invoke-virtual {v6, v5}, Landroid/content/ContentResolver;->unregisterContentObserver(Landroid/database/ContentObserver;)V

    goto :goto_0

    :catch_0
    move-exception v5

    goto :goto_1

    :cond_0
    :goto_0
    iput-object v4, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    if-eqz v5, :cond_1

    invoke-virtual {v5}, Landroid/os/HandlerThread;->quit()Z

    :cond_1
    iput-object v4, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    :cond_2
    new-instance v5, Landroid/os/HandlerThread;

    const-string v6, "VolumeDetectThread"

    invoke-direct {v5, v6}, Landroid/os/HandlerThread;-><init>(Ljava/lang/String;)V

    iput-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    invoke-virtual {v5}, Ljava/lang/Thread;->start()V

    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    if-eqz v5, :cond_4

    new-instance v6, Lkf/g;

    new-instance v7, Landroid/os/Handler;

    invoke-virtual {v5}, Landroid/os/HandlerThread;->getLooper()Landroid/os/Looper;

    move-result-object v5

    invoke-direct {v7, v5}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    invoke-direct {v6, v7}, Landroid/database/ContentObserver;-><init>(Landroid/os/Handler;)V

    iput-object p0, v6, Lkf/g;->b:Lcom/jazibkhan/equalizer/ui/activities/MainActivity;

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Landroid/media/AudioManager;

    invoke-virtual {v5, v1}, Landroid/media/AudioManager;->getStreamVolume(I)I

    move-result v5

    iput v5, v6, Lkf/g;->a:I

    iput-object v6, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v5

    sget-object v6, Landroid/provider/Settings$System;->CONTENT_URI:Landroid/net/Uri;

    iget-object v7, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    invoke-static {v7}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    const/4 v8, 0x1

    invoke-virtual {v5, v6, v8, v7}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    if-eqz v5, :cond_4

    new-instance v6, Ldf/f1;

    invoke-direct {v6, p0}, Ldf/f1;-><init>(Ljava/lang/Object;)V

    iput-object v6, v5, Lkf/g;->c:Ldf/f1;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_2

    :goto_1
    iget-object v6, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    if-eqz v6, :cond_3

    invoke-virtual {v6}, Landroid/os/HandlerThread;->quit()Z

    :cond_3
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v6

    invoke-virtual {v6, v5}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_4
    :goto_2
    :try_start_1
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    instance-of v5, v0, Landroid/media/AudioManager;

    if-eqz v5, :cond_5

    check-cast v0, Landroid/media/AudioManager;

    goto :goto_3

    :catch_1
    move-exception v0

    goto :goto_4

    :cond_5
    move-object v0, v4

    :goto_3
    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->l:Landroid/media/AudioManager;

    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v5, :cond_8

    iget-object v5, v5, Laf/a;->x:Landroidx/appcompat/widget/AppCompatSeekBar;

    invoke-static {v0}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    invoke-virtual {v0, v1}, Landroid/media/AudioManager;->getStreamMaxVolume(I)I

    move-result v0

    invoke-virtual {v5, v0}, Landroid/widget/ProgressBar;->setMax(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_7

    iget-object v0, v0, Laf/a;->x:Landroidx/appcompat/widget/AppCompatSeekBar;

    iget-object v5, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->l:Landroid/media/AudioManager;

    invoke-static {v5}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    invoke-virtual {v5, v1}, Landroid/media/AudioManager;->getStreamVolume(I)I

    move-result v1

    invoke-virtual {v0, v1}, Landroid/widget/ProgressBar;->setProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_6

    iget-object v0, v0, Laf/a;->x:Landroidx/appcompat/widget/AppCompatSeekBar;

    new-instance v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;

    invoke-direct {v1, p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity$d;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v0, v1}, Landroid/widget/SeekBar;->setOnSeekBarChangeListener(Landroid/widget/SeekBar$OnSeekBarChangeListener;)V

    goto :goto_6

    :cond_6
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_7
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_8
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    :goto_4
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_9

    iget-object v0, v0, Laf/a;->m:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v0, v2}, Landroid/view/View;->setVisibility(I)V

    goto :goto_6

    :cond_9
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_a
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_b
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_e

    iget-object v0, v0, Laf/a;->m:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v0, v2}, Landroid/view/View;->setVisibility(I)V

    :try_start_2
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    if-eqz v0, :cond_c

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    invoke-virtual {v1, v0}, Landroid/content/ContentResolver;->unregisterContentObserver(Landroid/database/ContentObserver;)V
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_2

    goto :goto_5

    :catch_2
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_c
    :goto_5
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    if-eqz v0, :cond_d

    invoke-virtual {v0}, Landroid/os/HandlerThread;->quit()Z

    :cond_d
    :goto_6
    return-void

    :cond_e
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4
.end method

.method public final J()V
    .locals 8

    new-instance v0, Lcom/google/android/material/bottomsheet/BottomSheetDialog;

    invoke-direct {v0, p0}, Lcom/google/android/material/bottomsheet/BottomSheetDialog;-><init>(Landroid/content/Context;)V

    invoke-static {p0}, Landroid/view/LayoutInflater;->from(Landroid/content/Context;)Landroid/view/LayoutInflater;

    move-result-object v1

    const v2, 0x7f0a00b1

    invoke-virtual {p0, v2}, Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;

    move-result-object v2

    check-cast v2, Landroid/view/ViewGroup;

    const v3, 0x7f0d002d

    invoke-virtual {v1, v3, v2}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;

    move-result-object v1

    const v2, 0x7f0a0132

    invoke-virtual {v1, v2}, Landroid/view/View;->findViewById(I)Landroid/view/View;

    move-result-object v2

    const-string v3, "findViewById(...)"

    invoke-static {v2, v3}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v2, Lcom/google/android/material/button/MaterialButton;

    const/16 v3, 0x8

    invoke-virtual {v2, v3}, Landroid/view/View;->setVisibility(I)V

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    const/4 v4, 0x0

    const-string v5, "mPref"

    if-eqz v3, :cond_2

    const-string v6, "session_id"

    const/4 v7, 0x0

    invoke-interface {v3, v6, v7}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v3

    if-eqz v3, :cond_1

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_0

    const-string v4, "only_music_player"

    invoke-interface {v3, v4, v7}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v3

    if-nez v3, :cond_1

    invoke-virtual {v2, v7}, Landroid/view/View;->setVisibility(I)V

    const v3, 0x7f13004c

    invoke-virtual {p0, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    new-instance v3, Ldf/d1;

    invoke-direct {v3, p0, v0}, Ldf/d1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;Lcom/google/android/material/bottomsheet/BottomSheetDialog;)V

    invoke-virtual {v2, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    goto :goto_0

    :cond_0
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_1
    :goto_0
    invoke-virtual {v0, v1}, Lcom/google/android/material/bottomsheet/BottomSheetDialog;->setContentView(Landroid/view/View;)V

    invoke-virtual {v0}, Landroid/app/Dialog;->show()V

    return-void

    :cond_2
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4
.end method

.method public final K()V
    .locals 4

    invoke-static {p0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result v0

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v1, [Landroid/os/Bundle;

    const-string v2, "startEqualizerService_service_already_running"

    invoke-virtual {v0, v2, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    return-void

    :cond_0
    sget-boolean v0, Lcom/jazibkhan/equalizer/MyApplication;->b:Z

    if-nez v0, :cond_1

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v1, [Landroid/os/Bundle;

    const-string v2, "startEqualizerService_app_in_background"

    invoke-virtual {v0, v2, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    return-void

    :cond_1
    new-instance v0, Landroid/content/Intent;

    const-class v2, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-direct {v0, p0, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    const-string v2, "com.jazibkhan.foregroundservice.action.startforeground"

    invoke-virtual {v0, v2}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    iget-boolean v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->i:Z

    if-eqz v2, :cond_3

    iget-object v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz v2, :cond_2

    iget v2, v2, Lcom/jazibkhan/equalizer/services/MainForegroundService;->j:I

    goto :goto_0

    :cond_2
    move v2, v1

    :goto_0
    iput v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->j:I

    :cond_3
    const-string v2, "session_id"

    iget v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->j:I

    invoke-virtual {v0, v2, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    const-string v2, "package_name"

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->k:Ljava/lang/String;

    invoke-virtual {v0, v2, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    sget-object v2, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v2

    iget-object v2, v2, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v1, [Landroid/os/Bundle;

    const-string v3, "startForegroundServiceWithChecks_MainActivity"

    invoke-virtual {v2, v3, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    return-void
.end method

.method public final b(DLjava/lang/Integer;)V
    .locals 11

    if-eqz p3, :cond_f

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->g:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v0

    const/4 v1, 0x0

    move v2, v1

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    const/4 v4, 0x3

    const/4 v5, 0x1

    const/4 v6, 0x0

    if-eqz v3, :cond_6

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    add-int/lit8 v7, v2, 0x1

    if-ltz v2, :cond_5

    check-cast v3, Landroid/widget/TextView;

    invoke-virtual {v3}, Landroid/view/View;->getId()I

    move-result v3

    invoke-virtual {p3}, Ljava/lang/Integer;->intValue()I

    move-result v8

    if-ne v3, v8, :cond_4

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p3

    const/16 v0, 0x64

    int-to-double v7, v0

    mul-double/2addr p1, v7

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    iget-boolean p2, p3, Ldf/b;->D:Z

    if-nez p2, :cond_0

    iget-object p2, p3, Ldf/b;->m:Ljava/util/ArrayList;

    iget v0, p3, Ldf/b;->w:I

    invoke-virtual {p2, v0}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Lye/u;

    iget-object p2, p2, Lye/u;->b:Ljava/util/List;

    invoke-interface {p2, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Ljava/lang/Number;

    invoke-virtual {p2}, Ljava/lang/Number;->intValue()I

    move-result p2

    if-ne p2, p1, :cond_1

    goto/16 :goto_2

    :cond_0
    iget-object p2, p3, Ldf/b;->u:Ljava/util/ArrayList;

    invoke-interface {p2, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Ljava/lang/Number;

    invoke-virtual {p2}, Ljava/lang/Number;->intValue()I

    move-result p2

    if-ne p2, p1, :cond_1

    goto/16 :goto_2

    :cond_1
    iget-boolean p2, p3, Ldf/b;->D:Z

    if-nez p2, :cond_3

    iget-object p2, p3, Ldf/b;->m:Ljava/util/ArrayList;

    iget v0, p3, Ldf/b;->w:I

    invoke-virtual {p2, v0}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Lye/u;

    iget-object p2, p2, Lye/u;->b:Ljava/util/List;

    check-cast p2, Ljava/lang/Iterable;

    invoke-interface {p2}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_1
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_3

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    add-int/lit8 v3, v1, 0x1

    if-ltz v1, :cond_2

    check-cast v0, Ljava/lang/Number;

    invoke-virtual {v0}, Ljava/lang/Number;->intValue()I

    move-result v0

    iget-object v7, p3, Ldf/b;->u:Ljava/util/ArrayList;

    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v8

    invoke-interface {v7, v1, v8}, Ljava/util/List;->set(ILjava/lang/Object;)Ljava/lang/Object;

    invoke-static {v0, v1}, Lkf/f;->z(II)V

    move v1, v3

    goto :goto_1

    :cond_2
    invoke-static {}, Lip/w0;->o()V

    throw v6

    :cond_3
    iget-object p2, p3, Ldf/b;->m:Ljava/util/ArrayList;

    invoke-virtual {p2}, Ljava/util/ArrayList;->size()I

    move-result p2

    sub-int/2addr p2, v5

    iput p2, p3, Ldf/b;->w:I

    invoke-static {p2}, Lkf/f;->E(I)V

    iput-boolean v5, p3, Ldf/b;->D:Z

    invoke-static {v5}, Lkf/f;->y(Z)V

    iget-object p2, p3, Ldf/b;->u:Ljava/util/ArrayList;

    invoke-static {p1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v0

    invoke-interface {p2, v2, v0}, Ljava/util/List;->set(ILjava/lang/Object;)Ljava/lang/Object;

    iget-object p2, p3, Ldf/b;->l:[F

    iget v0, p3, Ldf/b;->i:I

    iget v1, p3, Ldf/b;->j:I

    sub-int/2addr p1, v0

    const/16 v3, 0xbb8

    mul-int/2addr p1, v3

    sub-int/2addr v1, v0

    div-int/2addr p1, v1

    int-to-float p1, p1

    int-to-float v0, v3

    div-float/2addr p1, v0

    aput p1, p2, v2

    iget-object p1, p3, Ldf/b;->u:Ljava/util/ArrayList;

    invoke-interface {p1, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/lang/Number;

    invoke-virtual {p1}, Ljava/lang/Number;->intValue()I

    move-result p1

    invoke-static {p1, v2}, Lkf/f;->z(II)V

    invoke-static {p3}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance p2, Ldf/z;

    invoke-direct {p2, p3, v6}, Ldf/z;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v6, v6, p2, v4}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-static {}, Ldf/b;->n()Z

    move-result p1

    if-nez p1, :cond_f

    invoke-static {v5}, Ldf/b;->s(Z)V

    invoke-virtual {p3}, Ldf/b;->x()V

    return-void

    :cond_4
    move v2, v7

    goto/16 :goto_0

    :cond_5
    invoke-static {}, Lip/w0;->o()V

    throw v6

    :cond_6
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const-string v1, "binding"

    if-eqz v0, :cond_e

    iget-object v0, v0, Laf/a;->H:Landroid/widget/TextView;

    invoke-virtual {v0}, Landroid/view/View;->getId()I

    move-result v0

    invoke-virtual {p3}, Ljava/lang/Integer;->intValue()I

    move-result v2

    const-wide v7, 0x408f400000000000L    # 1000.0

    const-string v3, "mPref"

    const-wide/high16 v9, 0x4059000000000000L    # 100.0

    if-ne v0, v2, :cond_8

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p3

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    int-to-double p1, p1

    mul-double/2addr p1, v7

    div-double/2addr p1, v9

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    iput p1, p3, Ldf/b;->q:I

    sget-object p2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p2, :cond_7

    invoke-interface {p2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p2

    const-string v0, "bbslider"

    invoke-interface {p2, v0, p1}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p2}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p3}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance p2, Ldf/q;

    invoke-direct {p2, p3, v6}, Ldf/q;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v6, v6, p2, v4}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-static {}, Ldf/b;->n()Z

    move-result p1

    if-nez p1, :cond_f

    invoke-static {v5}, Ldf/b;->s(Z)V

    invoke-virtual {p3}, Ldf/b;->x()V

    return-void

    :cond_7
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v6

    :cond_8
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_d

    iget-object v0, v0, Laf/a;->K:Landroid/widget/TextView;

    invoke-virtual {v0}, Landroid/view/View;->getId()I

    move-result v0

    invoke-virtual {p3}, Ljava/lang/Integer;->intValue()I

    move-result v2

    if-ne v0, v2, :cond_a

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p3

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    int-to-double p1, p1

    const-wide v0, 0x40c3880000000000L    # 10000.0

    mul-double/2addr p1, v0

    div-double/2addr p1, v9

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    int-to-float p1, p1

    iput p1, p3, Ldf/b;->t:F

    sget-object p2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p2, :cond_9

    invoke-interface {p2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p2

    const-string v0, "loudslider"

    invoke-interface {p2, v0, p1}, Landroid/content/SharedPreferences$Editor;->putFloat(Ljava/lang/String;F)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p2}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p3}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance p2, Ldf/f0;

    invoke-direct {p2, p3, v6}, Ldf/f0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v6, v6, p2, v4}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-static {}, Ldf/b;->n()Z

    move-result p1

    if-nez p1, :cond_f

    invoke-static {v5}, Ldf/b;->s(Z)V

    invoke-virtual {p3}, Ldf/b;->x()V

    return-void

    :cond_9
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v6

    :cond_a
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_c

    iget-object v0, v0, Laf/a;->Q:Landroid/widget/TextView;

    invoke-virtual {v0}, Landroid/view/View;->getId()I

    move-result v0

    invoke-virtual {p3}, Ljava/lang/Integer;->intValue()I

    move-result p3

    if-ne v0, p3, :cond_f

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p3

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    int-to-double p1, p1

    mul-double/2addr p1, v7

    div-double/2addr p1, v9

    invoke-static {p1, p2}, Lom/a;->a(D)I

    move-result p1

    iput p1, p3, Ldf/b;->p:I

    sget-object p2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p2, :cond_b

    invoke-interface {p2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p2

    const-string v0, "virslider"

    invoke-interface {p2, v0, p1}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p2}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p3}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance p2, Ldf/x0;

    invoke-direct {p2, p3, v6}, Ldf/x0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v6, v6, p2, v4}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-static {}, Ldf/b;->n()Z

    move-result p1

    if-nez p1, :cond_f

    invoke-static {v5}, Ldf/b;->s(Z)V

    invoke-virtual {p3}, Ldf/b;->x()V

    return-void

    :cond_b
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v6

    :cond_c
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v6

    :cond_d
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v6

    :cond_e
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v6

    :cond_f
    :goto_2
    return-void
.end method

.method public final c(Ljava/lang/String;)V
    .locals 5

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    const/4 v1, 0x0

    const-string v2, "mPref"

    if-eqz v0, :cond_5

    const-string v3, "only_music_player"

    const/4 v4, 0x0

    invoke-interface {v0, v3, v4}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    const-string v3, "binding"

    if-eqz v0, :cond_2

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_1

    const-string v2, "session_id"

    invoke-interface {v0, v2, v4}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v0

    if-nez v0, :cond_2

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_0

    iget-object p1, p1, Laf/a;->G:Landroid/widget/TextView;

    const v0, 0x7f1301fa

    invoke-virtual {p0, v0}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void

    :cond_0
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_1
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->k:Ljava/lang/String;

    if-eqz p1, :cond_4

    const-string v0, ""

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_4

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_3

    iget-object v0, v0, Laf/a;->G:Landroid/widget/TextView;

    invoke-static {p0, p1}, Lkf/a;->c(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void

    :cond_3
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_4
    return-void

    :cond_5
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final d()V
    .locals 5

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    invoke-static {}, Lkf/f;->b()Z

    move-result v1

    iput-boolean v1, v0, Ldf/b;->y:Z

    invoke-static {}, Lkf/f;->f()Z

    move-result v1

    iput-boolean v1, v0, Ldf/b;->x:Z

    invoke-static {}, Lkf/f;->p()Z

    move-result v1

    iput-boolean v1, v0, Ldf/b;->A:Z

    invoke-static {}, Lkf/f;->l()Z

    move-result v1

    iput-boolean v1, v0, Ldf/b;->B:Z

    invoke-static {}, Lkf/f;->d()Z

    move-result v1

    iput-boolean v1, v0, Ldf/b;->C:Z

    invoke-static {}, Lkf/f;->i()Z

    move-result v1

    iput-boolean v1, v0, Ldf/b;->z:Z

    invoke-static {v0}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object v1

    new-instance v2, Ldf/o0;

    const/4 v3, 0x0

    invoke-direct {v2, v0, v3}, Ldf/o0;-><init>(Ldf/b;Lbm/e;)V

    const/4 v4, 0x3

    invoke-static {v1, v3, v3, v2, v4}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {v0}, Ldf/b;->x()V

    return-void
.end method

.method public final e(Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;)V
    .locals 0

    return-void
.end method

.method public final f(Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;IZ)V
    .locals 4

    if-eqz p3, :cond_8

    iget-object p3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const-string v0, "binding"

    const/4 v1, 0x0

    if-eqz p3, :cond_7

    iget-object p3, p3, Laf/a;->s:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p1, p3}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result p3

    const/4 v2, 0x3

    const-string v3, "mPref"

    if-eqz p3, :cond_1

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput p2, p1, Ldf/b;->q:I

    sget-object p3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p3, :cond_0

    invoke-interface {p3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p3

    const-string v0, "bbslider"

    invoke-interface {p3, v0, p2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p3

    new-instance v0, Ldf/p;

    invoke-direct {v0, p1, p2, v1}, Ldf/p;-><init>(Ldf/b;ILbm/e;)V

    invoke-static {p3, v1, v1, v0, v2}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_0
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_1
    iget-object p3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p3, :cond_6

    iget-object p3, p3, Laf/a;->u:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p1, p3}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result p3

    if-eqz p3, :cond_3

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    int-to-float p3, p2

    iput p3, p1, Ldf/b;->t:F

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_2

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v3, "loudslider"

    invoke-interface {v0, v3, p3}, Landroid/content/SharedPreferences$Editor;->putFloat(Ljava/lang/String;F)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p3

    new-instance v0, Ldf/e0;

    invoke-direct {v0, p1, p2, v1}, Ldf/e0;-><init>(Ldf/b;ILbm/e;)V

    invoke-static {p3, v1, v1, v0, v2}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_2
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_3
    iget-object p3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p3, :cond_5

    iget-object p3, p3, Laf/a;->w:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p1, p3}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_8

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput p2, p1, Ldf/b;->p:I

    sget-object p3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p3, :cond_4

    invoke-interface {p3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p3

    const-string v0, "virslider"

    invoke-interface {p3, v0, p2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p3

    new-instance v0, Ldf/w0;

    invoke-direct {v0, p1, p2, v1}, Ldf/w0;-><init>(Ldf/b;ILbm/e;)V

    invoke-static {p3, v1, v1, v0, v2}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_4
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_5
    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_6
    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_7
    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_8
    return-void
.end method

.method public final h()V
    .locals 3

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-object v0, v0, Ldf/b;->L:Llp/z0;

    sget-object v1, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    const/4 v2, 0x0

    invoke-virtual {v0, v2, v1}, Llp/z0;->i(Ljava/lang/Object;Ljava/lang/Object;)Z

    return-void
.end method

.method public final i(Lye/c;Lze/a;)V
    .locals 4

    const-string v0, "preset"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "selectedAudioDevice"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    invoke-virtual {v0}, Ldf/b;->u()V

    invoke-static {v0}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object v1

    new-instance v2, Ldf/h0;

    const/4 v3, 0x0

    invoke-direct {v2, v0, p2, p1, v3}, Ldf/h0;-><init>(Ldf/b;Lze/a;Lye/c;Lbm/e;)V

    const/4 p2, 0x3

    invoke-static {v1, v3, v3, v2, p2}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Lye/c;->g()I

    move-result p1

    invoke-virtual {v0, p1}, Ldf/b;->t(I)V

    return-void
.end method

.method public final k(Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;)V
    .locals 1

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {}, Ldf/b;->n()Z

    move-result v0

    if-nez v0, :cond_0

    const/4 v0, 0x1

    invoke-static {v0}, Ldf/b;->s(Z)V

    invoke-virtual {p1}, Ldf/b;->x()V

    :cond_0
    return-void
.end method

.method public final onCheckedChanged(Landroid/widget/CompoundButton;Z)V
    .locals 7

    const-string v0, "buttonView"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const-string v1, "binding"

    const/4 v2, 0x0

    if-eqz v0, :cond_19

    iget-object v0, v0, Laf/a;->B:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v0

    const/4 v3, 0x3

    const-string v4, "mPref"

    if-eqz v0, :cond_7

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput-boolean p2, p1, Ldf/b;->x:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_6

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "eqswitch"

    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object v0

    new-instance v1, Ldf/w;

    invoke-direct {v1, p1, v2}, Ldf/w;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {v0, v2, v2, v1, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Ldf/b;->x()V

    if-eqz p2, :cond_11

    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_5

    const-string p2, "eq_switch_on_count"

    const/4 v0, 0x0

    invoke-interface {p1, p2, v0}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result p1

    const/4 v1, 0x1

    add-int/2addr p1, v1

    sget-object v3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v3, :cond_4

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    invoke-interface {v3, p2, p1}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v3}, Landroid/content/SharedPreferences$Editor;->apply()V

    const/4 p2, 0x2

    if-ne p1, p2, :cond_11

    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_3

    const-string p2, "battery_opt_requested"

    invoke-interface {p1, p2, v0}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result p1

    if-eqz p1, :cond_0

    goto/16 :goto_0

    :cond_0
    const-string p1, "power"

    invoke-virtual {p0, p1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    const-string v0, "null cannot be cast to non-null type android.os.PowerManager"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->d(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast p1, Landroid/os/PowerManager;

    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Landroid/os/PowerManager;->isIgnoringBatteryOptimizations(Ljava/lang/String;)Z

    move-result p1

    if-eqz p1, :cond_1

    goto/16 :goto_0

    :cond_1
    new-instance p1, Lcom/google/android/material/bottomsheet/BottomSheetDialog;

    invoke-direct {p1, p0}, Lcom/google/android/material/bottomsheet/BottomSheetDialog;-><init>(Landroid/content/Context;)V

    invoke-static {p0}, Landroid/view/LayoutInflater;->from(Landroid/content/Context;)Landroid/view/LayoutInflater;

    move-result-object v0

    const v3, 0x7f0d002b

    invoke-virtual {v0, v3, v2}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;

    move-result-object v0

    const v3, 0x7f0a00c7

    invoke-virtual {v0, v3}, Landroid/view/View;->findViewById(I)Landroid/view/View;

    move-result-object v3

    check-cast v3, Lcom/google/android/material/button/MaterialButton;

    new-instance v5, Ldf/x1;

    const/4 v6, 0x0

    invoke-direct {v5, p1, p0, v6}, Ldf/x1;-><init>(Landroid/view/KeyEvent$Callback;Landroid/view/KeyEvent$Callback;I)V

    invoke-virtual {v3, v5}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-virtual {p1, v0}, Lcom/google/android/material/bottomsheet/BottomSheetDialog;->setContentView(Landroid/view/View;)V

    invoke-virtual {p1}, Landroid/app/Dialog;->show()V

    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_2

    invoke-static {p1, p2, v1}, Lcom/google/android/gms/measurement/internal/a;->b(Landroid/content/SharedPreferences;Ljava/lang/String;Z)V

    goto/16 :goto_0

    :cond_2
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_3
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_4
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_5
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_6
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_7
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_18

    iget-object v0, v0, Laf/a;->z:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_9

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput-boolean p2, p1, Ldf/b;->y:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_8

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "bbswitch"

    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/m;

    invoke-direct {v0, p1, v2}, Ldf/m;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v2, v2, v0, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Ldf/b;->x()V

    goto/16 :goto_0

    :cond_8
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_9
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_17

    iget-object v0, v0, Laf/a;->C:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_b

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput-boolean p2, p1, Ldf/b;->z:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_a

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "loudswitch"

    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/c0;

    invoke-direct {v0, p1, v2}, Ldf/c0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v2, v2, v0, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Ldf/b;->x()V

    goto/16 :goto_0

    :cond_a
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_b
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_16

    iget-object v0, v0, Laf/a;->E:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_d

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput-boolean p2, p1, Ldf/b;->A:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_c

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "virswitch"

    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/v0;

    invoke-direct {v0, p1, v2}, Ldf/v0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v2, v2, v0, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Ldf/b;->x()V

    goto :goto_0

    :cond_c
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_d
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_15

    iget-object v0, v0, Laf/a;->D:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_f

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput-boolean p2, p1, Ldf/b;->B:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_e

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "reverbswitch"

    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/k0;

    invoke-direct {v0, p1, v2}, Ldf/k0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v2, v2, v0, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Ldf/b;->x()V

    goto :goto_0

    :cond_e
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_f
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_14

    iget-object v0, v0, Laf/a;->A:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p1, v0}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_11

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput-boolean p2, p1, Ldf/b;->C:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_10

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "channel_bal_switch"

    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/r;

    invoke-direct {v0, p1, v2}, Ldf/r;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v2, v2, v0, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p1}, Ldf/b;->x()V

    goto :goto_0

    :cond_10
    invoke-static {v4}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_11
    :goto_0
    sget p1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 p2, 0x21

    if-lt p1, p2, :cond_13

    iget-boolean p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->q:Z

    if-nez p1, :cond_13

    const-string p1, "android.permission.POST_NOTIFICATIONS"

    invoke-static {p0, p1}, Lmi/i;->a(Landroid/content/Context;Ljava/lang/String;)Z

    move-result p1

    if-nez p1, :cond_13

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->p:Lmi/d;

    if-eqz p1, :cond_12

    invoke-virtual {p1}, Lmi/d;->b()V

    return-void

    :cond_12
    const-string p1, "notificationPermissionRequester"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_13
    return-void

    :cond_14
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_15
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_16
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_17
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_18
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_19
    invoke-static {v1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2
.end method

.method public final onCreate(Landroid/os/Bundle;)V
    .locals 53

    move-object/from16 v1, p0

    invoke-super/range {p0 .. p1}, Ldf/a;->onCreate(Landroid/os/Bundle;)V

    invoke-virtual {v1}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v0

    const-string v2, "getIntent(...)"

    invoke-static {v0, v2}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {v1, v0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->onNewIntent(Landroid/content/Intent;)V

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x21

    if-lt v0, v2, :cond_0

    new-instance v0, Lmi/d;

    invoke-direct {v0, v1}, Lmi/d;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    new-instance v2, Ldf/n1;

    const/4 v3, 0x0

    invoke-direct {v2, v1, v3}, Ldf/n1;-><init>(Ljava/lang/Object;I)V

    iput-object v2, v0, Lmi/d;->e:Ldf/n1;

    new-instance v2, Ldf/r1;

    invoke-direct {v2, v1}, Ldf/r1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    iput-object v2, v0, Lmi/d;->f:Ldf/r1;

    new-instance v2, Ldf/s1;

    invoke-direct {v2, v1}, Ldf/s1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    iput-object v2, v0, Lmi/d;->g:Ldf/s1;

    iput-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->p:Lmi/d;

    :cond_0
    invoke-virtual {v1}, Landroid/app/Activity;->getLayoutInflater()Landroid/view/LayoutInflater;

    move-result-object v0

    const v2, 0x7f0d0021

    const/4 v3, 0x0

    const/4 v4, 0x0

    invoke-virtual {v0, v2, v3, v4}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;

    move-result-object v0

    const v2, 0x7f0a0098

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    check-cast v5, Lcom/zipoapps/ads/banner/PhShimmerBannerAdView;

    const-string v6, "Missing required view with ID: "

    if-eqz v5, :cond_3b

    const v2, 0x7f0a00d0

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v9, v5

    check-cast v9, Landroid/widget/TextView;

    if-eqz v9, :cond_3b

    const v2, 0x7f0a00ea

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v10, v5

    check-cast v10, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v10, :cond_3b

    const v2, 0x7f0a00eb

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v11, v5

    check-cast v11, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v11, :cond_3b

    const v2, 0x7f0a00ec

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v12, v5

    check-cast v12, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v12, :cond_3b

    const v2, 0x7f0a00ed

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v13, v5

    check-cast v13, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v13, :cond_3b

    const v2, 0x7f0a00ee

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v14, v5

    check-cast v14, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v14, :cond_3b

    const v2, 0x7f0a00f0

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object v15, v5

    check-cast v15, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v15, :cond_3b

    const v2, 0x7f0a00f1

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v16, v5

    check-cast v16, Landroidx/constraintlayout/widget/ConstraintLayout;

    if-eqz v16, :cond_3b

    const v2, 0x7f0a0114

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v17, v5

    check-cast v17, Lcom/jazibkhan/equalizer/views/Curve;

    if-eqz v17, :cond_3b

    const v2, 0x7f0a0118

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    check-cast v5, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v5, :cond_3b

    const v2, 0x7f0a0119

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v18, v5

    check-cast v18, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v18, :cond_3b

    const v2, 0x7f0a011a

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    check-cast v5, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v5, :cond_3b

    const v2, 0x7f0a011b

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    check-cast v5, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v5, :cond_3b

    const v2, 0x7f0a011d

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v19, v5

    check-cast v19, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v19, :cond_3b

    const v2, 0x7f0a011e

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    check-cast v5, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v5, :cond_3b

    const v2, 0x7f0a011f

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v20, v5

    check-cast v20, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v20, :cond_3b

    const v2, 0x7f0a016f

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v21, v5

    check-cast v21, Lcom/google/android/material/card/MaterialCardView;

    if-eqz v21, :cond_3b

    const v2, 0x7f0a0170

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    check-cast v5, Landroid/widget/LinearLayout;

    if-eqz v5, :cond_3b

    const v2, 0x7f0a0171

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    move-object/from16 v22, v5

    check-cast v22, Landroid/widget/TextView;

    if-eqz v22, :cond_3b

    const v2, 0x7f0a0175

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v5

    if-eqz v5, :cond_3b

    move-object v2, v5

    check-cast v2, Lcom/google/android/material/card/MaterialCardView;

    const v7, 0x7f0a0360

    invoke-static {v7, v5}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v8

    check-cast v8, Lcom/google/android/material/button/MaterialButton;

    if-eqz v8, :cond_3a

    const v7, 0x7f0a0361

    invoke-static {v7, v5}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v23

    move-object/from16 v7, v23

    check-cast v7, Lcom/google/android/material/progressindicator/CircularProgressIndicator;

    if-eqz v7, :cond_39

    const v3, 0x7f0a043b

    invoke-static {v3, v5}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v23

    check-cast v23, Landroid/widget/TextView;

    if-eqz v23, :cond_38

    new-instance v5, Laf/m;

    invoke-direct {v5, v2, v2, v8, v7}, Laf/m;-><init>(Lcom/google/android/material/card/MaterialCardView;Lcom/google/android/material/card/MaterialCardView;Lcom/google/android/material/button/MaterialButton;Lcom/google/android/material/progressindicator/CircularProgressIndicator;)V

    const v2, 0x7f0a01d4

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v2

    check-cast v2, Landroidx/constraintlayout/widget/Guideline;

    const v2, 0x7f0a0249

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v24, v7

    check-cast v24, Landroid/widget/LinearLayout;

    if-eqz v24, :cond_3b

    const v2, 0x7f0a024a

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    check-cast v7, Landroid/widget/LinearLayout;

    if-eqz v7, :cond_3b

    const v2, 0x7f0a0300

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v25, v7

    check-cast v25, Lcom/google/android/material/button/MaterialButton;

    if-eqz v25, :cond_3b

    const v2, 0x7f0a037e

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v26, v7

    check-cast v26, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    if-eqz v26, :cond_3b

    const v2, 0x7f0a037f

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v27, v7

    check-cast v27, Lcom/jazibkhan/equalizer/views/MidSeekBar;

    if-eqz v27, :cond_3b

    const v2, 0x7f0a0380

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v28, v7

    check-cast v28, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    if-eqz v28, :cond_3b

    const v2, 0x7f0a0381

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v29, v7

    check-cast v29, Landroidx/appcompat/widget/AppCompatSeekBar;

    if-eqz v29, :cond_3b

    const v2, 0x7f0a0382

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v30, v7

    check-cast v30, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    if-eqz v30, :cond_3b

    const v2, 0x7f0a0383

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v31, v7

    check-cast v31, Landroidx/appcompat/widget/AppCompatSeekBar;

    if-eqz v31, :cond_3b

    const v2, 0x7f0a038a

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    check-cast v7, Landroidx/core/widget/NestedScrollView;

    if-eqz v7, :cond_3b

    const v2, 0x7f0a03b7

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v32, v7

    check-cast v32, Landroidx/appcompat/widget/AppCompatSpinner;

    if-eqz v32, :cond_3b

    const v2, 0x7f0a03d6

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v33, v7

    check-cast v33, Lcom/jazibkhan/equalizer/views/JSwitch;

    if-eqz v33, :cond_3b

    const v2, 0x7f0a03d7

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v34, v7

    check-cast v34, Lcom/jazibkhan/equalizer/views/JSwitch;

    if-eqz v34, :cond_3b

    const v2, 0x7f0a03d8

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v35, v7

    check-cast v35, Lcom/jazibkhan/equalizer/views/JSwitch;

    if-eqz v35, :cond_3b

    const v2, 0x7f0a03d9

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v36, v7

    check-cast v36, Lcom/jazibkhan/equalizer/views/JSwitch;

    if-eqz v36, :cond_3b

    const v2, 0x7f0a03da

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v37, v7

    check-cast v37, Lcom/jazibkhan/equalizer/views/JSwitch;

    if-eqz v37, :cond_3b

    const v2, 0x7f0a03db

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v38, v7

    check-cast v38, Lcom/jazibkhan/equalizer/views/JSwitch;

    if-eqz v38, :cond_3b

    const v2, 0x7f0a0408

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v39, v7

    check-cast v39, Lcom/google/android/material/appbar/MaterialToolbar;

    if-eqz v39, :cond_3b

    const v2, 0x7f0a0431

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v40, v7

    check-cast v40, Landroid/widget/TextView;

    if-eqz v40, :cond_3b

    const v2, 0x7f0a0434

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    move-object/from16 v41, v7

    check-cast v41, Landroid/widget/TextView;

    if-eqz v41, :cond_3b

    const v2, 0x7f0a0435

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    check-cast v7, Landroid/widget/TextView;

    if-eqz v7, :cond_3b

    const v2, 0x7f0a0439

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v7

    check-cast v7, Landroid/widget/TextView;

    if-eqz v7, :cond_3b

    invoke-static {v3, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v2

    check-cast v2, Landroid/widget/TextView;

    if-eqz v2, :cond_37

    const v2, 0x7f0a043d

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v42, v3

    check-cast v42, Landroid/widget/TextView;

    if-eqz v42, :cond_3b

    const v2, 0x7f0a043e

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v43, v3

    check-cast v43, Landroid/widget/TextView;

    if-eqz v43, :cond_3b

    const v2, 0x7f0a0440

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v44, v3

    check-cast v44, Landroid/widget/TextView;

    if-eqz v44, :cond_3b

    const v2, 0x7f0a0441

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    check-cast v3, Landroid/widget/TextView;

    if-eqz v3, :cond_3b

    const v2, 0x7f0a0443

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v45, v3

    check-cast v45, Landroid/widget/TextView;

    if-eqz v45, :cond_3b

    const v2, 0x7f0a0444

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v46, v3

    check-cast v46, Landroid/widget/TextView;

    if-eqz v46, :cond_3b

    const v2, 0x7f0a0445

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v47, v3

    check-cast v47, Landroid/widget/TextView;

    if-eqz v47, :cond_3b

    const v2, 0x7f0a0446

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v48, v3

    check-cast v48, Landroid/widget/TextView;

    if-eqz v48, :cond_3b

    const v2, 0x7f0a0448

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v49, v3

    check-cast v49, Landroid/widget/TextView;

    if-eqz v49, :cond_3b

    const v2, 0x7f0a0449

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    check-cast v3, Landroid/widget/TextView;

    if-eqz v3, :cond_3b

    const v2, 0x7f0a0451

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v50, v3

    check-cast v50, Landroid/widget/TextView;

    if-eqz v50, :cond_3b

    const v2, 0x7f0a0452

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    check-cast v3, Landroid/widget/TextView;

    if-eqz v3, :cond_3b

    const v2, 0x7f0a0453

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    check-cast v3, Landroid/widget/TextView;

    if-eqz v3, :cond_3b

    const v2, 0x7f0a0479

    invoke-static {v2, v0}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v3

    move-object/from16 v51, v3

    check-cast v51, Lcom/google/android/material/button/MaterialButton;

    if-eqz v51, :cond_3b

    new-instance v7, Laf/a;

    move-object v8, v0

    check-cast v8, Landroidx/constraintlayout/widget/ConstraintLayout;

    move-object/from16 v23, v5

    invoke-direct/range {v7 .. v51}, Laf/a;-><init>(Landroidx/constraintlayout/widget/ConstraintLayout;Landroid/widget/TextView;Landroidx/constraintlayout/widget/ConstraintLayout;Landroidx/constraintlayout/widget/ConstraintLayout;Landroidx/constraintlayout/widget/ConstraintLayout;Landroidx/constraintlayout/widget/ConstraintLayout;Landroidx/constraintlayout/widget/ConstraintLayout;Landroidx/constraintlayout/widget/ConstraintLayout;Landroidx/constraintlayout/widget/ConstraintLayout;Lcom/jazibkhan/equalizer/views/Curve;Lcom/google/android/material/card/MaterialCardView;Lcom/google/android/material/card/MaterialCardView;Lcom/google/android/material/card/MaterialCardView;Lcom/google/android/material/card/MaterialCardView;Landroid/widget/TextView;Laf/m;Landroid/widget/LinearLayout;Lcom/google/android/material/button/MaterialButton;Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;Lcom/jazibkhan/equalizer/views/MidSeekBar;Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;Landroidx/appcompat/widget/AppCompatSeekBar;Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;Landroidx/appcompat/widget/AppCompatSeekBar;Landroidx/appcompat/widget/AppCompatSpinner;Lcom/jazibkhan/equalizer/views/JSwitch;Lcom/jazibkhan/equalizer/views/JSwitch;Lcom/jazibkhan/equalizer/views/JSwitch;Lcom/jazibkhan/equalizer/views/JSwitch;Lcom/jazibkhan/equalizer/views/JSwitch;Lcom/jazibkhan/equalizer/views/JSwitch;Lcom/google/android/material/appbar/MaterialToolbar;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Landroid/widget/TextView;Lcom/google/android/material/button/MaterialButton;)V

    iput-object v7, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    iget-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const-string v2, "binding"

    if-eqz v0, :cond_36

    iget-object v0, v0, Laf/a;->a:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v1, v0}, Landroidx/appcompat/app/AppCompatActivity;->setContentView(Landroid/view/View;)V

    iget-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_35

    iget-object v0, v0, Laf/a;->F:Lcom/google/android/material/appbar/MaterialToolbar;

    invoke-virtual {v1, v0}, Landroidx/appcompat/app/AppCompatActivity;->setSupportActionBar(Landroidx/appcompat/widget/Toolbar;)V

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-nez v0, :cond_1

    invoke-virtual {v1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0}, Lk7/f;->a(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    const-string v3, "getDefaultSharedPreferences(...)"

    invoke-static {v0, v3}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    :cond_1
    invoke-static {v1}, Lk7/f;->b(Landroid/content/Context;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v1, v0, v4}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0, v1}, Landroid/content/SharedPreferences;->registerOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    :try_start_0
    invoke-static {v1}, Ly4/a;->a(Landroid/content/Context;)Ly4/a;

    move-result-object v0

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->s:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;

    new-instance v5, Landroid/content/IntentFilter;

    const-string v7, "main_activity_broadcast"

    invoke-direct {v5, v7}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0, v3, v5}, Ly4/a;->b(Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;Landroid/content/IntentFilter;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    invoke-virtual {v0}, Ljava/lang/Throwable;->printStackTrace()V

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v3

    invoke-virtual {v3, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_0
    iget-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->clear()V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->f:Ljava/util/ArrayList;

    invoke-virtual {v3}, Ljava/util/ArrayList;->clear()V

    iget-object v5, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->g:Ljava/util/ArrayList;

    invoke-virtual {v5}, Ljava/util/ArrayList;->clear()V

    new-instance v7, Ljava/util/ArrayList;

    invoke-direct {v7}, Ljava/util/ArrayList;-><init>()V

    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v8

    iget v8, v8, Ldf/b;->k:I

    move v9, v4

    :goto_1
    if-ge v9, v8, :cond_5

    new-instance v10, Llf/b;

    const/4 v11, 0x0

    invoke-direct {v10, v1, v11, v4}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;I)V

    invoke-static {v1}, Landroid/view/LayoutInflater;->from(Landroid/content/Context;)Landroid/view/LayoutInflater;

    move-result-object v11

    const v12, 0x7f0d0117

    const/4 v13, 0x1

    invoke-virtual {v11, v12, v10, v13}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;

    invoke-virtual {v10}, Landroid/view/View;->getRootView()Landroid/view/View;

    move-result-object v11

    const v12, 0x7f0a02e3

    invoke-static {v12, v11}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v14

    check-cast v14, Landroidx/appcompat/widget/AppCompatSeekBar;

    if-eqz v14, :cond_4

    const v12, 0x7f0a0438

    invoke-static {v12, v11}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v15

    check-cast v15, Landroid/widget/TextView;

    if-eqz v15, :cond_4

    const v12, 0x7f0a044f

    invoke-static {v12, v11}, Landroidx/appcompat/widget/p;->a(ILandroid/view/View;)Landroid/view/View;

    move-result-object v16

    move-object/from16 v12, v16

    check-cast v12, Landroid/widget/TextView;

    if-eqz v12, :cond_3

    new-instance v4, Laf/n;

    invoke-direct {v4, v11, v14, v15, v12}, Laf/n;-><init>(Landroid/view/View;Landroidx/appcompat/widget/AppCompatSeekBar;Landroid/widget/TextView;Landroid/widget/TextView;)V

    invoke-virtual {v10, v4}, Llf/b;->setBinding(Laf/n;)V

    invoke-virtual {v10, v13}, Landroid/widget/LinearLayout;->setOrientation(I)V

    new-instance v4, Landroid/widget/LinearLayout$LayoutParams;

    const/4 v11, -0x1

    const/high16 v12, 0x3f800000    # 1.0f

    const/4 v13, 0x0

    invoke-direct {v4, v13, v11, v12}, Landroid/widget/LinearLayout$LayoutParams;-><init>(IIF)V

    invoke-virtual {v10, v4}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    iget-object v4, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v4, :cond_2

    iget-object v4, v4, Laf/a;->q:Landroid/widget/LinearLayout;

    invoke-virtual {v4, v10}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V

    invoke-virtual {v10}, Llf/b;->getEqSlider()Landroidx/appcompat/widget/AppCompatSeekBar;

    move-result-object v4

    invoke-static {}, Landroid/view/View;->generateViewId()I

    move-result v11

    invoke-virtual {v4, v11}, Landroid/view/View;->setId(I)V

    invoke-virtual {v0, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-virtual {v10}, Llf/b;->getFreqText()Landroid/widget/TextView;

    move-result-object v4

    invoke-static {}, Landroid/view/View;->generateViewId()I

    move-result v11

    invoke-virtual {v4, v11}, Landroid/view/View;->setId(I)V

    invoke-virtual {v3, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-virtual {v10}, Llf/b;->getGainText()Landroid/widget/TextView;

    move-result-object v4

    invoke-static {}, Landroid/view/View;->generateViewId()I

    move-result v11

    invoke-virtual {v4, v11}, Landroid/view/View;->setId(I)V

    invoke-virtual {v5, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-static {}, Landroid/view/View;->generateViewId()I

    move-result v4

    invoke-virtual {v10, v4}, Landroid/view/View;->setId(I)V

    invoke-virtual {v7, v10}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    add-int/lit8 v9, v9, 0x1

    const/4 v4, 0x0

    goto/16 :goto_1

    :cond_2
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/16 v52, 0x0

    throw v52

    :cond_3
    const v12, 0x7f0a044f

    :cond_4
    invoke-virtual {v11}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {v0, v12}, Landroid/content/res/Resources;->getResourceName(I)Ljava/lang/String;

    move-result-object v0

    new-instance v2, Ljava/lang/NullPointerException;

    invoke-virtual {v6, v0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {v2, v0}, Ljava/lang/NullPointerException;-><init>(Ljava/lang/String;)V

    throw v2

    :cond_5
    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->k:I

    const/4 v4, 0x5

    if-gt v3, v4, :cond_b

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_a

    iget-object v3, v3, Laf/a;->q:Landroid/widget/LinearLayout;

    invoke-virtual {v3}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v3

    instance-of v4, v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    if-eqz v4, :cond_6

    check-cast v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    goto :goto_2

    :cond_6
    const/4 v3, 0x0

    :goto_2
    const v4, 0x7f07044b

    if-eqz v3, :cond_7

    invoke-virtual {v1}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v6

    invoke-virtual {v6, v4}, Landroid/content/res/Resources;->getDimensionPixelOffset(I)I

    move-result v6

    invoke-virtual {v3, v6}, Landroid/view/ViewGroup$MarginLayoutParams;->setMarginStart(I)V

    :cond_7
    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_9

    iget-object v3, v3, Laf/a;->q:Landroid/widget/LinearLayout;

    invoke-virtual {v3}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v3

    instance-of v6, v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    if-eqz v6, :cond_8

    check-cast v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    goto :goto_3

    :cond_8
    const/4 v3, 0x0

    :goto_3
    if-eqz v3, :cond_f

    invoke-virtual {v1}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v6

    invoke-virtual {v6, v4}, Landroid/content/res/Resources;->getDimensionPixelOffset(I)I

    move-result v4

    invoke-virtual {v3, v4}, Landroid/view/ViewGroup$MarginLayoutParams;->setMarginEnd(I)V

    goto :goto_6

    :cond_9
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/16 v52, 0x0

    throw v52

    :cond_a
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_b
    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_34

    iget-object v3, v3, Laf/a;->q:Landroid/widget/LinearLayout;

    invoke-virtual {v3}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v3

    instance-of v4, v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    if-eqz v4, :cond_c

    check-cast v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    goto :goto_4

    :cond_c
    const/4 v3, 0x0

    :goto_4
    const v4, 0x7f07046c

    if-eqz v3, :cond_d

    invoke-virtual {v1}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v6

    invoke-virtual {v6, v4}, Landroid/content/res/Resources;->getDimensionPixelOffset(I)I

    move-result v6

    invoke-virtual {v3, v6}, Landroid/view/ViewGroup$MarginLayoutParams;->setMarginStart(I)V

    :cond_d
    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_33

    iget-object v3, v3, Laf/a;->q:Landroid/widget/LinearLayout;

    invoke-virtual {v3}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v3

    instance-of v6, v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    if-eqz v6, :cond_e

    check-cast v3, Landroidx/constraintlayout/widget/ConstraintLayout$b;

    goto :goto_5

    :cond_e
    const/4 v3, 0x0

    :goto_5
    if-eqz v3, :cond_f

    invoke-virtual {v1}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v6

    invoke-virtual {v6, v4}, Landroid/content/res/Resources;->getDimensionPixelOffset(I)I

    move-result v4

    invoke-virtual {v3, v4}, Landroid/view/ViewGroup$MarginLayoutParams;->setMarginEnd(I)V

    :cond_f
    :goto_6
    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_32

    iget-object v3, v3, Laf/a;->q:Landroid/widget/LinearLayout;

    invoke-virtual {v3}, Landroid/view/View;->isLaidOut()Z

    move-result v4

    if-eqz v4, :cond_13

    invoke-virtual {v3}, Landroid/view/View;->isLayoutRequested()Z

    move-result v4

    if-nez v4, :cond_13

    const/4 v13, 0x0

    invoke-virtual {v7, v13}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/view/View;

    invoke-virtual {v4}, Landroid/view/View;->isLaidOut()Z

    move-result v6

    if-eqz v6, :cond_12

    invoke-virtual {v4}, Landroid/view/View;->isLayoutRequested()Z

    move-result v6

    if-nez v6, :cond_12

    iget-object v6, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v6, :cond_11

    iget-object v6, v6, Laf/a;->j:Lcom/jazibkhan/equalizer/views/Curve;

    invoke-virtual {v6}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup$LayoutParams;

    move-result-object v6

    invoke-virtual {v3}, Landroid/view/View;->getWidth()I

    move-result v3

    invoke-virtual {v4}, Landroid/view/View;->getWidth()I

    move-result v4

    sub-int/2addr v3, v4

    iput v3, v6, Landroid/view/ViewGroup$LayoutParams;->width:I

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_10

    iget-object v3, v3, Laf/a;->j:Lcom/jazibkhan/equalizer/views/Curve;

    invoke-virtual {v3}, Landroid/view/View;->requestLayout()V

    goto :goto_7

    :cond_10
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/16 v52, 0x0

    throw v52

    :cond_11
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_12
    new-instance v6, Ldf/c2;

    invoke-direct {v6, v1, v3}, Ldf/c2;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;Landroid/view/View;)V

    invoke-virtual {v4, v6}, Landroid/view/View;->addOnLayoutChangeListener(Landroid/view/View$OnLayoutChangeListener;)V

    goto :goto_7

    :cond_13
    const/4 v13, 0x0

    new-instance v4, Ldf/b2;

    invoke-direct {v4, v7, v1}, Ldf/b2;-><init>(Ljava/util/ArrayList;Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v3, v4}, Landroid/view/View;->addOnLayoutChangeListener(Landroid/view/View$OnLayoutChangeListener;)V

    :goto_7
    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->G()V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_31

    iget-object v3, v3, Laf/a;->e:Landroidx/constraintlayout/widget/ConstraintLayout;

    const/4 v4, 0x4

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_30

    iget-object v3, v3, Laf/a;->f:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_2f

    iget-object v3, v3, Laf/a;->c:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_2e

    iget-object v3, v3, Laf/a;->h:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_2d

    iget-object v3, v3, Laf/a;->g:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_2c

    iget-object v3, v3, Laf/a;->d:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_2b

    iget-object v3, v3, Laf/a;->i:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3, v4}, Landroid/view/View;->setVisibility(I)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_2a

    iget-object v3, v3, Laf/a;->E:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/JSwitch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_29

    iget-object v3, v3, Laf/a;->z:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/JSwitch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_28

    iget-object v3, v3, Laf/a;->C:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/JSwitch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_27

    iget-object v3, v3, Laf/a;->B:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/JSwitch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_26

    iget-object v3, v3, Laf/a;->D:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/JSwitch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_25

    iget-object v3, v3, Laf/a;->A:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/JSwitch;->setOnCheckedChangeListener(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_24

    iget-object v3, v3, Laf/a;->v:Landroidx/appcompat/widget/AppCompatSeekBar;

    invoke-virtual {v3, v1}, Landroid/widget/SeekBar;->setOnSeekBarChangeListener(Landroid/widget/SeekBar$OnSeekBarChangeListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_23

    iget-object v3, v3, Laf/a;->t:Lcom/jazibkhan/equalizer/views/MidSeekBar;

    new-instance v4, Ldf/z1;

    invoke-direct {v4, v1}, Ldf/z1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v3, v4}, Lcom/jazibkhan/equalizer/views/MidSeekBar;->setOnSeekbarListener(Lcom/jazibkhan/equalizer/views/MidSeekBar$a;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_22

    iget-object v3, v3, Laf/a;->b:Landroid/widget/TextView;

    new-instance v4, Ldf/g1;

    invoke-direct {v4, v1}, Ldf/g1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v3, v4}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_21

    iget-object v3, v3, Laf/a;->G:Landroid/widget/TextView;

    new-instance v4, Ldf/h1;

    invoke-direct {v4, v1}, Ldf/h1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v3, v4}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_20

    iget-object v3, v3, Laf/a;->M:Landroid/widget/TextView;

    new-instance v4, Ldf/i1;

    const/4 v6, 0x0

    invoke-direct {v4, v1, v6}, Ldf/i1;-><init>(Ljava/lang/Object;I)V

    invoke-virtual {v3, v4}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_1f

    iget-object v3, v3, Laf/a;->w:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setOnProgressChangedListener(Lmf/c;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_1e

    iget-object v3, v3, Laf/a;->s:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setOnProgressChangedListener(Lmf/c;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_1d

    iget-object v3, v3, Laf/a;->u:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {v3, v1}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setOnProgressChangedListener(Lmf/c;)V

    invoke-virtual {v1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->k:I

    move v4, v13

    :goto_8
    if-ge v4, v3, :cond_14

    invoke-virtual {v0, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Landroid/widget/SeekBar;

    invoke-virtual {v6, v1}, Landroid/widget/SeekBar;->setOnSeekBarChangeListener(Landroid/widget/SeekBar$OnSeekBarChangeListener;)V

    invoke-virtual {v0, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Landroid/widget/SeekBar;

    invoke-virtual {v6, v1}, Landroid/view/View;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V

    invoke-virtual {v5, v4}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Landroid/widget/TextView;

    new-instance v7, Ldf/j1;

    invoke-direct {v7, v1, v4}, Ldf/j1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;I)V

    invoke-virtual {v6, v7}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    add-int/lit8 v4, v4, 0x1

    goto :goto_8

    :cond_14
    iget-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1c

    iget-object v0, v0, Laf/a;->H:Landroid/widget/TextView;

    new-instance v3, Ldf/k1;

    invoke-direct {v3, v1}, Ldf/k1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v0, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1b

    iget-object v0, v0, Laf/a;->K:Landroid/widget/TextView;

    new-instance v3, Ldf/l1;

    invoke-direct {v3, v1}, Ldf/l1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v0, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v0, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1a

    iget-object v0, v0, Laf/a;->Q:Landroid/widget/TextView;

    new-instance v3, Ldf/m1;

    invoke-direct {v3, v1}, Ldf/m1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {v0, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    new-instance v0, Llf/a;

    new-instance v3, Ldf/o1;

    const/4 v4, 0x0

    invoke-direct {v3, v1, v4}, Ldf/o1;-><init>(Ljava/lang/Object;I)V

    invoke-direct {v0, v3}, Llf/a;-><init>(Ldf/o1;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_19

    iget-object v3, v3, Laf/a;->y:Landroidx/appcompat/widget/AppCompatSpinner;

    invoke-virtual {v3, v0}, Landroid/view/View;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V

    iget-object v3, v1, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_18

    iget-object v2, v3, Laf/a;->y:Landroidx/appcompat/widget/AppCompatSpinner;

    invoke-virtual {v2, v0}, Landroid/widget/AdapterView;->setOnItemSelectedListener(Landroid/widget/AdapterView$OnItemSelectedListener;)V

    invoke-static {v1}, Lam/b;->b(Landroidx/lifecycle/g0;)Landroidx/lifecycle/c0;

    move-result-object v0

    new-instance v2, Ldf/a2;

    const/4 v11, 0x0

    invoke-direct {v2, v1, v11}, Ldf/a2;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;Lbm/e;)V

    const/4 v3, 0x3

    invoke-static {v0, v11, v11, v2, v3}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    :try_start_1
    invoke-static {}, Lk/e;->f()Lq3/h;

    move-result-object v0

    iget-object v0, v0, Lq3/h;->a:Lq3/j;

    iget-object v0, v0, Lq3/j;->a:Landroid/os/LocaleList;

    invoke-virtual {v0}, Landroid/os/LocaleList;->isEmpty()Z

    move-result v0
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    const-string v2, "en"

    const/4 v3, 0x2

    const v4, 0x7f030009

    if-eqz v0, :cond_15

    :try_start_2
    invoke-static {}, Landroid/os/LocaleList;->getDefault()Landroid/os/LocaleList;

    move-result-object v0

    invoke-static {v0}, Lq3/h;->b(Landroid/os/LocaleList;)Lq3/h;

    move-result-object v0

    invoke-virtual {v1}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v5

    invoke-virtual {v5, v4}, Landroid/content/res/Resources;->getStringArray(I)[Ljava/lang/String;

    move-result-object v4

    iget-object v0, v0, Lq3/h;->a:Lq3/j;

    iget-object v0, v0, Lq3/j;->a:Landroid/os/LocaleList;

    invoke-virtual {v0, v4}, Landroid/os/LocaleList;->getFirstMatch([Ljava/lang/String;)Ljava/util/Locale;

    move-result-object v0

    if-eqz v0, :cond_16

    invoke-virtual {v0}, Ljava/util/Locale;->getLanguage()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_16

    invoke-static {v3, v0}, Lfp/z;->i0(ILjava/lang/String;)Ljava/lang/String;

    move-result-object v2

    goto :goto_9

    :catch_1
    move-exception v0

    goto :goto_a

    :cond_15
    invoke-static {}, Lk/e;->f()Lq3/h;

    move-result-object v0

    invoke-virtual {v1}, Landroidx/appcompat/app/AppCompatActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v5

    invoke-virtual {v5, v4}, Landroid/content/res/Resources;->getStringArray(I)[Ljava/lang/String;

    move-result-object v4

    iget-object v0, v0, Lq3/h;->a:Lq3/j;

    iget-object v0, v0, Lq3/j;->a:Landroid/os/LocaleList;

    invoke-virtual {v0, v4}, Landroid/os/LocaleList;->getFirstMatch([Ljava/lang/String;)Ljava/util/Locale;

    move-result-object v0

    if-eqz v0, :cond_16

    invoke-virtual {v0}, Ljava/util/Locale;->getLanguage()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_16

    invoke-static {v3, v0}, Lfp/z;->i0(ILjava/lang/String;)Ljava/lang/String;

    move-result-object v2

    :cond_16
    :goto_9
    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_17

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v3, "in_app_lang"

    invoke-interface {v0, v3, v2}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_b

    :cond_17
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/16 v52, 0x0

    throw v52
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_1

    :goto_a
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v2

    invoke-virtual {v2, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_b
    return-void

    :cond_18
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    const/16 v52, 0x0

    throw v52

    :cond_19
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_1a
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_1b
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_1c
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_1d
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_1e
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_1f
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_20
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_21
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_22
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_23
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_24
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_25
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_26
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_27
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_28
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_29
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_2a
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_2b
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_2c
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_2d
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_2e
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_2f
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_30
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_31
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_32
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_33
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_34
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_35
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_36
    const/16 v52, 0x0

    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v52

    :cond_37
    move v2, v3

    goto :goto_d

    :cond_38
    move v7, v3

    goto :goto_c

    :cond_39
    const v7, 0x7f0a0361

    :cond_3a
    :goto_c
    invoke-virtual {v5}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {v0, v7}, Landroid/content/res/Resources;->getResourceName(I)Ljava/lang/String;

    move-result-object v0

    new-instance v2, Ljava/lang/NullPointerException;

    invoke-virtual {v6, v0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {v2, v0}, Ljava/lang/NullPointerException;-><init>(Ljava/lang/String;)V

    throw v2

    :cond_3b
    :goto_d
    invoke-virtual {v0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {v0, v2}, Landroid/content/res/Resources;->getResourceName(I)Ljava/lang/String;

    move-result-object v0

    new-instance v2, Ljava/lang/NullPointerException;

    invoke-virtual {v6, v0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {v2, v0}, Ljava/lang/NullPointerException;-><init>(Ljava/lang/String;)V

    throw v2
.end method

.method public final onCreateOptionsMenu(Landroid/view/Menu;)Z
    .locals 2

    const-string v0, "menu"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {p0}, Landroidx/appcompat/app/AppCompatActivity;->getMenuInflater()Landroid/view/MenuInflater;

    move-result-object v0

    const v1, 0x7f0f0002

    invoke-virtual {v0, v1, p1}, Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V

    const/4 p1, 0x1

    return p1
.end method

.method public final onDestroy()V
    .locals 2

    invoke-super {p0}, Landroidx/appcompat/app/AppCompatActivity;->onDestroy()V

    invoke-static {p0}, Lk7/f;->b(Landroid/content/Context;)Ljava/lang/String;

    move-result-object v0

    const/4 v1, 0x0

    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0, p0}, Landroid/content/SharedPreferences;->unregisterOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    :try_start_0
    invoke-static {p0}, Ly4/a;->a(Landroid/content/Context;)Ly4/a;

    move-result-object v0

    iget-object v1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->s:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;

    invoke-virtual {v0, v1}, Ly4/a;->d(Lcom/jazibkhan/equalizer/ui/activities/MainActivity$a;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    invoke-virtual {v0}, Ljava/lang/Throwable;->printStackTrace()V

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :goto_0
    :try_start_1
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->m:Lkf/g;

    if-eqz v0, :cond_0

    invoke-virtual {p0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    invoke-virtual {v1, v0}, Landroid/content/ContentResolver;->unregisterContentObserver(Landroid/database/ContentObserver;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_1

    :catch_1
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_0
    :goto_1
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->n:Landroid/os/HandlerThread;

    if-eqz v0, :cond_1

    invoke-virtual {v0}, Landroid/os/HandlerThread;->quit()Z

    :cond_1
    return-void
.end method

.method public final onNewIntent(Landroid/content/Intent;)V
    .locals 5

    const-string v0, "intent"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-super {p0, p1}, Le/j;->onNewIntent(Landroid/content/Intent;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    invoke-virtual {p1}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p1

    const/4 v1, 0x0

    if-eqz p1, :cond_0

    invoke-virtual {p1}, Landroid/net/Uri;->getHost()Ljava/lang/String;

    move-result-object v2

    goto :goto_0

    :cond_0
    move-object v2, v1

    :goto_0
    const-string v3, "eq.japp.io"

    invoke-static {v2, v3}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_2

    invoke-virtual {p1}, Landroid/net/Uri;->getPathSegments()Ljava/util/List;

    move-result-object v2

    invoke-static {v2}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    invoke-static {v2}, Lyl/t;->K(Ljava/util/List;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    const-string v3, "s"

    invoke-static {v2, v3}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_1

    invoke-static {v0}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object v2

    new-instance v3, Ldf/r0;

    invoke-direct {v3, v0}, Ldf/r0;-><init>(Ldf/b;)V

    new-instance v4, Ldf/t0;

    invoke-direct {v4, v0, p1, v1}, Ldf/t0;-><init>(Ldf/b;Landroid/net/Uri;Lbm/e;)V

    const/4 p1, 0x2

    invoke-static {v2, v3, v1, v4, p1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_1
    invoke-static {v0}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance v2, Ldf/u0;

    invoke-direct {v2, v0, v1}, Ldf/u0;-><init>(Ldf/b;Lbm/e;)V

    const/4 v0, 0x3

    invoke-static {p1, v1, v1, v2, v0}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    :cond_2
    return-void
.end method

.method public final onOptionsItemSelected(Landroid/view/MenuItem;)Z
    .locals 7

    const-string v0, "item"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-interface {p1}, Landroid/view/MenuItem;->getItemId()I

    move-result v0

    const/4 v1, 0x0

    const/4 v2, 0x1

    sparse-switch v0, :sswitch_data_0

    goto/16 :goto_0

    :sswitch_0
    new-instance p1, Landroid/content/Intent;

    const-class v0, Lcom/jazibkhan/equalizer/ui/activities/AppSettingsActivity;

    invoke-direct {p1, p0, v0}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p0, p1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    return v2

    :sswitch_1
    new-instance p1, Lhf/k;

    invoke-direct {p1}, Lhf/k;-><init>()V

    invoke-virtual {p0}, Landroidx/fragment/app/u;->getSupportFragmentManager()Landroidx/fragment/app/h0;

    move-result-object v0

    const-string v1, "CustomPresetSaveDialog"

    invoke-virtual {p1, v0, v1}, Landroidx/fragment/app/n;->show(Landroidx/fragment/app/h0;Ljava/lang/String;)V

    return v2

    :sswitch_2
    const-string p1, "MainActivity"

    invoke-static {p0, p1}, Lkf/e;->c(Landroid/app/Activity;Ljava/lang/String;)V

    return v2

    :sswitch_3
    new-instance p1, Lhf/m;

    invoke-direct {p1}, Lhf/m;-><init>()V

    invoke-virtual {p0}, Landroidx/fragment/app/u;->getSupportFragmentManager()Landroidx/fragment/app/h0;

    move-result-object v0

    const-string v1, "CustomPresetDialog"

    invoke-virtual {p1, v0, v1}, Landroidx/fragment/app/n;->show(Landroidx/fragment/app/h0;Ljava/lang/String;)V

    return v2

    :sswitch_4
    :try_start_0
    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v3

    iget-object v3, v3, Lcom/zipoapps/premiumhelper/d;->i:Lwi/b;

    const-string v4, "SERVER_BASE_URL"

    const-string v5, "https://4pda.to/forum/index.php?showuser=7171802"

    invoke-virtual {v3, v3, v4, v5}, Lwi/b;->c(Lwi/a;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/String;

    new-instance v4, Ljava/lang/StringBuilder;

    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v3, "/wiki/flat-equalizer.html"

    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    new-instance v4, Ls/n$d;

    invoke-direct {v4}, Ls/n$d;-><init>()V

    iget-object v5, v4, Ls/n$d;->a:Landroid/content/Intent;

    const-string v6, "android.support.customtabs.extra.TITLE_VISIBILITY"

    invoke-virtual {v5, v6, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    const-string v1, "android.support.customtabs.extra.ENABLE_URLBAR_HIDING"

    invoke-virtual {v5, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    invoke-virtual {v4}, Ls/n$d;->a()Ls/n;

    move-result-object v1

    invoke-static {v3}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v2

    invoke-virtual {v1, p0, v2}, Ls/n;->a(Landroid/content/Context;Landroid/net/Uri;)V

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    invoke-virtual {v0}, Lcom/zipoapps/premiumhelper/d;->f()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->J()V

    goto :goto_0

    :sswitch_5
    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    const/4 v2, 0x0

    const-string v3, "mPref"

    if-eqz v0, :cond_2

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v4, "package_name"

    const-string v5, "Global Mix"

    invoke-interface {v0, v4, v5}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_1

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v2, "session_id"

    invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :cond_0

    new-instance v0, Landroid/content/Intent;

    const-class v3, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-direct {v0, p0, v3}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v2, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {v0, v4, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    const-string v2, "start_with_audio_session"

    invoke-virtual {v0, v2}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    sget-object v2, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v2

    iget-object v2, v2, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v1, [Landroid/os/Bundle;

    const-string v3, "Start_service_MainActivity_3"

    invoke-virtual {v2, v3, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    :cond_0
    :goto_0
    invoke-super {p0, p1}, Landroid/app/Activity;->onOptionsItemSelected(Landroid/view/MenuItem;)Z

    move-result p1

    return p1

    :cond_1
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :cond_2
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v2

    :sswitch_6
    new-instance p1, Lcom/zipoapps/premiumhelper/ui/settings/b$a;

    invoke-direct {p1}, Lcom/zipoapps/premiumhelper/ui/settings/b$a;-><init>()V

    invoke-virtual {p1}, Lcom/zipoapps/premiumhelper/ui/settings/b$a;->a()Lcom/zipoapps/premiumhelper/ui/settings/b;

    move-result-object p1

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->K:Lcom/zipoapps/premiumhelper/ui/settings/c;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    new-instance v0, Landroid/content/Intent;

    const-class v1, Lcom/zipoapps/premiumhelper/ui/settings/PHSettingsActivity;

    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p1}, Lcom/zipoapps/premiumhelper/ui/settings/b;->a()Landroid/os/Bundle;

    move-result-object p1

    invoke-virtual {v0, p1}, Landroid/content/Intent;->putExtras(Landroid/os/Bundle;)Landroid/content/Intent;

    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V

    return v2

    :sswitch_data_0
    .sparse-switch
        0x7f0a0034 -> :sswitch_6
        0x7f0a003f -> :sswitch_5
        0x7f0a0040 -> :sswitch_4
        0x7f0a0042 -> :sswitch_3
        0x7f0a0048 -> :sswitch_2
        0x7f0a0049 -> :sswitch_1
        0x7f0a004a -> :sswitch_0
    .end sparse-switch
.end method

.method public final onPrepareOptionsMenu(Landroid/view/Menu;)Z
    .locals 5

    const-string v0, "menu"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-super {p0, p1}, Landroid/app/Activity;->onPrepareOptionsMenu(Landroid/view/Menu;)Z

    const v0, 0x7f0a0048

    invoke-interface {p1, v0}, Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;

    move-result-object v0

    sget-object v1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v1

    iget-object v1, v1, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v1}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v1

    const/4 v2, 0x0

    if-eqz v1, :cond_0

    invoke-interface {v0, v2}, Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;

    :cond_0
    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    const/4 v1, 0x0

    const-string v3, "mPref"

    if-eqz v0, :cond_4

    const-string v4, "session_id"

    invoke-interface {v0, v4, v2}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v0

    if-eqz v0, :cond_2

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_1

    const-string v1, "only_music_player"

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-eqz v0, :cond_3

    goto :goto_0

    :cond_1
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    :goto_0
    const v0, 0x7f0a003f

    invoke-interface {p1, v0}, Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;

    move-result-object p1

    invoke-interface {p1, v2}, Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;

    :cond_3
    const/4 p1, 0x1

    return p1

    :cond_4
    invoke-static {v3}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final onProgressChanged(Landroid/widget/SeekBar;IZ)V
    .locals 6

    const-string v0, "seekBar"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    if-eqz p3, :cond_5

    iget-object p3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    invoke-virtual {p3}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object p3

    const/4 v0, 0x0

    :goto_0
    invoke-interface {p3}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    const/4 v2, 0x3

    const/4 v3, 0x0

    if-eqz v1, :cond_2

    invoke-interface {p3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    add-int/lit8 v4, v0, 0x1

    if-ltz v0, :cond_1

    check-cast v1, Landroid/widget/SeekBar;

    if-ne p1, v1, :cond_0

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iget-object p3, p1, Ldf/b;->u:Ljava/util/ArrayList;

    iget v1, p1, Ldf/b;->i:I

    iget v4, p1, Ldf/b;->j:I

    sub-int/2addr v4, v1

    mul-int/2addr v4, p2

    const/16 v5, 0xbb8

    div-int/2addr v4, v5

    add-int/2addr v4, v1

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {p3, v0, v1}, Ljava/util/List;->set(ILjava/lang/Object;)Ljava/lang/Object;

    iget-object p3, p1, Ldf/b;->l:[F

    int-to-float v1, p2

    int-to-float v4, v5

    div-float/2addr v1, v4

    aput v1, p3, v0

    iget-object p3, p1, Ldf/b;->u:Ljava/util/ArrayList;

    invoke-interface {p3, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p3

    check-cast p3, Ljava/lang/Number;

    invoke-virtual {p3}, Ljava/lang/Number;->intValue()I

    move-result p3

    invoke-static {p3, v0}, Lkf/f;->z(II)V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p3

    new-instance v1, Ldf/x;

    invoke-direct {v1, p1, v0, p2, v3}, Ldf/x;-><init>(Ldf/b;IILbm/e;)V

    invoke-static {p3, v3, v3, v1, v2}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_0
    move v0, v4

    goto :goto_0

    :cond_1
    invoke-static {}, Lip/w0;->o()V

    throw v3

    :cond_2
    iget-object p3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p3, :cond_4

    iget-object p3, p3, Laf/a;->v:Landroidx/appcompat/widget/AppCompatSeekBar;

    if-ne p1, p3, :cond_5

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    iput p2, p1, Ldf/b;->r:I

    sget-object p3, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p3, :cond_3

    invoke-interface {p3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p3

    const-string v0, "reverbslider"

    invoke-interface {p3, v0, p2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p3}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p3

    new-instance v0, Ldf/l0;

    invoke-direct {v0, p1, p2, v3}, Ldf/l0;-><init>(Ldf/b;ILbm/e;)V

    invoke-static {p3, v3, v3, v0, v2}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_3
    const-string p1, "mPref"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v3

    :cond_4
    const-string p1, "binding"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v3

    :cond_5
    return-void
.end method

.method public final onResume()V
    .locals 8

    const-string v0, ""

    invoke-super {p0}, Landroidx/fragment/app/u;->onResume()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget v2, v1, Ldf/b;->H:I

    const/4 v3, 0x1

    add-int/2addr v2, v3

    iput v2, v1, Ldf/b;->H:I

    sget-object v1, Lkf/f;->a:Landroid/content/SharedPreferences;

    const/4 v4, 0x0

    if-eqz v1, :cond_15

    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    const-string v5, "launch_count"

    invoke-interface {v1, v5, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v1}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    :try_start_0
    sget-object v1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v1

    iget-object v1, v1, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    const-string v2, "rate_intent"

    invoke-virtual {v1, v1, v2, v0}, Lcom/zipoapps/premiumhelper/c;->c(Lwi/a;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    invoke-static {v1, v0}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move v0, v3

    :goto_0
    const/4 v1, 0x0

    const/16 v2, 0x8

    const-string v5, "binding"

    if-eqz v0, :cond_7

    new-instance v0, Lkotlin/jvm/internal/b0;

    invoke-direct {v0}, Lkotlin/jvm/internal/b0;-><init>()V

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_6

    iget-object v3, v3, Laf/a;->o:Landroid/widget/TextView;

    const v6, 0x7f13004a

    invoke-virtual {p0, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v3, v6}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_5

    iget-object v3, v3, Laf/a;->R:Lcom/google/android/material/button/MaterialButton;

    const v6, 0x7f130301

    invoke-virtual {p0, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v3, v6}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_4

    iget-object v3, v3, Laf/a;->r:Lcom/google/android/material/button/MaterialButton;

    const v6, 0x7f1301ff

    invoke-virtual {p0, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v3, v6}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v3, :cond_3

    iget-object v3, v3, Laf/a;->n:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v3, v2}, Landroid/view/View;->setVisibility(I)V

    iget-object v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v2, :cond_2

    iget-object v2, v2, Laf/a;->R:Lcom/google/android/material/button/MaterialButton;

    new-instance v3, Ldf/v1;

    const/4 v6, 0x0

    invoke-direct {v3, v6, p0, v0}, Ldf/v1;-><init>(ILandroid/view/KeyEvent$Callback;Ljava/lang/Object;)V

    invoke-virtual {v2, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v2, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v2, :cond_1

    iget-object v2, v2, Laf/a;->r:Lcom/google/android/material/button/MaterialButton;

    new-instance v3, Ldf/w1;

    const/4 v6, 0x0

    invoke-direct {v3, v6, p0, v0}, Ldf/w1;-><init>(ILandroid/view/KeyEvent$Callback;Ljava/lang/Object;)V

    invoke-virtual {v2, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_0

    iget-object v0, v0, Laf/a;->n:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V

    goto/16 :goto_3

    :cond_0
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_1
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_2
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_3
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_4
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_5
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_6
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_7
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    :try_start_1
    sget-object v6, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v6}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v6

    iget-object v6, v6, Lcom/zipoapps/premiumhelper/d;->i:Lwi/b;

    const-string v7, "should_show_upgrade_prompt"

    invoke-interface {v6, v7, v1}, Lwi/a;->b(Ljava/lang/String;Z)Z

    move-result v6
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_1

    :catch_1
    move v6, v1

    :goto_1
    if-eqz v6, :cond_9

    :cond_8
    move v3, v1

    goto :goto_2

    :cond_9
    iget v6, v0, Ldf/b;->H:I

    const/16 v7, 0x28

    if-lt v6, v7, :cond_8

    iget-boolean v0, v0, Ldf/b;->I:Z

    if-nez v0, :cond_8

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->d:Lcom/zipoapps/premiumhelper/c;

    invoke-virtual {v0}, Lcom/zipoapps/premiumhelper/c;->i()Z

    move-result v0

    if-nez v0, :cond_8

    :goto_2
    if-eqz v3, :cond_11

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_10

    iget-object v0, v0, Laf/a;->o:Landroid/widget/TextView;

    const v3, 0x7f1302e3

    invoke-virtual {p0, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_f

    iget-object v0, v0, Laf/a;->R:Lcom/google/android/material/button/MaterialButton;

    const v3, 0x7f1302e2

    invoke-virtual {p0, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_e

    iget-object v0, v0, Laf/a;->r:Lcom/google/android/material/button/MaterialButton;

    const v3, 0x7f1301fc

    invoke-virtual {p0, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_d

    iget-object v0, v0, Laf/a;->n:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v0, v2}, Landroid/view/View;->setVisibility(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_c

    iget-object v0, v0, Laf/a;->R:Lcom/google/android/material/button/MaterialButton;

    new-instance v2, Ldf/t1;

    const/4 v3, 0x0

    invoke-direct {v2, p0, v3}, Ldf/t1;-><init>(Ljava/lang/Object;I)V

    invoke-virtual {v0, v2}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_b

    iget-object v0, v0, Laf/a;->r:Lcom/google/android/material/button/MaterialButton;

    new-instance v2, Lcom/google/android/material/search/k;

    const/4 v3, 0x1

    invoke-direct {v2, p0, v3}, Lcom/google/android/material/search/k;-><init>(Ljava/lang/Object;I)V

    invoke-virtual {v0, v2}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_a

    iget-object v0, v0, Laf/a;->n:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V

    goto :goto_3

    :cond_a
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_b
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_c
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_d
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_e
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_f
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_10
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_11
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_14

    iget-object v0, v0, Laf/a;->n:Lcom/google/android/material/card/MaterialCardView;

    invoke-virtual {v0, v2}, Landroid/view/View;->setVisibility(I)V

    :goto_3
    :try_start_2
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->l:Landroid/media/AudioManager;

    if-eqz v0, :cond_13

    iget-object v1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v1, :cond_12

    iget-object v1, v1, Laf/a;->x:Landroidx/appcompat/widget/AppCompatSeekBar;

    const/4 v2, 0x3

    invoke-virtual {v0, v2}, Landroid/media/AudioManager;->getStreamVolume(I)I

    move-result v0

    invoke-virtual {v1, v0}, Landroid/widget/ProgressBar;->setProgress(I)V

    goto :goto_5

    :catch_2
    move-exception v0

    goto :goto_4

    :cond_12
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_2

    :goto_4
    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    :cond_13
    :goto_5
    return-void

    :cond_14
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4

    :cond_15
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v4
.end method

.method public final onSharedPreferenceChanged(Landroid/content/SharedPreferences;Ljava/lang/String;)V
    .locals 8

    if-nez p1, :cond_0

    goto/16 :goto_1

    :cond_0
    if-eqz p2, :cond_17

    invoke-virtual {p2}, Ljava/lang/String;->hashCode()I

    move-result v0

    const/4 v1, 0x3

    const/4 v2, 0x1

    const-string v3, "is_channel_bal_visible"

    const-string v4, "session_id"

    const-string v5, "mPref"

    const/4 v6, 0x0

    const/4 v7, 0x0

    sparse-switch v0, :sswitch_data_0

    goto/16 :goto_1

    :sswitch_0
    const-string v0, "always_global"

    invoke-virtual {p2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-nez p2, :cond_1

    goto/16 :goto_1

    :cond_1
    invoke-interface {p1, v0, v6}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result p1

    if-eqz p1, :cond_17

    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_3

    invoke-interface {p1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    const-string p2, "package_name"

    const-string v0, "Global Mix"

    invoke-interface {p1, p2, v0}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->apply()V

    sget-object p1, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_2

    invoke-interface {p1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    invoke-interface {p1, v4, v6}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result p1

    if-eqz p1, :cond_17

    new-instance p1, Landroid/content/Intent;

    const-class v1, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-direct {p1, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p1, v4, v6}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-virtual {p1, p2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    const-string p2, "start_with_audio_session"

    invoke-virtual {p1, p2}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    sget-object p2, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {p2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object p2

    iget-object p2, p2, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v0, v6, [Landroid/os/Bundle;

    const-string v1, "Start_service_MainActivity_2"

    invoke-virtual {p2, v1, v0}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, p1}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    return-void

    :cond_2
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :cond_3
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :sswitch_1
    const-string p1, "loudness_max_gain"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_4

    goto/16 :goto_1

    :cond_4
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-virtual {p1}, Ldf/b;->o()V

    return-void

    :sswitch_2
    const-string p1, "frame_duration_pref"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_5

    goto/16 :goto_1

    :cond_5
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/a0;

    invoke-direct {v0, p1, v7}, Ldf/a0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :sswitch_3
    invoke-virtual {p2, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_6

    goto/16 :goto_1

    :cond_6
    invoke-virtual {p0}, Landroidx/appcompat/app/AppCompatActivity;->invalidateOptionsMenu()V

    invoke-static {p0}, Lkf/a;->g(Landroid/content/Context;)Z

    move-result p1

    if-nez p1, :cond_17

    invoke-virtual {p0}, Landroid/app/Activity;->recreate()V

    return-void

    :sswitch_4
    const-string p1, "alf5sdj4lw5j30234j2l423"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_7

    goto/16 :goto_1

    :cond_7
    invoke-virtual {p0}, Landroid/app/Activity;->recreate()V

    return-void

    :sswitch_5
    const-string p1, "bass_boost_freq"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_8

    goto/16 :goto_1

    :cond_8
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/n;

    invoke-direct {v0, p1, v7}, Ldf/n;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :sswitch_6
    const-string v0, "reverb_visible"

    invoke-virtual {p2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-nez p2, :cond_9

    goto/16 :goto_1

    :cond_9
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p2

    invoke-interface {p1, v0, v6}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result p1

    iput-boolean p1, p2, Ldf/b;->E:Z

    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_a

    invoke-interface {v2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v2

    invoke-interface {v2, v0, p1}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v2}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p2}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance v0, Ldf/m0;

    invoke-direct {v0, p2, v7}, Ldf/m0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_a
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :sswitch_7
    const-string p1, "is_legacy_mode"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-nez p2, :cond_b

    goto/16 :goto_1

    :cond_b
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p2

    invoke-virtual {p2}, Ldf/b;->p()V

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_e

    invoke-interface {v0, v3, v6}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-eqz v0, :cond_d

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_c

    invoke-interface {v0, p1, v6}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result p1

    if-nez p1, :cond_d

    sget p1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v0, 0x1c

    if-lt p1, v0, :cond_d

    goto :goto_0

    :cond_c
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :cond_d
    move v2, v6

    :goto_0
    iput-boolean v2, p2, Ldf/b;->F:Z

    invoke-static {p2}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance v0, Ldf/b0;

    invoke-direct {v0, p2, v7}, Ldf/b0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_e
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :sswitch_8
    const-string p1, "bass_boost_max_gain"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_f

    goto/16 :goto_1

    :cond_f
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-virtual {p1}, Ldf/b;->o()V

    return-void

    :sswitch_9
    const-string v0, "volume_visible"

    invoke-virtual {p2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-nez p2, :cond_10

    goto/16 :goto_1

    :cond_10
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p2

    invoke-interface {p1, v0, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result p1

    iput-boolean p1, p2, Ldf/b;->G:Z

    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_11

    invoke-interface {v2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v2

    invoke-interface {v2, v0, p1}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v2}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p2}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p1

    new-instance v0, Ldf/z0;

    invoke-direct {v0, p2, v7}, Ldf/z0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p1, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_11
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :sswitch_a
    invoke-virtual {p2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_12

    goto :goto_1

    :cond_12
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {}, Lkf/f;->s()Z

    move-result p2

    iput-boolean p2, p1, Ldf/b;->F:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_13

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0, v3, p2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {}, Lkf/f;->d()Z

    move-result p2

    iput-boolean p2, p1, Ldf/b;->C:Z

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/t;

    invoke-direct {v0, p1, v7}, Ldf/t;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_13
    invoke-static {v5}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v7

    :sswitch_b
    const-string p1, "sticky_service_equalizer"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_14

    goto :goto_1

    :cond_14
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-virtual {p1}, Ldf/b;->o()V

    return-void

    :sswitch_c
    const-string p1, "only_music_player"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_15

    goto :goto_1

    :cond_15
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object p2

    new-instance v0, Ldf/g0;

    invoke-direct {v0, p1, v7}, Ldf/g0;-><init>(Ldf/b;Lbm/e;)V

    invoke-static {p2, v7, v7, v0, v1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    invoke-virtual {p0}, Landroidx/appcompat/app/AppCompatActivity;->invalidateOptionsMenu()V

    return-void

    :sswitch_d
    const-string p1, "is_ten_band"

    invoke-virtual {p2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_16

    goto :goto_1

    :cond_16
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-virtual {p1}, Ldf/b;->p()V

    :cond_17
    :goto_1
    return-void

    nop

    :sswitch_data_0
    .sparse-switch
        -0x79d42374 -> :sswitch_d
        -0x79ab7812 -> :sswitch_c
        -0x68844b0e -> :sswitch_b
        -0x5c937991 -> :sswitch_a
        -0x2d73bb3 -> :sswitch_9
        0x9357ff6 -> :sswitch_8
        0x2200f0c4 -> :sswitch_7
        0x2bda8f05 -> :sswitch_6
        0x3cc516f4 -> :sswitch_5
        0x47a53e52 -> :sswitch_4
        0x630ddf64 -> :sswitch_3
        0x6391629c -> :sswitch_2
        0x64a2a450 -> :sswitch_1
        0x7a92cfd3 -> :sswitch_0
    .end sparse-switch
.end method

.method public final onStart()V
    .locals 4

    invoke-super {p0}, Landroidx/appcompat/app/AppCompatActivity;->onStart()V

    :try_start_0
    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->w()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const/4 v1, 0x1

    invoke-virtual {p0, v1}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->v(Z)V

    sget-object v1, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v1

    iget-object v1, v1, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    const/4 v2, 0x0

    new-array v2, v2, [Landroid/os/Bundle;

    const-string v3, "equalizer_not_operational"

    invoke-virtual {v1, v3, v2}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method

.method public final onStartTrackingTouch(Landroid/widget/SeekBar;)V
    .locals 8

    const-string v0, "seekBar"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v0

    const/4 v1, 0x0

    move v2, v1

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    const/4 v4, 0x1

    const/4 v5, 0x0

    if-eqz v3, :cond_7

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    add-int/lit8 v6, v2, 0x1

    if-ltz v2, :cond_6

    check-cast v3, Landroid/widget/SeekBar;

    if-ne p1, v3, :cond_5

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {}, Ldf/b;->n()Z

    move-result v0

    if-nez v0, :cond_0

    invoke-static {v4}, Ldf/b;->s(Z)V

    invoke-virtual {p1}, Ldf/b;->x()V

    :cond_0
    iget-boolean v0, p1, Ldf/b;->D:Z

    if-nez v0, :cond_2

    iget-object v0, p1, Ldf/b;->m:Ljava/util/ArrayList;

    iget v2, p1, Ldf/b;->w:I

    invoke-virtual {v0, v2}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lye/u;

    iget-object v0, v0, Lye/u;->b:Ljava/util/List;

    check-cast v0, Ljava/lang/Iterable;

    invoke-interface {v0}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_1
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_2

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    add-int/lit8 v3, v1, 0x1

    if-ltz v1, :cond_1

    check-cast v2, Ljava/lang/Number;

    invoke-virtual {v2}, Ljava/lang/Number;->intValue()I

    move-result v2

    iget-object v6, p1, Ldf/b;->u:Ljava/util/ArrayList;

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    invoke-interface {v6, v1, v7}, Ljava/util/List;->set(ILjava/lang/Object;)Ljava/lang/Object;

    invoke-static {v2, v1}, Lkf/f;->z(II)V

    move v1, v3

    goto :goto_1

    :cond_1
    invoke-static {}, Lip/w0;->o()V

    throw v5

    :cond_2
    iget-object v0, p1, Ldf/b;->m:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I

    move-result v0

    sub-int/2addr v0, v4

    iput v0, p1, Ldf/b;->w:I

    sget-object v1, Lkf/f;->a:Landroid/content/SharedPreferences;

    const-string v2, "mPref"

    if-eqz v1, :cond_4

    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    const-string v3, "spinnerpos"

    invoke-interface {v1, v3, v0}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v1}, Landroid/content/SharedPreferences$Editor;->apply()V

    iput-boolean v4, p1, Ldf/b;->D:Z

    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_3

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "is_custom_selected"

    invoke-interface {v0, v1, v4}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object v0

    new-instance v1, Ldf/p0;

    invoke-direct {v1, p1, v5}, Ldf/p0;-><init>(Ldf/b;Lbm/e;)V

    const/4 p1, 0x3

    invoke-static {v0, v5, v5, v1, p1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_3
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v5

    :cond_4
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v5

    :cond_5
    move v2, v6

    goto/16 :goto_0

    :cond_6
    invoke-static {}, Lip/w0;->o()V

    throw v5

    :cond_7
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_9

    iget-object v0, v0, Laf/a;->v:Landroidx/appcompat/widget/AppCompatSeekBar;

    if-ne p1, v0, :cond_8

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {}, Ldf/b;->n()Z

    move-result v0

    if-nez v0, :cond_8

    invoke-static {v4}, Ldf/b;->s(Z)V

    invoke-virtual {p1}, Ldf/b;->x()V

    :cond_8
    return-void

    :cond_9
    const-string p1, "binding"

    invoke-static {p1}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v5
.end method

.method public final onStop()V
    .locals 2

    invoke-super {p0}, Landroidx/appcompat/app/AppCompatActivity;->onStop()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    iput-object v1, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->w:Lcom/jazibkhan/equalizer/services/MainForegroundService$b;

    :cond_0
    :try_start_0
    iget-boolean v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->i:Z

    if-eqz v0, :cond_1

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->r:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;

    invoke-virtual {p0, v0}, Landroid/content/Context;->unbindService(Landroid/content/ServiceConnection;)V

    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->i:Z
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :cond_1
    return-void

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method

.method public final onStopTrackingTouch(Landroid/widget/SeekBar;)V
    .locals 5

    const-string v0, "seekBar"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->e:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v0

    const/4 v1, 0x0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_2

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    add-int/lit8 v3, v1, 0x1

    const/4 v4, 0x0

    if-ltz v1, :cond_1

    check-cast v2, Landroid/widget/SeekBar;

    if-ne p1, v2, :cond_0

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object p1

    invoke-static {p1}, Landroidx/lifecycle/p1;->a(Landroidx/lifecycle/o1;)Lw4/a;

    move-result-object v0

    new-instance v2, Ldf/q0;

    invoke-direct {v2, p1, v1, v4}, Ldf/q0;-><init>(Ldf/b;ILbm/e;)V

    const/4 p1, 0x3

    invoke-static {v0, v4, v4, v2, p1}, Lip/g;->c(Lip/h0;Lbm/h;Lip/j0;Lmm/p;I)Lip/n2;

    return-void

    :cond_0
    move v1, v3

    goto :goto_0

    :cond_1
    invoke-static {}, Lip/w0;->o()V

    throw v4

    :cond_2
    return-void
.end method

.method public final onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .locals 3

    const-string v0, "view"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "motionEvent"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x1

    if-eqz v0, :cond_1

    if-eq v0, v1, :cond_0

    goto :goto_0

    :cond_0
    invoke-virtual {p1}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    const/4 v2, 0x0

    invoke-interface {v0, v2}, Landroid/view/ViewParent;->requestDisallowInterceptTouchEvent(Z)V

    goto :goto_0

    :cond_1
    invoke-virtual {p1}, Landroid/view/View;->getParent()Landroid/view/ViewParent;

    move-result-object v0

    invoke-interface {v0, v1}, Landroid/view/ViewParent;->requestDisallowInterceptTouchEvent(Z)V

    :goto_0
    invoke-virtual {p1, p2}, Landroid/view/View;->onTouchEvent(Landroid/view/MotionEvent;)Z

    return v1
.end method

.method public final v(Z)V
    .locals 3

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const/4 v1, 0x0

    const-string v2, "binding"

    if-eqz v0, :cond_2

    iget-object v0, v0, Laf/a;->p:Laf/m;

    iget-object v0, v0, Laf/m;->b:Ljava/lang/Object;

    check-cast v0, Lcom/google/android/material/card/MaterialCardView;

    if-eqz p1, :cond_0

    const/4 p1, 0x0

    goto :goto_0

    :cond_0
    const/16 p1, 0x8

    :goto_0
    invoke-virtual {v0, p1}, Landroid/view/View;->setVisibility(I)V

    iget-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz p1, :cond_1

    iget-object p1, p1, Laf/a;->p:Laf/m;

    iget-object p1, p1, Laf/m;->c:Ljava/lang/Object;

    check-cast p1, Lcom/google/android/material/button/MaterialButton;

    new-instance v0, Ldf/u1;

    invoke-direct {v0, p0}, Ldf/u1;-><init>(Lcom/jazibkhan/equalizer/ui/activities/MainActivity;)V

    invoke-virtual {p1, v0}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    return-void

    :cond_1
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final w()V
    .locals 7

    sget-object v0, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v1

    iget-object v1, v1, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    const/4 v2, 0x0

    new-array v3, v2, [Landroid/os/Bundle;

    const-string v4, "doBindService"

    invoke-virtual {v1, v4, v3}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    new-instance v1, Landroid/content/Intent;

    const-class v3, Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-direct {v1, p0, v3}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    iget-object v3, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->r:Lcom/jazibkhan/equalizer/ui/activities/MainActivity$b;

    const/4 v4, 0x1

    invoke-virtual {p0, v1, v3, v4}, Landroid/content/Context;->bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z

    move-result v1

    new-instance v3, Ljava/lang/StringBuilder;

    const-string v5, "doBindService_bindResult_"

    invoke-direct {v3, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    const-string v5, "eventName"

    invoke-static {v3, v5}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v5

    iget-object v5, v5, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v6, v2, [Landroid/os/Bundle;

    invoke-virtual {v5, v3, v6}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    if-eqz v1, :cond_0

    iput-boolean v4, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->i:Z

    return-void

    :cond_0
    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/d$a;->a()Lcom/zipoapps/premiumhelper/d;

    move-result-object v0

    iget-object v0, v0, Lcom/zipoapps/premiumhelper/d;->m:Lsi/d;

    new-array v1, v2, [Landroid/os/Bundle;

    const-string v2, "equalizer_not_operational"

    invoke-virtual {v0, v2, v1}, Lsi/d;->s(Ljava/lang/String;[Landroid/os/Bundle;)V

    invoke-virtual {p0, v4}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->v(Z)V

    return-void
.end method

.method public final x()Ldf/b;
    .locals 1

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->d:Landroidx/lifecycle/q1;

    invoke-virtual {v0}, Landroidx/lifecycle/q1;->getValue()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ldf/b;

    return-object v0
.end method

.method public final y()V
    .locals 4

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    const/4 v1, 0x0

    const-string v2, "binding"

    if-eqz v0, :cond_5

    iget-object v0, v0, Laf/a;->z:Lcom/jazibkhan/equalizer/views/JSwitch;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->y:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/JSwitch;->setCheckedSilently(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_4

    iget-object v0, v0, Laf/a;->s:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->f:I

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setMaxProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_3

    iget-object v0, v0, Laf/a;->s:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget v3, v3, Ldf/b;->q:I

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setProgress(I)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_2

    iget-object v0, v0, Laf/a;->s:Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->y:Z

    invoke-virtual {v0, v3}, Lcom/jazibkhan/equalizer/views/ArcSeekBar/ArcSeekBar;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_1

    iget-object v0, v0, Laf/a;->H:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v3

    iget-boolean v3, v3, Ldf/b;->y:Z

    invoke-virtual {v0, v3}, Landroid/widget/TextView;->setEnabled(Z)V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->c:Laf/a;

    if-eqz v0, :cond_0

    iget-object v0, v0, Laf/a;->H:Landroid/widget/TextView;

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget v1, v1, Ldf/b;->q:I

    int-to-float v1, v1

    const/high16 v2, 0x447a0000    # 1000.0f

    div-float/2addr v1, v2

    const/16 v2, 0x64

    int-to-float v2, v2

    mul-float/2addr v1, v2

    invoke-static {v1}, Lom/a;->b(F)I

    move-result v1

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, "%"

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void

    :cond_0
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_1
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_2
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_3
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_4
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1

    :cond_5
    invoke-static {v2}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v1
.end method

.method public final z()V
    .locals 2

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v0

    iget-boolean v0, v0, Ldf/b;->C:Z

    if-nez v0, :cond_0

    goto :goto_0

    :cond_0
    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->h:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    if-eqz v0, :cond_1

    iget-object v0, v0, Lcom/jazibkhan/equalizer/services/MainForegroundService;->i:Lye/m0$b;

    if-eqz v0, :cond_1

    invoke-virtual {p0}, Lcom/jazibkhan/equalizer/ui/activities/MainActivity;->x()Ldf/b;

    move-result-object v1

    iget v1, v1, Ldf/b;->s:F

    invoke-virtual {v0, v1}, Lye/m0$b;->a(F)V

    :cond_1
    :goto_0
    return-void
.end method
