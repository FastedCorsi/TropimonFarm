package fr.tropimon.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DeferredUpdaterTest {
  @TempDir Path fixture;

  @Test
  void generatedInstallerPreservesChangedTarget() throws Exception {
    Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
    var field = TropimonSelfUpdater.class.getDeclaredField("WINDOWS_INSTALLER");
    field.setAccessible(true);
    Path script = fixture.resolve("install.ps1");
    Files.writeString(script, (String) field.get(null));
    Path mods = Files.createDirectories(fixture.resolve("instance/mods")),
        stage = Files.createDirectories(fixture.resolve("stage"));
    Path target = mods.resolve("test.jar"), incoming = stage.resolve("incoming.jar");
    Files.writeString(target, "old fixture");
    Files.writeString(incoming, "new fixture");
    String old = hash(target), next = hash(incoming);
    assertEquals(0, run(script, incoming, target, old, next));
    assertEquals(next, hash(target));
    assertTrue(Files.isDirectory(fixture.resolve("instance/mod-archive")));
    assertEquals(2, run(script, incoming, target, old, next));
    assertEquals(next, hash(target));
  }

  private int run(Path script, Path stage, Path target, String old, String next) throws Exception {
    var process =
        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script.toString(),
                "-ParentPid",
                "2147483647",
                "-Staged",
                stage.toString(),
                "-Target",
                target.toString(),
                "-ExpectedOldHash",
                old,
                "-NewHash",
                next,
                "-ModId",
                "test_fixture")
            .redirectErrorStream(true)
            .redirectOutput(fixture.resolve("test-output.txt").toFile())
            .start();
    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Synthetic deferred installer timeout");
    return process.exitValue();
  }

  private String hash(Path file) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }
}
