.class public final Lye/c;
.super Ljava/lang/Object;

# interfaces
.implements Ljava/io/Serializable;


# instance fields
.field private b:Ljava/lang/String;
    .annotation runtime Lie/b;
        value = "preset_name"
    .end annotation
.end field

.field private c:I
    .annotation runtime Lie/b;
        value = "vir_slider"
    .end annotation
.end field

.field private d:I
    .annotation runtime Lie/b;
        value = "bb_slider"
    .end annotation
.end field

.field private e:F
    .annotation runtime Lie/b;
        value = "loud_slider"
    .end annotation
.end field

.field private f:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation

    .annotation runtime Lie/b;
        value = "slider"
    .end annotation
.end field

.field private g:I
    .annotation runtime Lie/b;
        value = "spinner_pos"
    .end annotation
.end field

.field private h:Z
    .annotation runtime Lie/b;
        value = "vir_switch"
    .end annotation
.end field

.field private i:Z
    .annotation runtime Lie/b;
        value = "bb_switch"
    .end annotation
.end field

.field private j:Z
    .annotation runtime Lie/b;
        value = "loud_switch"
    .end annotation
.end field

.field private k:Z
    .annotation runtime Lie/b;
        value = "eq_switch"
    .end annotation
.end field

.field private l:Z
    .annotation runtime Lie/b;
        value = "is_custom_selected"
    .end annotation
.end field

.field private m:Z
    .annotation runtime Lie/b;
        value = "reverb_switch"
    .end annotation
.end field

.field private n:I
    .annotation runtime Lie/b;
        value = "reverb_slider"
    .end annotation
.end field

.field private o:Z
    .annotation runtime Lie/b;
        value = "channel_bal_switch"
    .end annotation
.end field

.field private p:F
    .annotation runtime Lie/b;
        value = "channel_bal_slider"
    .end annotation
.end field

.field private q:I
    .annotation runtime Lie/b;
        value = "id"
    .end annotation
.end field

.field private r:Z
    .annotation runtime Lie/b;
        value = "is_auto_apply"
    .end annotation
.end field


# direct methods
.method public constructor <init>(Ljava/lang/String;IIFLjava/util/ArrayList;IZZZZZZIZF)V
    .locals 1

    const-string v0, "presetName"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "slider"

    invoke-static {p5, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lye/c;->b:Ljava/lang/String;

    iput p2, p0, Lye/c;->c:I

    iput p3, p0, Lye/c;->d:I

    iput p4, p0, Lye/c;->e:F

    iput-object p5, p0, Lye/c;->f:Ljava/util/List;

    iput p6, p0, Lye/c;->g:I

    iput-boolean p7, p0, Lye/c;->h:Z

    iput-boolean p8, p0, Lye/c;->i:Z

    iput-boolean p9, p0, Lye/c;->j:Z

    iput-boolean p10, p0, Lye/c;->k:Z

    iput-boolean p11, p0, Lye/c;->l:Z

    iput-boolean p12, p0, Lye/c;->m:Z

    iput p13, p0, Lye/c;->n:I

    iput-boolean p14, p0, Lye/c;->o:Z

    move/from16 p1, p15

    iput p1, p0, Lye/c;->p:F

    return-void
.end method


# virtual methods
.method public final a()I
    .locals 1

    iget v0, p0, Lye/c;->d:I

    return v0
.end method

.method public final b()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->i:Z

    return v0
.end method

.method public final c()F
    .locals 1

    iget v0, p0, Lye/c;->p:F

    return v0
.end method

.method public final d()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->o:Z

    return v0
.end method

.method public final e()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->l:Z

    return v0
.end method

.method public final equals(Ljava/lang/Object;)Z
    .locals 4

    const/4 v0, 0x1

    if-ne p0, p1, :cond_0

    return v0

    :cond_0
    instance-of v1, p1, Lye/c;

    const/4 v2, 0x0

    if-nez v1, :cond_1

    return v2

    :cond_1
    check-cast p1, Lye/c;

    iget-object v1, p0, Lye/c;->b:Ljava/lang/String;

    iget-object v3, p1, Lye/c;->b:Ljava/lang/String;

    invoke-static {v1, v3}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2

    return v2

    :cond_2
    iget v1, p0, Lye/c;->c:I

    iget v3, p1, Lye/c;->c:I

    if-eq v1, v3, :cond_3

    return v2

    :cond_3
    iget v1, p0, Lye/c;->d:I

    iget v3, p1, Lye/c;->d:I

    if-eq v1, v3, :cond_4

    return v2

    :cond_4
    iget v1, p0, Lye/c;->e:F

    iget v3, p1, Lye/c;->e:F

    invoke-static {v1, v3}, Ljava/lang/Float;->compare(FF)I

    move-result v1

    if-eqz v1, :cond_5

    return v2

    :cond_5
    iget-object v1, p0, Lye/c;->f:Ljava/util/List;

    iget-object v3, p1, Lye/c;->f:Ljava/util/List;

    invoke-static {v1, v3}, Lkotlin/jvm/internal/l;->b(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_6

    return v2

    :cond_6
    iget v1, p0, Lye/c;->g:I

    iget v3, p1, Lye/c;->g:I

    if-eq v1, v3, :cond_7

    return v2

    :cond_7
    iget-boolean v1, p0, Lye/c;->h:Z

    iget-boolean v3, p1, Lye/c;->h:Z

    if-eq v1, v3, :cond_8

    return v2

    :cond_8
    iget-boolean v1, p0, Lye/c;->i:Z

    iget-boolean v3, p1, Lye/c;->i:Z

    if-eq v1, v3, :cond_9

    return v2

    :cond_9
    iget-boolean v1, p0, Lye/c;->j:Z

    iget-boolean v3, p1, Lye/c;->j:Z

    if-eq v1, v3, :cond_a

    return v2

    :cond_a
    iget-boolean v1, p0, Lye/c;->k:Z

    iget-boolean v3, p1, Lye/c;->k:Z

    if-eq v1, v3, :cond_b

    return v2

    :cond_b
    iget-boolean v1, p0, Lye/c;->l:Z

    iget-boolean v3, p1, Lye/c;->l:Z

    if-eq v1, v3, :cond_c

    return v2

    :cond_c
    iget-boolean v1, p0, Lye/c;->m:Z

    iget-boolean v3, p1, Lye/c;->m:Z

    if-eq v1, v3, :cond_d

    return v2

    :cond_d
    iget v1, p0, Lye/c;->n:I

    iget v3, p1, Lye/c;->n:I

    if-eq v1, v3, :cond_e

    return v2

    :cond_e
    iget-boolean v1, p0, Lye/c;->o:Z

    iget-boolean v3, p1, Lye/c;->o:Z

    if-eq v1, v3, :cond_f

    return v2

    :cond_f
    iget v1, p0, Lye/c;->p:F

    iget p1, p1, Lye/c;->p:F

    invoke-static {v1, p1}, Ljava/lang/Float;->compare(FF)I

    move-result p1

    if-eqz p1, :cond_10

    return v2

    :cond_10
    return v0
.end method

.method public final f()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->k:Z

    return v0
.end method

.method public final g()I
    .locals 1

    iget v0, p0, Lye/c;->q:I

    return v0
.end method

.method public final h()F
    .locals 1

    iget v0, p0, Lye/c;->e:F

    return v0
.end method

.method public final hashCode()I
    .locals 3

    iget-object v0, p0, Lye/c;->b:Ljava/lang/String;

    invoke-virtual {v0}, Ljava/lang/String;->hashCode()I

    move-result v0

    const/16 v1, 0x1f

    mul-int/2addr v0, v1

    iget v2, p0, Lye/c;->c:I

    invoke-static {v2, v0, v1}, Lcom/applovin/impl/sdk/ad/p;->e(III)I

    move-result v0

    iget v2, p0, Lye/c;->d:I

    invoke-static {v2, v0, v1}, Lcom/applovin/impl/sdk/ad/p;->e(III)I

    move-result v0

    iget v2, p0, Lye/c;->e:F

    invoke-static {v2, v0, v1}, Leh/b;->a(FII)I

    move-result v0

    iget-object v2, p0, Lye/c;->f:Ljava/util/List;

    invoke-static {v0, v1, v2}, Landroidx/transition/s;->c(IILjava/util/List;)I

    move-result v0

    iget v2, p0, Lye/c;->g:I

    invoke-static {v2, v0, v1}, Lcom/applovin/impl/sdk/ad/p;->e(III)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->h:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->i:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->j:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->k:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->l:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->m:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget v2, p0, Lye/c;->n:I

    invoke-static {v2, v0, v1}, Lcom/applovin/impl/sdk/ad/p;->e(III)I

    move-result v0

    iget-boolean v2, p0, Lye/c;->o:Z

    invoke-static {v0, v1, v2}, Leh/d;->c(IIZ)I

    move-result v0

    iget v1, p0, Lye/c;->p:F

    invoke-static {v1}, Ljava/lang/Float;->hashCode(F)I

    move-result v1

    add-int/2addr v1, v0

    return v1
.end method

.method public final i()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->j:Z

    return v0
.end method

.method public final j()Ljava/lang/String;
    .locals 1

    iget-object v0, p0, Lye/c;->b:Ljava/lang/String;

    return-object v0
.end method

.method public final k()I
    .locals 1

    iget v0, p0, Lye/c;->n:I

    return v0
.end method

.method public final l()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->m:Z

    return v0
.end method

.method public final m()Ljava/util/List;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation

    iget-object v0, p0, Lye/c;->f:Ljava/util/List;

    return-object v0
.end method

.method public final n()I
    .locals 1

    iget v0, p0, Lye/c;->g:I

    return v0
.end method

.method public final o()I
    .locals 1

    iget v0, p0, Lye/c;->c:I

    return v0
.end method

.method public final p()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->h:Z

    return v0
.end method

.method public final q()Z
    .locals 1

    iget-boolean v0, p0, Lye/c;->r:Z

    return v0
.end method

.method public final r(Z)V
    .locals 0

    iput-boolean p1, p0, Lye/c;->r:Z

    return-void
.end method

.method public final s(I)V
    .locals 0

    iput p1, p0, Lye/c;->q:I

    return-void
.end method

.method public final t(Ljava/lang/String;)V
    .locals 1

    const-string v0, "<set-?>"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iput-object p1, p0, Lye/c;->b:Ljava/lang/String;

    return-void
.end method

.method public final toString()Ljava/lang/String;
    .locals 17

    move-object/from16 v0, p0

    iget-object v1, v0, Lye/c;->b:Ljava/lang/String;

    iget v2, v0, Lye/c;->c:I

    iget v3, v0, Lye/c;->d:I

    iget v4, v0, Lye/c;->e:F

    iget-object v5, v0, Lye/c;->f:Ljava/util/List;

    iget v6, v0, Lye/c;->g:I

    iget-boolean v7, v0, Lye/c;->h:Z

    iget-boolean v8, v0, Lye/c;->i:Z

    iget-boolean v9, v0, Lye/c;->j:Z

    iget-boolean v10, v0, Lye/c;->k:Z

    iget-boolean v11, v0, Lye/c;->l:Z

    iget-boolean v12, v0, Lye/c;->m:Z

    iget v13, v0, Lye/c;->n:I

    iget-boolean v14, v0, Lye/c;->o:Z

    iget v15, v0, Lye/c;->p:F

    new-instance v0, Ljava/lang/StringBuilder;

    move/from16 v16, v15

    const-string v15, "CustomPreset(presetName="

    invoke-direct {v0, v15}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, ", virSlider="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, ", bbSlider="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, ", loudSlider="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v4}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    const-string v1, ", slider="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v1, ", spinnerPos="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, ", virSwitch="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v7}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", bbSwitch="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v8}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", loudSwitch="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v9}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", eqSwitch="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v10}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", customSelected="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v11}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", reverbSwitch="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v12}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", reverbSlider="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v13}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, ", channelBalSwitch="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v14}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v1, ", channelBalSlider="

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move/from16 v1, v16

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    const-string v1, ")"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
