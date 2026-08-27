.class public final Lcom/jazibkhan/equalizer/a;
.super Ljava/lang/Object;


# instance fields
.field public final a:Lye/d;

.field public final b:Llp/f;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Llp/f<",
            "Ljava/util/List<",
            "Lye/c;",
            ">;>;"
        }
    .end annotation
.end field

.field public final c:Llp/f;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Llp/f<",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;>;"
        }
    .end annotation
.end field

.field public final d:Llp/f;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Llp/f<",
            "Ljava/util/List<",
            "Lze/a;",
            ">;>;"
        }
    .end annotation
.end field

.field public e:Lbf/a;

.field public final f:Llp/h0;


# direct methods
.method public constructor <init>(Landroid/app/Application;)V
    .locals 4

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    sget-object v0, Lcom/jazibkhan/equalizer/AppDatabase;->l:Lcom/jazibkhan/equalizer/AppDatabase$f;

    invoke-virtual {v0, p1}, Lcom/jazibkhan/equalizer/AppDatabase$f;->a(Landroid/content/Context;)Lcom/jazibkhan/equalizer/AppDatabase;

    move-result-object p1

    invoke-virtual {p1}, Lcom/jazibkhan/equalizer/AppDatabase;->w()Lye/d;

    move-result-object p1

    iput-object p1, p0, Lcom/jazibkhan/equalizer/a;->a:Lye/d;

    invoke-interface {p1}, Lye/d;->j()Lv7/m;

    move-result-object v0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/a;->b:Llp/f;

    invoke-interface {p1}, Lye/d;->b()Lv7/m;

    move-result-object v0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/a;->c:Llp/f;

    invoke-interface {p1}, Lye/d;->f()Lv7/m;

    move-result-object v0

    iput-object v0, p0, Lcom/jazibkhan/equalizer/a;->d:Llp/f;

    invoke-interface {p1}, Lye/d;->n()Lv7/m;

    invoke-interface {p1}, Lye/d;->j()Lv7/m;

    move-result-object v0

    invoke-interface {p1}, Lye/d;->n()Lv7/m;

    move-result-object p1

    new-instance v1, Lcom/jazibkhan/equalizer/a$a;

    const/4 v2, 0x0

    const/4 v3, 0x3

    invoke-direct {v1, v3, v2}, Ldm/i;-><init>(ILbm/e;)V

    new-instance v2, Llp/h0;

    invoke-direct {v2, v0, p1, v1}, Llp/h0;-><init>(Llp/f;Llp/f;Lmm/q;)V

    iput-object v2, p0, Lcom/jazibkhan/equalizer/a;->f:Llp/h0;

    return-void
.end method

.method public static final a(Lcom/jazibkhan/equalizer/a;)Lbf/a;
    .locals 3

    iget-object v0, p0, Lcom/jazibkhan/equalizer/a;->e:Lbf/a;

    if-nez v0, :cond_0

    new-instance v0, Ldr/b0$b;

    invoke-direct {v0}, Ldr/b0$b;-><init>()V

    new-instance v1, Lcom/google/gson/Gson;

    invoke-direct {v1}, Lcom/google/gson/Gson;-><init>()V

    new-instance v2, Ler/a;

    invoke-direct {v2, v1}, Ler/a;-><init>(Lcom/google/gson/Gson;)V

    iget-object v1, v0, Ldr/b0$b;->d:Ljava/util/ArrayList;

    invoke-virtual {v1, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    const-string v1, "https://k7dbmkqmme.execute-api.ap-south-1.amazonaws.com/prod/v1/"

    invoke-virtual {v0, v1}, Ldr/b0$b;->a(Ljava/lang/String;)V

    new-instance v1, Lgq/y$a;

    invoke-direct {v1}, Lgq/y$a;-><init>()V

    new-instance v2, Lbf/f;

    invoke-direct {v2}, Ljava/lang/Object;-><init>()V

    invoke-virtual {v1, v2}, Lgq/y$a;->a(Lgq/v;)V

    new-instance v2, Lgq/y;

    invoke-direct {v2, v1}, Lgq/y;-><init>(Lgq/y$a;)V

    iput-object v2, v0, Ldr/b0$b;->b:Lgq/y;

    invoke-virtual {v0}, Ldr/b0$b;->b()Ldr/b0;

    move-result-object v0

    const-class v1, Lbf/a;

    invoke-virtual {v0, v1}, Ldr/b0;->b(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v0

    const-string v1, "create(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/l;->e(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v0, Lbf/a;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/a;->e:Lbf/a;

    :cond_0
    iget-object p0, p0, Lcom/jazibkhan/equalizer/a;->e:Lbf/a;

    invoke-static {p0}, Lkotlin/jvm/internal/l;->c(Ljava/lang/Object;)V

    return-object p0
.end method
