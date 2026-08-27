package com.jazibkhan.equalizer.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import com.jazibkhan.equalizer.R;
import kotlin.KotlinVersion;
import m3.d;
import ye.r0;

/* loaded from: classes2.dex */
public class Curve extends View {

    /* renamed from: n, reason: collision with root package name */
    public static final int f11055n = Color.argb(100, 239, 154, 154);

    /* renamed from: o, reason: collision with root package name */
    public static final int f11056o = Color.argb(0, 239, 154, 154);

    /* renamed from: p, reason: collision with root package name */
    public static final int f11057p = Color.rgb(239, 154, 154);

    /* renamed from: q, reason: collision with root package name */
    public static final int f11058q = Color.argb(26, KotlinVersion.MAX_COMPONENT_VALUE, KotlinVersion.MAX_COMPONENT_VALUE, KotlinVersion.MAX_COMPONENT_VALUE);

    /* renamed from: b, reason: collision with root package name */
    public final Rect f11059b;

    /* renamed from: c, reason: collision with root package name */
    public Paint f11060c;

    /* renamed from: d, reason: collision with root package name */
    public final Path f11061d;

    /* renamed from: e, reason: collision with root package name */
    public final Paint f11062e;

    /* renamed from: f, reason: collision with root package name */
    public final Path f11063f;

    /* renamed from: g, reason: collision with root package name */
    public final int f11064g;

    /* renamed from: h, reason: collision with root package name */
    public final int f11065h;

    /* renamed from: i, reason: collision with root package name */
    public final int f11066i;

    /* renamed from: j, reason: collision with root package name */
    public boolean f11067j;

    /* renamed from: k, reason: collision with root package name */
    public final int f11068k;

    /* renamed from: l, reason: collision with root package name */
    public final float f11069l;

    /* renamed from: m, reason: collision with root package name */
    public float[] f11070m;

    public Curve(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.f11059b = new Rect();
        this.f11060c = new Paint();
        this.f11061d = new Path();
        Paint paint = new Paint();
        this.f11062e = paint;
        this.f11063f = new Path();
        int i10 = f11057p;
        this.f11064g = i10;
        this.f11065h = f11055n;
        this.f11066i = f11056o;
        this.f11067j = true;
        int i11 = f11058q;
        this.f11068k = i11;
        this.f11069l = 4.0f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4.0f);
        paint.setColor(i10);
        paint.setAntiAlias(true);
        this.f11060c.setStyle(Paint.Style.FILL);
        this.f11060c.setColor(i10);
        this.f11060c.setAntiAlias(true);
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(attributeSet, r0.f52424b);
        paint.setStrokeWidth(obtainStyledAttributes.getDimension(3, 4.0f));
        int color = obtainStyledAttributes.getColor(6, i10);
        this.f11065h = color;
        this.f11065h = d.f(color, 127);
        this.f11066i = obtainStyledAttributes.getColor(5, i10);
        this.f11067j = obtainStyledAttributes.getBoolean(1, true);
        this.f11068k = obtainStyledAttributes.getColor(4, i11);
        this.f11064g = obtainStyledAttributes.getColor(0, i10);
        this.f11069l = obtainStyledAttributes.getDimension(2, 4.0f);
        obtainStyledAttributes.recycle();
    }

    public final void a(Path path, float f10, float f11) {
        path.reset();
        float f12 = this.f11059b.left;
        float paddingBottom = ((f11 - (getPaddingBottom() * 2)) * (1.0f - this.f11070m[0])) + getPaddingBottom();
        path.moveTo(f12, paddingBottom);
        int i10 = 1;
        float f13 = paddingBottom;
        while (true) {
            float[] fArr = this.f11070m;
            if (i10 >= fArr.length) {
                return;
            }
            float length = (f10 / (fArr.length - 1)) + f12;
            float paddingBottom2 = ((f11 - (getPaddingBottom() * 2)) * (1.0f - fArr[i10])) + getPaddingBottom();
            float f14 = (f12 + length) / 2.0f;
            path.cubicTo(f14, f13, f14, paddingBottom2, length, paddingBottom2);
            i10++;
            f13 = paddingBottom2;
            f12 = length;
        }
    }

    public final void b(float[] fArr) {
        this.f11070m = fArr;
        super.invalidate();
    }

    public Paint getFillPaint() {
        return this.f11060c;
    }

    @Override // android.view.View
    public final boolean isEnabled() {
        return this.f11067j;
    }

    @Override // android.view.View
    public final void onDraw(Canvas canvas) {
        Rect rect = this.f11059b;
        getDrawingRect(rect);
        boolean z10 = this.f11067j;
        Paint paint = this.f11062e;
        if (z10) {
            Paint paint2 = this.f11060c;
            int i10 = this.f11064g;
            paint2.setColor(i10);
            paint.setColor(i10);
        } else {
            paint.setColor(this.f11068k);
            this.f11060c.setColor(getResources().getColor(R.color.transparent_color));
        }
        if (this.f11070m != null) {
            int i11 = rect.bottom - rect.top;
            float f10 = rect.right - rect.left;
            float f11 = i11;
            boolean z11 = this.f11067j;
            Path path = this.f11063f;
            if (!z11) {
                path.reset();
                float f12 = rect.left;
                float paddingBottom = ((f11 - (getPaddingBottom() * 2)) * (1.0f - this.f11070m[0])) + getPaddingBottom();
                float f13 = this.f11069l;
                path.moveTo(f12 + f13, paddingBottom);
                float f14 = paddingBottom;
                int i12 = 1;
                while (true) {
                    float[] fArr = this.f11070m;
                    if (i12 >= fArr.length) {
                        break;
                    }
                    float length = (f10 / (fArr.length - 1)) + f12;
                    float paddingBottom2 = ((f11 - (getPaddingBottom() * 2)) * (1.0f - fArr[i12])) + getPaddingBottom();
                    float f15 = (f12 + length) / 2.0f;
                    path.cubicTo(f15, f14, f15, paddingBottom2, length - f13, paddingBottom2);
                    path.moveTo(length + f13, paddingBottom2);
                    i12++;
                    f14 = paddingBottom2;
                    f12 = length;
                }
            } else {
                Path path2 = this.f11061d;
                a(path2, f10, f11);
                a(path, f10, f11);
                path2.lineTo(rect.right, rect.bottom);
                path2.lineTo(rect.left, rect.bottom);
                path2.lineTo(rect.left, rect.top);
                path2.close();
                this.f11060c.setShader(new LinearGradient(0.0f, getPaddingBottom(), 0.0f, rect.bottom - getPaddingBottom(), this.f11065h, this.f11066i, Shader.TileMode.CLAMP));
                canvas.drawPath(path2, this.f11060c);
            }
            canvas.drawPath(path, paint);
        }
    }

    @Override // android.view.View
    public void setEnabled(boolean z10) {
        super.setEnabled(z10);
        this.f11067j = z10;
        invalidate();
    }

    public void setFillPaint(Paint paint) {
        this.f11060c = paint;
    }
}
