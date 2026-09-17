package fr.tropimon.farm;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Block recognition and catalogue requirements, kept separate from live validation. */
public final class HabitatBlocksScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFF86E3AD;

  private record Row(String label, String detail, ItemStack icon, boolean heading) {}

  private final Screen parent;
  private final BlockPos pos;
  private final long scope;
  private final List<TerrainHints.Line> declarations;
  private final String provenance;
  private final List<Row> rows = new ArrayList<>();
  private boolean expanded;
  private int scroll;
  private long revision = -1;
  private HabitatMonitor.Observation observed;

  HabitatBlocksScreen(
      Screen parent, BlockPos pos, List<FarmCatalog.Entry> entries, String provenance) {
    super("Tropimon Better Farm · blocs et zone");
    this.parent = parent;
    this.pos = pos;
    this.provenance = provenance;
    scope = HabitatMonitor.INSTANCE.scopeRevision();
    declarations = TerrainHints.from(entries);
  }

  @Override
  protected void init() {
    super.init();
    rebuild();
  }

  static Block block(String raw) {
    Identifier id = Identifier.tryParse(raw.split("\\[", 2)[0]);
    return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
  }

  private void rebuild() {
    rows.clear();
    for (var line : declarations) {
      if (!line.block()) {
        String label = line.label();
        if (line.heading() && line.value().startsWith("Entrée ")) {
          String properties = label.contains(" ") ? label.substring(label.indexOf(' ')) : "";
          String id = HabitatCycles.speciesId(label);
          label =
              Text.translatable("cobblemon.species." + id.substring(id.indexOf(':') + 1) + ".name")
                  .getString();
          label += properties;
        }
        rows.add(new Row(label, line.value(), ItemStack.EMPTY, line.heading()));
      } else if (line.value().startsWith("#")) {
        Identifier id = Identifier.tryParse(line.value().substring(1));
        List<Block> members = new ArrayList<>();
        if (id != null)
          Registries.BLOCK
              .getEntryList(TagKey.of(RegistryKeys.BLOCK, id))
              .ifPresent(tag -> tag.forEach(entry -> members.add(entry.value())));
        rows.add(
            new Row(
                line.label() + " : " + line.value(),
                members.isEmpty()
                    ? "Tag non résolu côté client"
                    : members.size() + " blocs dans le tag reçu · développer pour les voir",
                members.isEmpty() ? ItemStack.EMPTY : members.getFirst().asItem().getDefaultStack(),
                false));
        if (expanded) {
          for (var member : members.stream().limit(256).toList())
            rows.add(
                new Row(
                    member.getName().getString(),
                    Registries.BLOCK.getId(member).toString(),
                    member.asItem().getDefaultStack(),
                    false));
          if (members.size() > 256)
            rows.add(
                new Row(
                    "Tag volumineux",
                    "256 premiers blocs affichés sur " + members.size(),
                    ItemStack.EMPTY,
                    false));
        }
      } else {
        Block block = block(line.value());
        rows.add(
            new Row(
                line.label() + " : " + (block == null ? line.value() : block.getName().getString()),
                line.value(),
                block == null ? ItemStack.EMPTY : block.asItem().getDefaultStack(),
                false));
      }
    }
    if (rows.isEmpty())
      rows.add(
          new Row(
              "Catalogue non sélectionné",
              "Choisis un habitat candidat dans Cycles pour voir ses exigences.",
              ItemStack.EMPTY,
              false));
    scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - 6));
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    var monitor = HabitatMonitor.INSTANCE;
    if (revision != monitor.revision()) {
      revision = monitor.revision();
      observed =
          scope == monitor.scopeRevision() && pos != null
              ? monitor.entries(client).stream()
                  .filter(o -> o.pos().equals(pos) && o.present())
                  .findFirst()
                  .orElse(null)
              : null;
    }
    begin(
        c,
        ACCENT,
        "TROPIMON / BLOCS & ZONE",
        "Reconnaître le bloc · lire les conditions du terrain");
    chip(c, "Retour", 467, 14, 73, false, ACCENT);
    c.fill(18, 53, 542, 107, PANEL);
    Block mimic = observed == null ? null : block(observed.mimic());
    if (mimic != null) c.drawItem(mimic.asItem().getDefaultStack(), 27, 65);
    label(
        c,
        observed == null
            ? "Aucun bloc d'habitat observé ici"
            : "Habitat observé · apparence : "
                + (mimic == null ? observed.mimic() : mimic.getName().getString()),
        52,
        61,
        WHITE);
    label(
        c,
        observed == null
            ? "Cette fiche décrit le catalogue choisi."
            : observed.pos().getX()
                + " / "
                + observed.pos().getY()
                + " / "
                + observed.pos().getZ()
                + " · "
                + observed.mimic(),
        52,
        76,
        MUTED);
    label(
        c,
        "Le décor seul ne prouve pas une zone ; poser ces blocs ne crée pas un habitat.",
        26,
        94,
        MUTED);
    chip(
        c,
        expanded ? "Tags : blocs développés" : "Tags : afficher les blocs",
        20,
        115,
        220,
        expanded,
        ACCENT);
    label(c, textRenderer.trimToWidth(provenance, 288), 250, 122, MUTED);
    int mx = localX(mouseX), my = localY(mouseY);
    Row hovered = null;
    for (int i = 0; i < 6 && scroll + i < rows.size(); i++) {
      Row row = rows.get(scroll + i);
      int y = 145 + i * 25;
      c.fill(20, y - 1, 540, y + 23, row.heading() ? 0xFF40574B : PANEL);
      if (!row.icon().isEmpty()) c.drawItem(row.icon(), 25, y + 3);
      int x = row.icon().isEmpty() ? 27 : 48;
      label(
          c,
          textRenderer.trimToWidth(row.label(), 532 - x),
          x,
          y + 2,
          row.heading() ? ACCENT : WHITE);
      label(c, textRenderer.trimToWidth(row.detail(), 532 - x), x, y + 13, MUTED);
      if (hit(mx, my, 20, y, 520, 24)) hovered = row;
    }
    label(
        c,
        "Molette : liste · survol : texte complet · conditions non évaluées sur place",
        22,
        301,
        MUTED);
    if (hovered != null)
      c.drawOrderedTooltip(
          textRenderer,
          textRenderer.wrapLines(Text.literal(hovered.label() + " : " + hovered.detail()), 400),
          mx,
          my);
    end(c);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (hit(mx, my, 467, 14, 73, 21)) {
      close();
      return true;
    }
    if (hit(mx, my, 20, 115, 220, 21)) {
      expanded = !expanded;
      scroll = 0;
      rebuild();
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double h, double v) {
    scroll = Math.clamp(scroll + (v > 0 ? -1 : 1), 0, Math.max(0, rows.size() - 6));
    return true;
  }

  @Override
  public void close() {
    client.setScreen(parent);
  }
}
