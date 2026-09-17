package fr.tropimon.farm;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Isolated synthetic world only; excluded from all delivery JARs. */
public final class SmokeClient implements ClientModInitializer {
  int ticks, stage = -1;
  long start;
  net.minecraft.client.gui.screen.Screen screen;
  BlockPos habitatPos;

  @Override
  public void onInitializeClient() {
    if (!Boolean.getBoolean("tropimon.smoke")) return;
    start = System.nanoTime();
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (stage == 99) return;
          try {
            if (System.nanoTime() - start > 240_000_000_000L)
              throw new AssertionError("Smoke timeout: " + stage);
            if (client.getOverlay() != null || ++ticks < 40) return;
            switch (stage) {
              case -1 -> {
                stage = 0;
                ticks = 0;
                client.options.getViewDistance().setValue(2);
                client.options.getGuiScale().setValue(2);
                client.options.pauseOnLostFocus = false;
                client
                    .getTutorialManager()
                    .setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client
                    .createIntegratedServerLoader()
                    .createAndStart(
                        "instrument-smoke-" + System.currentTimeMillis(),
                        new LevelInfo(
                            "Instrument verification",
                            GameMode.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(1L, false, false),
                        registries ->
                            registries
                                .get(RegistryKeys.WORLD_PRESET)
                                .get(WorldPresets.FLAT)
                                .createDimensionsRegistryHolder(),
                        client.currentScreen);
              }
              case 0 -> {
                if (client.player == null) return;
                habitatPos = client.player.getBlockPos().add(3, 0, 3);
                client
                    .getServer()
                    .submit(
                        () -> {
                          var world = client.getServer().getOverworld();
                          var state =
                              com.cobblemon.mod.common.CobblemonBlocks.HABITAT_BLOCK
                                  .getDefaultState();
                          world.setBlockState(habitatPos, state, 3);
                          var block =
                              (com.cobblemon.mod.common.block.habitat.HabitatBlockEntity)
                                  world.getBlockEntity(habitatPos);
                          block.setMimicId(
                              net.minecraft.util.Identifier.of("minecraft", "moss_block"));
                          block.setDisplaySpeciesIds(
                              List.of(
                                  net.minecraft.util.Identifier.of("cobblemon", "pikachu"),
                                  net.minecraft.util.Identifier.of("cobblemon", "eevee")));
                          world.updateListeners(habitatPos, state, state, 3);
                        })
                    .get();
                stage = 1;
                ticks = 0;
              }
              case 1 -> {
                HabitatMonitor.INSTANCE.scan(client);
                var entries = HabitatMonitor.INSTANCE.entries(client);
                if (entries.stream().noneMatch(e -> e.pos().equals(habitatPos))) return;
                require(
                    entries.stream().anyMatch(e -> e.pos().equals(habitatPos) && e.present()),
                    "actual synchronized habitat detected");
                HabitatMonitor.INSTANCE.toggle(habitatPos);
                screen = new HabitatRadarScreen();
                client.setScreen(screen);
                stage = 2;
                ticks = 0;
              }
              case 2 -> {
                shot(client, "farm-radar");
                require(
                    HabitatMonitor.INSTANCE.entries(client).stream().anyMatch(e -> e.pinned()),
                    "habitat pinned");
                screen = new FarmScreen();
                client.setScreen(screen);
                stage = 3;
                ticks = 0;
              }
              case 3 -> {
                if ((boolean) field(screen, "loading")) return;
                require(field(screen, "catalog") != null, "catalogue loaded");
                require(
                    ((List<?>) field(screen, "filtered")).size() > 100,
                    "habitat catalogue populated");
                shot(client, "farm-catalogue");
                client
                    .getServer()
                    .submit(() -> client.getServer().getOverworld().removeBlock(habitatPos, false))
                    .get();
                stage = 4;
                ticks = 0;
              }
              case 4 -> {
                HabitatMonitor.INSTANCE.scan(client);
                require(
                    HabitatMonitor.INSTANCE.entries(client).stream()
                        .anyMatch(
                            e ->
                                e.pos().equals(habitatPos)
                                    && e.loaded()
                                    && !e.present()
                                    && e.pinned()),
                    "removed habitat never remains live");
                done(client);
              }
            }
          } catch (Throwable ex) {
            ex.printStackTrace();
            System.err.println("TROPIMON_SMOKE_FAILED");
            client.scheduleStop();
            stage = 99;
          }
        });
  }

  static Object field(Object instance, String name) throws Exception {
    var f = instance.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(instance);
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    System.out.println("TROPIMON_CHECK: " + message);
  }

  static void shot(MinecraftClient client, String name) throws Exception {
    Path dir = client.runDirectory.toPath().resolve("verification");
    Files.createDirectories(dir);
    try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
      image.writeTo(dir.resolve(name + ".png"));
    }
  }

  void done(MinecraftClient client) {
    System.out.println("TROPIMON_SMOKE_OK");
    client.scheduleStop();
    stage = 99;
  }
}
