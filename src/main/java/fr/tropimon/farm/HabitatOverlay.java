package fr.tropimon.farm;

import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/** Passive in-world markers and a card for the actual block under the crosshair. */
final class HabitatOverlay {
  private static List<HabitatMonitor.Observation> nearby = List.of(), watched = List.of();
  private static HabitatMonitor.Observation target;
  private static long revision = -1;
  private static Object world;
  private static long scope = -1;
  private static int ticks;

  static void tick(MinecraftClient client) {
    if (client.world == null || client.player == null) {
      nearby = watched = List.of();
      target = null;
      revision = -1;
      world = null;
      return;
    }
    var monitor = HabitatMonitor.INSTANCE;
    world = client.world;
    scope = monitor.scopeRevision();
    target = client.currentScreen == null ? monitor.targeted(client) : null;
    if (revision == monitor.revision() && ++ticks < 10) return;
    ticks = 0;
    revision = monitor.revision();
    var entries = monitor.entries(client);
    nearby =
        entries.stream()
            .filter(HabitatMonitor.Observation::present)
            .filter(
                o ->
                    o.pos().getSquaredDistance(client.player.getPos())
                        <= HabitatMonitor.RANGE * HabitatMonitor.RANGE)
            .limit(64)
            .toList();
    watched = entries.stream().filter(HabitatMonitor.Observation::pinned).limit(3).toList();
  }

  static void world(WorldRenderContext context) {
    var client = MinecraftClient.getInstance();
    if (!visible(client) || context.matrixStack() == null || context.consumers() == null) return;
    var matrices = context.matrixStack();
    var camera = context.camera().getPos();
    matrices.push();
    matrices.translate(-camera.x, -camera.y, -camera.z);
    var lines = context.consumers().getBuffer(RenderLayer.getLines());
    for (var entry : nearby) {
      // Recheck the received block: stale scan entries must not keep a ghost outline.
      if (!(client.world.getBlockEntity(entry.pos()) instanceof HabitatBlockEntity)) continue;
      boolean selected = target != null && target.pos().equals(entry.pos());
      WorldRenderer.drawBox(
          matrices,
          lines,
          new Box(entry.pos()).expand(0.006),
          selected ? 1F : 0.42F,
          selected ? 0.84F : 0.90F,
          selected ? 0.36F : 0.64F,
          0.95F);
    }
    matrices.pop();
  }

  static void render(DrawContext context, String detailsKey) {
    var client = MinecraftClient.getInstance();
    if (!visible(client)) return;
    if (nearby.isEmpty() && watched.isEmpty() && target == null) return;
    int width = Math.min(248, client.getWindow().getScaledWidth() - 16);
    int x = client.getWindow().getScaledWidth() - width - 8;
    List<String> rows = new ArrayList<>();
    rows.add("Tropimon Better Farm");
    if (target != null) {
      var block = HabitatBlocksScreen.block(target.mimic());
      rows.add("Bloc visé : " + (block == null ? target.mimic() : block.getName().getString()));
      rows.add(target.pos().getX() + " / " + target.pos().getY() + " / " + target.pos().getZ());
      rows.add("Style : " + (target.activated() ? "activé" : "naturel"));
      rows.add("Annulation spawns ordinaires : " + (target.cancelsSpawns() ? "oui" : "non"));
      rows.add("Espèces d'affichage reçues :");
      if (target.species().isEmpty()) rows.add("Aucune espèce transmise");
      for (String id : target.species().stream().limit(6).toList())
        rows.add(
            "  "
                + Text.translatable(
                        "cobblemon.species." + id.substring(id.indexOf(':') + 1) + ".name")
                    .getString());
      if (target.species().size() > 6)
        rows.add("  + " + (target.species().size() - 6) + " espèces · " + detailsKey + " détails");
      rows.add("Phase : non transmise · " + detailsKey + " cycles");
    } else rows.add("Vise un habitat pour afficher sa fiche");
    if (!nearby.isEmpty()) {
      rows.add("À proximité · contours verts");
      for (var o : nearby.stream().limit(3).toList())
        if (client.world.getBlockEntity(o.pos()) instanceof HabitatBlockEntity)
          rows.add(position(o) + " · " + distance(o, client) + " m");
    }
    if (!watched.isEmpty()) {
      rows.add("Repères suivis");
      for (var o : watched)
        rows.add(
            position(o)
                + " · "
                + (!o.loaded()
                    ? "hors chargement"
                    : !o.present() ? "absent" : distance(o, client) + " m"));
    }
    var lines = new ArrayList<net.minecraft.text.OrderedText>();
    for (String row : rows) lines.addAll(client.textRenderer.wrapLines(Text.literal(row), width));
    int maxRows = Math.max(2, (client.getWindow().getScaledHeight() - 56) / 11);
    if (lines.size() > maxRows) {
      lines.subList(maxRows - 1, lines.size()).clear();
      lines.add(Text.literal(detailsKey + " : fiche complète et repères").asOrderedText());
    }
    int y = Math.max(8, client.getWindow().getScaledHeight() - lines.size() * 11 - 42);
    context.fill(x - 4, y - 3, x + width + 4, y + lines.size() * 11 + 3, 0xDC292D30);
    context.fill(x - 4, y - 3, x + width + 4, y - 1, 0xFF86E3AD);
    for (int i = 0; i < lines.size(); i++)
      context.drawText(
          client.textRenderer,
          lines.get(i),
          x,
          y + i * 11,
          i == 0 ? 0xFF86E3AD : 0xFFF1F3EC,
          false);
  }

  private static String position(HabitatMonitor.Observation o) {
    return o.pos().getX() + ", " + o.pos().getY() + ", " + o.pos().getZ();
  }

  static boolean visible(MinecraftClient client) {
    return client.world != null
        && client.world == world
        && client.player != null
        && scope == HabitatMonitor.INSTANCE.scopeRevision()
        && client.currentScreen == null
        && !client.options.hudHidden;
  }

  private static long distance(HabitatMonitor.Observation o, MinecraftClient client) {
    return Math.round(Math.sqrt(o.pos().getSquaredDistance(client.player.getPos())));
  }
}
