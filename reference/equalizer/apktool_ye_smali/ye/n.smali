.class public final synthetic Lye/n;
.super Ljava/lang/Object;

# interfaces
.implements Lmm/l;


# instance fields
.field public final synthetic b:Lye/r;

.field public final synthetic c:Lze/c;


# direct methods
.method public synthetic constructor <init>(Lye/r;Lze/c;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lye/n;->b:Lye/r;

    iput-object p2, p0, Lye/n;->c:Lze/c;

    return-void
.end method


# virtual methods
.method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 2

    check-cast p1, Lc8/b;

    const-string v0, "_connection"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/l;->f(Ljava/lang/Object;Ljava/lang/String;)V

    iget-object v0, p0, Lye/n;->b:Lye/r;

    iget-object v0, v0, Lye/r;->d:Lye/r$c;

    iget-object v1, p0, Lye/n;->c:Lze/c;

    invoke-virtual {v0, p1, v1}, Landroidx/work/x;->T(Lc8/b;Ljava/lang/Object;)J

    move-result-wide v0

    invoke-static {v0, v1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p1

    return-object p1
.end method
