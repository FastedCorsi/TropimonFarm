package fr.tropimon.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class TerrainHintsTest {
  private FarmCatalog.Entry entry(String... details) {
    return new FarmCatalog.Entry(
        false, "test", "test", "pikachu", "common", "5", "", 1, List.of(details), false);
  }

  @Test
  void preservesExclusionGroupAndLocalPresetProvenance() {
    var lines =
        TerrainHints.from(
            List.of(
                entry(
                    "condition : {\"neededNearbyBlocks\":[\"#minecraft:iron_ores\"]}",
                    "Préréglage wild :"
                        + " {\"anticondition\":{\"neededNearbyBlocks\":[\"minecraft:stone\"],\"structures\":[\"#minecraft:village\"]}}")));
    assertTrue(lines.stream().anyMatch(l -> l.block() && l.value().equals("#minecraft:iron_ores")));
    assertTrue(lines.stream().anyMatch(l -> l.heading() && l.label().equals("Préréglage wild")));
    assertEquals(1, lines.stream().filter(l -> l.label().equals("Exclusion combinée")).count());
    assertTrue(
        lines.stream()
            .anyMatch(
                l -> l.label().startsWith("Structures") && l.value().equals("#minecraft:village")));
  }

  @Test
  void unknownDataStaysVisibleAndNoRequirementsAreInvented() {
    var lines =
        TerrainHints.from(
            List.of(entry("Préréglage missing : indisponible", "condition : malformed")));
    assertTrue(lines.stream().anyMatch(l -> l.value().equals("indisponible")));
    assertTrue(lines.stream().anyMatch(l -> l.value().equals("malformed")));
    assertTrue(
        TerrainHints.from(List.of(entry())).stream()
            .anyMatch(l -> l.value().startsWith("Aucun bloc")));
    assertFalse(lines.stream().anyMatch(TerrainHints.Line::block));
  }
}
