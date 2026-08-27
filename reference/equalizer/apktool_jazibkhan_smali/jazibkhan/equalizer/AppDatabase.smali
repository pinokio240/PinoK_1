.class public abstract Lcom/jazibkhan/equalizer/AppDatabase;
.super Lt7/x;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/jazibkhan/equalizer/AppDatabase$f;
    }
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u000c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0004\u0008\'\u0018\u00002\u00020\u0001:\u0001\u0004B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003\u00a8\u0006\u0005"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/AppDatabase;",
        "Lt7/x;",
        "<init>",
        "()V",
        "f",
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
.field public static final l:Lcom/jazibkhan/equalizer/AppDatabase$f;

.field public static volatile m:Lcom/jazibkhan/equalizer/AppDatabase;

.field public static final n:Lcom/jazibkhan/equalizer/AppDatabase$a;

.field public static final o:Lcom/jazibkhan/equalizer/AppDatabase$b;

.field public static final p:Lcom/jazibkhan/equalizer/AppDatabase$c;

.field public static final q:Lcom/jazibkhan/equalizer/AppDatabase$d;

.field public static final r:Lcom/jazibkhan/equalizer/AppDatabase$e;


# direct methods
.method static constructor <clinit>()V
    .locals 3

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$f;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->l:Lcom/jazibkhan/equalizer/AppDatabase$f;

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$a;

    const/4 v1, 0x1

    const/4 v2, 0x2

    invoke-direct {v0, v1, v2}, Lw7/a;-><init>(II)V

    sput-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->n:Lcom/jazibkhan/equalizer/AppDatabase$a;

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$b;

    const/4 v1, 0x3

    invoke-direct {v0, v2, v1}, Lw7/a;-><init>(II)V

    sput-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->o:Lcom/jazibkhan/equalizer/AppDatabase$b;

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$c;

    const/4 v2, 0x4

    invoke-direct {v0, v1, v2}, Lw7/a;-><init>(II)V

    sput-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->p:Lcom/jazibkhan/equalizer/AppDatabase$c;

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$d;

    const/4 v1, 0x5

    invoke-direct {v0, v2, v1}, Lw7/a;-><init>(II)V

    sput-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->q:Lcom/jazibkhan/equalizer/AppDatabase$d;

    new-instance v0, Lcom/jazibkhan/equalizer/AppDatabase$e;

    const/4 v2, 0x6

    invoke-direct {v0, v1, v2}, Lw7/a;-><init>(II)V

    sput-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->r:Lcom/jazibkhan/equalizer/AppDatabase$e;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Lt7/x;-><init>()V

    return-void
.end method


# virtual methods
.method public abstract w()Lye/d;
.end method
