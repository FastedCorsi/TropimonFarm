package fr.tropimon.farm;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Code-native instrument shell; every mod owns its copy, with no shared runtime. */
abstract class InstrumentScreen extends Screen {
  static final int W = 560,
      H = 340,
      INK = 0xFF0B1621,
      PANEL = 0xFF122938,
      MUTED = 0xFF94B5BE,
      WHITE = 0xFFEAF8F1;
  protected int left, top;
  protected float scale;

  InstrumentScreen(String title) {
    super(Text.literal(title));
  }

  @Override
  protected void init() {
    scale = Math.min(1.5F, Math.min((width - 12F) / W, (height - 12F) / H));
    left = (int) ((width - W * scale) / 2);
    top = (int) ((height - H * scale) / 2);
  }

  protected int localX(double x) {
    return (int) ((x - left) / scale);
  }

  protected int localY(double y) {
    return (int) ((y - top) / scale);
  }

  protected void begin(DrawContext c, int accent, String label, String subtitle) {
    c.fill(0, 0, width, height, 0xC508101B);
    c.getMatrices().push();
    c.getMatrices().translate(left, top, 0);
    c.getMatrices().scale(scale, scale, 1);
    c.fill(0, 0, W, H, 0xFF040B13);
    c.fill(2, 2, W - 2, H - 2, accent);
    c.fill(7, 7, W - 7, H - 7, INK);
    c.fill(7, 7, W - 7, 46, PANEL);
    c.fill(17, 18, 28, 29, accent);
    c.fill(20, 20, 24, 24, WHITE);
    label(c, label, 39, 16, WHITE);
    label(c, subtitle, 39, 31, MUTED);
    label(c, "By FastedCorsi", 16, H - 18, MUTED);
    label(c, "ÉCHAP  /  FERMER", W - 119, H - 18, MUTED);
  }

  protected void end(DrawContext c) {
    c.getMatrices().pop();
  }

  protected void label(DrawContext c, String s, int x, int y, int color) {
    c.drawText(textRenderer, s, x, y, color, false);
  }

  protected void text(DrawContext c, String s, int x, int y, int maxWidth, int color) {
    int line = 0;
    for (var part : textRenderer.wrapLines(Text.literal(s), maxWidth)) {
      c.drawText(textRenderer, part, x, y + line * 12, color, false);
      line++;
    }
  }

  protected void chip(DrawContext c, String s, int x, int y, int w, boolean on, int accent) {
    c.fill(x, y, x + w, y + 21, on ? accent : PANEL);
    label(c, s, x + 7, y + 7, on ? INK : WHITE);
  }

  protected boolean hit(int mx, int my, int x, int y, int w, int h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  @Override
  public boolean shouldPause() {
    return false;
  }
}
