package com.setsuna.integration.apollo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApolloTeamMessageParserTest {

    @Test
    void parsesTeamUpdateInsideGoogleAnyEnvelope() throws IOException {
        UUID uuid = UUID.fromString("fedcba98-7654-3210-0123-456789abcdef");
        byte[] playerUuid = concat(
                fixed64Field(1, uuid.getMostSignificantBits()),
                fixed64Field(2, uuid.getLeastSignificantBits()));
        byte[] location = concat(
                stringField(1, "world-1"),
                doubleField(2, 12.25),
                doubleField(3, 64.5),
                doubleField(4, -8.75));
        byte[] color = varintField(1, 0x12ABEF);
        byte[] member = concat(
                bytesField(1, playerUuid),
                bytesField(2, new byte[]{8, 1}),
                bytesField(3, location),
                bytesField(4, color),
                stringField(5, "{\"text\":\"Alice\",\"extra\":[{\"text\":\"_TV\"}]}"));
        byte[] update = bytesField(1, member);
        byte[] any = concat(
                stringField(1, "type.googleapis.com/lunarclient.apollo.team.v1.UpdateTeamMembersMessage"),
                bytesField(2, update));

        ApolloTeamMessageParser.Update parsed = assertInstanceOf(
                ApolloTeamMessageParser.Update.class,
                ApolloTeamMessageParser.parse(any));
        assertEquals(1, parsed.members().size());
        ApolloTeamMessageParser.Member result = parsed.members().getFirst();
        assertEquals(uuid, result.uuid());
        assertEquals("Alice_TV", result.name());
        assertEquals("world-1", result.world());
        assertEquals(12.25, result.x());
        assertEquals(64.5, result.y());
        assertEquals(-8.75, result.z());
        assertEquals(0xFF12ABEF, result.color());
    }

    @Test
    void recognizesResetAndIgnoresOtherApolloMessages() throws IOException {
        byte[] reset = concat(
                stringField(1, "type.googleapis.com/lunarclient.apollo.team.v1.ResetTeamMembersMessage"),
                bytesField(2, new byte[0]));
        byte[] other = concat(
                stringField(1, "type.googleapis.com/lunarclient.apollo.other.v1.Message"),
                bytesField(2, new byte[]{8, 1}));

        assertEquals(ApolloTeamMessageParser.Reset.INSTANCE,
                ApolloTeamMessageParser.parse(reset));
        assertEquals(ApolloTeamMessageParser.Ignored.INSTANCE,
                ApolloTeamMessageParser.parse(other));
    }

    @Test
    void parsesMultipleMembersIncludingTrackedPlayersWithoutLocations() throws IOException {
        UUID nearbyUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID remoteUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        byte[] nearbyMember = concat(
                bytesField(1, uuid(nearbyUuid)),
                bytesField(4, varintField(1, 0x55AA77)));
        byte[] remoteLocation = concat(
                stringField(1, "world"),
                doubleField(2, 120.0),
                doubleField(3, 70.0),
                doubleField(4, -30.0));
        byte[] remoteMember = concat(
                bytesField(1, uuid(remoteUuid)),
                bytesField(3, remoteLocation),
                stringField(5, "Remote"));
        byte[] update = concat(bytesField(1, nearbyMember), bytesField(1, remoteMember));
        byte[] any = concat(
                stringField(1, "type.googleapis.com/lunarclient.apollo.team.v1.UpdateTeamMembersMessage"),
                bytesField(2, update));

        ApolloTeamMessageParser.Update parsed = assertInstanceOf(
                ApolloTeamMessageParser.Update.class,
                ApolloTeamMessageParser.parse(any));
        assertEquals(2, parsed.members().size());
        assertEquals(nearbyUuid, parsed.members().get(0).uuid());
        assertEquals(false, parsed.members().get(0).hasLocation());
        assertEquals(remoteUuid, parsed.members().get(1).uuid());
        assertEquals(true, parsed.members().get(1).hasLocation());
    }

    @Test
    void acceptsPlainNamesAndRejectsTruncatedFields() {
        assertEquals("Alice", ApolloTeamMessageParser.plainName("Alice"));
        assertEquals("Alice", ApolloTeamMessageParser.plainName("\"Alice\""));
        assertThrows(IOException.class,
                () -> ApolloTeamMessageParser.parse(new byte[]{10, 5, 'b', 'a'}));
    }

    private static byte[] stringField(int field, String value) {
        return bytesField(field, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytesField(int field, byte[] value) {
        return concat(varint((long) field << 3 | 2), varint(value.length), value);
    }

    private static byte[] varintField(int field, long value) {
        return concat(varint((long) field << 3), varint(value));
    }

    private static byte[] doubleField(int field, double value) {
        return fixed64Field(field, Double.doubleToRawLongBits(value));
    }

    private static byte[] fixed64Field(int field, long value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(varint((long) field << 3 | 1));
        for (int index = 0; index < 8; index++) {
            output.write((int) (value >>> index * 8) & 0xFF);
        }
        return output.toByteArray();
    }

    private static byte[] uuid(UUID uuid) {
        return concat(
                fixed64Field(1, uuid.getMostSignificantBits()),
                fixed64Field(2, uuid.getLeastSignificantBits()));
    }

    private static byte[] varint(long value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((value & ~0x7FL) != 0L) {
            output.write((int) value & 0x7F | 0x80);
            value >>>= 7;
        }
        output.write((int) value);
        return output.toByteArray();
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }
}
