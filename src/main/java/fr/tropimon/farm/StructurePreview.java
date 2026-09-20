package fr.tropimon.farm;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Local templates only: no server queries, world edits or inferred live structures. */
final class StructurePreview {
  record Template(Path path, String id) {}

  record Cell(BlockPos pos, BlockState state) {}

  record Model(List<Cell> cells, List<BlockState> materials, int x, int y, int z) {}

  record Index(Map<String, List<Template>> pools, int skipped) {}

  static NbtCompound read(Path path) throws IOException {
    if (Files.size(path) > 2_097_152) throw new IOException("Structure exceeds preview limit");
    try (var in = Files.newInputStream(path)) {
      return NbtIo.readCompressed(in, NbtSizeTracker.of(16_777_216));
    }
  }

  static String poolKey(String resource) {
    return resource.replace("/habitat_pools/", ":").replaceFirst("\\.json$", "");
  }

  static Index index() throws IOException {
    Map<String, Path> resources = new TreeMap<>();
    var mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
    mods.sort(
        Comparator.comparing(
            m -> m.getMetadata().getId().equals("cobblemon") ? "" : m.getMetadata().getId()));
    for (var mod : mods)
      for (var root : mod.getRootPaths()) {
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) continue;
        try (var namespaces = Files.list(data)) {
          for (var namespace : namespaces.filter(Files::isDirectory).toList()) {
            Path dir = namespace.resolve("structure");
            if (!Files.isDirectory(dir)) continue;
            try (var paths = Files.walk(dir)) {
              for (var path :
                  paths.filter(p -> p.toString().endsWith(".nbt")).limit(2000).toList()) {
                String id =
                    namespace.getFileName()
                        + ":"
                        + dir.relativize(path).toString().replace('\\', '/');
                resources.put(id, path);
              }
            }
          }
        }
      }
    Map<String, List<Template>> pools = new TreeMap<>();
    int skipped = 0;
    for (var entry : resources.entrySet()) {
      try {
        var nbt = read(entry.getValue());
        Set<String> ids = new HashSet<>();
        for (var element : nbt.getList("blocks", NbtElement.COMPOUND_TYPE)) {
          var block = ((NbtCompound) element).getCompound("nbt");
          if (block.getString("id").equals("cobblemon:habitat_block")
              && block.contains("PoolId", NbtElement.STRING_TYPE))
            ids.add(block.getString("PoolId"));
        }
        for (String id : ids)
          pools
              .computeIfAbsent(id, key -> new ArrayList<>())
              .add(new Template(entry.getValue(), entry.getKey()));
      } catch (IOException | RuntimeException ex) {
        skipped++;
      }
    }
    pools.replaceAll((key, value) -> List.copyOf(value));
    return new Index(Map.copyOf(pools), skipped);
  }

  static Model model(NbtCompound nbt) throws IOException {
    var size = nbt.getList("size", NbtElement.INT_TYPE);
    if (size.size() != 3) throw new IOException("Invalid structure dimensions");
    int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
    if (sx < 1 || sy < 1 || sz < 1 || sx > 128 || sy > 128 || sz > 128)
      throw new IOException("Structure too large for preview");
    var palette = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
    if (palette.isEmpty()) palette = nbt.getList("palettes", NbtElement.LIST_TYPE).getList(0);
    List<BlockState> states = new ArrayList<>();
    for (var element : palette)
      states.add(
          NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), (NbtCompound) element));
    var blocks = nbt.getList("blocks", NbtElement.COMPOUND_TYPE);
    if (blocks.size() > 65536) throw new IOException("Structure exceeds preview block limit");
    Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
    Map<BlockState, Integer> materials = new HashMap<>();
    for (var element : blocks) {
      var block = (NbtCompound) element;
      var pos = block.getList("pos", NbtElement.INT_TYPE);
      int stateIndex = block.getInt("state");
      if (pos.size() != 3 || stateIndex < 0 || stateIndex >= states.size()) continue;
      var state = states.get(stateIndex);
      var entity = block.getCompound("nbt");
      if (entity.getString("id").equals("cobblemon:habitat_block") || state.isOf(Blocks.JIGSAW)) {
        String appearance = entity.getString(state.isOf(Blocks.JIGSAW) ? "final_state" : "MimicId");
        var id = Identifier.tryParse(appearance.split("\\[", 2)[0]);
        state =
            id != null && Registries.BLOCK.containsId(id)
                ? Registries.BLOCK.get(id).getDefaultState()
                : Blocks.AIR.getDefaultState();
      }
      if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID) || state.isOf(Blocks.STRUCTURE_BLOCK))
        continue;
      BlockPos p = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
      if (p.getX() < 0
          || p.getY() < 0
          || p.getZ() < 0
          || p.getX() >= sx
          || p.getY() >= sy
          || p.getZ() >= sz) continue;
      cells.put(p, state);
      materials.merge(state.getBlock().getDefaultState(), 1, Integer::sum);
    }
    List<Cell> visible = new ArrayList<>();
    cells.forEach(
        (pos, state) -> {
          boolean hidden = state.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, pos);
          for (Direction direction : Direction.values()) {
            var neighbor = cells.get(pos.offset(direction));
            if (neighbor == null
                || !neighbor.isOpaqueFullCube(
                    net.minecraft.world.EmptyBlockView.INSTANCE, pos.offset(direction))) {
              hidden = false;
              break;
            }
          }
          if (!hidden) visible.add(new Cell(pos, state));
        });
    if (visible.size() > 16000) throw new IOException("Structure exceeds preview render limit");
    return new Model(
        List.copyOf(visible),
        materials.entrySet().stream()
            .sorted(Map.Entry.<BlockState, Integer>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList(),
        sx,
        sy,
        sz);
  }

  static void render(
      DrawContext c, Model model, int x, int y, int w, int h, float yaw, float zoom) {
    var client = MinecraftClient.getInstance();
    var matrices = c.getMatrices();
    c.draw();
    matrices.push();
    float fit =
        Math.min(
            w / (1.5F * (model.x() + model.z())), h / (model.y() + .65F * (model.x() + model.z())));
    matrices.translate(x + w / 2F, y + h / 2F, 120);
    matrices.scale(fit * zoom, -fit * zoom, fit * zoom);
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));
    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    matrices.translate(-model.x() / 2F, -model.y() / 2F, -model.z() / 2F);
    try {
      for (var cell : model.cells()) {
        matrices.push();
        matrices.translate(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
        client
            .getBlockRenderManager()
            .renderBlockAsEntity(
                cell.state(),
                matrices,
                c.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV);
        matrices.pop();
      }
      // DrawContext.draw disables depth testing, which would scramble overlapping blocks.
      c.getVertexConsumers().draw();
    } finally {
      matrices.pop();
    }
  }
}
