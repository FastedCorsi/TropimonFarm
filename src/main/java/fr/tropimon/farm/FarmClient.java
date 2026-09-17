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
          HabitatOverlay.tick(c);
          while (key.wasPressed()) {
            var target = HabitatMonitor.INSTANCE.targeted(c);
            c.setScreen(new HabitatRadarScreen(target == null ? null : target.pos()));
          }
        });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
        (h, c) -> HabitatMonitor.INSTANCE.reset());
    net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
        (context, tick) ->
            HabitatOverlay.render(context, key.getBoundKeyLocalizedText().getString()));
    net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(
        HabitatOverlay::world);
    TropimonSelfUpdater.start(LoggerFactory.getLogger("tropimon_farm"));
  }
}
