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
  HabitatPhaseAudit audit;

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
                if (Boolean.getBoolean("tropimon.habitatAudit"))
                  require(
                      client
                          .runDirectory
                          .toPath()
                          .toAbsolutePath()
                          .normalize()
                          .endsWith(Path.of("build", "verify-habitat-audit")),
                      "audit isolated directory enforced");
                stage = 0;
                ticks = 0;
                client.options.getViewDistance().setValue(2);
                client.options.getGuiScale().setValue(Integer.getInteger("tropimon.smoke.guiScale", 2));
                client.onResolutionChanged();
                System.out.println("FARM_GUI viewport=" + client.getWindow().getScaledWidth() + "x"
                    + client.getWindow().getScaledHeight() + " effective=" + client.getWindow().getScaleFactor());
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
                          world.setBlockState(
                              habitatPos.add(-2, 0, 0),
                              net.minecraft.block.Blocks.MOSS_BLOCK.getDefaultState(),
                              3);
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
                var entries = HabitatMonitor.INSTANCE.entries(client);
                if (entries.stream().noneMatch(e -> e.pos().equals(habitatPos))) return;
                require(client.currentScreen == null, "automatic detection with no interface open");
                require(
                    entries.stream().noneMatch(e -> e.pinned()),
                    "automatic detection needs no bookmark");
                require(
                    entries.stream().anyMatch(e -> e.pos().equals(habitatPos) && e.present()),
                    "actual synchronized habitat detected");
                lookAt(client, habitatPos);
                stage = 30;
                ticks = 0;
              }
              case 30 -> {
                var target = HabitatMonitor.INSTANCE.targeted(client);
                require(
                    target != null && target.pos().equals(habitatPos),
                    "actual crosshair selects habitat in game");
                require(
                    target.species().size() == 2 && target.mimic().equals("minecraft:moss_block"),
                    "target card uses that block's received information");
                shot(client, "better-farm-ingame");
                lookAt(client, habitatPos.add(-2, 0, 0));
                stage = 31;
                ticks = 0;
              }
              case 31 -> {
                require(
                    client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit
                        && hit.getBlockPos().equals(habitatPos.add(-2, 0, 0)),
                    "crosshair actually hits ordinary matching decor");
                require(
                    HabitatMonitor.INSTANCE.targeted(client) == null,
                    "ordinary matching decor does not identify an habitat");
                require(
                    HabitatMonitor.INSTANCE.entries(client).stream()
                        .noneMatch(e -> e.pos().equals(habitatPos.add(-2, 0, 0))),
                    "ordinary decor has no marker");
                HabitatMonitor.INSTANCE.toggle(habitatPos);
                screen = new HabitatRadarScreen(habitatPos);
                client.setScreen(screen);
                stage = 2;
                ticks = 0;
              }
              case 2 -> {
                shot(client, "farm-radar");
                require(
                    HabitatMonitor.INSTANCE.entries(client).stream().anyMatch(e -> e.pinned()),
                    "habitat pinned");
                screen = new HabitatCycleScreen(habitatPos);
                client.setScreen(screen);
                stage = 20;
                ticks = 0;
              }
              case 20 -> {
                if ((boolean) field(screen, "loading")) return;
                require(!((List<?>) field(screen, "pools")).isEmpty(), "cycle pools loaded");
                require(
                    (int) field(screen, "estimate") == 0,
                    "no estimate without explicit assumptions");
                // The synthetic display list need not match a catalogue pool: select all
                // explicitly.
                var cycle = (HabitatCycleScreen) screen;
                screen.mouseClicked(
                    cycle.left + 30 * cycle.scale, cycle.top + 120 * cycle.scale, 0);
                screen.mouseClicked(
                    cycle.left + 300 * cycle.scale, cycle.top + 64 * cycle.scale, 0);
                require(HabitatClock.age() >= 0, "ordinary time packet observed on client");
                require(
                    (int) field(screen, "estimate") > 0,
                    "explicit hypothesis uses received world age");
                stage = 21;
                ticks = 0;
              }
              case 21 -> {
                shot(client, "farm-cycles");
                var cycle = (HabitatCycleScreen) screen;
                screen.mouseClicked(
                    cycle.left + 30 * cycle.scale, cycle.top + 300 * cycle.scale, 0);
                require(
                    client.currentScreen instanceof HabitatBlocksScreen,
                    "cycle terrain button opens block guide");
                screen = client.currentScreen;
                stage = 22;
                ticks = 0;
              }
              case 22 -> {
                require(
                    field(screen, "observed") != null,
                    "terrain guide shows synchronized habitat block");
                shot(client, "farm-blocks");
                HabitatClock.reset();
                require(HabitatClock.age() == -1, "clock reset clears prior session age");
                screen = new FarmScreen();
                client.setScreen(screen);
                stage = 3;
                ticks = 0;
              }
              case 3 -> {
                if ((boolean) field(screen, "loading")
                    || (boolean) field(screen, "indexing")
                    || (boolean) field(screen, "previewLoading")) return;
                require(field(screen, "catalog") != null, "catalogue loaded");
                require(
                    ((List<?>) field(screen, "filtered")).size() > 100,
                    "habitat catalogue populated");
                shot(client, "farm-catalogue");
                require(
                    ((List<?>) field(screen, "groups")).size()
                        < ((List<?>) field(screen, "filtered")).size(),
                    "catalogue groups repeated species into habitats");
                require(
                    field(screen, "preview") != null,
                    "selected habitat has a real local structure preview");
                var model = (StructurePreview.Model) field(screen, "preview");
                require(
                    !model.cells().isEmpty() && !model.materials().isEmpty(),
                    "preview includes blocks and recognition materials");
                var farm = (FarmScreen) screen;
                screen.mouseClicked(farm.left + 330 * farm.scale, farm.top + 85 * farm.scale, 0);
                require((int) field(screen, "tab") == 1, "Pokemon tab opens");
                stage = 32;
                ticks = 0;
              }
              case 32 -> {
                shot(client, "farm-pokemon");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("zen_garden");
                require(
                    ((List<?>) field(screen, "groups")).size() == 1, "search selects one habitat");
                var farm = (FarmScreen) screen;
                screen.mouseClicked(farm.left + 245 * farm.scale, farm.top + 85 * farm.scale, 0);
                stage = 33;
                ticks = 0;
              }
              case 33 -> {
                if ((boolean) field(screen, "previewLoading")) return;
                require(
                    field(screen, "preview") != null,
                    "Zen Garden template is linked by its embedded habitat pool");
                shot(client, "farm-structure");
                var farm = (FarmScreen) screen;
                var oldModel = field(screen, "preview");
                screen.mouseClicked(farm.left + 437 * farm.scale, farm.top + 261 * farm.scale, 0);
                require((int) field(screen, "variant") == 1, "next structure variant selected");
                require(
                    field(screen, "preview") != oldModel,
                    "previous preview cleared on variant change");
                screen.mouseDragged(
                    farm.left + 350 * farm.scale,
                    farm.top + 160 * farm.scale,
                    0,
                    30 * farm.scale,
                    0);
                require((float) field(screen, "yaw") > 135, "preview rotates with drag");
                screen.mouseScrolled(
                    farm.left + 350 * farm.scale, farm.top + 160 * farm.scale, 0, 1);
                require((float) field(screen, "zoom") > 1.25F, "preview zoom works");
                stage = 34;
                ticks = 0;
              }
              case 34 -> {
                if ((boolean) field(screen, "previewLoading")) return;
                require(field(screen, "preview") != null, "next structure variant loaded");
                shot(client, "farm-structure-variant");
                var farm = (FarmScreen) screen;
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("no_such_habitat_test");
                require(
                    ((List<?>) field(screen, "groups")).isEmpty()
                        && field(screen, "preview") == null,
                    "no stale preview after empty search");
                search.setText("");
                screen.mouseClicked(farm.left + 395 * farm.scale, farm.top + 85 * farm.scale, 0);
                require(
                    client.currentScreen instanceof HabitatBlocksScreen,
                    "catalogue opens block conditions");
                client.currentScreen.close();
                require(client.currentScreen == screen, "block guide returns to same catalogue");
                var selected =
                    ((List<FarmCatalog.Entry>) field(screen, "filtered"))
                        .stream()
                            .filter(
                                e ->
                                    e.details().stream()
                                        .anyMatch(
                                            d ->
                                                d.contains("neededBaseBlocks")
                                                    || d.contains("neededNearbyBlocks")))
                            .findFirst();
                // Habitat pools may omit block conditions: use the same installed world-spawn
                // catalogue.
                if (selected.isEmpty())
                  selected =
                      ((FarmCatalog.Result) field(screen, "catalog"))
                          .entries().stream()
                              .filter(
                                  e ->
                                      e.details().stream()
                                          .anyMatch(
                                              d ->
                                                  d.contains("neededBaseBlocks")
                                                      || d.contains("neededNearbyBlocks")))
                              .findFirst();
                require(
                    selected.isPresent(),
                    "installed catalogue includes block requirements and presets");
                screen =
                    new HabitatBlocksScreen(
                        screen,
                        habitatPos,
                        List.of(selected.get()),
                        "Catalogue local · essai isolé");
                client.setScreen(screen);
                stage = 23;
                ticks = 0;
              }
              case 23 -> {
                require(
                    !((List<?>) field(screen, "rows")).isEmpty(), "block requirements rendered");
                boolean icon = false;
                for (var row : (List<?>) field(screen, "rows"))
                  if (!((net.minecraft.item.ItemStack) field(row, "icon")).isEmpty()) icon = true;
                require(icon, "required blocks have real item icons");
                shot(client, "farm-block-conditions");
                var blocks = (HabitatBlocksScreen) screen;
                screen.mouseClicked(
                    blocks.left + 30 * blocks.scale, blocks.top + 125 * blocks.scale, 0);
                require((boolean) field(screen, "expanded"), "client block tags can be expanded");
                for (int i = 0; i < 6; i++) screen.mouseScrolled(0, 0, 0, -1);
                stage = 24;
                ticks = 0;
              }
              case 24 -> {
                shot(client, "farm-block-tags");
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
                if (Boolean.getBoolean("tropimon.habitatAudit")) {
                  client.setScreen(null);
                  audit = new HabitatPhaseAudit(habitatPos);
                  stage = 5;
                  ticks = 0;
                } else done(client);
              }
              case 5 -> {
                if (audit.step(client)) done(client);
                ticks = 0;
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

  static void lookAt(MinecraftClient client, BlockPos pos) {
    // The developer can keep using the desktop while this isolated test runs.
    client.mouse.unlockCursor();
    var direction = net.minecraft.util.math.Vec3d.ofCenter(pos).subtract(client.player.getEyePos());
    client.player.setYaw((float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90));
    client.player.setPitch(
        (float)
            -Math.toDegrees(
                Math.atan2(
                    direction.y,
                    Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
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
