.class public final Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;
.super Landroidx/viewpager/widget/ViewPager$n;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->onCreate(Landroid/os/Bundle;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = null
.end annotation


# instance fields
.field public final synthetic a:Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;

.field public final synthetic b:Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;


# direct methods
.method public constructor <init>(Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;)V
    .locals 0

    iput-object p1, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;->a:Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;

    iput-object p2, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;->b:Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;

    invoke-direct {p0}, Landroidx/viewpager/widget/ViewPager$n;-><init>()V

    return-void
.end method


# virtual methods
.method public final onPageSelected(I)V
    .locals 2

    iget-object v0, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;->a:Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;

    invoke-virtual {v0}, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$a;->getCount()I

    move-result v0

    add-int/lit8 v0, v0, -0x1

    iget-object v1, p0, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity$b;->b:Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;

    if-ge p1, v0, :cond_0

    iget-object p1, v1, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->e:Lcom/google/android/material/button/MaterialButton;

    if-eqz p1, :cond_1

    const v0, 0x7f1301f7

    invoke-virtual {v1, v0}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void

    :cond_0
    iget-object p1, v1, Lcom/jazibkhan/equalizer/ui/activities/WelcomeActivity;->e:Lcom/google/android/material/button/MaterialButton;

    if-eqz p1, :cond_1

    const v0, 0x7f1300cf

    invoke-virtual {v1, v0}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    :cond_1
    return-void
.end method
