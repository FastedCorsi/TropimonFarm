package fr.tropimon.farm;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.lwjgl.glfw.GLFW;

/** Habitat field instrument. Decorative scope is never presented as entity positions. */
public final class FarmScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFF86E3AD;
  private FarmCatalog.Result catalog;
  private List<FarmCatalog.Entry> filtered = List.of();
  private FarmCatalog.Entry selected;
  private TextFieldWidget search;
  private int offset, detailOffset, phase, generation;
  private boolean habitats = true, snapshot, loading;
  private String error = "", target = "";
  private final List<String> lines = new ArrayList<>();

  public FarmScreen() {
    super("Tropimon Farm");
  }

  @Override
  protected void init() {
    super.init();
    String old = search == null ? "" : search.getText();
    search = new TextFieldWidget(textRenderer, 24, 63, 233, 18, Text.literal("Espèce ou habitat"));
    search.setPlaceholder(Text.literal("Espèce, habitat ou biome…"));
    search.setMaxLength(80);
    search.setChangedListener(s -> filter());
    search.setText(old);
    if (catalog == null && !loading) load();
    else filter();
  }

  private void load() {
    loading = true;
    error = "";
    int revision = ++generation;
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return FarmCatalog.load(snapshot);
              } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
              }
            })
        .whenComplete(
            (result, failure) ->
                client.execute(
                    () -> {
                      if (revision != generation) return;
                      loading = false;
                      if (failure != null) {
                        catalog = null;
                        filtered = List.of();
                        selected = null;
                        error = "Données illisibles : aucune estimation affichée.";
                      } else {
                        catalog = result;
                        filter();
                      }
                    }));
  }

  private static String place(FarmCatalog.Entry entry) {
    return Language.getInstance().hasTranslation(entry.place())
        ? Language.getInstance().get(entry.place())
        : entry.place();
  }

  private static String species(FarmCatalog.Entry entry) {
    String id = entry.species().split(" ")[0].toLowerCase(Locale.ROOT);
    String key = "cobblemon.species." + id + ".name";
    String name =
        Language.getInstance().hasTranslation(key)
            ? Language.getInstance().get(key)
            : entry.species();
    return entry.species().contains(" ")
        ? name + " " + entry.species().substring(entry.species().indexOf(' ') + 1)
        : name;
  }

  private void filter() {
    if (catalog == null) return;
    String q = FarmCatalog.normalize(search.getText());
    filtered =
        catalog.entries().stream()
            .filter(
                e ->
                    e.habitat() == habitats
                        && (!habitats || FarmCatalog.phaseMatches(e.phases(), phase)))
            .filter(
                e ->
                    FarmCatalog.normalize(
                            species(e) + " " + e.species() + " " + place(e) + " " + e.details())
                        .contains(q))
            .toList();
    offset = 0;
    if (!filtered.contains(selected)) selected = filtered.isEmpty() ? null : filtered.getFirst();
    details();
  }

  private void paragraph(String value) {
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 251)) {
      StringBuilder s = new StringBuilder();
      ordered.accept(
          (i, style, cp) -> {
            s.appendCodePoint(cp);
            return true;
          });
      lines.add(s.toString());
    }
  }

  private void details() {
    lines.clear();
    detailOffset = 0;
    if (selected == null) return;
    paragraph(place(selected));
    paragraph("Niveau : " + selected.level() + " · Rareté : " + selected.bucket());
    paragraph(
        "Poids relatif : "
            + (Double.isNaN(selected.weight()) ? "non précisé" : selected.weight())
            + " (pas un pourcentage)");
    if (selected.habitat())
      paragraph("Phases : " + (selected.phases().isBlank() ? "non précisées" : selected.phases()));
    for (String detail : selected.details()) paragraph(detail);
    paragraph(
        selected.snapshot()
            ? "Source : instantané Tropimon / HunterBoard 1.4.0, 16/09/2026."
            : "Source : ressources des mods installés.");
    paragraph(
        "Les datapacks privés du serveur ne sont pas synchronisés par ce catalogue. Conditions non"
            + " évaluées ≠ apparition garantie.");
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    int mx = localX(mouseX), my = localY(mouseY);
    begin(c, ACCENT, "TROPIMON / FARM", "Détecteur de conditions · habitats & apparitions");
    chip(c, "Radar des habitats", 367, 16, 163, false, ACCENT);
    c.fill(16, 54, 266, 308, PANEL);
    search.render(c, mx, my, delta);
    chip(c, "Habitats", 23, 90, 77, habitats, ACCENT);
    chip(c, "Apparitions", 105, 90, 94, !habitats, ACCENT);
    chip(c, phase == 0 ? "Ph. *" : "Ph. " + phase, 204, 90, 54, phase != 0, ACCENT);
    for (int i = 0; i < 9 && offset + i < filtered.size(); i++) {
      var e = filtered.get(offset + i);
      int y = 121 + i * 18;
      if (e == selected) c.fill(21, y - 3, 259, y + 13, 0xFF315543);
      label(c, textRenderer.trimToWidth(species(e), 107), 26, y, WHITE);
      label(c, textRenderer.trimToWidth(place(e), 112), 140, y, MUTED);
    }
    label(c, loading ? "Chargement…" : filtered.size() + " résultats · molette", 25, 290, MUTED);
    c.fill(279, 54, 544, 158, PANEL);
    scope(c, 318, 105);
    label(c, "CIBLE SUIVIE", 367, 66, ACCENT);
    label(c, textRenderer.trimToWidth(target.isBlank() ? "Aucune" : target, 164), 367, 84, WHITE);
    label(c, "Guide, pas un radar spatial", 367, 101, MUTED);
    chip(c, "Épingler la sélection", 367, 123, 163, false, ACCENT);
    chip(
        c,
        snapshot ? "Tropimon · instantané 1.4.0" : "Cobblemon · fichiers installés",
        280,
        166,
        263,
        snapshot,
        ACCENT);
    if (selected != null) {
      label(c, textRenderer.trimToWidth(species(selected), 248), 288, 198, ACCENT);
      for (int i = 0; i < 7 && detailOffset + i < lines.size(); i++)
        label(c, lines.get(detailOffset + i), 288, 216 + i * 12, WHITE);
    } else
      text(
          c,
          error.isBlank()
              ? (loading
                  ? "Lecture des ressources…"
                  : "Aucun résultat : élargis la recherche ou change de profil.")
              : error,
          288,
          215,
          243,
          MUTED);
    if (catalog != null && catalog.skipped() > 0)
      label(c, catalog.skipped() + " entrées ignorées (format inconnu)", 280, 308, 0xFFFFBD76);
    else label(c, "Profil explicite · aucune donnée supposée en direct", 280, 308, MUTED);
    end(c);
  }

  private void scope(DrawContext c, int x, int y) {
    for (int radius : new int[] {16, 29, 39})
      for (int angle = 0; angle < 120; angle++) {
        double a = angle * Math.PI / 60;
        int px = x + (int) (Math.cos(a) * radius), py = y + (int) (Math.sin(a) * radius);
        c.fill(px, py, px + 1, py + 1, 0xFF467F67);
      }
    c.fill(x - 39, y, x + 40, y + 1, 0xFF315E51);
    c.fill(x, y - 39, x + 1, y + 40, 0xFF315E51);
    c.fill(x - 3, y - 3, x + 4, y + 4, ACCENT);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (hit(mx, my, 367, 16, 163, 21)) {
      client.setScreen(new HabitatRadarScreen());
      return true;
    }
    search.setFocused(hit(mx, my, 24, 63, 233, 18));
    if (search.mouseClicked(mx, my, button)) return true;
    if (hit(mx, my, 23, 90, 176, 21)) {
      habitats = mx < 105;
      filter();
      return true;
    }
    if (hit(mx, my, 204, 90, 54, 21)) {
      int max = 1;
      if (catalog != null)
        for (var entry : catalog.entries())
          if (entry.habitat())
            for (String token : entry.phases().split("[^0-9]+")) {
              try {
                max = Math.max(max, Math.min(64, Integer.parseInt(token)));
              } catch (NumberFormatException ignored) {
              }
            }
      phase = (phase + 1) % (max + 1);
      filter();
      return true;
    }
    if (hit(mx, my, 21, 118, 238, 162)) {
      int i = offset + (my - 118) / 18;
      if (i < filtered.size()) {
        selected = filtered.get(i);
        details();
      }
      return true;
    }
    if (hit(mx, my, 280, 166, 263, 21) && !loading) {
      snapshot = !snapshot;
      catalog = null;
      filtered = List.of();
      selected = null;
      load();
      return true;
    }
    if (hit(mx, my, 367, 123, 163, 21) && selected != null) {
      target = species(selected);
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    int delta = vertical > 0 ? -3 : 3;
    if (localX(x) < 270) offset = Math.clamp(offset + delta, 0, Math.max(0, filtered.size() - 9));
    else detailOffset = Math.clamp(detailOffset + delta, 0, Math.max(0, lines.size() - 7));
    return true;
  }

  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
    return search.isFocused() && search.keyPressed(key, scan, mods)
        || super.keyPressed(key, scan, mods);
  }

  @Override
  public boolean charTyped(char ch, int mods) {
    return search.charTyped(ch, mods) || super.charTyped(ch, mods);
  }

  @Override
  public void removed() {
    generation++;
  }
}
