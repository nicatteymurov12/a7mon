package az.rahattv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small line icons drawn in code, so no image files or emoji fonts are needed. */
final class Glyph extends Drawable {
    static final int PLAY = 0, BAG = 1, GLOBE = 2, TV = 3, USB = 4, BOLT = 5, CHECK = 6, GEAR = 7, STAR = 8, SLIDERS = 9;

    private final int kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(int kind, int color) {
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        if (s <= 0) return;
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float r = s / 2f * 0.86f;
        stroke.setStrokeWidth(Math.max(2f, s * 0.08f));
        Path p = new Path();
        switch (kind) {
            case PLAY:
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.72f, cx + r, cy + r * 0.72f), r * 0.25f, r * 0.25f, stroke);
                p.moveTo(cx - r * 0.25f, cy - r * 0.35f);
                p.lineTo(cx + r * 0.38f, cy);
                p.lineTo(cx - r * 0.25f, cy + r * 0.35f);
                p.close();
                c.drawPath(p, fill);
                break;
            case BAG:
                p.moveTo(cx - r * 0.8f, cy - r * 0.35f);
                p.lineTo(cx + r * 0.8f, cy - r * 0.35f);
                p.lineTo(cx + r * 0.65f, cy + r * 0.85f);
                p.lineTo(cx - r * 0.65f, cy + r * 0.85f);
                p.close();
                c.drawPath(p, stroke);
                c.drawArc(new RectF(cx - r * 0.4f, cy - r * 0.95f, cx + r * 0.4f, cy + r * 0.25f), 180, 180, false, stroke);
                Path t = new Path();
                t.moveTo(cx - r * 0.18f, cy + r * 0.05f);
                t.lineTo(cx + r * 0.28f, cy + r * 0.27f);
                t.lineTo(cx - r * 0.18f, cy + r * 0.5f);
                t.close();
                c.drawPath(t, fill);
                break;
            case GLOBE:
                c.drawCircle(cx, cy, r, stroke);
                c.drawOval(new RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), stroke);
                c.drawLine(cx - r, cy, cx + r, cy, stroke);
                break;
            case TV:
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.7f, cx + r, cy + r * 0.45f), r * 0.15f, r * 0.15f, stroke);
                c.drawLine(cx - r * 0.45f, cy + r * 0.8f, cx + r * 0.45f, cy + r * 0.8f, stroke);
                break;
            case USB:
                c.drawLine(cx, cy - r, cx, cy + r * 0.3f, stroke);
                p.moveTo(cx - r * 0.4f, cy - r * 0.1f);
                p.lineTo(cx, cy + r * 0.3f);
                p.lineTo(cx + r * 0.4f, cy - r * 0.1f);
                c.drawPath(p, stroke);
                c.drawLine(cx - r * 0.85f, cy + r * 0.85f, cx + r * 0.85f, cy + r * 0.85f, stroke);
                break;
            case BOLT:
                p.moveTo(cx + r * 0.15f, cy - r);
                p.lineTo(cx - r * 0.55f, cy + r * 0.12f);
                p.lineTo(cx - r * 0.02f, cy + r * 0.12f);
                p.lineTo(cx - r * 0.15f, cy + r);
                p.lineTo(cx + r * 0.55f, cy - r * 0.12f);
                p.lineTo(cx + r * 0.02f, cy - r * 0.12f);
                p.close();
                c.drawPath(p, fill);
                break;
            case CHECK:
                c.drawCircle(cx, cy, r, stroke);
                p.moveTo(cx - r * 0.45f, cy + r * 0.02f);
                p.lineTo(cx - r * 0.1f, cy + r * 0.38f);
                p.lineTo(cx + r * 0.5f, cy - r * 0.32f);
                c.drawPath(p, stroke);
                break;
            case GEAR:
                c.drawCircle(cx, cy, r * 0.32f, stroke);
                c.drawCircle(cx, cy, r * 0.7f, stroke);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    float x1 = cx + (float) Math.cos(a) * r * 0.7f, y1 = cy + (float) Math.sin(a) * r * 0.7f;
                    float x2 = cx + (float) Math.cos(a) * r, y2 = cy + (float) Math.sin(a) * r;
                    c.drawLine(x1, y1, x2, y2, stroke);
                }
                break;
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + Math.PI * i / 5;
                    float rr = (i % 2 == 0) ? r : r * 0.45f;
                    float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                c.drawPath(p, fill);
                break;
            case SLIDERS:
            default:
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * r * 0.6f;
                    c.drawLine(cx - r, y, cx + r, y, stroke);
                    c.drawCircle(cx + i * r * 0.45f, y, r * 0.17f, fill);
                }
                break;
        }
    }

    @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
