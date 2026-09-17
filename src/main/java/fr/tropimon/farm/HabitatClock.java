package fr.tropimon.farm;

import net.minecraft.client.MinecraftClient;

/** Latest ordinary time packet only; no extrapolation from the machine clock. */
public final class HabitatClock {
  private static Object world;
  private static long age = -1, received;

  public static void accept(long value) {
    world = MinecraftClient.getInstance().world;
    age = value;
    received = System.nanoTime();
  }

  static void reset() {
    world = null;
    age = -1;
  }

  static long age() {
    return world != null
            && world == MinecraftClient.getInstance().world
            && System.nanoTime() - received <= 10_000_000_000L
        ? age
        : -1;
  }
}
