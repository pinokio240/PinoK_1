.class public final Lcom/jazibkhan/equalizer/MyApplication;
.super Lbin/mt/signature/nurik;

# interfaces
.implements Landroidx/lifecycle/k;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\u0018\u00002\u00020\u00012\u00020\u0002B\u0007\u00a2\u0006\u0004\u0008\u0003\u0010\u0004\u00a8\u0006\u0005"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/MyApplication;",
        "Landroid/app/Application;",
        "Landroidx/lifecycle/k;",
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
.field public static b:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/app/Application;-><init>()V

    return-void
.end method


# virtual methods
.method public final onCreate()V
    .locals 24

    move-object/from16 v1, p0

    invoke-super {v1}, Landroid/app/Application;->onCreate()V

    sget-object v0, Landroidx/lifecycle/v0;->j:Landroidx/lifecycle/v0;

    iget-object v0, v0, Landroidx/lifecycle/v0;->g:Landroidx/lifecycle/h0;

    invoke-virtual {v0, v1}, Landroidx/lifecycle/h0;->addObserver(Landroidx/lifecycle/f0;)V

    sget-object v2, Lcom/zipoapps/premiumhelper/d;->M:Lcom/zipoapps/premiumhelper/d$a;

    const-string v0, "queryIntentActivities(...)"

    new-instance v15, Ljava/util/HashMap;

    invoke-direct {v15}, Ljava/util/HashMap;-><init>()V

    sget-object v3, Lsi/g;->q0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f1302c8

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    const-string v5, "getString(...)"

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->r0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f1302cc

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->K:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f13014f

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->N:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130211

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->O:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130212

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->S:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f13025f

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->b:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0002

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->c:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130020

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->d:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130021

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->e:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130022

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->f:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130023

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->g:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130024

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->i:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130026

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->h:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130025

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->j:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130027

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->k:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130028

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->i0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05001a

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->l0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05001c

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->H:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05000e

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->n0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05001e

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->v0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050021

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->T:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050015

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->G:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05000f

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->t0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050022

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->w0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050023

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->o0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05001f

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->s0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f1302d5

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->V:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130264

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->Q:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f13021d

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->a:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f13002a

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->y:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f1300dd

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->n:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f130033

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->P:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0064

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->g0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0070

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->f0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b006f

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->U:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0068

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->e0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050017

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->t:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050006

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->q:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050003

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->o:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050002

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->p0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050020

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->z:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0010

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->A:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f1300de

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->B:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0011

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->E:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0014

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->F:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f1300ee

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->C:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0013

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->u0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0073

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->w:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050009

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->u:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050007

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->v:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050008

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->M:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0063

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->a0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b006c

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->b0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b006d

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->Z:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b006b

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->h0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050018

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->L:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b003a

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->Y:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f13027a

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v4, v5}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->W:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b006a

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-static {v15, v3, v4}, Lak/d0;->b(Ljava/util/HashMap;Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v3, Lsi/g;->R:Lwi/d;

    iget-object v4, v3, Lwi/d;->a:Ljava/lang/String;

    const v5, 0x7f13025a

    invoke-virtual {v1, v5}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->d0:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f050016

    invoke-static {v1, v6}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->X:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f0b0069

    invoke-static {v1, v6}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->l:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f13002f

    invoke-virtual {v1, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->c0:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f0b006e

    invoke-static {v1, v6}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->m:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f130030

    invoke-virtual {v1, v6}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->p:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f0b0008

    invoke-static {v1, v6}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v4, Lsi/g;->D:Lwi/d;

    iget-object v4, v4, Lwi/d;->a:Ljava/lang/String;

    const v6, 0x7f0b0065

    invoke-static {v1, v6}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v15, v4, v6}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v1, v5}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->j0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050019

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->x0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f0b0074

    invoke-static {v1, v4}, Lak/p;->c(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->x:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05000a

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->r:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050004

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->s:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f13006b

    invoke-virtual {v1, v4}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->k0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05001b

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->m0:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f05001d

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->I:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050010

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    sget-object v3, Lsi/g;->J:Lwi/d;

    iget-object v3, v3, Lwi/d;->a:Ljava/lang/String;

    const v4, 0x7f050011

    invoke-static {v1, v4}, Lak/p;->b(Lcom/jazibkhan/equalizer/MyApplication;I)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v15, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    const/4 v3, 0x0

    new-array v8, v3, [I

    new-array v9, v3, [I

    new-array v10, v3, [I

    const v4, 0x7f060388

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v18

    const v4, 0x7f060389

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v19

    const v4, 0x7f060385

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v20

    const v4, 0x7f06038a

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v21

    const v4, 0x7f060387

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v22

    new-instance v16, Lpj/p;

    const v17, 0x7f060386

    invoke-direct/range {v16 .. v22}, Lpj/p;-><init>(ILjava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;)V

    new-instance v14, Landroid/os/Bundle;

    invoke-direct {v14}, Landroid/os/Bundle;-><init>()V

    const-string v4, "test_advertising_ids"

    const v5, 0x7f03000e

    invoke-virtual {v1}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v6

    invoke-virtual {v6, v5}, Landroid/content/res/Resources;->obtainTypedArray(I)Landroid/content/res/TypedArray;

    move-result-object v5

    const-string v6, "obtainTypedArray(...)"

    invoke-static {v5, v6}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v6, Ljava/util/ArrayList;

    invoke-direct {v6}, Ljava/util/ArrayList;-><init>()V

    invoke-virtual {v5}, Landroid/content/res/TypedArray;->length()I

    move-result v7

    move v11, v3

    :goto_0
    if-ge v11, v7, :cond_0

    invoke-virtual {v5, v11}, Landroid/content/res/TypedArray;->getString(I)Ljava/lang/String;

    move-result-object v12

    invoke-virtual {v6, v12}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    add-int/lit8 v11, v11, 0x1

    goto :goto_0

    :cond_0
    invoke-virtual {v5}, Landroid/content/res/TypedArray;->recycle()V

    invoke-static {v6}, Lyl/t;->G(Ljava/lang/Iterable;)Ljava/util/ArrayList;

    move-result-object v5

    new-array v3, v3, [Ljava/lang/String;

    invoke-virtual {v5, v3}, Ljava/util/ArrayList;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;

    move-result-object v3

    check-cast v3, [Ljava/lang/String;

    invoke-virtual {v14, v4, v3}, Landroid/os/BaseBundle;->putStringArray(Ljava/lang/String;[Ljava/lang/String;)V

    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v3

    new-instance v4, Landroid/content/Intent;

    const-string v5, "com.premiumhelper.INTRO_ACTIVITY"

    invoke-direct {v4, v5}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v4, v5}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v4

    const/high16 v5, 0x10000

    invoke-virtual {v3, v4, v5}, Landroid/content/pm/PackageManager;->queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;

    move-result-object v3

    invoke-static {v3, v0}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-interface {v3}, Ljava/util/List;->size()I

    move-result v4

    const/16 v17, 0x0

    const/4 v6, 0x1

    if-nez v4, :cond_1

    move-object/from16 v3, v17

    goto :goto_1

    :cond_1
    invoke-interface {v3}, Ljava/util/List;->size()I

    move-result v4

    if-gt v4, v6, :cond_30

    invoke-static {v3}, Lyl/t;->I(Ljava/util/List;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/content/pm/ResolveInfo;

    iget-object v3, v3, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    iget-object v3, v3, Landroid/content/pm/ActivityInfo;->name:Ljava/lang/String;

    invoke-static {v3}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    :goto_1
    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v4

    new-instance v7, Landroid/content/Intent;

    const-string v11, "com.premiumhelper.MAIN_ACTIVITY"

    invoke-direct {v7, v11}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v11

    invoke-virtual {v7, v11}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v7

    invoke-virtual {v4, v7, v5}, Landroid/content/pm/PackageManager;->queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;

    move-result-object v4

    invoke-static {v4, v0}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-interface {v4}, Ljava/util/List;->size()I

    move-result v7

    if-ne v7, v6, :cond_2f

    invoke-static {v4}, Lyl/t;->I(Ljava/util/List;)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/content/pm/ResolveInfo;

    iget-object v4, v4, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    iget-object v4, v4, Landroid/content/pm/ActivityInfo;->name:Ljava/lang/String;

    invoke-static {v4}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v4

    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v7

    new-instance v11, Landroid/content/Intent;

    const-string v12, "com.premiumhelper.WARM_SPLASH_ACTIVITY"

    invoke-direct {v11, v12}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v12

    invoke-virtual {v11, v12}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v11

    invoke-virtual {v7, v11, v5}, Landroid/content/pm/PackageManager;->queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;

    move-result-object v5

    invoke-static {v5, v0}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-interface {v5}, Ljava/util/List;->size()I

    move-result v0

    if-nez v0, :cond_2

    const-class v0, Lcom/zipoapps/premiumhelper/ui/splash/WarmSplashActivity;

    goto :goto_2

    :cond_2
    invoke-interface {v5}, Ljava/util/List;->size()I

    move-result v0

    if-gt v0, v6, :cond_2e

    invoke-static {v5}, Lyl/t;->I(Ljava/util/List;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/content/pm/ResolveInfo;

    iget-object v0, v0, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    iget-object v0, v0, Landroid/content/pm/ActivityInfo;->name:Ljava/lang/String;

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    :goto_2
    sget-object v5, Lsi/g;->K:Lwi/d;

    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/CharSequence;

    if-eqz v5, :cond_2d

    invoke-interface {v5}, Ljava/lang/CharSequence;->length()I

    move-result v5

    if-eqz v5, :cond_2d

    sget-object v5, Lsi/g;->N:Lwi/d;

    iget-object v7, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v7}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/String;

    if-eqz v7, :cond_3

    invoke-virtual {v7}, Ljava/lang/String;->length()I

    move-result v7

    if-eqz v7, :cond_4

    :cond_3
    sget-object v7, Lsi/g;->O:Lwi/d;

    iget-object v11, v7, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v11}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v11

    check-cast v11, Ljava/lang/String;

    if-eqz v11, :cond_5

    invoke-virtual {v11}, Ljava/lang/String;->length()I

    move-result v11

    if-eqz v11, :cond_4

    goto :goto_3

    :cond_4
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v2, "PremiumHelper: ONE_TIME and ONETIME_OFFER_STRIKETHROUGH cannot be empty"

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_5
    :goto_3
    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/String;

    if-eqz v5, :cond_7

    invoke-virtual {v5}, Ljava/lang/String;->length()I

    move-result v5

    if-lez v5, :cond_7

    iget-object v5, v7, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/CharSequence;

    if-eqz v5, :cond_6

    invoke-interface {v5}, Ljava/lang/CharSequence;->length()I

    move-result v5

    if-eqz v5, :cond_6

    goto :goto_4

    :cond_6
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v2, "PremiumHelper: You must configure both ONE_TIME and ONETIME_OFFER_STRIKETHROUGH sku to show one-time relaunch view."

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_7
    :goto_4
    sget-object v5, Lsi/g;->s0:Lwi/d;

    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/CharSequence;

    if-eqz v5, :cond_2c

    invoke-interface {v5}, Ljava/lang/CharSequence;->length()I

    move-result v5

    if-eqz v5, :cond_2c

    sget-object v5, Lsi/g;->V:Lwi/d;

    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/CharSequence;

    if-eqz v5, :cond_2b

    invoke-interface {v5}, Ljava/lang/CharSequence;->length()I

    move-result v5

    if-eqz v5, :cond_2b

    sget-object v5, Lsi/g;->a:Lwi/d;

    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/String;

    if-nez v5, :cond_9

    :cond_8
    :goto_5
    move-object v5, v3

    goto/16 :goto_f

    :cond_9
    const-string v7, "APPLOVIN"

    invoke-virtual {v5, v7}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v7

    if-eqz v7, :cond_15

    sget-object v5, Lsi/g;->g:Lwi/d;

    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/CharSequence;

    if-eqz v7, :cond_b

    invoke-interface {v7}, Ljava/lang/CharSequence;->length()I

    move-result v7

    if-nez v7, :cond_a

    goto :goto_6

    :cond_a
    move-object/from16 v5, v17

    :cond_b
    :goto_6
    sget-object v7, Lsi/g;->i:Lwi/d;

    iget-object v7, v7, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v7}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v11

    check-cast v11, Ljava/lang/CharSequence;

    if-eqz v11, :cond_d

    invoke-interface {v11}, Ljava/lang/CharSequence;->length()I

    move-result v11

    if-nez v11, :cond_c

    goto :goto_7

    :cond_c
    move-object/from16 v7, v17

    :cond_d
    :goto_7
    sget-object v11, Lsi/g;->h:Lwi/d;

    iget-object v11, v11, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v11}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v12

    check-cast v12, Ljava/lang/CharSequence;

    if-eqz v12, :cond_f

    invoke-interface {v12}, Ljava/lang/CharSequence;->length()I

    move-result v12

    if-nez v12, :cond_e

    goto :goto_8

    :cond_e
    move-object/from16 v11, v17

    :cond_f
    :goto_8
    sget-object v12, Lsi/g;->j:Lwi/d;

    iget-object v12, v12, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v12}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v13

    check-cast v13, Ljava/lang/CharSequence;

    if-eqz v13, :cond_11

    invoke-interface {v13}, Ljava/lang/CharSequence;->length()I

    move-result v13

    if-nez v13, :cond_10

    goto :goto_9

    :cond_10
    move-object/from16 v12, v17

    :cond_11
    :goto_9
    sget-object v13, Lsi/g;->k:Lwi/d;

    iget-object v13, v13, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v13}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v18

    check-cast v18, Ljava/lang/CharSequence;

    if-eqz v18, :cond_13

    invoke-interface/range {v18 .. v18}, Ljava/lang/CharSequence;->length()I

    move-result v18

    if-nez v18, :cond_12

    goto :goto_a

    :cond_12
    move-object/from16 v13, v17

    :cond_13
    :goto_a
    filled-new-array {v5, v7, v11, v12, v13}, [Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lyl/n;->A([Ljava/lang/Object;)Ljava/util/ArrayList;

    move-result-object v5

    invoke-virtual {v5}, Ljava/util/ArrayList;->isEmpty()Z

    move-result v7

    if-eqz v7, :cond_14

    goto/16 :goto_5

    :cond_14
    new-instance v0, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "PremiumHelper: ads_provider is set to APPLOVIN but the following ad unit IDs are not defined locally: "

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_15
    const-string v7, "ADMOB"

    invoke-virtual {v5, v7}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    move-result v5

    if-eqz v5, :cond_8

    sget-object v5, Lsi/g;->c:Lwi/d;

    iget-object v5, v5, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v5}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/CharSequence;

    if-eqz v7, :cond_17

    invoke-interface {v7}, Ljava/lang/CharSequence;->length()I

    move-result v7

    if-nez v7, :cond_16

    goto :goto_b

    :cond_16
    move-object/from16 v5, v17

    :cond_17
    :goto_b
    sget-object v7, Lsi/g;->d:Lwi/d;

    iget-object v7, v7, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v7}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v11

    check-cast v11, Ljava/lang/CharSequence;

    if-eqz v11, :cond_19

    invoke-interface {v11}, Ljava/lang/CharSequence;->length()I

    move-result v11

    if-nez v11, :cond_18

    goto :goto_c

    :cond_18
    move-object/from16 v7, v17

    :cond_19
    :goto_c
    sget-object v11, Lsi/g;->e:Lwi/d;

    iget-object v11, v11, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v11}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v12

    check-cast v12, Ljava/lang/CharSequence;

    if-eqz v12, :cond_1b

    invoke-interface {v12}, Ljava/lang/CharSequence;->length()I

    move-result v12

    if-nez v12, :cond_1a

    goto :goto_d

    :cond_1a
    move-object/from16 v11, v17

    :cond_1b
    :goto_d
    sget-object v12, Lsi/g;->f:Lwi/d;

    iget-object v12, v12, Lwi/d;->a:Ljava/lang/String;

    invoke-virtual {v15, v12}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v13

    check-cast v13, Ljava/lang/CharSequence;

    if-eqz v13, :cond_1d

    invoke-interface {v13}, Ljava/lang/CharSequence;->length()I

    move-result v13

    if-nez v13, :cond_1c

    goto :goto_e

    :cond_1c
    move-object/from16 v12, v17

    :cond_1d
    :goto_e
    filled-new-array {v5, v7, v11, v12}, [Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lyl/n;->A([Ljava/lang/Object;)Ljava/util/ArrayList;

    move-result-object v5

    invoke-virtual {v5}, Ljava/util/ArrayList;->isEmpty()Z

    move-result v7

    if-eqz v7, :cond_1e

    goto/16 :goto_5

    :cond_1e
    new-instance v0, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    const-string v3, "PremiumHelper: ads_provider is set to ADMOB but the following ad unit IDs are not defined locally: "

    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :goto_f
    new-instance v3, Lcom/zipoapps/premiumhelper/configuration/appconfig/PremiumHelperConfiguration;

    const/4 v7, 0x0

    move-object/from16 v13, v16

    const/16 v16, 0x0

    const/4 v11, 0x0

    const/4 v12, 0x0

    move/from16 v23, v6

    move-object v6, v0

    move/from16 v0, v23

    invoke-direct/range {v3 .. v16}, Lcom/zipoapps/premiumhelper/configuration/appconfig/PremiumHelperConfiguration;-><init>(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;Lcom/zipoapps/premiumhelper/util/PHMessagingService$a;[I[I[IZZLpj/p;Landroid/os/Bundle;Ljava/util/Map;Lkotlin/jvm/internal/g;)V

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    sget-object v4, Lcom/zipoapps/premiumhelper/d;->O:Lcom/zipoapps/premiumhelper/d;

    if-eqz v4, :cond_1f

    goto :goto_11

    :cond_1f
    monitor-enter v2

    :try_start_0
    sget-object v4, Lcom/zipoapps/premiumhelper/d;->O:Lcom/zipoapps/premiumhelper/d;

    if-nez v4, :cond_21

    sget-object v4, Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker;->b:Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker$a;

    invoke-virtual {v4}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    invoke-static {}, Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker$a;->a()Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker;

    move-result-object v4

    iget-object v4, v4, Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker;->a:Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker$StartupData;

    if-eqz v4, :cond_20

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v5

    invoke-virtual {v4, v5, v6}, Lcom/zipoapps/premiumhelper/performance/StartupPerformanceTracker$StartupData;->setPhStartTimestamp(J)V

    :cond_20
    new-instance v4, Lcom/zipoapps/premiumhelper/d;

    invoke-direct {v4, v1, v3}, Lcom/zipoapps/premiumhelper/d;-><init>(Lcom/jazibkhan/equalizer/MyApplication;Lcom/zipoapps/premiumhelper/configuration/appconfig/PremiumHelperConfiguration;)V

    sput-object v4, Lcom/zipoapps/premiumhelper/d;->O:Lcom/zipoapps/premiumhelper/d;

    invoke-static {v4}, Lcom/zipoapps/premiumhelper/d;->d(Lcom/zipoapps/premiumhelper/d;)V

    goto :goto_10

    :catchall_0
    move-exception v0

    goto/16 :goto_15

    :cond_21
    :goto_10
    sget-object v3, Lxl/e0;->a:Lxl/e0;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit v2

    :goto_11
    invoke-static {v1}, Lkf/f;->r(Landroid/content/Context;)V

    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_2a

    const-string v3, "539584hfsjdhfj2342dsf"

    const-wide/16 v4, 0x0

    invoke-interface {v2, v3, v4, v5}, Landroid/content/SharedPreferences;->getLong(Ljava/lang/String;J)J

    move-result-wide v2

    cmp-long v2, v2, v4

    if-nez v2, :cond_23

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v2

    sget-object v4, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v4, :cond_22

    invoke-interface {v4}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v4

    const-string v5, "539584hfsjdhfj2342dsf"

    invoke-interface {v4, v5, v2, v3}, Landroid/content/SharedPreferences$Editor;->putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v4}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_12

    :cond_22
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_23
    :goto_12
    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_29

    invoke-interface {v2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v2

    const-string v3, "migration"

    invoke-interface {v2, v3, v0}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v2}, Landroid/content/SharedPreferences$Editor;->apply()V

    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v3, 0x1d

    if-ge v2, v3, :cond_26

    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_25

    const-string v3, "night_mode"

    invoke-interface {v2, v3}, Landroid/content/SharedPreferences;->contains(Ljava/lang/String;)Z

    move-result v2

    if-nez v2, :cond_26

    sget-object v2, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v2, :cond_24

    invoke-interface {v2}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v2

    const-string v3, "night_mode"

    invoke-static {v0}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;

    move-result-object v0

    invoke-interface {v2, v3, v0}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    invoke-interface {v2}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_13

    :cond_24
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_25
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_26
    :goto_13
    sget-object v0, Lkf/f;->a:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_28

    const-string v2, "night_mode"

    const-string v3, "-1"

    invoke-interface {v0, v2, v3}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_27

    invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result v0

    goto :goto_14

    :cond_27
    const/4 v0, -0x1

    :goto_14
    invoke-static {v0}, Lk/e;->B(I)V

    return-void

    :cond_28
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_29
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :cond_2a
    const-string v0, "mPref"

    invoke-static {v0}, Lkotlin/jvm/internal/l;->k(Ljava/lang/String;)V

    throw v17

    :goto_15
    monitor-exit v2

    throw v0

    :cond_2b
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v2, "PremiumHelper: You must configure Privacy url"

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_2c
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v2, "PremiumHelper: You must configure Terms and Conditions url"

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_2d
    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v2, "PremiumHelper: Please configure default name for main offer SKU."

    invoke-direct {v0, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_2e
    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v2, "PremiumHelper: Please configure only one activity class with filter action <com.premiumhelper.WARM_SPLASH_ACTIVITY>"

    invoke-direct {v0, v2}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_2f
    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v2, "PremiumHelper: Please configure only one activity class with filter action <com.premiumhelper.MAIN_ACTIVITY>"

    invoke-direct {v0, v2}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_30
    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v2, "PremiumHelper: Please configure only one activity class with filter action <com.premiumhelper.INTRO_ACTIVITY>"

    invoke-direct {v0, v2}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method public final onCreate(Landroidx/lifecycle/g0;)V
    .locals 0

    return-void
.end method

.method public final onDestroy(Landroidx/lifecycle/g0;)V
    .locals 0

    return-void
.end method

.method public final onPause(Landroidx/lifecycle/g0;)V
    .locals 0

    const/4 p1, 0x0

    sput-boolean p1, Lcom/jazibkhan/equalizer/MyApplication;->b:Z

    return-void
.end method

.method public final onResume(Landroidx/lifecycle/g0;)V
    .locals 0

    return-void
.end method

.method public final onStart(Landroidx/lifecycle/g0;)V
    .locals 0

    const/4 p1, 0x1

    sput-boolean p1, Lcom/jazibkhan/equalizer/MyApplication;->b:Z

    return-void
.end method

.method public final onStop(Landroidx/lifecycle/g0;)V
    .locals 0

    return-void
.end method
