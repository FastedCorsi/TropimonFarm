package fr.tropimon.farm;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Code-native instrument shell; every mod owns its copy, with no shared runtime. */
abstract class InstrumentScreen extends Screen {
  static final int W = 560,
      H = 340,
      INK = 0xFF292D30,
      PANEL = 0xFF383E42,
      MUTED = 0xFFB4C2BE,
      WHITE = 0xFFF1F3EC;
  protected int left, top;
  protected float scale;

  InstrumentScreen(String title) {
    super(Text.literal(title));
  }

  @Override
  protected void init() {
    scale = Math.min(1F, Math.min((width - 36F) / W, (height - 36F) / H));
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
    c.fill(0, 0, width, height, 0x880D1214);
    c.getMatrices().push();
    c.getMatrices().translate(left, top, 0);
    c.getMatrices().scale(scale, scale, 1);
    c.fill(4, 6, W + 4, H + 6, 0x77000000);
    c.fill(3, 0, W - 3, H, 0xFF151819);
    c.fill(0, 3, W, H - 3, 0xFF151819);
    c.fill(3, 3, W - 3, H - 3, 0xFF717A76);
    c.fill(7, 7, W - 7, H - 7, INK);
    c.fill(7, 7, W - 7, 46, PANEL);
    c.fill(7, 7, W - 7, 10, accent);
    // Pixel Poké Ball: drawn locally, no texture copied from another mod.
    c.fill(17, 17, 31, 31, 0xFF151819);
    c.fill(19, 18, 29, 23, 0xFFE87570);
    c.fill(19, 25, 29, 30, WHITE);
    c.fill(22, 22, 26, 26, 0xFF151819);
    c.fill(23, 23, 25, 25, WHITE);
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
    c.fill(x + 1, y, x + w - 1, y + 21, 0xFF141B19);
    c.fill(x, y + 1, x + w, y + 20, 0xFF141B19);
    c.fill(x + 1, y + 1, x + w - 1, y + 19, on ? accent : PANEL);
    c.fill(x + 2, y + 1, x + w - 2, y + 2, on ? 0xFFD3F5D6 : 0xFF626D68);
    label(c, textRenderer.trimToWidth(s, w - 14), x + 7, y + 7, on ? INK : WHITE);
  }

  protected boolean hit(int mx, int my, int x, int y, int w, int h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  @Override
  public boolean shouldPause() {
    return false;
  }
}
