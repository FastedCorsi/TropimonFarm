package fr.tropimon.farm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FarmCatalogTest {
  @Test
  void discontinuousPhases() {
    assertTrue(FarmCatalog.phaseMatches("1,3-4", 3));
    assertFalse(FarmCatalog.phaseMatches("1,3-4", 2));
    assertTrue(FarmCatalog.phaseMatches("1,3-4", 0));
    assertFalse(FarmCatalog.phaseMatches("unknown", 3));
  }

  @Test
  void replaceResourceNotSpecies() {
    var files = new HashMap<String, JsonObject>();
    var base = JsonParser.parseString("{\"spawns\":[{\"pokemon\":\"pikachu\"}]}").getAsJsonObject();
    files.put("cobblemon/a", base);
    files.put("addon/b", base);
    var other = new JsonObject();
    FarmCatalog.overlay(files, "cobblemon/a", other);
    assertSame(base, files.get("addon/b"));
    assertSame(other, files.get("cobblemon/a"));
  }

  @Test
  void accents() {
    assertEquals("habitat boise", FarmCatalog.normalize("Habitat boisé"));
  }

  @Test
  void snapshotManifestComplete() throws Exception {
    try (var in = getClass().getResourceAsStream("/assets/tropimon_farm/snapshot/index.json")) {
      assertNotNull(in);
      var index =
          JsonParser.parseString(
                  new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      int count = 0;
      for (String kind : List.of("habitat_pools", "spawn_pool_world"))
        for (String action : List.of("replace", "add"))
          for (var file : index.getAsJsonObject(kind).getAsJsonArray(action)) {
            try (var data =
                getClass()
                    .getResourceAsStream(
                        "/assets/tropimon_farm/snapshot/" + kind + "/" + file.getAsString())) {
              assertNotNull(data);
              var obj =
                  JsonParser.parseString(
                          new String(data.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                      .getAsJsonObject();
              assertTrue(obj.has("spawns"));
              count++;
            }
          }
      assertEquals(75, count);
    }
  }
}
