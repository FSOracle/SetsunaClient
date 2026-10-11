package com.setsuna.module.modules.combat.killauraplus;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraPlusHostContractTest {
    private static final String HOST = "com/setsuna/module/modules/combat/KillAuraPlus";
    private static final String CONTEXT =
            "com/setsuna/module/modules/combat/killauraplus/MinecraftKillAuraContext";
    private static final String ENGINE =
            "com/setsuna/module/modules/combat/killauraplus/KillAuraPlusEngine";
    private static final String SETTINGS = "com/setsuna/setting/settings/";
    private static final String LISTEN = "Lcom/setsuna/event/Listen;";
    private static final String TICK = "(Lcom/setsuna/event/events/PlayerTickEvent$Pre;)V";

    @Test
    void hostDoesNotListenForOrCancelItsOwnOutgoingAttackPackets() throws Exception {
        ClassScan host = readClass(HOST);
        MethodScan tick = host.method("onTick", TICK);
        assertTrue(tick.annotations.contains(LISTEN));
        for (MethodScan method : host.methods.values()) {
            assertFalse(method.annotations.contains(LISTEN)
                    && method.descriptor.contains("Lcom/setsuna/event/events/PacketEvent$Send;"),
                    "Outgoing packet listener: " + method.name);
            assertFalse(method.hasCall("com/setsuna/event/events/PacketEvent$Send", "cancel"),
                    "Outgoing packet cancellation: " + method.name);
            assertFalse(method.hasCall("com/setsuna/event/events/PacketEvent$Send", "setPacket"),
                    "Outgoing packet replacement: " + method.name);
            assertFalse(Set.of("onPacketSend", "onStartUseItem", "beginUseAction")
                    .contains(method.name), method.name);
        }
        assertFalse(host.fields.containsValue(
                "Lcom/setsuna/module/modules/combat/killauraplus/KillAuraPacketSequence;"));
    }

    @Test
    void originalTargetEspNamesAndDefaultsAreConstructedExactlyOnce() throws Exception {
        List<Object> constructor = readClass(HOST).method("<init>", "()V").instructions;
        for (String name : List.of("TargetESP", "TargetESPRadius", "TargetESPAlpha",
                "TargetESPSideColor", "TargetESPLineColor")) {
            assertEquals(1L, constructor.stream().filter(name::equals).count(), name);
        }
        assertSettingConstruction(constructor, "TargetESP", 0,
                new Call(SETTINGS + "BooleanSetting", "<init>", "(Ljava/lang/String;Z)V"));
        assertSettingConstruction(constructor, "TargetESPRadius", 0.75d, 0.1d, 2.0d, 0.05d,
                new Call(SETTINGS + "DoubleSetting", "<init>", "(Ljava/lang/String;DDDD)V"));
        assertSettingConstruction(constructor, "TargetESPAlpha", 1.0d, 0.0d, 2.0d, 0.05d,
                new Call(SETTINGS + "DoubleSetting", "<init>", "(Ljava/lang/String;DDDD)V"));
        assertSettingConstruction(constructor, "TargetESPSideColor",
                new Field(Opcodes.GETSTATIC, "java/awt/Color", "WHITE", "Ljava/awt/Color;"),
                new Call(SETTINGS + "ColorSetting", "<init>",
                        "(Ljava/lang/String;Ljava/awt/Color;)V"));
        assertSettingConstruction(constructor, "TargetESPLineColor", 255, 255, 255, 233,
                new Call("java/awt/Color", "<init>", "(IIII)V"),
                new Call(SETTINGS + "ColorSetting", "<init>",
                        "(Ljava/lang/String;Ljava/awt/Color;)V"));
        assertFalse(constructor.contains("Raycast"));
    }

    @Test
    void tickSynchronizesAllTargetEspSettingsBeforeCallingTheEngine() throws Exception {
        MethodScan tick = readClass(HOST).method("onTick", TICK);
        Call configureEsp = new Call(ENGINE, "configureTargetEsp",
                "(ZFFLjava/awt/Color;Ljava/awt/Color;)V");
        int configureIndex = tick.instructions.indexOf(configureEsp);
        assertTrue(configureIndex >= 0, "Target ESP settings must reach the Clap adapter");
        assertEquals(1L, tick.instructions.stream().filter(configureEsp::equals).count());
        assertSubsequence(tick.instructions.subList(0, configureIndex),
                new Field(Opcodes.GETFIELD, HOST, "esp", "L" + SETTINGS + "BooleanSetting;"),
                new Field(Opcodes.GETFIELD, HOST, "circleRadius", "L" + SETTINGS + "DoubleSetting;"),
                new Field(Opcodes.GETFIELD, HOST, "circleAlphaFactor", "L" + SETTINGS + "DoubleSetting;"),
                new Field(Opcodes.GETFIELD, HOST, "sideColor", "L" + SETTINGS + "ColorSetting;"),
                new Field(Opcodes.GETFIELD, HOST, "lineColor", "L" + SETTINGS + "ColorSetting;"));
        assertSubsequence(tick.instructions, new Call(CONTEXT, "isUseInputActive", "()Z"),
                new Call(CONTEXT, "isNormalAttackSupported", "()Z"), configureEsp,
                new Call(ENGINE, "tick",
                        "(Lcom/setsuna/module/modules/combat/killauraplus/KillAuraConfig;Z)"
                                + "Lcom/setsuna/module/modules/combat/killauraplus/KillAuraAction;"));
    }

    @Test
    void hostKeepsTargetHudAndMixinFacingMethodsWithoutInitializingMinecraft() throws Exception {
        ClassScan host = readClass(HOST);
        for (String method : List.of("id()Ljava/lang/String;", "getInfo()Ljava/lang/String;",
                "target()Lnet/minecraft/world/entity/LivingEntity;", "hasLockedTarget()Z",
                "isFakeBlocking()Z", "isSwordsEspActive()Z",
                "lastBlockReason()Lcom/setsuna/module/modules/combat/killauraplus/KillAuraBlockReason;")) {
            MethodScan scan = host.methods.get(method);
            assertNotNull(scan, method);
            assertTrue((scan.access & Opcodes.ACC_PUBLIC) != 0, method);
        }
        MethodScan target = host.method("target", "()Lnet/minecraft/world/entity/LivingEntity;");
        assertSubsequence(target.instructions,
                new Call(ENGINE, "attackTargetEntityId", "()Ljava/lang/Integer;"),
                new Call(CONTEXT, "livingEntity", "(I)Lnet/minecraft/world/entity/LivingEntity;"));
        assertTrue(host.method("getInfo", "()Ljava/lang/String;").instructions.contains("Single"));
        assertTrue(host.method("getInfo", "()Ljava/lang/String;").instructions.contains("Switch"));
        assertTrue(host.method("isFakeBlocking", "()Z").hasCall(ENGINE, "isFakeBlocking"));
    }

    @Test
    void minecraftContextDelegatesAttackAndSwingToVanillaWithoutExtraPacketGates() throws Exception {
        ClassScan context = readClass(CONTEXT);
        MethodScan attack = context.method("attackEntity", "(I)V");
        assertSubsequence(attack.instructions,
                new Field(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", "gameMode",
                        "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;"),
                new Call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "attack",
                        "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"));
        MethodScan swing = context.method("swingMainHand", "()V");
        assertTrue(swing.instructions.contains(new Field(Opcodes.GETSTATIC,
                "net/minecraft/world/InteractionHand", "MAIN_HAND",
                "Lnet/minecraft/world/InteractionHand;")));
        assertTrue(swing.instructions.stream().anyMatch(instruction -> instruction instanceof Call call
                && call.name.equals("swing")
                && call.descriptor.equals("(Lnet/minecraft/world/InteractionHand;)V")));
        for (MethodScan method : context.methods.values()) {
            assertFalse(Set.of("alignRotation", "raycastEntityId", "lastSentRotationRayHitsTarget",
                    "synchronizeAttackSprintState", "prepareAttackPacket").contains(method.name),
                    method.name);
        }
    }

    private static ClassScan readClass(String name) throws Exception {
        ClassScan scan = new ClassScan();
        // Read bytes by name so no host static initializer or game bootstrap runs.
        try (var bytes = KillAuraPlusHostContractTest.class.getClassLoader()
                .getResourceAsStream(name + ".class")) {
            assertNotNull(bytes, name);
            new ClassReader(bytes).accept(scan, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return scan;
    }

    private static void assertSubsequence(List<Object> actual, Object... expected) {
        int matched = 0;
        for (Object instruction : actual) {
            if (instruction.equals(expected[matched]) && ++matched == expected.length) return;
        }
        assertEquals(expected.length, matched, "Missing bytecode sequence: " + List.of(expected));
    }

    private static void assertSettingConstruction(List<Object> constructor, Object... expected) {
        int start = constructor.indexOf(expected[0]);
        assertTrue(start >= 0, "Missing setting: " + expected[0]);
        List<Object> setting = constructor.subList(start, constructor.size());
        int end = setting.indexOf(expected[expected.length - 1]);
        assertTrue(end >= 0, "Missing setting constructor: " + expected[0]);
        assertEquals(List.of(expected), setting.subList(0, end + 1),
                "Unexpected setting default: " + expected[0]);
    }

    private static final class ClassScan extends ClassVisitor {
        private final Map<String, MethodScan> methods = new HashMap<>();
        private final Map<String, String> fields = new HashMap<>();

        private ClassScan() {
            super(Opcodes.ASM9);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            fields.put(name, descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodScan scan = new MethodScan(access, name, descriptor);
            methods.put(name + descriptor, scan);
            return scan;
        }

        private MethodScan method(String name, String descriptor) {
            MethodScan scan = methods.get(name + descriptor);
            assertNotNull(scan, name + descriptor);
            return scan;
        }
    }

    private static final class MethodScan extends MethodVisitor {
        private final int access;
        private final String name;
        private final String descriptor;
        private final Set<String> annotations = new HashSet<>();
        private final List<Object> instructions = new ArrayList<>();

        private MethodScan(int access, String name, String descriptor) {
            super(Opcodes.ASM9);
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            annotations.add(descriptor);
            return null;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            instructions.add(new Field(opcode, owner, name, descriptor));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                    boolean isInterface) {
            instructions.add(new Call(owner, name, descriptor));
        }

        @Override
        public void visitLdcInsn(Object value) {
            instructions.add(value);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) instructions.add(operand);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                instructions.add(opcode - Opcodes.ICONST_0);
            } else if (opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1) {
                instructions.add((double) (opcode - Opcodes.DCONST_0));
            }
        }

        private boolean hasCall(String owner, String name) {
            return instructions.stream().anyMatch(instruction -> instruction instanceof Call call
                    && call.owner.equals(owner) && call.name.equals(name));
        }
    }

    private record Call(String owner, String name, String descriptor) {
    }

    private record Field(int opcode, String owner, String name, String descriptor) {
    }
}
