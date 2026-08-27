.class public Lcom/jazibkhan/equalizer/views/Curve;
.super Landroid/view/View;


# static fields
.field public static final n:I

.field public static final o:I

.field public static final p:I

.field public static final q:I


# instance fields
.field public final b:Landroid/graphics/Rect;

.field public c:Landroid/graphics/Paint;

.field public final d:Landroid/graphics/Path;

.field public final e:Landroid/graphics/Paint;

.field public final f:Landroid/graphics/Path;

.field public final g:I

.field public final h:I

.field public final i:I

.field public j:Z

.field public final k:I

.field public final l:F

.field public m:[F


# direct methods
.method static constructor <clinit>()V
    .locals 3

    const/16 v0, 0x64

    const/16 v1, 0xef

    const/16 v2, 0x9a

    invoke-static {v0, v1, v2, v2}, Landroid/graphics/Color;->argb(IIII)I

    move-result v0

    sput v0, Lcom/jazibkhan/equalizer/views/Curve;->n:I

    const/4 v0, 0x0

    invoke-static {v0, v1, v2, v2}, Landroid/graphics/Color;->argb(IIII)I

    move-result v0

    sput v0, Lcom/jazibkhan/equalizer/views/Curve;->o:I

    invoke-static {v1, v2, v2}, Landroid/graphics/Color;->rgb(III)I

    move-result v0

    sput v0, Lcom/jazibkhan/equalizer/views/Curve;->p:I

    const/16 v0, 0x1a

    const/16 v1, 0xff

    invoke-static {v0, v1, v1, v1}, Landroid/graphics/Color;->argb(IIII)I

    move-result v0

    sput v0, Lcom/jazibkhan/equalizer/views/Curve;->q:I

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Landroid/util/AttributeSet;)V
    .locals 7

    invoke-direct {p0, p1, p2}, Landroid/view/View;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V

    new-instance v0, Landroid/graphics/Rect;

    invoke-direct {v0}, Landroid/graphics/Rect;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->b:Landroid/graphics/Rect;

    new-instance v0, Landroid/graphics/Paint;

    invoke-direct {v0}, Landroid/graphics/Paint;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    new-instance v0, Landroid/graphics/Path;

    invoke-direct {v0}, Landroid/graphics/Path;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->d:Landroid/graphics/Path;

    new-instance v0, Landroid/graphics/Paint;

    invoke-direct {v0}, Landroid/graphics/Paint;-><init>()V

    iput-object v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->e:Landroid/graphics/Paint;

    new-instance v1, Landroid/graphics/Path;

    invoke-direct {v1}, Landroid/graphics/Path;-><init>()V

    iput-object v1, p0, Lcom/jazibkhan/equalizer/views/Curve;->f:Landroid/graphics/Path;

    sget v1, Lcom/jazibkhan/equalizer/views/Curve;->p:I

    iput v1, p0, Lcom/jazibkhan/equalizer/views/Curve;->g:I

    sget v2, Lcom/jazibkhan/equalizer/views/Curve;->n:I

    iput v2, p0, Lcom/jazibkhan/equalizer/views/Curve;->h:I

    sget v2, Lcom/jazibkhan/equalizer/views/Curve;->o:I

    iput v2, p0, Lcom/jazibkhan/equalizer/views/Curve;->i:I

    const/4 v2, 0x1

    iput-boolean v2, p0, Lcom/jazibkhan/equalizer/views/Curve;->j:Z

    sget v3, Lcom/jazibkhan/equalizer/views/Curve;->q:I

    iput v3, p0, Lcom/jazibkhan/equalizer/views/Curve;->k:I

    const/high16 v4, 0x40800000    # 4.0f

    iput v4, p0, Lcom/jazibkhan/equalizer/views/Curve;->l:F

    sget-object v5, Landroid/graphics/Paint$Style;->STROKE:Landroid/graphics/Paint$Style;

    invoke-virtual {v0, v5}, Landroid/graphics/Paint;->setStyle(Landroid/graphics/Paint$Style;)V

    sget-object v5, Landroid/graphics/Paint$Cap;->ROUND:Landroid/graphics/Paint$Cap;

    invoke-virtual {v0, v5}, Landroid/graphics/Paint;->setStrokeCap(Landroid/graphics/Paint$Cap;)V

    invoke-virtual {v0, v4}, Landroid/graphics/Paint;->setStrokeWidth(F)V

    invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setColor(I)V

    invoke-virtual {v0, v2}, Landroid/graphics/Paint;->setAntiAlias(Z)V

    iget-object v5, p0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    sget-object v6, Landroid/graphics/Paint$Style;->FILL:Landroid/graphics/Paint$Style;

    invoke-virtual {v5, v6}, Landroid/graphics/Paint;->setStyle(Landroid/graphics/Paint$Style;)V

    iget-object v5, p0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    invoke-virtual {v5, v1}, Landroid/graphics/Paint;->setColor(I)V

    iget-object v5, p0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    invoke-virtual {v5, v2}, Landroid/graphics/Paint;->setAntiAlias(Z)V

    sget-object v5, Lye/r0;->b:[I

    invoke-virtual {p1, p2, v5}, Landroid/content/Context;->obtainStyledAttributes(Landroid/util/AttributeSet;[I)Landroid/content/res/TypedArray;

    move-result-object p1

    const/4 p2, 0x3

    invoke-virtual {p1, p2, v4}, Landroid/content/res/TypedArray;->getDimension(IF)F

    move-result p2

    invoke-virtual {v0, p2}, Landroid/graphics/Paint;->setStrokeWidth(F)V

    const/4 p2, 0x6

    invoke-virtual {p1, p2, v1}, Landroid/content/res/TypedArray;->getColor(II)I

    move-result p2

    iput p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->h:I

    const/16 v0, 0x7f

    invoke-static {p2, v0}, Lm3/d;->f(II)I

    move-result p2

    iput p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->h:I

    const/4 p2, 0x5

    invoke-virtual {p1, p2, v1}, Landroid/content/res/TypedArray;->getColor(II)I

    move-result p2

    iput p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->i:I

    invoke-virtual {p1, v2, v2}, Landroid/content/res/TypedArray;->getBoolean(IZ)Z

    move-result p2

    iput-boolean p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->j:Z

    const/4 p2, 0x4

    invoke-virtual {p1, p2, v3}, Landroid/content/res/TypedArray;->getColor(II)I

    move-result p2

    iput p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->k:I

    const/4 p2, 0x0

    invoke-virtual {p1, p2, v1}, Landroid/content/res/TypedArray;->getColor(II)I

    move-result p2

    iput p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->g:I

    const/4 p2, 0x2

    invoke-virtual {p1, p2, v4}, Landroid/content/res/TypedArray;->getDimension(IF)F

    move-result p2

    iput p2, p0, Lcom/jazibkhan/equalizer/views/Curve;->l:F

    invoke-virtual {p1}, Landroid/content/res/TypedArray;->recycle()V

    return-void
.end method


# virtual methods
.method public final a(Landroid/graphics/Path;FF)V
    .locals 11

    invoke-virtual {p1}, Landroid/graphics/Path;->reset()V

    iget-object v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->b:Landroid/graphics/Rect;

    iget v0, v0, Landroid/graphics/Rect;->left:I

    int-to-float v0, v0

    iget-object v1, p0, Lcom/jazibkhan/equalizer/views/Curve;->m:[F

    const/4 v2, 0x0

    aget v1, v1, v2

    const/high16 v2, 0x3f800000    # 1.0f

    sub-float v1, v2, v1

    invoke-virtual {p0}, Landroid/view/View;->getPaddingBottom()I

    move-result v3

    mul-int/lit8 v3, v3, 0x2

    int-to-float v3, v3

    sub-float v3, p3, v3

    mul-float/2addr v3, v1

    invoke-virtual {p0}, Landroid/view/View;->getPaddingBottom()I

    move-result v1

    int-to-float v1, v1

    add-float/2addr v3, v1

    invoke-virtual {p1, v0, v3}, Landroid/graphics/Path;->moveTo(FF)V

    const/4 v1, 0x1

    move v10, v1

    move v5, v3

    :goto_0
    iget-object v3, p0, Lcom/jazibkhan/equalizer/views/Curve;->m:[F

    array-length v4, v3

    if-ge v10, v4, :cond_0

    array-length v4, v3

    sub-int/2addr v4, v1

    int-to-float v4, v4

    div-float v4, p2, v4

    add-float v8, v4, v0

    aget v3, v3, v10

    sub-float v3, v2, v3

    invoke-virtual {p0}, Landroid/view/View;->getPaddingBottom()I

    move-result v4

    mul-int/lit8 v4, v4, 0x2

    int-to-float v4, v4

    sub-float v4, p3, v4

    mul-float/2addr v4, v3

    invoke-virtual {p0}, Landroid/view/View;->getPaddingBottom()I

    move-result v3

    int-to-float v3, v3

    add-float v7, v4, v3

    add-float/2addr v0, v8

    const/high16 v3, 0x40000000    # 2.0f

    div-float v4, v0, v3

    move v6, v4

    move v9, v7

    move-object v3, p1

    invoke-virtual/range {v3 .. v9}, Landroid/graphics/Path;->cubicTo(FFFFFF)V

    add-int/lit8 v10, v10, 0x1

    move v5, v7

    move v0, v8

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final b([F)V
    .locals 0

    iput-object p1, p0, Lcom/jazibkhan/equalizer/views/Curve;->m:[F

    invoke-super {p0}, Landroid/view/View;->invalidate()V

    return-void
.end method

.method public getFillPaint()Landroid/graphics/Paint;
    .locals 1

    iget-object v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    return-object v0
.end method

.method public final isEnabled()Z
    .locals 1

    iget-boolean v0, p0, Lcom/jazibkhan/equalizer/views/Curve;->j:Z

    return v0
.end method

.method public final onDraw(Landroid/graphics/Canvas;)V
    .locals 18

    move-object/from16 v0, p0

    move-object/from16 v1, p1

    iget-object v2, v0, Lcom/jazibkhan/equalizer/views/Curve;->b:Landroid/graphics/Rect;

    invoke-virtual {v0, v2}, Landroid/view/View;->getDrawingRect(Landroid/graphics/Rect;)V

    iget-boolean v3, v0, Lcom/jazibkhan/equalizer/views/Curve;->j:Z

    iget-object v4, v0, Lcom/jazibkhan/equalizer/views/Curve;->e:Landroid/graphics/Paint;

    if-eqz v3, :cond_0

    iget-object v3, v0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    iget v5, v0, Lcom/jazibkhan/equalizer/views/Curve;->g:I

    invoke-virtual {v3, v5}, Landroid/graphics/Paint;->setColor(I)V

    invoke-virtual {v4, v5}, Landroid/graphics/Paint;->setColor(I)V

    goto :goto_0

    :cond_0
    iget v3, v0, Lcom/jazibkhan/equalizer/views/Curve;->k:I

    invoke-virtual {v4, v3}, Landroid/graphics/Paint;->setColor(I)V

    iget-object v3, v0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    invoke-virtual {v0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v5

    const v6, 0x7f0603d1

    invoke-virtual {v5, v6}, Landroid/content/res/Resources;->getColor(I)I

    move-result v5

    invoke-virtual {v3, v5}, Landroid/graphics/Paint;->setColor(I)V

    :goto_0
    iget-object v3, v0, Lcom/jazibkhan/equalizer/views/Curve;->m:[F

    if-eqz v3, :cond_3

    iget v3, v2, Landroid/graphics/Rect;->bottom:I

    iget v5, v2, Landroid/graphics/Rect;->top:I

    sub-int/2addr v3, v5

    iget v5, v2, Landroid/graphics/Rect;->right:I

    iget v6, v2, Landroid/graphics/Rect;->left:I

    sub-int/2addr v5, v6

    int-to-float v5, v5

    int-to-float v3, v3

    iget-boolean v6, v0, Lcom/jazibkhan/equalizer/views/Curve;->j:Z

    iget-object v7, v0, Lcom/jazibkhan/equalizer/views/Curve;->f:Landroid/graphics/Path;

    if-eqz v6, :cond_1

    iget-object v6, v0, Lcom/jazibkhan/equalizer/views/Curve;->d:Landroid/graphics/Path;

    invoke-virtual {v0, v6, v5, v3}, Lcom/jazibkhan/equalizer/views/Curve;->a(Landroid/graphics/Path;FF)V

    invoke-virtual {v0, v7, v5, v3}, Lcom/jazibkhan/equalizer/views/Curve;->a(Landroid/graphics/Path;FF)V

    iget v3, v2, Landroid/graphics/Rect;->right:I

    int-to-float v3, v3

    iget v5, v2, Landroid/graphics/Rect;->bottom:I

    int-to-float v5, v5

    invoke-virtual {v6, v3, v5}, Landroid/graphics/Path;->lineTo(FF)V

    iget v3, v2, Landroid/graphics/Rect;->left:I

    int-to-float v3, v3

    iget v5, v2, Landroid/graphics/Rect;->bottom:I

    int-to-float v5, v5

    invoke-virtual {v6, v3, v5}, Landroid/graphics/Path;->lineTo(FF)V

    iget v3, v2, Landroid/graphics/Rect;->left:I

    int-to-float v3, v3

    iget v5, v2, Landroid/graphics/Rect;->top:I

    int-to-float v5, v5

    invoke-virtual {v6, v3, v5}, Landroid/graphics/Path;->lineTo(FF)V

    invoke-virtual {v6}, Landroid/graphics/Path;->close()V

    iget-object v3, v0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    new-instance v8, Landroid/graphics/LinearGradient;

    invoke-virtual {v0}, Landroid/view/View;->getPaddingBottom()I

    move-result v5

    int-to-float v10, v5

    iget v2, v2, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {v0}, Landroid/view/View;->getPaddingBottom()I

    move-result v5

    sub-int/2addr v2, v5

    int-to-float v12, v2

    iget v14, v0, Lcom/jazibkhan/equalizer/views/Curve;->i:I

    sget-object v15, Landroid/graphics/Shader$TileMode;->CLAMP:Landroid/graphics/Shader$TileMode;

    const/4 v9, 0x0

    const/4 v11, 0x0

    iget v13, v0, Lcom/jazibkhan/equalizer/views/Curve;->h:I

    invoke-direct/range {v8 .. v15}, Landroid/graphics/LinearGradient;-><init>(FFFFIILandroid/graphics/Shader$TileMode;)V

    invoke-virtual {v3, v8}, Landroid/graphics/Paint;->setShader(Landroid/graphics/Shader;)Landroid/graphics/Shader;

    iget-object v2, v0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    invoke-virtual {v1, v6, v2}, Landroid/graphics/Canvas;->drawPath(Landroid/graphics/Path;Landroid/graphics/Paint;)V

    goto :goto_2

    :cond_1
    invoke-virtual {v7}, Landroid/graphics/Path;->reset()V

    iget v2, v2, Landroid/graphics/Rect;->left:I

    int-to-float v2, v2

    iget-object v6, v0, Lcom/jazibkhan/equalizer/views/Curve;->m:[F

    const/4 v8, 0x0

    aget v6, v6, v8

    const/high16 v14, 0x3f800000    # 1.0f

    sub-float v6, v14, v6

    invoke-virtual {v0}, Landroid/view/View;->getPaddingBottom()I

    move-result v8

    mul-int/lit8 v8, v8, 0x2

    int-to-float v8, v8

    sub-float v8, v3, v8

    mul-float/2addr v8, v6

    invoke-virtual {v0}, Landroid/view/View;->getPaddingBottom()I

    move-result v6

    int-to-float v6, v6

    add-float/2addr v8, v6

    iget v6, v0, Lcom/jazibkhan/equalizer/views/Curve;->l:F

    add-float v9, v2, v6

    invoke-virtual {v7, v9, v8}, Landroid/graphics/Path;->moveTo(FF)V

    const/4 v15, 0x1

    move v9, v8

    move v8, v15

    :goto_1
    iget-object v10, v0, Lcom/jazibkhan/equalizer/views/Curve;->m:[F

    array-length v11, v10

    if-ge v8, v11, :cond_2

    array-length v11, v10

    sub-int/2addr v11, v15

    int-to-float v11, v11

    div-float v11, v5, v11

    add-float v16, v11, v2

    aget v10, v10, v8

    sub-float v10, v14, v10

    invoke-virtual {v0}, Landroid/view/View;->getPaddingBottom()I

    move-result v11

    mul-int/lit8 v11, v11, 0x2

    int-to-float v11, v11

    sub-float v11, v3, v11

    mul-float/2addr v11, v10

    invoke-virtual {v0}, Landroid/view/View;->getPaddingBottom()I

    move-result v10

    int-to-float v10, v10

    add-float/2addr v11, v10

    add-float v2, v2, v16

    const/high16 v10, 0x40000000    # 2.0f

    div-float/2addr v2, v10

    sub-float v12, v16, v6

    move v10, v2

    move v13, v11

    move/from16 v17, v8

    move v8, v2

    move/from16 v2, v17

    invoke-virtual/range {v7 .. v13}, Landroid/graphics/Path;->cubicTo(FFFFFF)V

    add-float v8, v16, v6

    invoke-virtual {v7, v8, v11}, Landroid/graphics/Path;->moveTo(FF)V

    add-int/lit8 v8, v2, 0x1

    move v9, v11

    move/from16 v2, v16

    goto :goto_1

    :cond_2
    :goto_2
    invoke-virtual {v1, v7, v4}, Landroid/graphics/Canvas;->drawPath(Landroid/graphics/Path;Landroid/graphics/Paint;)V

    :cond_3
    return-void
.end method

.method public setEnabled(Z)V
    .locals 0

    invoke-super {p0, p1}, Landroid/view/View;->setEnabled(Z)V

    iput-boolean p1, p0, Lcom/jazibkhan/equalizer/views/Curve;->j:Z

    invoke-virtual {p0}, Landroid/view/View;->invalidate()V

    return-void
.end method

.method public setFillPaint(Landroid/graphics/Paint;)V
    .locals 0

    iput-object p1, p0, Lcom/jazibkhan/equalizer/views/Curve;->c:Landroid/graphics/Paint;

    return-void
.end method
