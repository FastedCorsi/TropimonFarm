package fr.tropimon.farm;

import java.util.*;
import kotlin.random.RandomKt;

/** Catalogue hypotheses and the vanilla phase formula. No server access. */
final class HabitatCycles {
  enum Order {
    UNKNOWN,
    SIMPLE,
    FIXED_RANDOM,
    FULL_RANDOM
  }

  record Pool(
      String resource,
      String name,
      List<FarmCatalog.Entry> entries,
      Set<String> species,
      int phases) {}

  static String speciesId(String value) {
    String id = value.strip().split(" ")[0].toLowerCase(Locale.ROOT);
    return id.contains(":") ? id : "cobblemon:" + id;
  }

  static int phaseCount(List<FarmCatalog.Entry> entries) {
    Set<Integer> phases = new HashSet<>();
    for (var e : entries) {
      if (e.phases().isBlank()) continue;
      for (String token : e.phases().split(",")) {
        if (!token.strip().matches("[1-9][0-9]*(?:-[1-9][0-9]*)?")) return 0;
        String[] range = token.strip().split("-");
        try {
          int a = Integer.parseInt(range[0]), b = Integer.parseInt(range[range.length - 1]);
          if (a > b || b > 256) return 0;
          for (int phase = a; phase <= b; phase++) phases.add(phase);
        } catch (NumberFormatException ex) {
          return 0;
        }
      }
    }
    // Cobblemon counts the union of phase indices, rather than the largest index.
    return Math.max(1, phases.size());
  }

  static List<Pool> pools(FarmCatalog.Result catalog) {
    Map<String, List<FarmCatalog.Entry>> grouped = new TreeMap<>();
    for (var e : catalog.entries())
      if (e.habitat()) grouped.computeIfAbsent(e.resource(), k -> new ArrayList<>()).add(e);
    List<Pool> result = new ArrayList<>();
    grouped.forEach(
        (key, rows) -> {
          Set<String> species = new HashSet<>();
          rows.forEach(e -> species.add(speciesId(e.species())));
          result.add(
              new Pool(
                  key,
                  rows.getFirst().place(),
                  List.copyOf(rows),
                  Set.copyOf(species),
                  phaseCount(rows)));
        });
    return List.copyOf(result);
  }

  static List<Pool> candidates(List<Pool> pools, List<String> observed) {
    if (observed.isEmpty()) return List.of();
    Set<String> ids = new HashSet<>();
    observed.forEach(s -> ids.add(speciesId(s)));
    // The monitor caps the display list at 32: equality cannot be required at that boundary.
    return pools.stream()
        .filter(p -> observed.size() >= 32 ? p.species().containsAll(ids) : p.species().equals(ids))
        .toList();
  }

  static int phase(long age, long packedPos, int count, Order order) {
    if (age < 0 || count < 1 || count > 256 || order == Order.UNKNOWN) return 0;
    if (count == 1) return 1;
    long day = age / 24000;
    int index = (int) (day % count);
    if (order == Order.SIMPLE) return index + 1;
    if (order == Order.FULL_RANDOM) return RandomKt.Random(day + packedPos).nextInt(count) + 1;
    List<Integer> phases = new ArrayList<>();
    for (int i = 1; i <= count; i++) phases.add(i);
    return kotlin.collections.CollectionsKt.shuffled(phases, RandomKt.Random(packedPos)).get(index);
  }
}
