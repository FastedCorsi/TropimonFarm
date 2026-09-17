package fr.tropimon.farm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;

class UpdaterPolicyTest {
  private static Object invoke(String name, Class<?>[] signature, Object... args) throws Exception {
    var method = TropimonSelfUpdater.class.getDeclaredMethod(name, signature);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  @Test
  void officialRepositoryOnly() throws Exception {
    var signature = new Class<?>[] {String.class, String.class};
    assertNotNull(
        invoke(
            "checkedAsset",
            signature,
            "TropimonFarm-0.2.0.jar",
            "https://github.com/FastedCorsi/TropimonFarm/releases/download/v0.2.0/TropimonFarm-0.2.0.jar"));
    for (String name :
        new String[] {"../evil.jar", "evil\\payload.jar", "C:payload.jar", "..jar"}) {
      assertThrows(
          InvocationTargetException.class,
          () ->
              invoke(
                  "checkedAsset",
                  signature,
                  name,
                  "https://github.com/FastedCorsi/TropimonFarm/releases/download/v1/evil.jar"));
    }
    assertThrows(
        InvocationTargetException.class,
        () ->
            invoke(
                "checkedAsset",
                signature,
                "mod.jar",
                "https://example.invalid/FastedCorsi/TropimonFarm/releases/download/mod.jar"));
  }

  @Test
  void ambiguousJarsRejected() throws Exception {
    JsonArray assets = new JsonArray();
    for (String name : new String[] {"one.jar", "two.jar"}) {
      JsonObject a = new JsonObject();
      a.addProperty("name", name);
      a.addProperty(
          "browser_download_url",
          "https://github.com/FastedCorsi/TropimonFarm/releases/download/v1/" + name);
      assets.add(a);
    }
    assertThrows(
        InvocationTargetException.class,
        () -> invoke("selectJar", new Class<?>[] {JsonArray.class}, assets));
  }

  @Test
  void noDowngrade() throws Exception {
    assertEquals(
        -1,
        invoke("compareVersions", new Class<?>[] {String.class, String.class}, "0.1.0", "0.2.0"));
    assertEquals(
        0,
        invoke(
            "compareVersions",
            new Class<?>[] {String.class, String.class},
            "0.1.0+1.21.1",
            "0.1.0"));
  }
}
