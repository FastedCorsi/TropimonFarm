package fr.tropimon.farm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class FarmClient implements ClientModInitializer {
  public void onInitializeClient() {
    var key =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.tropimon_farm.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.tropimon_farm"));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          HabitatMonitor.INSTANCE.tick(c);
          while (key.wasPressed()) c.setScreen(new HabitatRadarScreen());
        });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
        (h, c) -> HabitatMonitor.INSTANCE.reset());
    net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
        (context, tick) -> {
          var client = net.minecraft.client.MinecraftClient.getInstance();
          if (client.player == null
              || client.options.hudHidden
              || client.currentScreen instanceof InstrumentScreen) return;
          var watched =
              HabitatMonitor.INSTANCE.entries(client).stream()
                  .filter(HabitatMonitor.Observation::pinned)
                  .limit(3)
                  .toList();
          if (watched.isEmpty()) return;
          int x = client.getWindow().getScaledWidth() - 186, y = 8;
          context.fill(x - 5, y - 4, x + 181, y + 14 + watched.size() * 13, 0xDC0B1621);
          context.drawText(client.textRenderer, "HABITATS SUIVIS · F8", x, y, 0xFF86E3AD, false);
          y += 14;
          for (var observation : watched) {
            String status =
                !observation.loaded()
                    ? "hors chargement"
                    : !observation.present()
                        ? "absent"
                        : Math.round(
                                Math.sqrt(
                                    observation.pos().getSquaredDistance(client.player.getPos())))
                            + " m";
            String row =
                observation.pos().getX() + ", " + observation.pos().getZ() + " · " + status;
            context.drawText(
                client.textRenderer,
                client.textRenderer.trimToWidth(row, 178),
                x,
                y,
                observation.present() ? 0xFFEAF8F1 : 0xFF9BAAAF,
                false);
            y += 13;
          }
        });
    TropimonSelfUpdater.start(LoggerFactory.getLogger("tropimon_farm"));
  }
}
