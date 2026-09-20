package fr.tropimon.farm;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.lwjgl.glfw.GLFW;

/** A place-first catalogue. Preview data never represents an observed server structure. */
public final class FarmScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFF86E3AD;

  private record Group(String key, String name, List<FarmCatalog.Entry> entries) {}

  private FarmCatalog.Result catalog;
  private StructurePreview.Index structures;
  private List<FarmCatalog.Entry> filtered = List.of(), selectedEntries = List.of();
  private List<Group> groups = List.of();
  private Group selected;
  private TextFieldWidget search;
  private int offset, detailOffset, tab, generation, previewGeneration, variant;
  private boolean habitats = true, snapshot = true, loading, previewLoading, indexing;
  private String error = "", previewError = "";
  private List<StructurePreview.Template> templates = List.of();
  private StructurePreview.Model preview;
  private float yaw = 135, zoom = 1.25F;
  private final List<String> lines = new ArrayList<>();

  public FarmScreen() {
    super("Tropimon Better Farm");
  }

  @Override
  protected void init() {
    super.init();
    String old = search == null ? "" : search.getText();
    search =
        new TextFieldWidget(
            textRenderer, 24, 91, 184, 19, Text.literal("Rechercher un habitat ou un Pokémon"));
    search.setPlaceholder(Text.literal("Habitat ou Pokémon…"));
    search.setMaxLength(80);
    search.setText(old);
    search.setChangedListener(s -> filter());
    if (catalog == null && !loading) load();
    else if (catalog != null) filter();
  }

  private void load() {
    loading = true;
    error = "";
    int revision = ++generation;
    boolean profile = snapshot;
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return FarmCatalog.load(profile);
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
                        error = "Catalogue indisponible. Change de source pour réessayer.";
                        return;
                      }
                      catalog = result;
                      filter();
                    }));
    if (structures == null && !indexing) {
      indexing = true;
      CompletableFuture.supplyAsync(
              () -> {
                try {
                  return StructurePreview.index();
                } catch (Exception ex) {
                  throw new java.util.concurrent.CompletionException(ex);
                }
              })
          .whenComplete(
              (result, failure) ->
                  client.execute(
                      () -> {
                        indexing = false;
                        if (failure == null) {
                          structures = result;
                          selectPreview();
                        } else previewError = "Modèles locaux indisponibles";
                      }));
    }
  }

  private static String place(FarmCatalog.Entry e) {
    return Language.getInstance().hasTranslation(e.place())
        ? Language.getInstance().get(e.place())
        : e.place();
  }

  private static String species(FarmCatalog.Entry e) {
    String id = e.species().split(" ")[0].toLowerCase(Locale.ROOT).replace("cobblemon:", "");
    String key = "cobblemon.species." + id + ".name";
    String name =
        Language.getInstance().hasTranslation(key) ? Language.getInstance().get(key) : e.species();
    return e.species().contains(" ")
        ? name + e.species().substring(e.species().indexOf(' '))
        : name;
  }

  private void filter() {
    if (catalog == null) return;
    String q = FarmCatalog.normalize(search.getText());
    filtered =
        catalog.entries().stream()
            .filter(e -> e.habitat() == habitats)
            .filter(
                e ->
                    FarmCatalog.normalize(
                            species(e) + " " + e.species() + " " + place(e) + " " + e.details())
                        .contains(q))
            .toList();
    Map<String, List<FarmCatalog.Entry>> grouped = new LinkedHashMap<>();
    for (var e : filtered)
      grouped.computeIfAbsent(habitats ? e.resource() : e.species(), k -> new ArrayList<>()).add(e);
    groups =
        grouped.entrySet().stream()
            .map(
                e ->
                    new Group(
                        e.getKey(),
                        habitats
                            ? place(e.getValue().getFirst())
                            : species(e.getValue().getFirst()),
                        List.copyOf(e.getValue())))
            .sorted(Comparator.comparing(Group::name))
            .toList();
    String previous = selected == null ? "" : selected.key();
    selected =
        groups.stream()
            .filter(g -> g.key().equals(previous))
            .findFirst()
            .orElse(groups.isEmpty() ? null : groups.getFirst());
    offset = 0;
    selectionChanged();
  }

  private void selectionChanged() {
    selectedEntries =
        selected == null
            ? List.of()
            : catalog.entries().stream()
                .filter(
                    e ->
                        habitats
                            ? e.habitat() && e.resource().equals(selected.key())
                            : !e.habitat() && e.species().equals(selected.key()))
                .toList();
    detailOffset = 0;
    details();
    selectPreview();
  }

  private void paragraph(String value) {
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 290)) {
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
    if (selected == null) return;
    paragraph(
        snapshot
            ? "Catalogue Tropimon : instantané du 16/09/2026."
            : "Catalogue Cobblemon : fichiers installés.");
    paragraph(
        "Les réglages privés du serveur peuvent différer. Les apparitions dépendent du terrain et"
            + " du hasard.");
    if (snapshot)
      paragraph("Tropimon a supprimé les cycles : aucune phase prédite dans ce catalogue.");
    paragraph(
        "Structure : modèle local, parfois une pièce d'un ensemble généré. Le terrain et les"
            + " variantes du serveur peuvent différer.");
    if (structures != null && structures.skipped() > 0)
      paragraph(structures.skipped() + " modèles n'ont pas pu être lus.");
    paragraph("Détails techniques");
    for (var e : selectedEntries) {
      paragraph(species(e) + " · " + place(e));
      paragraph("Niveau " + e.level() + " · " + rarity(e.bucket()));
      paragraph("Poids relatif : " + e.weight() + " (ce n'est pas un pourcentage)");
      if (!snapshot && !e.phases().isBlank()) paragraph("Phases déclarées : " + e.phases());
      for (String d : e.details()) paragraph(d);
    }
  }

  private void selectPreview() {
    templates =
        structures == null || selected == null || !habitats
            ? List.of()
            : structures.pools().getOrDefault(StructurePreview.poolKey(selected.key()), List.of());
    variant = 0;
    yaw = 135;
    zoom = 1.25F;
    loadPreview();
  }

  private void loadPreview() {
    int revision = ++previewGeneration;
    preview = null;
    previewError = "";
    previewLoading = !templates.isEmpty();
    if (!previewLoading) return;
    var template = templates.get(variant);
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return StructurePreview.read(template.path());
              } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
              }
            })
        .whenComplete(
            (nbt, failure) ->
                client.execute(
                    () -> {
                      if (revision != previewGeneration) return;
                      previewLoading = false;
                      try {
                        if (failure != null) throw new java.io.IOException("Preview unavailable");
                        preview = StructurePreview.model(nbt);
                      } catch (Exception ex) {
                        previewError = "Ce modèle ne peut pas être affiché.";
                      }
                    }));
  }

  private static String rarity(String bucket) {
    return switch (bucket) {
      case "common" -> "Commun";
      case "uncommon" -> "Peu commun";
      case "rare" -> "Rare";
      case "ultra-rare" -> "Très rare";
      default -> bucket;
    };
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    int mx = localX(mouseX), my = localY(mouseY);
    begin(c, ACCENT, "TROPIMON / BETTER FARM", "Explorer les habitats et préparer sa recherche");
    chip(c, "À proximité", 444, 16, 94, false, ACCENT);
    c.fill(16, 54, 216, 310, PANEL);
    chip(c, "Habitats", 24, 61, 89, habitats, ACCENT);
    chip(c, "Nature", 119, 61, 89, !habitats, ACCENT);
    search.render(c, mx, my, delta);
    for (int i = 0; i < 6 && offset + i < groups.size(); i++) {
      var group = groups.get(offset + i);
      int y = 122 + i * 25;
      if (group.equals(selected)) {
        c.fill(22, y - 3, 209, y + 21, 0xFF315543);
        c.fill(22, y - 3, 24, y + 21, ACCENT);
      }
      label(
          c,
          textRenderer.trimToWidth(group.name(), 173),
          29,
          y,
          group.equals(selected) ? ACCENT : WHITE);
      int count =
          habitats
              ? (int) group.entries().stream().map(FarmCatalog.Entry::species).distinct().count()
              : group.entries().size();
      label(
          c,
          count + (habitats ? " Pokémon correspondants" : " conditions d'apparition"),
          29,
          y + 11,
          MUTED);
    }
    label(
        c,
        loading
            ? "Chargement…"
            : groups.size() + (habitats ? " habitats" : " Pokémon") + " · molette",
        25,
        278,
        MUTED);
    chip(c, snapshot ? "Source : Tropimon" : "Source : Cobblemon", 23, 289, 185, snapshot, ACCENT);
    if (groups.size() > 6) {
      c.fill(211, 119, 213, 269, 0xFF20282A);
      int thumb = Math.max(10, 150 * 6 / groups.size());
      int y = 119 + (150 - thumb) * offset / (groups.size() - 6);
      c.fill(211, y, 213, y + thumb, ACCENT);
    }
    c.fill(226, 54, 544, 310, 0xFF303639);
    if (selected == null) {
      text(
          c,
          loading
              ? "Chargement du catalogue…"
              : error.isEmpty() ? "Aucun résultat. Essaie un autre habitat ou Pokémon." : error,
          242,
          130,
          280,
          MUTED);
      end(c);
      return;
    }
    label(c, textRenderer.trimToWidth(selected.name(), 296), 237, 62, WHITE);
    String[] tabs = {"Structure", "Pokémon", "Blocs", "Infos"};
    for (int i = 0; i < 4; i++) chip(c, tabs[i], 236 + i * 75, 78, 72, tab == i, ACCENT);
    if (tab == 0) {
      c.fill(236, 105, 534, 249, 0xFF20282A);
      if (preview != null) {
        // Scissor rectangles use screen coordinates, not the transformed panel matrix.
        c.enableScissor(
            left + (int) (236 * scale),
            top + (int) (105 * scale),
            left + (int) Math.ceil(534 * scale),
            top + (int) Math.ceil(249 * scale));
        StructurePreview.render(c, preview, 236, 108, 298, 138, yaw, zoom);
        c.disableScissor();
        label(
            c, preview.x() + " × " + preview.y() + " × " + preview.z() + " blocs", 244, 111, MUTED);
        label(c, "Décor", 490, 234, MUTED);
        for (int i = 0; i < Math.min(8, preview.materials().size()); i++) {
          var state = preview.materials().get(i);
          c.drawItem(state.getBlock().asItem().getDefaultStack(), 244 + i * 19, 230);
          if (hit(mx, my, 244 + i * 19, 230, 16, 16))
            c.drawTooltip(textRenderer, state.getBlock().getName(), mx, my);
        }
      } else
        text(
            c,
            previewLoading || indexing
                ? "Lecture des structures locales…"
                : !previewError.isEmpty()
                    ? previewError
                    : "Aucun modèle local associé. Consulte les onglets Pokémon et Blocs.",
            251,
            155,
            266,
            MUTED);
      chip(c, "<", 236, 254, 27, false, ACCENT);
      label(
          c,
          templates.isEmpty()
              ? "Modèle indisponible"
              : "Variante " + (variant + 1) + " / " + templates.size(),
          273,
          261,
          WHITE);
      chip(c, ">", 425, 254, 27, false, ACCENT);
      chip(c, "Vue", 461, 254, 73, false, ACCENT);
      label(c, "Glisser : tourner · molette : zoom", 242, 281, MUTED);
      label(c, "Modèle local / pièce · terrain non observé", 242, 295, MUTED);
    } else if (tab == 1) {
      for (int i = 0; i < 7 && detailOffset + i < selectedEntries.size(); i++) {
        var e = selectedEntries.get(detailOffset + i);
        int y = 111 + i * 25;
        c.fill(236, y - 2, 534, y + 21, i % 2 == 0 ? PANEL : 0xFF303639);
        label(c, textRenderer.trimToWidth(species(e), 166), 244, y, WHITE);
        label(c, "Niv. " + e.level(), 424, y, ACCENT);
        label(
            c,
            textRenderer.trimToWidth(
                rarity(e.bucket()) + " · " + (habitats ? "Conditions : onglet Blocs" : place(e)),
                282),
            244,
            y + 11,
            MUTED);
      }
      label(c, selectedEntries.size() + " entrées · molette pour parcourir", 242, 294, MUTED);
    } else {
      for (int i = 0; i < 15 && detailOffset + i < lines.size(); i++)
        label(
            c,
            lines.get(detailOffset + i),
            238,
            109 + i * 12,
            i + detailOffset < 6 ? MUTED : WHITE);
      label(c, "Molette : lire les détails", 242, 295, MUTED);
    }
    end(c);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (button != 0) return super.mouseClicked(x, y, button);
    search.setFocused(hit(mx, my, 24, 91, 184, 19));
    if (search.mouseClicked(mx, my, button)) return true;
    if (hit(mx, my, 444, 16, 94, 21)) {
      client.setScreen(new HabitatRadarScreen());
      return true;
    }
    if (hit(mx, my, 24, 61, 184, 21)) {
      habitats = mx < 116;
      tab = habitats ? 0 : 1;
      filter();
      return true;
    }
    if (hit(mx, my, 23, 289, 185, 21) && !loading) {
      snapshot = !snapshot;
      catalog = null;
      groups = List.of();
      selected = null;
      load();
      return true;
    }
    if (hit(mx, my, 22, 119, 187, 150)) {
      int i = offset + (my - 119) / 25;
      if (i < groups.size()) {
        selected = groups.get(i);
        selectionChanged();
      }
      return true;
    }
    if (selected == null) return super.mouseClicked(x, y, button);
    if (hit(mx, my, 236, 78, 300, 21)) {
      int next = (mx - 236) / 75;
      if (next == 2)
        client.setScreen(
            new HabitatBlocksScreen(
                this,
                null,
                selectedEntries,
                snapshot ? "Catalogue Tropimon · 16/09/2026" : "Catalogue local Cobblemon"));
      else {
        tab = next;
        detailOffset = 0;
        details();
      }
      return true;
    }
    if (tab == 0) {
      if (!templates.isEmpty()
          && (hit(mx, my, 236, 254, 27, 21) || hit(mx, my, 425, 254, 27, 21))) {
        variant = Math.floorMod(variant + (mx < 300 ? -1 : 1), templates.size());
        loadPreview();
        return true;
      }
      if (hit(mx, my, 461, 254, 73, 21)) {
        yaw += 90;
        zoom = 1.25F;
        return true;
      }
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (button == 0 && tab == 0 && hit(localX(x), localY(y), 236, 105, 298, 144)) {
      yaw += (float) (dx / scale);
      return true;
    }
    return super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double h, double v) {
    int mx = localX(x), my = localY(y);
    if (hit(mx, my, 22, 119, 187, 150))
      offset = Math.clamp(offset + (v > 0 ? -1 : 1), 0, Math.max(0, groups.size() - 6));
    else if (tab == 0 && hit(mx, my, 236, 105, 298, 144))
      zoom = Math.clamp(zoom + (float) v * .15F, .5F, 3F);
    else if (hit(mx, my, 236, 105, 298, 180))
      detailOffset =
          Math.clamp(
              detailOffset + (v > 0 ? -2 : 2),
              0,
              Math.max(0, (tab == 1 ? selectedEntries.size() - 7 : lines.size() - 15)));
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
}
