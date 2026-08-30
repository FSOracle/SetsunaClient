package com.setsuna.integration.apollo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Decodes only the Apollo Team messages needed by Team Viewer. */
public final class ApolloTeamMessageParser {

    private static final String UPDATE_TYPE =
            "lunarclient.apollo.team.v1.UpdateTeamMembersMessage";
    private static final String RESET_TYPE =
            "lunarclient.apollo.team.v1.ResetTeamMembersMessage";
    private static final int MAX_MEMBERS = 4096;

    private ApolloTeamMessageParser() {
    }

    public static Message parse(byte[] payload) throws IOException {
        Reader envelope = new Reader(payload);
        String typeUrl = "";
        byte[] value = null;
        while (envelope.hasRemaining()) {
            int tag = envelope.readTag();
            switch (tag) {
                case 10 -> typeUrl = envelope.readString();
                case 18 -> value = envelope.readBytes();
                default -> envelope.skip(tag);
            }
        }
        String type = typeUrl.substring(typeUrl.lastIndexOf('/') + 1);
        if (RESET_TYPE.equals(type)) {
            return Reset.INSTANCE;
        }
        if (!UPDATE_TYPE.equals(type) || value == null) {
            return Ignored.INSTANCE;
        }
        return parseUpdate(value);
    }

    private static Update parseUpdate(byte[] value) throws IOException {
        Reader reader = new Reader(value);
        List<Member> members = new ArrayList<>();
        while (reader.hasRemaining()) {
            int tag = reader.readTag();
            if (tag == 10) {
                if (members.size() >= MAX_MEMBERS) {
                    throw new IOException("Apollo team update contains too many members");
                }
                Member member = parseMember(reader.readBytes());
                if (member != null) {
                    members.add(member);
                }
            } else {
                reader.skip(tag);
            }
        }
        return new Update(List.copyOf(members));
    }

    private static Member parseMember(byte[] value) throws IOException {
        Reader reader = new Reader(value);
        UUID uuid = null;
        Location location = null;
        int color = 0xFFFFFF;
        String name = "";
        while (reader.hasRemaining()) {
            int tag = reader.readTag();
            switch (tag) {
                case 10 -> uuid = parseUuid(reader.readBytes());
                case 26 -> location = parseLocation(reader.readBytes());
                case 34 -> color = parseColor(reader.readBytes());
                case 42 -> name = plainName(reader.readString());
                default -> reader.skip(tag);
            }
        }
        if (uuid == null) {
            return null;
        }
        return new Member(uuid, name,
                location == null ? "" : location.world,
                location == null ? Double.NaN : location.x,
                location == null ? Double.NaN : location.y,
                location == null ? Double.NaN : location.z,
                0xFF000000 | color & 0xFFFFFF);
    }

    private static UUID parseUuid(byte[] value) throws IOException {
        Reader reader = new Reader(value);
        long high = 0L;
        long low = 0L;
        while (reader.hasRemaining()) {
            int tag = reader.readTag();
            switch (tag) {
                case 9 -> high = reader.readFixed64();
                case 17 -> low = reader.readFixed64();
                // Accept the legacy varint form as well, but Apollo's schema uses fixed64.
                case 8 -> high = reader.readVarint64();
                case 16 -> low = reader.readVarint64();
                default -> reader.skip(tag);
            }
        }
        return new UUID(high, low);
    }

    private static Location parseLocation(byte[] value) throws IOException {
        Reader reader = new Reader(value);
        String world = "";
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        while (reader.hasRemaining()) {
            int tag = reader.readTag();
            switch (tag) {
                case 10 -> world = reader.readString();
                case 17 -> x = reader.readDouble();
                case 25 -> y = reader.readDouble();
                case 33 -> z = reader.readDouble();
                default -> reader.skip(tag);
            }
        }
        return new Location(world, x, y, z);
    }

    private static int parseColor(byte[] value) throws IOException {
        Reader reader = new Reader(value);
        int color = 0xFFFFFF;
        while (reader.hasRemaining()) {
            int tag = reader.readTag();
            if (tag == 8) {
                color = (int) reader.readVarint64();
            } else {
                reader.skip(tag);
            }
        }
        return color;
    }

    static String plainName(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        try {
            StringBuilder output = new StringBuilder();
            appendComponentText(JsonParser.parseString(source), output);
            return output.isEmpty() ? source : output.toString();
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    private static void appendComponentText(JsonElement element, StringBuilder output) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            output.append(element.getAsString());
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                appendComponentText(child, output);
            }
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("text")) {
            appendComponentText(object.get("text"), output);
        }
        JsonArray extra = object.has("extra") && object.get("extra").isJsonArray()
                ? object.getAsJsonArray("extra") : null;
        if (extra != null) {
            appendComponentText(extra, output);
        }
    }

    public sealed interface Message permits Update, Reset, Ignored {
    }

    public record Update(List<Member> members) implements Message {
    }

    public enum Reset implements Message {
        INSTANCE
    }

    public enum Ignored implements Message {
        INSTANCE
    }

    public record Member(UUID uuid, String name, String world,
                         double x, double y, double z, int color) {
        public boolean hasLocation() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    private record Location(String world, double x, double y, double z) {
    }

    private static final class Reader {
        private final byte[] data;
        private int position;

        private Reader(byte[] data) {
            this.data = data;
        }

        private boolean hasRemaining() {
            return position < data.length;
        }

        private int readTag() throws IOException {
            int tag = (int) readVarint64();
            if (tag == 0) {
                throw new IOException("Invalid protobuf tag");
            }
            return tag;
        }

        private long readVarint64() throws IOException {
            long result = 0L;
            for (int shift = 0; shift < 64; shift += 7) {
                require(1);
                int value = data[position++] & 0xFF;
                result |= (long) (value & 0x7F) << shift;
                if ((value & 0x80) == 0) {
                    return result;
                }
            }
            throw new IOException("Malformed protobuf varint");
        }

        private byte[] readBytes() throws IOException {
            long rawLength = readVarint64();
            if (rawLength < 0L || rawLength > Integer.MAX_VALUE) {
                throw new IOException("Invalid protobuf length");
            }
            int length = (int) rawLength;
            require(length);
            byte[] value = new byte[length];
            System.arraycopy(data, position, value, 0, length);
            position += length;
            return value;
        }

        private String readString() throws IOException {
            return new String(readBytes(), StandardCharsets.UTF_8);
        }

        private double readDouble() throws IOException {
            return Double.longBitsToDouble(readFixed64());
        }

        private long readFixed64() throws IOException {
            require(8);
            long bits = 0L;
            for (int index = 0; index < 8; index++) {
                bits |= (long) (data[position++] & 0xFF) << index * 8;
            }
            return bits;
        }

        private void skip(int tag) throws IOException {
            switch (tag & 7) {
                case 0 -> readVarint64();
                case 1 -> advance(8);
                case 2 -> {
                    long length = readVarint64();
                    if (length < 0L || length > Integer.MAX_VALUE) {
                        throw new IOException("Invalid protobuf length");
                    }
                    advance((int) length);
                }
                case 5 -> advance(4);
                default -> throw new IOException("Unsupported protobuf wire type " + (tag & 7));
            }
        }

        private void advance(int count) throws IOException {
            require(count);
            position += count;
        }

        private void require(int count) throws IOException {
            if (count < 0 || count > data.length - position) {
                throw new IOException("Truncated protobuf payload");
            }
        }
    }
}
