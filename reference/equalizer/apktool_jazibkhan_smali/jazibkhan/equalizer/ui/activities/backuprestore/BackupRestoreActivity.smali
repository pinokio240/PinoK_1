.class public final Lcom/jazibkhan/equalizer/ui/activities/backuprestore/BackupRestoreActivity;
.super Ldf/a;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u000c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003\u00a8\u0006\u0004"
    }
    d2 = {
        "Lcom/jazibkhan/equalizer/ui/activities/backuprestore/BackupRestoreActivity;",
        "Ldf/a;",
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
.field public static final synthetic d:I


# instance fields
.field public c:Lmi/c;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ldf/a;-><init>()V

    return-void
.end method


# virtual methods
.method public final onCreate(Landroid/os/Bundle;)V
    .locals 3

    invoke-super {p0, p1}, Ldf/a;->onCreate(Landroid/os/Bundle;)V

    const v0, 0x7f0d001d

    invoke-virtual {p0, v0}, Landroidx/appcompat/app/AppCompatActivity;->setContentView(I)V

    new-instance v0, Lmi/c;

    const-string v1, "android.permission.READ_EXTERNAL_STORAGE"

    const-string v2, "android.permission.WRITE_EXTERNAL_STORAGE"

    filled-new-array {v1, v2}, [Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, p0, v1}, Lmi/c;-><init>(Lcom/jazibkhan/equalizer/ui/activities/backuprestore/BackupRestoreActivity;[Ljava/lang/String;)V

    new-instance v1, Lef/a;

    const/4 v2, 0x0

    invoke-direct {v1, v2}, Lef/a;-><init>(I)V

    iput-object v1, v0, Lmi/c;->e:Lef/a;

    new-instance v1, Lef/b;

    invoke-direct {v1, p0, v2}, Lef/b;-><init>(Ljava/lang/Object;I)V

    iput-object v1, v0, Lmi/c;->f:Lef/b;

    new-instance v1, Lef/c;

    invoke-direct {v1, v0}, Lef/c;-><init>(Lmi/c;)V

    iput-object v1, v0, Lmi/c;->g:Lef/c;

    iput-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/backuprestore/BackupRestoreActivity;->c:Lmi/c;

    if-nez p1, :cond_0

    invoke-virtual {p0}, Landroidx/fragment/app/u;->getSupportFragmentManager()Landroidx/fragment/app/h0;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    new-instance v0, Landroidx/fragment/app/a;

    invoke-direct {v0, p1}, Landroidx/fragment/app/a;-><init>(Landroidx/fragment/app/h0;)V

    new-instance p1, Lef/f;

    invoke-direct {p1}, Lef/f;-><init>()V

    const v1, 0x7f0a0102

    invoke-virtual {v0, p1, v1}, Landroidx/fragment/app/r0;->d(Landroidx/fragment/app/p;I)V

    invoke-virtual {v0}, Landroidx/fragment/app/a;->g()V

    :cond_0
    return-void
.end method
