.class public final synthetic Lye/a;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/a;


# instance fields
.field public final synthetic b:Lcom/jazibkhan/equalizer/AppDatabase_Impl;


# direct methods
.method public synthetic constructor <init>(Lcom/jazibkhan/equalizer/AppDatabase_Impl;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lye/a;->b:Lcom/jazibkhan/equalizer/AppDatabase_Impl;

    return-void
.end method


# virtual methods
.method public final invoke()Ljava/lang/Object;
    .locals 2

    new-instance v0, Lye/r;

    iget-object v1, p0, Lye/a;->b:Lcom/jazibkhan/equalizer/AppDatabase_Impl;

    invoke-direct {v0, v1}, Lye/r;-><init>(Lt7/x;)V

    return-object v0
.end method
