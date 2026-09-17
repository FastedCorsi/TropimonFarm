package fr.tropimon.farm;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class HabitatRadarScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFF86E3AD;
  private BlockPos selected;
  private int offset, detailOffset;
  private long revision = -1;
  private List<HabitatMonitor.Observation> entries = List.of();
  private final List<String> details = new ArrayList<>();
  private String feedback = "";

  public HabitatRadarScreen() {
    this(null);
  }

  public HabitatRadarScreen(BlockPos selected) {
    super("Tropimon Better Farm · radar d'habitats");
    this.selected = selected;
  }

  @Override
  protected void init() {
    super.init();
    HabitatMonitor.INSTANCE.scan(client);
    revision = -1;
  }

  private void refresh() {
    var monitor = HabitatMonitor.INSTANCE;
    if (revision == monitor.revision()) return;
    revision = monitor.revision();
    entries = monitor.entries(client);
    if (selected == null && !entries.isEmpty()) selected = entries.getFirst().pos();
    offset = Math.clamp(offset, 0, Math.max(0, entries.size() - 4));
    rebuild();
  }

  private HabitatMonitor.Observation chosen() {
    return entries.stream().filter(o -> o.pos().equals(selected)).findFirst().orElse(null);
  }

  private void paragraph(String value) {
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 252)) {
      StringBuilder s = new StringBuilder();
      ordered.accept(
          (i, style, cp) -> {
            s.appendCodePoint(cp);
            return true;
          });
      details.add(s.toString());
    }
  }

  private void rebuild() {
    details.clear();
    var chosen = chosen();
    if (chosen == null) return;
    paragraph(
        "Bloc : "
            + chosen.pos().getX()
            + " / "
            + chosen.pos().getY()
            + " / "
            + chosen.pos().getZ());
    paragraph(
        chosen.present()
            ? "Habitat confirmé dans un chunk chargé"
            : chosen.loaded()
                ? "Bloc absent : habitat retiré ou remplacé"
                : "Hors chargement : dernière observation seulement");
    if (!chosen.mimic().isBlank()) paragraph("Apparence : " + chosen.mimic());
    if (chosen.present()) {
      paragraph("Style reçu : " + (chosen.activated() ? "activé" : "naturel"));
      paragraph(
          "Spawns ordinaires annulés/remplacés : " + (chosen.cancelsSpawns() ? "oui" : "non"));
    }
    paragraph("Espèces d'affichage reçues (pas le pool complet) :");
    if (chosen.species().isEmpty()) paragraph("Aucune espèce transmise.");
    for (String id : chosen.species()) {
      String name = id.substring(id.indexOf(':') + 1);
      paragraph("• " + Text.translatable("cobblemon.species." + name + ".name").getString());
    }
    paragraph("Phase active : non transmise");
    paragraph("Délai du prochain spawn : non transmis");
    paragraph("Cycles et Pokémon possibles : voir la fiche, selon le profil choisi.");
    detailOffset = Math.clamp(detailOffset, 0, Math.max(0, details.size() - 8));
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    refresh();
    int mx = localX(mouseX), my = localY(mouseY);
    begin(
        c, ACCENT, "TROPIMON / RADAR HABITATS", "Surveillance passive des nouveaux blocs de spawn");
    chip(c, "Catalogue / conditions", 353, 16, 187, false, ACCENT);
    c.fill(16, 55, 270, 309, PANEL);
    int cx = 142, cy = 132, r = 67;
    for (int radius : new int[] {22, 44, 67})
      for (int a = 0; a < 160; a++) {
        double angle = a * Math.PI / 80;
        int x = cx + (int) (radius * Math.cos(angle)), y = cy + (int) (radius * Math.sin(angle));
        c.fill(x, y, x + 1, y + 1, 0xFF3A6859);
      }
    c.fill(cx - r, cy, cx + r, cy + 1, 0xFF3A6859);
    c.fill(cx, cy - r, cx + 1, cy + r, 0xFF3A6859);
    label(c, "N", cx - 2, cy - r - 12, MUTED);
    c.fill(cx - 2, cy - 2, cx + 3, cy + 3, WHITE);
    if (client.player != null)
      for (var entry : entries) {
        double dx = entry.pos().getX() + 0.5 - client.player.getX(),
            dz = entry.pos().getZ() + 0.5 - client.player.getZ();
        if (dx * dx + dz * dz > HabitatMonitor.RANGE * HabitatMonitor.RANGE) continue;
        int x = cx + (int) (dx * r / HabitatMonitor.RANGE),
            y = cy + (int) (dz * r / HabitatMonitor.RANGE);
        int color =
            !entry.loaded()
                ? 0xFF8B9A9A
                : !entry.present() ? 0xFFDF7777 : entry.pinned() ? 0xFFFFD876 : ACCENT;
        c.fill(x - 2, y - 2, x + 3, y + 3, color);
        if (entry.pos().equals(selected)) c.drawBorder(x - 4, y - 4, 9, 9, WHITE);
        if (hit(mx, my, x - 4, y - 4, 9, 9))
          label(
              c,
              Math.round(Math.sqrt(entry.pos().getSquaredDistance(client.player.getPos()))) + " m",
              x + 7,
              y - 4,
              WHITE);
      }
    label(c, "Rayon " + HabitatMonitor.RANGE + " m · chunks reçus", 32, 211, MUTED);
    label(c, entries.size() + " habitats · " + feedback, 25, 229, WHITE);
    for (int i = 0; i < 4 && offset + i < entries.size(); i++) {
      var entry = entries.get(offset + i);
      int y = 246 + i * 14;
      if (entry.pos().equals(selected)) c.fill(22, y - 2, 263, y + 11, 0xFF315543);
      String distance =
          client.player == null
              ? "?"
              : Long.toString(
                  Math.round(Math.sqrt(entry.pos().getSquaredDistance(client.player.getPos()))));
      label(
          c,
          (entry.pinned() ? "★ " : "")
              + entry.pos().getX()
              + ", "
              + entry.pos().getY()
              + ", "
              + entry.pos().getZ()
              + " · "
              + distance
              + "m",
          26,
          y,
          entry.present() ? WHITE : MUTED);
    }
    var entry = chosen();
    var mimic = entry == null ? null : HabitatBlocksScreen.block(entry.mimic());
    if (mimic != null) c.drawItem(mimic.asItem().getDefaultStack(), 285, 61);
    label(
        c,
        entry == null
            ? "Aucun habitat reçu"
            : textRenderer.trimToWidth(
                mimic == null ? "BLOC D'HABITAT" : mimic.getName().getString(), 229),
        mimic == null ? 285 : 306,
        65,
        ACCENT);
    if (entry == null)
      text(
          c,
          "Approche d'un habitat pour le détecter. Aucun chunk n'est chargé ni demandé par le"
              + " radar.",
          285,
          94,
          249,
          MUTED);
    else {
      chip(
          c,
          entry.pinned() ? "Retirer de la surveillance" : "Épingler cet habitat",
          284,
          86,
          252,
          entry.pinned(),
          ACCENT);
      for (int i = 0; i < 8 && detailOffset + i < details.size(); i++)
        label(c, details.get(detailOffset + i), 285, 119 + i * 13, WHITE);
      label(c, "Molette : détails / liste", 285, 235, MUTED);
      chip(c, "Cycles / Pokémon possibles", 284, 257, 252, false, ACCENT);
      chip(c, "Reconnaître le bloc / zone", 284, 283, 252, false, ACCENT);
    }
    end(c);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (hit(mx, my, 353, 16, 187, 21)) {
      client.setScreen(new FarmScreen());
      return true;
    }
    if (hit(mx, my, 284, 257, 252, 21) && chosen() != null) {
      client.setScreen(new HabitatCycleScreen(chosen().pos()));
      return true;
    }
    if (hit(mx, my, 284, 283, 252, 21) && chosen() != null) {
      client.setScreen(
          new HabitatBlocksScreen(
              this, chosen().pos(), List.of(), "Observation du bloc · client seul"));
      return true;
    }
    if (hit(mx, my, 22, 244, 241, 56)) {
      int i = offset + (my - 244) / 14;
      if (i < entries.size()) {
        selected = entries.get(i).pos();
        detailOffset = 0;
        rebuild();
      }
      return true;
    }
    if (hit(mx, my, 284, 86, 252, 21) && selected != null) {
      feedback = HabitatMonitor.INSTANCE.toggle(selected) ? "" : "Limite de repères";
      revision = -1;
      refresh();
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double h, double v) {
    int delta = v > 0 ? -1 : 1;
    if (localX(x) < 270) offset = Math.clamp(offset + delta, 0, Math.max(0, entries.size() - 4));
    else detailOffset = Math.clamp(detailOffset + delta, 0, Math.max(0, details.size() - 8));
    return true;
  }
}
