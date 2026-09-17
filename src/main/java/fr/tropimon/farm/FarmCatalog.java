package fr.tropimon.farm;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** A resource catalogue, explicitly not a live spawn scanner or probability calculator. */
final class FarmCatalog {
  record Entry(
      boolean habitat,
      String resource,
      String place,
      String species,
      String bucket,
      String level,
      String phases,
      double weight,
      List<String> details,
      boolean snapshot) {}

  record Result(List<Entry> entries, int skipped) {}

  static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  static boolean phaseMatches(String expression, int phase) {
    if (phase == 0 || expression.isBlank()) return true;
    for (String token : expression.split(",")) {
      String[] range = token.strip().split("-");
      try {
        int a = Integer.parseInt(range[0]);
        int b = range.length == 2 ? Integer.parseInt(range[1]) : a;
        if (phase >= a && phase <= b) return true;
      } catch (NumberFormatException ignored) {
      }
    }
    return false;
  }

  static void overlay(Map<String, JsonObject> files, String key, JsonObject replacement) {
    files.put(key, replacement);
  }

  static Result load(boolean snapshot) throws IOException {
    Map<String, JsonObject> files = new TreeMap<>();
    Set<String> custom = new HashSet<>();
    int skipped = 0;
    var mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
    mods.sort(
        Comparator.comparing(
            m -> m.getMetadata().getId().equals("cobblemon") ? "" : m.getMetadata().getId()));
    for (var mod : mods)
      for (Path root : mod.getRootPaths()) {
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) continue;
        try (var namespaces = Files.list(data)) {
          for (Path namespace : namespaces.filter(Files::isDirectory).toList())
            for (String kind : List.of("habitat_pools", "spawn_pool_world")) {
              Path dir = namespace.resolve(kind);
              if (!Files.isDirectory(dir)) continue;
              try (var paths = Files.walk(dir)) {
                for (Path path :
                    paths.filter(p -> p.toString().endsWith(".json")).limit(12000).toList()) {
                  String key =
                      namespace.getFileName()
                          + "/"
                          + kind
                          + "/"
                          + dir.relativize(path).toString().replace('\\', '/');
                  try {
                    if (Files.size(path) > 1_048_576) {
                      skipped++;
                      continue;
                    }
                    files.put(
                        key, JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                  } catch (RuntimeException ex) {
                    skipped++;
                  }
                }
              }
            }
        }
      }
    if (snapshot) {
      JsonObject manifest = resource("index.json");
      for (String kind : List.of("habitat_pools", "spawn_pool_world"))
        for (String action : List.of("replace", "add"))
          for (var name : manifest.getAsJsonObject(kind).getAsJsonArray(action)) {
            String file = name.getAsString();
            String key = "cobblemon/" + kind + "/" + file;
            overlay(files, key, resource(kind + "/" + file));
            custom.add(key);
          }
    }
    List<Entry> entries = new ArrayList<>();
    for (var source : files.entrySet()) {
      JsonObject file = source.getValue();
      if (file.has("enabled") && !file.get("enabled").getAsBoolean()) continue;
      if (!dependenciesMatch(file)) continue;
      boolean habitat = source.getKey().contains("/habitat_pools/");
      var spawns = file.getAsJsonArray("spawns");
      if (spawns == null) continue;
      for (var element : spawns) {
        try {
          JsonObject spawn = element.getAsJsonObject();
          String species = value(spawn, habitat ? "species" : "pokemon", "");
          if (species.isBlank()) continue;
          String place =
              habitat ? value(file, "name", source.getKey()) : value(spawn, "id", source.getKey());
          List<String> details = new ArrayList<>();
          details.add("Ressource : " + source.getKey());
          details.add("Placement : " + value(spawn, "spawnablePositionType", "non précisé"));
          for (var field : spawn.entrySet()) {
            if (Set.of(
                    "species",
                    "pokemon",
                    "weight",
                    "bucket",
                    "level",
                    "levelRange",
                    "phases",
                    "spawnablePositionType",
                    "id")
                .contains(field.getKey())) continue;
            details.add(field.getKey() + " : " + field.getValue().toString());
          }
          for (var field : file.entrySet())
            if (!Set.of("spawns", "name", "enabled", "neededInstalledMods", "neededUninstalledMods")
                .contains(field.getKey()))
              details.add("Habitat / " + field.getKey() + " : " + field.getValue());
          entries.add(
              new Entry(
                  habitat,
                  source.getKey(),
                  place,
                  species,
                  value(spawn, "bucket", "non précisé"),
                  value(spawn, habitat ? "levelRange" : "level", "non précisé"),
                  value(spawn, "phases", ""),
                  spawn.has("weight") ? spawn.get("weight").getAsDouble() : Double.NaN,
                  List.copyOf(details),
                  custom.contains(source.getKey())));
        } catch (RuntimeException ex) {
          skipped++;
        }
      }
    }
    entries.sort(Comparator.comparing(Entry::species).thenComparing(Entry::place));
    return new Result(List.copyOf(entries), skipped);
  }

  private static boolean dependenciesMatch(JsonObject file) {
    for (String key : List.of("neededInstalledMods", "neededUninstalledMods")) {
      if (!file.has(key)) continue;
      for (var id : file.getAsJsonArray(key)) {
        boolean loaded = FabricLoader.getInstance().isModLoaded(id.getAsString());
        if (key.equals("neededInstalledMods") != loaded) return false;
      }
    }
    return true;
  }

  private static String value(JsonObject json, String key, String fallback) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
  }

  private static JsonObject resource(String name) throws IOException {
    if (name.contains("..") || name.startsWith("/"))
      throw new IOException("Invalid snapshot resource");
    try (var input =
        FarmCatalog.class.getResourceAsStream("/assets/tropimon_farm/snapshot/" + name)) {
      if (input == null) throw new IOException("Missing snapshot resource");
      return JsonParser.parseString(new String(input.readNBytes(1_048_576), StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }
}
