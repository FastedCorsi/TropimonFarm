package fr.tropimon.farm;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Client catalogue matched to received species. Every phase prediction is an explicit hypothesis.
 */
public final class HabitatCycleScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFF86E3AD;
  private final BlockPos pos;
  private final long scope;
  private boolean snapshot, all, loading, invalid;
  private int generation, poolIndex, viewPhase = 1, scroll, estimate;
  private long revision = -1, sampledAge = -2;
  private String error = "", fingerprint = "";
  private HabitatCycles.Order order = HabitatCycles.Order.UNKNOWN;
  private List<HabitatCycles.Pool> pools = List.of(), choices = List.of();
  private final List<String> lines = new ArrayList<>();

  public HabitatCycleScreen(BlockPos pos) {
    super("Tropimon Better Farm · cycles");
    this.pos = pos.toImmutable();
    scope = HabitatMonitor.INSTANCE.scopeRevision();
  }

  @Override
  protected void init() {
    super.init();
    if (pools.isEmpty() && !loading) load();
  }

  @Override
  public void removed() {
    generation++;
  }

  private void load() {
    loading = true;
    error = "";
    pools = choices = List.of();
    resetHypothesis();
    int request = ++generation;
    boolean profile = snapshot;
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return HabitatCycles.pools(FarmCatalog.load(profile));
              } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
              }
            })
        .whenComplete(
            (result, failure) ->
                client.execute(
                    () -> {
                      if (request != generation) return;
                      loading = false;
                      if (failure != null) error = "Catalogue indisponible";
                      else pools = result;
                      revision = -1;
                      refresh();
                    }));
  }

  private void resetHypothesis() {
    order = HabitatCycles.Order.UNKNOWN;
    poolIndex = 0;
    viewPhase = 1;
    scroll = 0;
    estimate = 0;
  }

  private HabitatMonitor.Observation observation() {
    return HabitatMonitor.INSTANCE.entries(client).stream()
        .filter(e -> e.pos().equals(pos))
        .findFirst()
        .orElse(null);
  }

  private HabitatCycles.Pool pool() {
    return choices.isEmpty() ? null : choices.get(Math.floorMod(poolIndex, choices.size()));
  }

  private void refresh() {
    var monitor = HabitatMonitor.INSTANCE;
    if (scope != monitor.scopeRevision()) {
      invalid = true;
      resetHypothesis();
      lines.clear();
      return;
    }
    long age = HabitatClock.age();
    if (revision == monitor.revision() && age == sampledAge) return;
    revision = monitor.revision();
    sampledAge = age;
    var o = observation();
    String key = o == null || !o.present() ? "" : o.mimic() + o.species();
    if (!fingerprint.equals(key)) {
      resetHypothesis();
      fingerprint = key;
    }
    choices = all ? pools : HabitatCycles.candidates(pools, o == null ? List.of() : o.species());
    rebuild();
  }

  private static String name(String raw) {
    return Text.translatable(raw).getString();
  }

  private void add(String text) {
    for (var part : textRenderer.wrapLines(Text.literal(text), 512)) {
      StringBuilder out = new StringBuilder();
      part.accept(
          (i, style, cp) -> {
            out.appendCodePoint(cp);
            return true;
          });
      lines.add(out.toString());
    }
  }

  private void rebuild() {
    lines.clear();
    estimate = 0;
    var p = pool();
    if (p == null) return;
    var o = observation();
    if (!snapshot && o != null && o.present() && !invalid)
      estimate = HabitatCycles.phase(sampledAge, pos.asLong(), p.phases(), order);
    if (p.phases() == 0) add("Phases non interprétables : estimation désactivée.");
    for (var e : p.entries()) {
      if (!snapshot && !FarmCatalog.phaseMatches(e.phases(), viewPhase)) continue;
      String id = HabitatCycles.speciesId(e.species());
      String species =
          Text.translatable("cobblemon.species." + id.substring(id.indexOf(':') + 1) + ".name")
              .getString();
      add(species + " · niv. " + e.level() + " · " + e.bucket() + " · poids " + e.weight());
      for (String detail : e.details()) if (!detail.startsWith("Ressource :")) add("  " + detail);
    }
    if (lines.isEmpty()) add("Aucune entrée du catalogue pour cette phase.");
    scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - 7));
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    refresh();
    begin(
        c,
        ACCENT,
        "HABITAT / CYCLES",
        pos.getX() + " / " + pos.getY() + " / " + pos.getZ() + " · catalogue et hypothèses");
    chip(c, "Radar", 467, 14, 73, false, ACCENT);
    chip(
        c,
        snapshot ? "Profil : Tropimon · HB 1.4.0" : "Profil : ressources installées",
        20,
        54,
        256,
        snapshot,
        ACCENT);
    chip(
        c,
        snapshot ? "Cycles désactivés sur Tropimon" : "Ordre supposé : " + order,
        284,
        54,
        252,
        false,
        ACCENT);
    if (invalid) {
      text(
          c,
          "Session ou région changée. Revenir au radar pour sélectionner le bloc.",
          22,
          95,
          510,
          WHITE);
      end(c);
      return;
    }
    var p = pool();
    chip(c, "<", 20, 82, 25, false, ACCENT);
    chip(c, ">", 511, 82, 25, false, ACCENT);
    label(
        c,
        textRenderer.trimToWidth(
            p == null
                ? (loading
                    ? "Chargement…"
                    : error.isEmpty() ? "Aucun candidat : essayer tous les habitats" : error)
                : "Candidat "
                    + (Math.floorMod(poolIndex, choices.size()) + 1)
                    + "/"
                    + choices.size()
                    + " · "
                    + name(p.name()),
            454),
        50,
        89,
        WHITE);
    chip(
        c,
        all ? "Liste : tous les habitats" : "Liste : espèces correspondantes",
        20,
        110,
        256,
        all,
        ACCENT);
    chip(
        c,
        snapshot
            ? "Toutes les apparitions du catalogue"
            : "Voir phase : " + viewPhase + " / " + (p == null ? "?" : p.phases()),
        284,
        110,
        252,
        false,
        ACCENT);
    label(
        c,
        snapshot
            ? "Tropimon : aucun cycle à prédire."
            : estimate == 0
                ? "Phase estimée : inconnue (bloc, horloge ou ordre manquant)"
                : "Phase estimée : " + estimate + " · cliquer pour afficher ses Pokémon",
        22,
        143,
        estimate == 0 ? MUTED : ACCENT);
    label(
        c,
        estimate == 0
            ? "Choisir un candidat et un ordre ne confirme pas les réglages serveur."
            : "Prochaine tranche : "
                + (24000 - sampledAge % 24000)
                + " ticks de simulation · dernier paquet reçu",
        22,
        157,
        MUTED);
    label(
        c,
        snapshot
            ? "Instantané Tropimon du 16/09/2026 : configuration actuelle non vérifiée."
            : "Ressources locales : les datapacks du serveur peuvent différer.",
        22,
        174,
        MUTED);
    label(
        c,
        "Pokémon possibles selon ces données, jamais le prochain spawn garanti.",
        22,
        187,
        MUTED);
    c.fill(18, 201, 541, 286, PANEL);
    for (int i = 0; i < 7 && scroll + i < lines.size(); i++)
      label(c, lines.get(scroll + i), 23, 205 + i * 12, WHITE);
    chip(c, "Blocs / zone de cette phase", 20, 291, 240, false, ACCENT);
    label(c, "Molette : Pokémon et conditions", 278, 298, MUTED);
    end(c);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (hit(mx, my, 467, 14, 73, 21)) {
      client.setScreen(new HabitatRadarScreen());
      return true;
    }
    if (invalid) return super.mouseClicked(x, y, button);
    if (hit(mx, my, 20, 291, 240, 21) && pool() != null) {
      client.setScreen(
          new HabitatBlocksScreen(
              this,
              pos,
              pool().entries().stream()
                  .filter(e -> snapshot || FarmCatalog.phaseMatches(e.phases(), viewPhase))
                  .toList(),
              snapshot
                  ? "Tropimon · instantané HB 1.4.0"
                  : "Catalogue local · réglages non confirmés"));
      return true;
    }
    if (hit(mx, my, 20, 54, 256, 21)) {
      snapshot = !snapshot;
      load();
      return true;
    }
    if (!snapshot && hit(mx, my, 284, 54, 252, 21)) {
      order = HabitatCycles.Order.values()[(order.ordinal() + 1) % 4];
      rebuild();
      return true;
    }
    if (hit(mx, my, 20, 82, 25, 21) || hit(mx, my, 511, 82, 25, 21)) {
      poolIndex += mx < 50 ? -1 : 1;
      order = HabitatCycles.Order.UNKNOWN;
      viewPhase = 1;
      scroll = 0;
      rebuild();
      return true;
    }
    if (hit(mx, my, 20, 110, 256, 21)) {
      all = !all;
      resetHypothesis();
      revision = -1;
      refresh();
      return true;
    }
    if (!snapshot && hit(mx, my, 284, 110, 252, 21) && pool() != null) {
      viewPhase = viewPhase % Math.max(1, pool().phases()) + 1;
      scroll = 0;
      rebuild();
      return true;
    }
    if (hit(mx, my, 20, 139, 516, 14) && estimate > 0) {
      viewPhase = estimate;
      scroll = 0;
      rebuild();
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double h, double v) {
    scroll = Math.clamp(scroll + (v > 0 ? -1 : 1), 0, Math.max(0, lines.size() - 7));
    return true;
  }
}
