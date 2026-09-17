package fr.tropimon.farm;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RegionSignalTest {
  @Test
  void boundedReadWithoutConsumption() {
    var b = Unpooled.buffer();
    String json = "{\"name\":\"TestRealm\"}";
    b.writeByte(25)
        .writeByte(RegionSignal.ID.length())
        .writeCharSequence(RegionSignal.ID, StandardCharsets.UTF_8);
    b.writeByte(json.length()).writeCharSequence(json, StandardCharsets.UTF_8);
    try {
      assertEquals("TestRealm", RegionSignal.inspect(b));
      assertEquals(0, b.readerIndex());
      b.writerIndex(b.writerIndex() - 1);
      assertNull(RegionSignal.inspect(b));
    } finally {
      b.release();
    }
  }
}
