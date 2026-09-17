package fr.tropimon.farm;

import com.google.gson.*;
import java.util.*;

/** Readable catalogue declarations; never an evaluation of the surrounding world. */
final class TerrainHints {
  record Line(String label, String value, boolean block, boolean heading) {}

  static List<Line> from(List<FarmCatalog.Entry> entries) {
    List<Line> lines = new ArrayList<>();
    int index = 0;
    for (var entry : entries) {
      lines.add(new Line(entry.species(), "Entrée " + (++index), false, true));
      boolean condition = false;
      for (String detail :
          entry.details().stream()
              .sorted(
                  Comparator.comparingInt(
                      d ->
                          d.contains("\"neededBaseBlocks\"") || d.contains("\"neededNearbyBlocks\"")
                              ? 0
                              : 1))
              .toList()) {
        int separator = detail.indexOf(" : ");
        if (separator < 0) continue;
        String key = detail.substring(0, separator), value = detail.substring(separator + 3);
        if (key.equals("Placement"))
          lines.add(new Line("Emplacement", placement(value), false, false));
        else if (key.equals("condition")
            || key.equals("anticondition")
            || key.startsWith("Préréglage ")) {
          condition = true;
          try {
            var json = JsonParser.parseString(value).getAsJsonObject();
            if (key.startsWith("Préréglage ")) {
              lines.add(new Line(key, "déclaration locale", false, true));
              for (var field : json.entrySet()) {
                if (field.getKey().equals("condition") || field.getKey().equals("anticondition"))
                  condition(lines, field.getKey(), field.getValue().getAsJsonObject());
                else lines.add(new Line(field.getKey(), field.getValue().toString(), false, false));
              }
            } else condition(lines, key, json);
          } catch (RuntimeException ex) {
            lines.add(new Line(key, value, false, false));
          }
        }
      }
      if (!condition)
        lines.add(
            new Line(
                "Terrain",
                "Aucun bloc ni biome exigé explicitement dans cette entrée.",
                false,
                false));
    }
    return List.copyOf(lines);
  }

  private static void condition(List<Line> lines, String key, JsonObject fields) {
    lines.add(
        new Line(
            key.equals("anticondition") ? "Exclusion combinée" : "Condition combinée",
            "Lire les critères de ce groupe ensemble",
            false,
            true));
    for (var field :
        fields.entrySet().stream()
            .sorted(
                Comparator.comparingInt(
                    f ->
                        Set.of("neededBaseBlocks", "neededNearbyBlocks").contains(f.getKey())
                            ? 0
                            : 1))
            .toList()) {
      String label =
          switch (field.getKey()) {
            case "neededBaseBlocks" ->
                key.equals("anticondition") ? "Sol · exclusion" : "Sol admissible";
            case "neededNearbyBlocks" ->
                key.equals("anticondition") ? "Proximité · exclusion" : "Blocs à proximité";
            case "biomes" -> "Biomes";
            case "structures" -> "Structures";
            case "fluid" -> "Fluide";
            case "minY" -> "Altitude minimale";
            case "maxY" -> "Altitude maximale";
            case "minSkyLight" -> "Lumière du ciel minimale";
            case "maxSkyLight" -> "Lumière du ciel maximale";
            case "minLight" -> "Lumière minimale";
            case "maxLight" -> "Lumière maximale";
            case "canSeeSky" -> "Ciel visible";
            case "timeRange" -> "Heure";
            default -> field.getKey();
          };
      boolean block = Set.of("neededBaseBlocks", "neededNearbyBlocks").contains(field.getKey());
      JsonElement value = field.getValue();
      if (value.isJsonArray())
        for (var item : value.getAsJsonArray())
          lines.add(new Line(label + " · liste", plain(item), block, false));
      else lines.add(new Line(label, plain(value), block, false));
    }
  }

  private static String plain(JsonElement value) {
    return value.isJsonPrimitive() ? value.getAsString() : value.toString();
  }

  private static String placement(String value) {
    return switch (value) {
      case "grounded" -> "Au sol";
      case "submerged" -> "Sous l'eau";
      case "seafloor" -> "Fond de l'eau";
      case "fishing" -> "Pêche";
      case "surface" -> "Surface";
      default -> value;
    };
  }
}
