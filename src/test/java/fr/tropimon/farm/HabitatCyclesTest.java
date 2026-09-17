package fr.tropimon.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class HabitatCyclesTest {
  private FarmCatalog.Entry row(String resource, String species, String phases) {
    return new FarmCatalog.Entry(
        true, resource, resource, species, "common", "5-10", phases, 1, List.of(), false);
  }

  @Test
  void groupsResourcesAndMatchesAllSpeciesWithoutConfirmingPool() {
    var pools =
        HabitatCycles.pools(
            new FarmCatalog.Result(
                List.of(
                    row("a", "Pikachu", "1,3"), row("a", "Eevee", "2"), row("b", "Pikachu", "1")),
                0));
    assertEquals(
        "a",
        HabitatCycles.candidates(pools, List.of("cobblemon:eevee", "cobblemon:pikachu"))
            .getFirst()
            .resource());
    assertEquals(3, pools.getFirst().phases());
    assertTrue(HabitatCycles.candidates(pools, List.of()).isEmpty());
    assertTrue(HabitatCycles.candidates(pools, List.of("cobblemon:mew")).isEmpty());
  }

  @Test
  void invalidPhaseAndUnknownOrderFailClosed() {
    assertEquals(2, HabitatCycles.phaseCount(List.of(row("a", "Pikachu", "1,3"))));
    assertEquals(
        3, HabitatCycles.phaseCount(List.of(row("a", "Pikachu", "1-3"), row("a", "Eevee", "2-3"))));
    assertEquals(0, HabitatCycles.phaseCount(List.of(row("a", "Pikachu", "3-1"))));
    assertEquals(0, HabitatCycles.phaseCount(List.of(row("a", "Pikachu", "unknown"))));
    assertEquals(0, HabitatCycles.phase(-1, 0, 3, HabitatCycles.Order.SIMPLE));
    assertEquals(0, HabitatCycles.phase(24000, 0, 3, HabitatCycles.Order.UNKNOWN));
  }

  @Test
  void phaseBoundariesAndRandomCycles() {
    assertEquals(1, HabitatCycles.phase(23999, 42, 3, HabitatCycles.Order.SIMPLE));
    assertEquals(2, HabitatCycles.phase(24000, 42, 3, HabitatCycles.Order.SIMPLE));
    assertEquals(1, HabitatCycles.phase(72000, 42, 3, HabitatCycles.Order.SIMPLE));
    Set<Integer> cycle = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      int p = HabitatCycles.phase(i * 24000L, 42, 5, HabitatCycles.Order.FIXED_RANDOM);
      cycle.add(p);
      assertEquals(
          p, HabitatCycles.phase((i + 5) * 24000L, 42, 5, HabitatCycles.Order.FIXED_RANDOM));
      assertEquals(
          HabitatCycles.phase(i * 24000L, 42, 5, HabitatCycles.Order.FULL_RANDOM),
          HabitatCycles.phase(i * 24000L + 23999, 42, 5, HabitatCycles.Order.FULL_RANDOM));
    }
    assertEquals(5, cycle.size());
  }
}
