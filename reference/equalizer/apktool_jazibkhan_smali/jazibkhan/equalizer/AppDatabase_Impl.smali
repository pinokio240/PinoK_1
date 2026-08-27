.class public final Lcom/jazibkhan/equalizer/AppDatabase_Impl;
.super Lcom/jazibkhan/equalizer/AppDatabase;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u000c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003\u00a8\u0006\u0004"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/AppDatabase_Impl;",
        "Lcom/jazibkhan/equalizer/AppDatabase;",
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


# instance fields
.field public final s:Lxl/s;


# direct methods
.method public constructor <init>()V
    .locals 1

    invoke-direct {p0}, Lcom/jazibkhan/equalizer/AppDatabase;-><init>()V

    new-instance v0, Lye/a;

    invoke-direct {v0, p0}, Lye/a;-><init>(Lcom/jazibkhan/equalizer/AppDatabase_Impl;)V

    invoke-static {v0}, Lxl/k;->b(Lmm/a;)Lxl/s;

    move-result-object v0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/AppDatabase_Impl;->s:Lxl/s;

    return-void
.end method


# virtual methods
.method public final d(Ljava/util/LinkedHashMap;)Ljava/util/List;
    .locals 0

    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    return-object p1
.end method

.method public final e()Lt7/j;
    .locals 6

    new-instance v0, Ljava/util/LinkedHashMap;

    invoke-direct {v0}, Ljava/util/LinkedHashMap;-><init>()V

    new-instance v1, Ljava/util/LinkedHashMap;

    invoke-direct {v1}, Ljava/util/LinkedHashMap;-><init>()V

    new-instance v2, Lt7/j;

    const-string v3, "audio_devices"

    const-string v4, "auto_apply_config"

    const-string v5, "custom_preset"

    filled-new-array {v5, v3, v4}, [Ljava/lang/String;

    move-result-object v3

    invoke-direct {v2, p0, v0, v1, v3}, Lt7/j;-><init>(Lt7/x;Ljava/util/HashMap;Ljava/util/HashMap;[Ljava/lang/String;)V

    return-object v2
.end method

.method public final f()Lt7/f0;
    .locals 1

    new-instance v0, Lye/b;

    invoke-direct {v0, p0}, Lye/b;-><init>(Lcom/jazibkhan/equalizer/AppDatabase_Impl;)V

    return-object v0
.end method

.method public final l()Ljava/util/Set;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/Set<",
            "Ltm/d<",
            "Ljava/lang/Object;",
            ">;>;"
        }
    .end annotation

    new-instance v0, Ljava/util/LinkedHashSet;

    invoke-direct {v0}, Ljava/util/LinkedHashSet;-><init>()V

    return-object v0
.end method

.method public final n()Ljava/util/LinkedHashMap;
    .locals 3

    new-instance v0, Ljava/util/LinkedHashMap;

    invoke-direct {v0}, Ljava/util/LinkedHashMap;-><init>()V

    const-class v1, Lye/d;

    sget-object v2, Lkotlin/jvm/internal/e0;->a:Lkotlin/jvm/internal/f0;

    invoke-virtual {v2, v1}, Lkotlin/jvm/internal/f0;->b(Ljava/lang/Class;)Ltm/d;

    move-result-object v1

    sget-object v2, Lyl/v;->b:Lyl/v;

    invoke-interface {v0, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-object v0
.end method

.method public final w()Lye/d;
    .locals 1

    iget-object v0, p0, Lcom/jazibkhan/equalizer/AppDatabase_Impl;->s:Lxl/s;

    invoke-virtual {v0}, Lxl/s;->getValue()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lye/d;

    return-object v0
.end method
