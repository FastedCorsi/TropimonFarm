package fr.tropimon.farm;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/** Only the official region identifier is read; no foreign classes are required. */
public final class RegionSignal {
  public static final String ID = "tropimon:set_current_server_packet";

  public static String inspect(ByteBuf frame) {
    int position = frame.readerIndex(), end = frame.writerIndex(), bytes = 0, part;
    do {
      if (position >= end || bytes++ == 5) return null;
      part = frame.getUnsignedByte(position++);
    } while ((part & 128) != 0);
    if (position >= end
        || frame.getUnsignedByte(position++) != ID.length()
        || end - position < ID.length()) return null;
    for (int i = 0; i < ID.length(); i++)
      if (frame.getByte(position + i) != ID.charAt(i)) return null;
    try {
      ByteBuf input = frame.duplicate().readerIndex(position + ID.length());
      int size = 0;
      for (int i = 0; i < 3; i++) {
        int b = input.readUnsignedByte();
        size |= (b & 127) << (7 * i);
        if ((b & 128) == 0) {
          if (size > 4096 || size != input.readableBytes()) return null;
          String json = input.toString(input.readerIndex(), size, StandardCharsets.UTF_8);
          String name = JsonParser.parseString(json).getAsJsonObject().get("name").getAsString();
          return name.isBlank() || name.length() > 128 ? null : name;
        }
      }
    } catch (RuntimeException ignored) {
    }
    return null;
  }
}
