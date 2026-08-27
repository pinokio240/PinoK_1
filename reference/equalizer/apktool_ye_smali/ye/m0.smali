.class public final Lye/m0;
.super Ljava/lang/Object;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lye/m0$a;,
        Lye/m0$b;,
        Lye/m0$c;,
        Lye/m0$d;,
        Lye/m0$e;,
        Lye/m0$f;
    }
.end annotation


# static fields
.field public static final a:Lye/m0$c;

.field public static final b:Lye/m0$a;

.field public static final c:Lye/m0$d;

.field public static final d:Lye/m0$e;

.field public static final e:Lye/m0$f;

.field public static final f:Lye/m0$b;

.field public static g:I

.field public static h:I

.field public static i:Landroid/media/audiofx/DynamicsProcessing;

.field public static j:Landroid/media/audiofx/DynamicsProcessing$Config$Builder;

.field public static k:Z

.field public static l:I

.field public static m:I

.field public static n:I

.field public static o:I

.field public static p:Z

.field public static q:Z


# direct methods
.method static constructor <clinit>()V
    .locals 1

    new-instance v0, Lye/m0$c;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lye/m0;->a:Lye/m0$c;

    new-instance v0, Lye/m0$a;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lye/m0;->b:Lye/m0$a;

    new-instance v0, Lye/m0$d;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lye/m0;->c:Lye/m0$d;

    new-instance v0, Lye/m0$e;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lye/m0;->d:Lye/m0$e;

    new-instance v0, Lye/m0$f;

    invoke-direct {v0}, Lye/m0$f;-><init>()V

    sput-object v0, Lye/m0;->e:Lye/m0$f;

    new-instance v0, Lye/m0$b;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lye/m0;->f:Lye/m0$b;

    const/4 v0, 0x5

    sput v0, Lye/m0;->g:I

    const/16 v0, 0x50

    sput v0, Lye/m0;->l:I

    const/16 v0, 0xa

    sput v0, Lye/m0;->m:I

    const/16 v0, 0xf

    sput v0, Lye/m0;->n:I

    const/16 v0, 0x14

    sput v0, Lye/m0;->o:I

    return-void
.end method

.method public static a()V
    .locals 2

    :try_start_0
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_0

    invoke-static {v0}, Lye/x;->a(Landroid/media/audiofx/DynamicsProcessing;)V

    :cond_0
    sget-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;

    if-eqz v0, :cond_1

    invoke-static {v0}, Lye/y;->a(Landroid/media/audiofx/DynamicsProcessing;)V

    :cond_1
    const/4 v0, 0x0

    sput-object v0, Lye/m0;->i:Landroid/media/audiofx/DynamicsProcessing;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    invoke-static {}, Lpc/d;->a()Lpc/d;

    move-result-object v1

    invoke-virtual {v1, v0}, Lpc/d;->b(Ljava/lang/Throwable;)V

    return-void
.end method
