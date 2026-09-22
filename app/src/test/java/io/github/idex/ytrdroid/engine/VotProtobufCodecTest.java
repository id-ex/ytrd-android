package io.github.idex.ytrdroid.engine;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static io.github.idex.ytrdroid.engine.VotProtobufCodec.*;
import static org.junit.Assert.*;

public class VotProtobufCodecTest {
    @Test public void roundTripVarint() {
        assertArrayEquals(new byte[]{0}, encodeVarint(0));
        assertArrayEquals(new byte[]{1}, encodeVarint(1));
        assertArrayEquals(new byte[]{(byte)0xAC, 0x02}, encodeVarint(300));
        assertEquals(300, readVarint(encodeVarint(300), 0)[0]);
    }

    @Test public void roundTripStringField() {
        byte[] encoded = encodeString(3, "hello");
        Map<Integer, Object> fields = readProtobuf(encoded);
        assertEquals("hello", getString(fields, 3));
    }

    @Test public void roundTripIntField() {
        byte[] encoded = encodeInt(4, 1);
        Map<Integer, Object> fields = readProtobuf(encoded);
        assertEquals(Integer.valueOf(1), getInt(fields, 4));
    }

    @Test public void readyResponseParsesCorrectly() {
        byte[] url = encodeString(1, "https://vtrans.s3-private.mds.yandex.net/tts/prod/audio.mp3");
        byte[] status = encodeInt(4, 1);
        byte[] response = concat(url, status);
        Map<Integer, Object> fields = readProtobuf(response);
        assertEquals(Integer.valueOf(1), getInt(fields, 4));
        assertTrue(getString(fields, 1).startsWith("https://"));
    }

    @Test public void waitingResponseStatus() {
        Map<Integer, Object> fields = readProtobuf(encodeInt(4, 2));
        assertEquals(Integer.valueOf(2), getInt(fields, 4));
    }

    @Test(expected = ProtocolException.class)
    public void truncatedVarintThrows() {
        readVarint(new byte[]{(byte) 0x80}, 0);
    }

    @Test(expected = ProtocolException.class)
    public void truncatedStringThrows() {
        readProtobuf(new byte[]{10, 5, 65});
    }

    @Test(expected = ProtocolException.class)
    public void hugeDeclaredLengthThrows() {
        readProtobuf(new byte[]{10, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 7});
    }

    @Test(expected = ProtocolException.class)
    public void unknownWireTypeThrows() {
        readProtobuf(new byte[]{0x20, 1, 0x0F});
    }

    @Test(expected = ProtocolException.class)
    public void nullDataThrows() {
        readProtobuf(null);
    }

    @Test public void emptyDataParsesToEmptyFields() {
        assertTrue(readProtobuf(new byte[0]).isEmpty());
    }

    @Test public void negativeVarintRoundTrips() {
        byte[] encoded = encodeVarint(-1);
        long decoded = readVarint(encoded, 0)[0];
        // Negative values are encoded as unsigned 64-bit; decoding yields the unsigned representation.
        assertEquals(-1L, decoded);
    }

    @Test public void multipleFieldsMaintainAll() {
        byte[] data = concat(encodeInt(4, 1),
                encodeString(1, "url"),
                encodeString(9, "message"));
        Map<Integer, Object> fields = readProtobuf(data);
        assertEquals(Integer.valueOf(1), getInt(fields, 4));
        assertEquals("url", getString(fields, 1));
        assertEquals("message", getString(fields, 9));
    }

    @Test public void boolFieldEncodesDecode() {
        byte[] data = concat(encodeBool(5, true), encodeBool(18, false));
        Map<Integer, Object> fields = readProtobuf(data);
        assertEquals(Integer.valueOf(1), getInt(fields, 5));
        assertEquals(Integer.valueOf(0), getInt(fields, 18));
    }

    @Test public void doubleFieldRoundTrips() {
        byte[] data = encodeDouble(6, 341.0);
        Map<Integer, Object> fields = readProtobuf(data);
        byte[] raw = (byte[]) fields.get(6);
        assertNotNull(raw);
        assertEquals(8, raw.length);
        double value = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN).getDouble();
        assertEquals(341.0, value, 0.001);
    }
}
