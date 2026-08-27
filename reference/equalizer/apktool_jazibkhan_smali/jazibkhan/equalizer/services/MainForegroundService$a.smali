.class public final Lcom/jazibkhan/equalizer/services/MainForegroundService$a;
.super Landroid/os/Binder;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/jazibkhan/equalizer/services/MainForegroundService;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x11
    name = "a"
.end annotation


# instance fields
.field public final synthetic b:Lcom/jazibkhan/equalizer/services/MainForegroundService;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/services/MainForegroundService;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    iput-object p1, p0, Lcom/jazibkhan/equalizer/services/MainForegroundService$a;->b:Lcom/jazibkhan/equalizer/services/MainForegroundService;

    invoke-direct {p0}, Landroid/os/Binder;-><init>()V

    return-void
.end method
