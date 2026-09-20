package fr.tropimon.farm;

import com.cobblemon.mod.common.CobblemonBlocks;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.habitats.*;
import com.cobblemon.mod.common.api.habitats.spawningstyle.*;
import com.cobblemon.mod.common.block.habitat.*;
import com.cobblemon.mod.common.client.gui.habitat.HabitatEditGUI;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.level.LevelProperties;

/** Local diagnostic only. No entrypoint or class is included in the public mod. By FastedCorsi. */
final class HabitatPhaseAudit {
  final BlockPos pos;
  final List<String> evidence = new ArrayList<>();
  int stage, phaseCount, attempts, unloadedSamples, reloadSamples;
  NbtCompound saved;
  HabitatBlockEntity previousClient;
  HabitatEditGUI editor;

  HabitatPhaseAudit(BlockPos pos) {
    this.pos = pos;
  }

  void check(boolean ok, String description) {
    if (!ok) throw new AssertionError(description);
    note("PASS " + description);
  }

  void note(String value) {
    evidence.add(value);
    System.out.println("HABITAT_AUDIT " + value);
  }

  HabitatBlockEntity block(ServerWorld w) {
    return (HabitatBlockEntity) w.getBlockEntity(pos);
  }

  void tick(ServerWorld w, HabitatBlockEntity b) {
    HabitatBlockEntity.Companion.getTICKER().tick(w, pos, w.getBlockState(pos), b);
  }

  void age(ServerWorld w, long age) {
    ((LevelProperties) w.getLevelProperties()).setTime(age);
  }

  void sync(ServerWorld w) {
    w.updateListeners(pos, w.getBlockState(pos), w.getBlockState(pos), 3);
  }

  boolean step(MinecraftClient c) throws Exception {
    if (stage == 10) {
      if (c.world != null) return false;
      check(
          c.world == null && HabitatMonitor.INSTANCE.entries(c).isEmpty(),
          "disconnect clears observations");
      c.createIntegratedServerLoader()
          .createAndStart(
              "habitat-session-audit-" + System.currentTimeMillis(),
              new net.minecraft.world.level.LevelInfo(
                  "Habitat second test session",
                  GameMode.CREATIVE,
                  false,
                  net.minecraft.world.Difficulty.PEACEFUL,
                  true,
                  new net.minecraft.world.GameRules(),
                  net.minecraft.resource.DataConfiguration.SAFE_MODE),
              new net.minecraft.world.gen.GeneratorOptions(2L, false, false),
              r ->
                  r.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET)
                      .get(net.minecraft.world.gen.WorldPresets.FLAT)
                      .createDimensionsRegistryHolder(),
              c.currentScreen);
      stage++;
      return false;
    }
    if (stage == 11 && c.player == null) return false;
    check(
        c.getServer() != null && c.getCurrentServerEntry() == null,
        "integrated isolated server only stage=" + stage);
    var w = c.getServer().getOverworld();
    switch (stage) {
      case 0 -> {
        c.getServer().submit(() -> serverChecks(w)).get();
        stage++;
      }
      case 1 -> {
        var b = (HabitatBlockEntity) c.world.getBlockEntity(pos);
        if (b == null) return false;
        previousClient = b;
        note(
            "CLIENT phase="
                + b.getCurrentPhase()
                + " pool="
                + b.getPool().getId()
                + " style="
                + b.getSpawningStyle().getType()
                + " state="
                + c.world.getBlockState(pos));
        check(
            b.getCurrentPhase() == 1,
            "ordinary client phase remains default 1 while server phase is 3");
        check(b.getMimicId().equals(Identifier.ofVanilla("moss_block")), "mimic synchronized");
        check(!b.getDisplaySpeciesIds().isEmpty(), "display species synchronized");
        check(
            c.world.getBlockState(pos).get(HabitatBlock.Companion.getACTIVATED_STYLE()),
            "activated style blockstate synchronized despite default client style");
        check(
            c.world.getBlockState(pos).get(HabitatBlock.Companion.getCANCELS_REGULAR_SPAWNS()),
            "cancels regular spawns flag synchronized");
        c.getServer()
            .submit(
                () -> {
                  var p = c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                  p.changeGameMode(GameMode.SURVIVAL);
                  check(
                      block(w)
                              .onUse(
                                  p,
                                  new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false))
                          == ActionResult.PASS,
                      "survival interaction refuses editor");
                })
            .get();
        stage++;
      }
      case 2 -> {
        check(!(c.currentScreen instanceof HabitatEditGUI), "no editor received in survival");
        c.getServer()
            .submit(
                () -> {
                  var p = c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                  p.changeGameMode(GameMode.CREATIVE);
                  block(w)
                      .onUse(p, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
                })
            .get();
        stage++;
      }
      case 3 -> {
        if (!(c.currentScreen instanceof HabitatEditGUI e)) return false;
        editor = e;
        check(
            e.getCurrentPhase() == 3,
            "official creative editor packet conveys authoritative phase 3");
        check(
            e.getHabitatSettingsDTO().isActivatedSpawning(),
            "official editor conveys activated settings");
        check(!e.getPools().isEmpty(), "official editor conveys server pools");
        note(
            "EDITOR pools="
                + e.getPools().size()
                + " spawned="
                + e.getCurrentlySpawned()
                + " trigger="
                + e.getHabitatSettingsDTO().getActivatedSettings().getTrigger());
        c.setScreen(null);
        c.getServer()
            .submit(
                () -> {
                  age(w, 24000);
                  tick(w, block(w));
                  sync(w);
                })
            .get();
        stage++;
      }
      case 4 -> {
        check(
            c.getServer().submit(() -> block(w).getCurrentPhase()).get() == 2,
            "server changes phase to 2");
        check(editor.getCurrentPhase() == 3, "editor snapshot remains stale after phase change");
        check(
            ((HabitatBlockEntity) c.world.getBlockEntity(pos)).getCurrentPhase() == 1,
            "forced ordinary update still omits phase");
        c.getServer()
            .submit(
                () -> {
                  var p = c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                  p.teleport(
                      w, pos.getX() + 1024, pos.getY() + 10, pos.getZ() + 1024, Set.of(), 0, 0);
                })
            .get();
        stage++;
      }
      case 5 -> {
        HabitatMonitor.INSTANCE.scan(c);
        var entry =
            HabitatMonitor.INSTANCE.entries(c).stream()
                .filter(e -> e.pos().equals(pos))
                .findFirst();
        if (entry.isEmpty() || entry.get().loaded()) return false;
        // Let ordinary unload packets arrive before returning to the original chunk.
        if (++unloadedSamples < 4) return false;
        check(
            !entry.get().present(), "real client chunk unload marks pin unloaded and not present");
        c.getServer()
            .submit(
                () -> {
                  var p = c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                  p.teleport(w, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2, Set.of(), 0, 0);
                })
            .get();
        stage++;
      }
      case 6 -> {
        var b = c.world.getBlockEntity(pos);
        if (!(b instanceof HabitatBlockEntity h)) {
          if (++reloadSamples == 1 || reloadSamples == 10)
            note(
                "RELOAD_WAIT player="
                    + c.player.getBlockPos()
                    + " target="
                    + pos
                    + " clientState="
                    + c.world.getBlockState(pos)
                    + " serverState="
                    + c.getServer().submit(() -> w.getBlockState(pos).toString()).get());
          check(reloadSamples < 20, "bounded wait for returning habitat chunk");
          return false;
        }
        note("RELOAD client object reused=" + (b == previousClient));
        check(h.getCurrentPhase() == 1, "initial chunk reload still lacks authoritative phase");
        HabitatMonitor.INSTANCE.scan(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream()
                .anyMatch(e -> e.pos().equals(pos) && e.present()),
            "reloaded habitat present");
        c.getServer()
            .submit(
                () -> {
                  saved = block(w).createNbt(w.getRegistryManager());
                  w.removeBlock(pos, false);
                })
            .get();
        stage++;
      }
      case 7 -> {
        HabitatMonitor.INSTANCE.scan(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream()
                .anyMatch(e -> e.pos().equals(pos) && e.loaded() && !e.present()),
            "removed habitat marked absent");
        c.getServer()
            .submit(
                () -> {
                  w.setBlockState(pos, CobblemonBlocks.HABITAT_BLOCK.getDefaultState(), 3);
                  block(w).read(saved, w.getRegistryManager());
                  check(
                      block(w).getCurrentPhase() == 1,
                      "save NBT does not persist current phase; recreated entity starts at 1");
                  age(w, 24000);
                  tick(w, block(w));
                  sync(w);
                  check(
                      block(w).getCurrentPhase() == 2,
                      "save NBT recreation recalculates phase from world age");
                })
            .get();
        stage++;
      }
      case 8 -> {
        if (!(c.world.getBlockEntity(pos) instanceof HabitatBlockEntity)) return false;
        HabitatOverlay.tick(c);
        check(HabitatOverlay.visible(c), "in-game overlay active before region switch");
        HabitatMonitor.regionChanged("audit-other-region");
        check(
            !HabitatOverlay.visible(c),
            "region switch hides stale overlay before next client tick");
        HabitatMonitor.INSTANCE.tick(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream().noneMatch(e -> e.pinned()),
            "new region does not inherit pin");
        HabitatMonitor.regionChanged("");
        HabitatMonitor.INSTANCE.tick(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream().anyMatch(e -> e.pinned()),
            "original region restores its pin");
        HabitatMonitor.INSTANCE.reset();
        check(HabitatMonitor.INSTANCE.entries(c).isEmpty(), "session reset clears observations");
        HabitatMonitor.INSTANCE.tick(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream().anyMatch(e -> e.pinned()),
            "same world restores persisted pin after reset");
        c.getServer()
            .submit(
                () -> {
                  var p = c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                  p.teleport(
                      c.getServer().getWorld(net.minecraft.world.World.NETHER),
                      0,
                      100,
                      0,
                      Set.of(),
                      0,
                      0);
                })
            .get();
        stage++;
      }
      case 9 -> {
        if (!c.world.getRegistryKey().equals(net.minecraft.world.World.NETHER)) return false;
        HabitatMonitor.INSTANCE.tick(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream().noneMatch(e -> e.pinned()),
            "dimension change does not inherit overworld pin");
        stage++;
        // Disconnect outside END_CLIENT_TICK: shutdown can pump another rendered frame.
        // Clear synthetic advancement toasts before that reentrant loading screen.
        c.send(() -> {
          c.getToastManager().clear();
          c.world.disconnect();
          c.disconnect(new net.minecraft.client.gui.screen.TitleScreen());
        });
      }
      case 11 -> {
        HabitatMonitor.INSTANCE.tick(c);
        check(
            HabitatMonitor.INSTANCE.entries(c).stream().noneMatch(e -> e.pinned()),
            "new isolated world does not inherit previous session pin");
        note("COMPLETE");
        var dir = c.runDirectory.toPath().resolve("verification");
        Files.createDirectories(dir);
        Files.write(dir.resolve("habitat-audit.txt"), evidence);
        return true;
      }
    }
    return false;
  }

  void serverChecks(ServerWorld w) {
    w.setBlockState(pos, CobblemonBlocks.HABITAT_BLOCK.getDefaultState(), 3);
    var b = block(w);
    var pool =
        HabitatPools.INSTANCE.getHabitatPoolsById().values().stream()
            .filter(p -> p.getPhaseCount() >= 3)
            .sorted(Comparator.comparing(p -> p.getId().toString()))
            .findFirst()
            .orElseThrow();
    b.setPool(pool);
    b.setPhaseOrder(HabitatPhaseOrder.SIMPLE);
    b.setMimicId(Identifier.ofVanilla("moss_block"));
    phaseCount = pool.getPhaseCount();
    note(
        "SERVER pool="
            + pool.getId()
            + " phaseCount="
            + phaseCount
            + " spawnRows="
            + pool.getSpawns().size());
    for (var order : HabitatPhaseOrder.values()) {
      b.setPhaseOrder(order);
      List<Integer> sequence = new ArrayList<>();
      for (int day = 0; day < phaseCount * 2; day++) {
        int p = b.calculatePhase(day * 24000L);
        sequence.add(p);
        check(
            p
                == HabitatCycles.phase(
                    day * 24000L,
                    pos.asLong(),
                    phaseCount,
                    HabitatCycles.Order.valueOf(order.name())),
            "client phase formula matches Cobblemon " + order + " day=" + day);
        check(
            p == b.calculatePhase(day * 24000L + 23999) && p >= 1 && p <= phaseCount,
            order + " stable within day=" + day);
      }
      if (order != HabitatPhaseOrder.FULL_RANDOM)
        check(
            new HashSet<>(sequence.subList(0, phaseCount)).size() == phaseCount
                && sequence
                    .subList(0, phaseCount)
                    .equals(sequence.subList(phaseCount, phaseCount * 2)),
            order + " complete repeating cycle");
      note("PHASE_SEQUENCE " + order + " " + sequence);
    }
    b.setPhaseOrder(HabitatPhaseOrder.SIMPLE);
    b.setSpawningStyle(new NaturalHabitatSpawning(b));
    tick(w, b);
    age(w, 23960);
    tick(w, b);
    check(b.getCurrentPhase() == 1, "phase before boundary");
    w.setTimeOfDay(48000);
    tick(w, b);
    check(b.getCurrentPhase() == 1, "daylight time change does not change phase");
    age(w, 24000);
    tick(w, b);
    check(b.getCurrentPhase() == 2, "world age 24000 advances phase");
    age(w, 48001);
    tick(w, b);
    check(b.getCurrentPhase() == 2, "phase refresh waits for age multiple of 40 ticks");
    age(w, 48040);
    tick(w, b);
    check(b.getCurrentPhase() == 3, "phase refresh at 40 tick boundary");
    var a = new ActivatedHabitatSpawning(b);
    b.setSpawningStyle(a);
    a.setTrigger(ActivatedHabitatSpawning.Trigger.REDSTONE);
    a.setChance(1);
    a.setMaxSpawns(2);
    a.setMaxSpawnsPerActivation(1);
    a.setCancelledNaturalSpawningRange(12);
    b.refreshFromSettings();
    b.setInitialized(true);
    conditionChecks(w, b, a);
    var sub =
        CobblemonEvents.HABITAT_SPAWN_ACTIVATED.subscribe(
            (java.util.function.Consumer<
                    com.cobblemon.mod.common.api.events.habitats.HabitatSpawnActivatedEvent>)
                event -> {
                  if (event.getHabitatBlockEntity() == b) {
                    attempts++;
                    event.cancel();
                  }
                });
    try {
      tick(w, b);
      check(attempts == 0, "redstone no power no activation");
      w.setBlockState(
          pos.up(),
          Blocks.LEVER
              .getDefaultState()
              .with(
                  net.minecraft.state.property.Properties.BLOCK_FACE,
                  net.minecraft.block.enums.BlockFace.FLOOR)
              .with(net.minecraft.state.property.Properties.POWERED, true),
          3);
      note(
          "REDSTONE strong="
              + w.getReceivedStrongRedstonePower(pos)
              + " spawnDetails="
              + b.getSpawnDetails().size());
      tick(w, b);
      check(attempts == 1 && b.getReceivingSignal(), "redstone rising edge activates");
      for (int i = 0; i < 5; i++) tick(w, b);
      check(attempts == 1, "held redstone does not retrigger");
      w.removeBlock(pos.up(), false);
      tick(w, b);
      check(!b.getReceivingSignal(), "redstone falling edge resets latch");
      w.setBlockState(
          pos.up(),
          Blocks.LEVER
              .getDefaultState()
              .with(
                  net.minecraft.state.property.Properties.BLOCK_FACE,
                  net.minecraft.block.enums.BlockFace.FLOOR)
              .with(net.minecraft.state.property.Properties.POWERED, true),
          3);
      tick(w, b);
      check(attempts == 2, "second rising edge reactivates");
      w.removeBlock(pos.up(), false);
      tick(w, b);
      a.setTrigger(ActivatedHabitatSpawning.Trigger.TICK);
      for (int i = 0; i < 5; i++) tick(w, b);
      check(attempts == 7, "TICK attempts every tick without fixed cooldown");
      a.setChance(0);
      tick(w, b);
      check(attempts == 7, "chance zero blocks activation");
      a.setChance(1);
      a.setMaxSpawns(0);
      tick(w, b);
      check(attempts == 7, "capacity zero blocks activation");
      a.setMaxSpawns(2);
      a.setTrigger(ActivatedHabitatSpawning.Trigger.RANDOM_TICK);
      for (int i = 0; i < 5; i++) tick(w, b);
      check(attempts == 7, "RANDOM_TICK not driven by block entity ticker");
    } finally {
      CobblemonEvents.HABITAT_SPAWN_ACTIVATED.unsubscribe(sub);
    }
    a.setTrigger(ActivatedHabitatSpawning.Trigger.REDSTONE);
    a.setChance(0);
    b.refreshFromSettings();
    var initial = b.toInitialChunkDataNbt(w.getRegistryManager());
    var update = ((BlockEntityUpdateS2CPacket) b.toUpdatePacket()).getNbt();
    var full = b.createNbt(w.getRegistryManager());
    note("NBT_INITIAL " + initial);
    note("NBT_UPDATE " + update);
    note("NBT_SAVE_KEYS " + new TreeSet<>(full.getKeys()));
    check(initial.equals(update), "initial chunk and ordinary update carry same NBT");
    check(
        initial.getKeys().equals(Set.of("MimicId", "DisplaySpecies")),
        "ordinary NBT restricted to mimic and display species");
    note(
        "DISPLAY count="
            + b.getDisplaySpeciesIds().size()
            + " spawnDetails="
            + b.getSpawnDetails().size());
    b.markDirty();
    sync(w);
  }

  void conditionChecks(ServerWorld w, HabitatBlockEntity b, ActivatedHabitatSpawning a) {
    var cause = new com.cobblemon.mod.common.api.spawning.SpawnCause(a.getSpawner(), null);
    var condition =
        new com.cobblemon.mod.common.api.spawning.condition.SpawningCondition<
            com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition>() {
          @Override
          public Class<
                  ? extends com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition>
              spawnablePositionClass() {
            return com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition.class;
          }
        };
    var phases =
        new com.cobblemon.mod.common.api.spawning.IntRanges(
            new kotlin.ranges.IntRange(1, 1), new kotlin.ranges.IntRange(3, 4));
    condition.getAppendages().add(new HabitatSpawn.PhaseAppendageCondition(b, phases));
    condition.setTimeRange(
        new com.cobblemon.mod.common.api.spawning.TimeRange(new kotlin.ranges.IntRange(0, 12000)));
    condition.setMinLight(5);
    condition.setMaxLight(10);
    java.util.function.IntFunction<
            com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition>
        position =
            light ->
                new com.cobblemon.mod.common.api.spawning.position.BasicSpawnablePosition(
                    cause, w, pos, light, 15, true, new ArrayList<>());
    w.setTimeOfDay(1000);
    b.setCurrentPhase(3);
    check(
        condition.isSatisfiedBy(position.apply(5)) && condition.isSatisfiedBy(position.apply(10)),
        "engine condition accepts inclusive light boundaries 5 and 10");
    check(
        !condition.isSatisfiedBy(position.apply(4)) && !condition.isSatisfiedBy(position.apply(11)),
        "engine condition rejects light 4 and 11");
    b.setCurrentPhase(2);
    check(!condition.isSatisfiedBy(position.apply(7)), "discontinuous phases 1,3-4 reject phase 2");
    b.setCurrentPhase(4);
    check(condition.isSatisfiedBy(position.apply(7)), "discontinuous phases accept phase 4");
    w.setTimeOfDay(13000);
    check(
        !condition.isSatisfiedBy(position.apply(7)),
        "spawn time condition rejects night independently of habitat phase");
    w.setTimeOfDay(12000);
    check(
        condition.isSatisfiedBy(position.apply(7)),
        "spawn time condition includes configured endpoint");
    w.setTimeOfDay(1000);
    b.setCurrentPhase(3);
    check(
        b.getDisplaySpeciesIds()
            .equals(
                b.getPool().getSpawns().stream()
                    .map(s -> s.getSpecies().getResourceIdentifier())
                    .distinct()
                    .toList()),
        "vanilla display list is distinct pool species across all phases");
    note(
        "CONDITIONS controlled position inputs; no claim of actual Pokemon spawn or measured spawn"
            + " rate");
  }
}
