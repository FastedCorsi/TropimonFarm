package fr.tropimon.farm;

import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.LoggerFactory;

/** Bounded passive monitoring of block entities already sent to this client. */
public final class HabitatMonitor {
  static final HabitatMonitor INSTANCE = new HabitatMonitor();
  static final int RANGE = 96, MAX_PINS = 64;

  record Observation(
      BlockPos pos,
      String mimic,
      List<String> species,
      boolean loaded,
      boolean present,
      long observed,
      boolean pinned,
      boolean activated,
      boolean cancelsSpawns) {}

  private final Map<BlockPos, Observation> observations = new HashMap<>();
  private final Set<BlockPos> pins = new HashSet<>();
  private ClientWorld world;
  private Path pinFile;
  private int ticks;
  private long revision;
  private long scopeRevision;

  long scopeRevision() {
    return scopeRevision;
  }

  private String region = "";

  List<Observation> entries(MinecraftClient client) {
    if (client.player == null) return List.of();
    return observations.values().stream()
        .sorted(Comparator.comparingDouble(o -> o.pos().getSquaredDistance(client.player.getPos())))
        .toList();
  }

  long revision() {
    return revision;
  }

  private void clearWorld() {
    scopeRevision++;
    HabitatClock.reset();
    world = null;
    pinFile = null;
    pins.clear();
    observations.clear();
    ticks = 0;
    revision++;
  }

  void reset() {
    region = "";
    clearWorld();
  }

  public static void regionChanged(String name) {
    if (!INSTANCE.region.equals(name)) {
      INSTANCE.clearWorld();
      INSTANCE.region = name;
    }
  }

  void tick(MinecraftClient client) {
    if (client.world == null || client.player == null) {
      if (world != null) reset();
      return;
    }
    if (world != client.world) {
      clearWorld();
      world = client.world;
      loadPins(client);
      scan(client);
    }
    if (++ticks >= 40) {
      ticks = 0;
      scan(client);
    }
  }

  void scan(MinecraftClient client) {
    if (client.world == null || client.player == null) return;
    BlockPos center = client.player.getBlockPos();
    Map<BlockPos, Observation> next = new HashMap<>();
    long now = System.currentTimeMillis();
    // No chunk creation, block-volume scan, network request or server-side mutation.
    int radius = RANGE / 16;
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++) {
        var chunk =
            client
                .world
                .getChunkManager()
                .getChunk(
                    (center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, ChunkStatus.FULL, false);
        if (chunk == null) continue;
        for (var entity : chunk.getBlockEntities().values())
          if (entity instanceof HabitatBlockEntity habitat) {
            if (habitat.getPos().getSquaredDistance(center) > RANGE * RANGE || next.size() >= 512)
              continue;
            observe(next, habitat, now);
          }
      }
    for (BlockPos pos : pins) {
      if (next.containsKey(pos)) continue;
      var chunk =
          client
              .world
              .getChunkManager()
              .getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
      if (chunk != null) {
        var entity = chunk.getBlockEntity(pos);
        if (entity instanceof HabitatBlockEntity habitat) {
          observe(next, habitat, now);
          continue;
        }
      }
      Observation old = observations.get(pos);
      next.put(
          pos,
          new Observation(
              pos,
              old == null ? "" : old.mimic(),
              old == null ? List.of() : old.species(),
              chunk != null,
              false,
              old == null ? 0 : old.observed(),
              true,
              false,
              false));
    }
    observations.clear();
    observations.putAll(next);
    revision++;
  }

  private void observe(Map<BlockPos, Observation> dest, HabitatBlockEntity block, long now) {
    dest.put(block.getPos().toImmutable(), observation(block, now));
  }

  Observation targeted(MinecraftClient client) {
    if (client.world == null
        || !(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)
        || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return null;
    var entity = client.world.getBlockEntity(hit.getBlockPos());
    return entity instanceof HabitatBlockEntity habitat
        ? observation(habitat, System.currentTimeMillis())
        : null;
  }

  private Observation observation(HabitatBlockEntity block, long now) {
    // The normal sync omits pool, phase and spawning settings: getters for those contain defaults.
    List<String> species =
        block.getDisplaySpeciesIds().stream().map(Object::toString).limit(32).toList();
    BlockPos pos = block.getPos().toImmutable();
    return new Observation(
        pos,
        block.getMimicId().toString(),
        species,
        true,
        true,
        now,
        pins.contains(pos),
        block
            .getCachedState()
            .get(
                com.cobblemon.mod.common.block.habitat.HabitatBlock.Companion.getACTIVATED_STYLE()),
        block
            .getCachedState()
            .get(
                com.cobblemon.mod.common.block.habitat.HabitatBlock.Companion
                    .getCANCELS_REGULAR_SPAWNS()));
  }

  boolean toggle(BlockPos pos) {
    if (!pins.remove(pos)) {
      if (pins.size() >= MAX_PINS) return false;
      pins.add(pos.toImmutable());
    }
    savePins();
    scan(MinecraftClient.getInstance());
    return true;
  }

  private void loadPins(MinecraftClient client) {
    try {
      String server =
          client.getCurrentServerEntry() != null
              ? client.getCurrentServerEntry().address
              : client.getServer() != null
                  ? "local:"
                      + client
                          .getServer()
                          .getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                          .toAbsolutePath()
                          .normalize()
                  : "offline";
      String key =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (server + "\n" + region + "\n" + world.getRegistryKey().getValue())
                              .getBytes(StandardCharsets.UTF_8)));
      pinFile =
          FabricLoader.getInstance().getConfigDir().resolve("tropimon_farm").resolve(key + ".json");
      if (!Files.isRegularFile(pinFile) || Files.size(pinFile) > 16384) return;
      var array = JsonParser.parseString(Files.readString(pinFile)).getAsJsonArray();
      for (var element : array) {
        if (pins.size() >= MAX_PINS) break;
        var a = element.getAsJsonArray();
        if (a.size() != 3) continue;
        int x = a.get(0).getAsInt(), y = a.get(1).getAsInt(), z = a.get(2).getAsInt();
        if (Math.abs((long) x) <= 30_000_000
            && Math.abs((long) z) <= 30_000_000
            && y >= -2048
            && y <= 2048) pins.add(new BlockPos(x, y, z));
      }
    } catch (Exception ex) {
      pins.clear();
      LoggerFactory.getLogger("tropimon_farm")
          .warn("Habitat bookmarks unavailable ({})", ex.getClass().getSimpleName());
    }
  }

  private void savePins() {
    if (pinFile == null) return;
    try {
      JsonArray array = new JsonArray();
      for (var pos : pins) {
        JsonArray a = new JsonArray();
        a.add(pos.getX());
        a.add(pos.getY());
        a.add(pos.getZ());
        array.add(a);
      }
      Files.createDirectories(pinFile.getParent());
      Path staged = pinFile.resolveSibling(pinFile.getFileName() + ".tmp");
      Files.writeString(staged, array.toString(), StandardCharsets.UTF_8);
      Files.move(staged, pinFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ex) {
      LoggerFactory.getLogger("tropimon_farm")
          .warn("Habitat bookmarks not saved ({})", ex.getClass().getSimpleName());
    }
  }
}
