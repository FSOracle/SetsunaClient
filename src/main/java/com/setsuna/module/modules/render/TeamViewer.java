package com.setsuna.module.modules.render;

import com.setsuna.Setsuna;
import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.integration.apollo.ApolloTeamMessageParser;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.ui.hud.EpsilonHudModule;
import com.setsuna.util.player.HealthDetectionUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.joml.Vector3f;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Compact Apollo teammate HUD with projected world markers and direction arrows. */
public final class TeamViewer extends EpsilonHudModule {

    private static final int HEALTH_COLOR = 0xFFB8C4C0;
    private static final int BASE_WIDTH = 148;
    private static final int ROW_HEIGHT = 15;
    private static final int PANEL_PADDING = 4;
    private static final int AVATAR_SIZE = 10;
    private static final int TEXT_X = PANEL_PADDING + AVATAR_SIZE + 4;
    private static final int HEALTH_GAP = 5;
    private static final float FONT_SIZE = 7.5F;
    private static final float DIRECTION_SIZE = 2.5F;
    private static final float DIRECTION_SLOT_WIDTH = 8.0F;
    private static final double MARKER_PROJECTION_DISTANCE = 64.0;
    private static final Paint SKIN_PAINT = new Paint().setAntiAlias(false);
    private static final Paint PANEL_BORDER_PAINT = new Paint()
            .setAntiAlias(true)
            .setMode(PaintMode.STROKE)
            .setStrokeWidth(1.0F);

    public static final TeamViewer INSTANCE = new TeamViewer();

    public final BooleanSetting showMarker = add(new BooleanSetting("Show Marker", true));
    public final DoubleSetting markerSize = add(new DoubleSetting("Marker Size", 7.0, 2.0, 20.0, 0.5));
    public final DoubleSetting markerHeight = add(new DoubleSetting("Marker Height", 2.5, 0.0, 10.0, 0.1));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(18, 26, 24, 208), true).visibleWhen(background::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color",
            new Color(44, 57, 53, 160), true).visibleWhen(border::get));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 2.0, 0.0, 12.0, 0.5)
            .visibleWhen(() -> background.get() || border.get()));
    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 2.0, 0.05));
    public final ColorSetting distanceColor = add(
            new ColorSetting("Distance Color", new Color(105, 207, 210), false));

    private final Map<UUID, TrackedMember> members = new HashMap<>();
    private final List<DrawEntry> drawEntries = new ArrayList<>();
    private String selfApolloWorld;
    private int lastDrawnMarkers;
    private long receivedPayloads;
    private long receivedUpdates;
    private long ignoredPayloads;
    private long malformedPayloads;
    private long lastPayloadAt;

    private TeamViewer() {
        super("Team Viewer", 18, 180, BASE_WIDTH, ROW_HEIGHT + PANEL_PADDING * 2.0F);
    }

    public void acceptApollo(byte[] payload) {
        synchronized (this) {
            receivedPayloads++;
            lastPayloadAt = System.currentTimeMillis();
        }
        try {
            ApolloTeamMessageParser.Message message = ApolloTeamMessageParser.parse(payload);
            if (message instanceof ApolloTeamMessageParser.Reset) {
                clearTeam();
            } else if (message instanceof ApolloTeamMessageParser.Update update) {
                synchronized (this) {
                    receivedUpdates++;
                }
                applyUpdate(update);
            } else {
                synchronized (this) {
                    ignoredPayloads++;
                }
            }
        } catch (IOException | RuntimeException error) {
            synchronized (this) {
                malformedPayloads++;
            }
            Setsuna.LOGGER.debug("Ignored malformed Apollo team packet", error);
        }
    }

    public synchronized void clearTeam() {
        members.clear();
        drawEntries.clear();
        lastDrawnMarkers = 0;
        selfApolloWorld = null;
    }

    private synchronized void applyUpdate(ApolloTeamMessageParser.Update update) {
        long now = System.currentTimeMillis();
        UUID self = mc.player == null ? null : mc.player.getUUID();
        for (ApolloTeamMessageParser.Member incoming : update.members()) {
            if (incoming.uuid().equals(self)) {
                selfApolloWorld = incoming.world();
            }
            members.compute(incoming.uuid(), (uuid, existing) -> {
                if (existing == null) {
                    return new TrackedMember(incoming, now);
                }
                existing.update(incoming, now);
                return existing;
            });
        }
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        synchronized (this) {
            drawEntries.clear();
        }
        if (noPlayer() || !showMarker.get()) {
            synchronized (this) {
                lastDrawnMarkers = 0;
            }
            return;
        }

        List<MemberFrame> frame = frameMembers(System.currentTimeMillis());
        String localWorld;
        synchronized (this) {
            localWorld = selfApolloWorld;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        UUID self = mc.player.getUUID();
        double guiScale = mc.getWindow().getGuiScale();
        Map<UUID, MemberFrame> markerMembers = new HashMap<>();
        for (MemberFrame member : frame) {
            markerMembers.put(member.uuid, member);
        }
        addLoadedScoreboardMembers(markerMembers);
        List<DrawEntry> nextEntries = new ArrayList<>();

        for (MemberFrame member : markerMembers.values()) {
            if (member.uuid.equals(self) || !sameWorld(localWorld, member.world)) {
                continue;
            }
            Vec3 position = resolvePosition(member, partialTick);
            if (position == null) {
                continue;
            }
            Vector3f projected = projectMarker(position.add(0.0, markerHeight.get(), 0.0));
            if (!Float.isFinite(projected.x) || !Float.isFinite(projected.y)
                    || projected.z < 0.0F || projected.z > 1.0F) {
                continue;
            }
            nextEntries.add(new DrawEntry(
                    projected.x / (float) guiScale,
                    projected.y / (float) guiScale,
                    member.color));
        }
        synchronized (this) {
            drawEntries.addAll(nextEntries);
            lastDrawnMarkers = nextEntries.size();
        }
    }

    private void addLoadedScoreboardMembers(Map<UUID, MemberFrame> markerMembers) {
        Team selfTeam = mc.player.getTeam();
        if (selfTeam == null) {
            return;
        }
        for (Player player : mc.level.players()) {
            Team team = player.getTeam();
            if (player == mc.player || team == null
                    || !selfTeam.getName().equals(team.getName())) {
                continue;
            }
            markerMembers.putIfAbsent(player.getUUID(), new MemberFrame(
                    player.getUUID(), player.getName().getString(), "",
                    player.position(), teamColor(team)));
        }
    }

    private Vector3f projectMarker(Vec3 markerPosition) {
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 offset = markerPosition.subtract(camera);
        double distance = offset.length();
        if (distance > MARKER_PROJECTION_DISTANCE) {
            markerPosition = camera.add(offset.scale(MARKER_PROJECTION_DISTANCE / distance));
        }
        return WorldToScreen.getWorldPositionToScreen(markerPosition);
    }

    @Listen
    private void onRenderArrows(Render2DEvent event) {
        List<DrawEntry> entries;
        synchronized (this) {
            entries = List.copyOf(drawEntries);
        }
        Canvas canvas = event.canvas();
        float size = markerSize.get().floatValue();
        for (DrawEntry entry : entries) {
            drawWorldMarker(canvas, entry.x, entry.y, size, applyOpacity(entry.color));
        }
    }

    private void drawWorldMarker(Canvas canvas, float x, float y, float size, int color) {
        float half = size * 0.5F;
        Point[] shadow = {
                new Point(x - half - 1.0F, y - half - 1.0F),
                new Point(x, y + half + 1.0F),
                new Point(x + half + 1.0F, y - half - 1.0F)
        };
        Point[] arrow = {
                new Point(x - half, y - half),
                new Point(x, y + half),
                new Point(x + half, y - half)
        };
        try (Path shadowPath = Path.makePolygon(shadow, true);
             Path arrowPath = Path.makePolygon(arrow, true)) {
            SkijaUi.fillPath(canvas, shadowPath, applyOpacity(0xB0000000));
            SkijaUi.fillPath(canvas, arrowPath, color);
        }
    }

    private void drawDirectionArrow(Canvas canvas, float x, float y, float angle, int color) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        Point[] shadow = arrowPoints(x, y, DIRECTION_SIZE + 0.75F, cos, sin);
        Point[] arrow = arrowPoints(x, y, DIRECTION_SIZE, cos, sin);
        try (Path shadowPath = Path.makePolygon(shadow, true);
             Path arrowPath = Path.makePolygon(arrow, true)) {
            SkijaUi.fillPath(canvas, shadowPath, 0xB0000000);
            SkijaUi.fillPath(canvas, arrowPath, color);
        }
    }

    private static Point[] arrowPoints(float centerX, float centerY, float size,
                                       float cos, float sin) {
        return new Point[]{
                rotatePoint(centerX, centerY, 0.0F, -size, cos, sin),
                rotatePoint(centerX, centerY, -size * 0.65F, size * 0.8F, cos, sin),
                rotatePoint(centerX, centerY, size * 0.65F, size * 0.8F, cos, sin)
        };
    }

    private static Point rotatePoint(float centerX, float centerY, float x, float y,
                                     float cos, float sin) {
        return new Point(centerX + x * cos - y * sin,
                centerY + x * sin + y * cos);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) {
            updateBounds(BASE_WIDTH * scale.get().floatValue(),
                    (ROW_HEIGHT + PANEL_PADDING * 2.0F) * scale.get().floatValue());
            return;
        }

        List<HudEntry> entries = hudEntries();
        float panelScale = scale.get().floatValue();
        float baseHeight = entries.isEmpty()
                ? ROW_HEIGHT + PANEL_PADDING * 2.0F
                : entries.size() * ROW_HEIGHT + PANEL_PADDING * 2.0F;
        float width = BASE_WIDTH * panelScale;
        float height = baseHeight * panelScale;
        float x = renderX(event, width);
        float y = renderY(event, height);
        updateBounds(width, height);
        if (entries.isEmpty()) {
            return;
        }

        Canvas canvas = event.canvas();
        canvas.save();
        try {
            canvas.translate(x, y);
            canvas.scale(panelScale, panelScale);
            float radius = Math.min(borderRadius.get().floatValue(), baseHeight * 0.5F);
            if (background.get()) {
                SkijaUi.rounded(canvas, 0.0F, 0.0F, BASE_WIDTH, baseHeight,
                        radius, backgroundColor.argb());
            }
            if (border.get()) {
                drawPanelBorder(canvas, BASE_WIDTH, baseHeight, radius, borderColor.argb());
            }

            for (int index = 0; index < entries.size(); index++) {
                drawHudRow(canvas, entries.get(index), PANEL_PADDING + index * ROW_HEIGHT);
            }
        } finally {
            canvas.restore();
        }
    }

    private static void drawPanelBorder(Canvas canvas, float width, float height,
                                        float radius, int color) {
        float inset = 0.5F;
        PANEL_BORDER_PAINT.setColor(color);
        canvas.drawRRect(RRect.makeXYWH(inset, inset, width - inset * 2.0F,
                height - inset * 2.0F, Math.max(0.0F, radius - inset)),
                PANEL_BORDER_PAINT);
    }

    private List<HudEntry> hudEntries() {
        long now = System.currentTimeMillis();
        List<MemberFrame> frame = frameMembers(now);
        String localWorld;
        synchronized (this) {
            localWorld = selfApolloWorld;
        }
        UUID self = mc.player.getUUID();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Map<UUID, HudEntry> entries = new HashMap<>();
        for (MemberFrame member : frame) {
            if (member.uuid.equals(self)
                    || !sameWorld(localWorld, member.world)) {
                continue;
            }
            Player player = findPlayer(member);
            String name = resolveName(member, player);
            if (name.isBlank()) {
                continue;
            }
            Vec3 position = player == null ? member.position : resolvePosition(member, partialTick);
            Double distance = position == null || !position.isFinite()
                    ? null : mc.player.position().distanceTo(position);
            Float direction = relativeDirection(position, partialTick);
            entries.put(member.uuid, new HudEntry(member.uuid, name, distance, member.color,
                    direction, resolveHealth(member, player), resolveSkin(member)));
        }
        addScoreboardTeamEntries(entries, partialTick);
        List<HudEntry> sorted = new ArrayList<>(entries.values());
        sorted.sort((left, right) -> {
            if (left.distance == null && right.distance != null) return 1;
            if (left.distance != null && right.distance == null) return -1;
            if (left.distance != null) {
                int distanceOrder = Double.compare(left.distance, right.distance);
                if (distanceOrder != 0) return distanceOrder;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name);
        });
        return List.copyOf(sorted);
    }

    private void addScoreboardTeamEntries(Map<UUID, HudEntry> entries, float partialTick) {
        if (mc.getConnection() == null) {
            return;
        }
        PlayerInfo selfInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        Team selfTeam = selfInfo == null ? mc.player.getTeam() : selfInfo.getTeam();
        if (selfTeam == null) {
            return;
        }

        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            UUID uuid = info.getProfile().id();
            Team team = info.getTeam();
            if (uuid.equals(mc.player.getUUID()) || entries.containsKey(uuid)
                    || team == null || !selfTeam.getName().equals(team.getName())) {
                continue;
            }

            String name = info.getProfile().name();
            int color = teamColor(team);
            MemberFrame member = new MemberFrame(uuid, name, "",
                    new Vec3(Double.NaN, Double.NaN, Double.NaN), color);
            Player player = findPlayer(member);
            Vec3 position = resolvePosition(member, partialTick);
            Double distance = position == null ? null : mc.player.position().distanceTo(position);
            Float direction = relativeDirection(position, partialTick);
            entries.put(uuid, new HudEntry(uuid, name, distance, color,
                    direction, resolveHealth(member, player), skin(info)));
        }
    }

    private void drawHudRow(Canvas canvas, HudEntry entry, float rowY) {
        float avatarY = rowY + (ROW_HEIGHT - AVATAR_SIZE) * 0.5F;
        drawAvatar(canvas, entry, PANEL_PADDING, avatarY);

        String health = entry.health == null ? "--" : Integer.toString(entry.health);
        float healthWidth = SkijaUi.textWidth(health, FONT_SIZE);
        float healthX = BASE_WIDTH - PANEL_PADDING - healthWidth;
        String distance = entry.distance == null ? "--" : Math.round(entry.distance) + "m";
        float distanceWidth = SkijaUi.textWidth(distance, FONT_SIZE);
        float maxNameWidth = Math.max(8.0F,
                healthX - HEALTH_GAP - distanceWidth - DIRECTION_SLOT_WIDTH - 3.0F - TEXT_X);
        String name = fit(entry.name, maxNameWidth);
        float nameWidth = SkijaUi.textWidth(name, FONT_SIZE);
        int nameColor = 0xFF000000 | (entry.color & 0x00FFFFFF);
        float directionX = TEXT_X + nameWidth + DIRECTION_SLOT_WIDTH * 0.5F;
        float distanceX = TEXT_X + nameWidth + DIRECTION_SLOT_WIDTH;

        SkijaUi.textShadow(canvas, name, TEXT_X, rowY, ROW_HEIGHT, nameColor, FONT_SIZE);
        if (entry.direction != null) {
            drawDirectionArrow(canvas, directionX, rowY + ROW_HEIGHT * 0.5F,
                    entry.direction, distanceColor.argb());
        }
        SkijaUi.textShadow(canvas, distance, distanceX, rowY,
                ROW_HEIGHT, distanceColor.argb(), FONT_SIZE);
        SkijaUi.textShadow(canvas, health, healthX, rowY, ROW_HEIGHT, HEALTH_COLOR, FONT_SIZE);
    }

    private void drawAvatar(Canvas canvas, HudEntry entry, float x, float y) {
        if (entry.skin != null) {
            try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(entry.skin)) {
                if (borrowed != null) {
                    float textureWidth = borrowed.image().getWidth();
                    float textureHeight = borrowed.image().getHeight();
                    Rect destination = Rect.makeXYWH(x, y, AVATAR_SIZE, AVATAR_SIZE);
                    drawSkinLayer(canvas, borrowed, destination, textureWidth, textureHeight, 8.0F, 8.0F);
                    drawSkinLayer(canvas, borrowed, destination, textureWidth, textureHeight, 40.0F, 8.0F);
                    return;
                }
            } catch (Throwable ignored) {
                // Fall through to the team-colour tile while a skin is unavailable.
            }
        }
        SkijaUi.fill(canvas, x, y, AVATAR_SIZE, AVATAR_SIZE, 0xFF000000 | entry.color & 0x00FFFFFF);
        SkijaUi.outline(canvas, x, y, AVATAR_SIZE, AVATAR_SIZE, 0.0F, 1.0F, 0xA0FFFFFF);
    }

    private static void drawSkinLayer(Canvas canvas, SkijaRenderer.BorrowedImage borrowed,
                                      Rect destination, float textureWidth, float textureHeight,
                                      float sourceX, float sourceY) {
        Rect source = Rect.makeLTRB(
                sourceX / 64.0F * textureWidth,
                sourceY / 64.0F * textureHeight,
                (sourceX + 8.0F) / 64.0F * textureWidth,
                (sourceY + 8.0F) / 64.0F * textureHeight);
        canvas.drawImageRect(borrowed.image(), source, destination,
                SamplingMode.DEFAULT, SKIN_PAINT, true);
    }

    private Identifier resolveSkin(MemberFrame member) {
        if (mc.getConnection() == null) {
            return null;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(member.uuid);
        if (info == null && !member.name.isBlank()) {
            info = mc.getConnection().getPlayerInfoIgnoreCase(member.name);
        }
        return skin(info);
    }

    private static Identifier skin(PlayerInfo info) {
        return info == null || info.getSkin() == null || info.getSkin().body() == null
                ? null : info.getSkin().body().texturePath();
    }

    private static int teamColor(Team team) {
        Integer color = team.getColor().getColor();
        return 0xFF000000 | (color == null ? 0xFFFFFF : color & 0x00FFFFFF);
    }

    private Integer resolveHealth(MemberFrame member, Player player) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        if (objective != null) {
            ScoreHolder holder = scoreHolder(member, player);
            ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(holder, objective);
            if (score != null) {
                return score.value();
            }
        }
        return player == null ? null : Math.round(HealthDetectionUtils.getHealth(player));
    }

    private ScoreHolder scoreHolder(MemberFrame member, Player player) {
        if (player != null) {
            return ScoreHolder.fromGameProfile(player.getGameProfile());
        }
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(member.uuid);
            if (info != null) {
                return ScoreHolder.fromGameProfile(info.getProfile());
            }
        }
        return ScoreHolder.forNameOnly(member.name);
    }

    private static String fit(String text, float maximumWidth) {
        if (SkijaUi.textWidth(text, FONT_SIZE) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0
                && SkijaUi.textWidth(text.substring(0, end) + suffix, FONT_SIZE) > maximumWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + suffix;
    }

    private Vec3 resolvePosition(MemberFrame member, float partialTick) {
        Player player = findPlayer(member);
        if (player == null) {
            return member.position.isFinite() ? member.position : null;
        }
        return new Vec3(
                Mth.lerp(partialTick, player.xOld, player.getX()),
                Mth.lerp(partialTick, player.yOld, player.getY()),
                Mth.lerp(partialTick, player.zOld, player.getZ()));
    }

    private String resolveName(MemberFrame member, Player player) {
        if (!member.name.isBlank()) {
            return member.name;
        }
        if (player != null) {
            return player.getName().getString();
        }
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(member.uuid);
            if (info != null) {
                return info.getProfile().name();
            }
        }
        return "";
    }

    private static boolean sameWorld(String localWorld, String memberWorld) {
        return localWorld == null || localWorld.isBlank()
                || memberWorld == null || memberWorld.isBlank()
                || localWorld.equals(memberWorld);
    }

    private Float relativeDirection(Vec3 position, float partialTick) {
        if (position == null || !position.isFinite()) {
            return null;
        }
        double selfX = Mth.lerp(partialTick, mc.player.xOld, mc.player.getX());
        double selfZ = Mth.lerp(partialTick, mc.player.zOld, mc.player.getZ());
        double deltaX = position.x - selfX;
        double deltaZ = position.z - selfZ;
        if (deltaX * deltaX + deltaZ * deltaZ < 1.0E-8) {
            return null;
        }
        float yawRadians = (float) Math.toRadians(mc.player.getYRot(partialTick));
        return (float) (Math.atan2(deltaZ, deltaX) - yawRadians - Math.PI / 2.0);
    }

    private Player findPlayer(MemberFrame member) {
        for (Player player : mc.level.players()) {
            if (player.getUUID().equals(member.uuid)
                    || !member.name.isBlank() && player.getName().getString().equalsIgnoreCase(member.name)) {
                return player;
            }
        }
        return null;
    }

    private synchronized List<MemberFrame> frameMembers(long now) {
        return members.values().stream().map(member -> member.frame(now)).toList();
    }

    public synchronized List<MemberSnapshot> members() {
        return members.values().stream()
                .map(TrackedMember::snapshot)
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name))
                .toList();
    }

    public Optional<MemberSnapshot> findMember(String name) {
        return members().stream().filter(member -> member.name.equalsIgnoreCase(name)).findFirst();
    }

    public synchronized Status status() {
        long packetAge = lastPayloadAt == 0L ? -1L : System.currentTimeMillis() - lastPayloadAt;
        return new Status(receivedPayloads, receivedUpdates, ignoredPayloads, malformedPayloads,
                members.size(), lastDrawnMarkers, selfApolloWorld, packetAge);
    }

    @Override
    public String getInfo() {
        int remoteMembers = Math.max(0, members().size() - (mc.player == null ? 0 : 1));
        return remoteMembers == 0 ? null : Integer.toString(remoteMembers);
    }

    @Override
    public int editorColor() {
        return 0xFF69CFD2;
    }

    public record MemberSnapshot(UUID uuid, String name, String world,
                                 double x, double y, double z, int color) {
        public String coordinates() {
            return String.format(Locale.ROOT, "%s: X=%.1f Y=%.1f Z=%.1f W=%s",
                    name, x, y, z, world);
        }
    }

    public record Status(long payloads, long updates, long ignored, long malformed,
                         int members, int drawn, String selfWorld, long lastPacketAgeMs) {
    }

    private record DrawEntry(float x, float y, int color) {
    }

    private record HudEntry(UUID uuid, String name, Double distance, int color, Float direction,
                            Integer health, Identifier skin) {
    }

    private record MemberFrame(UUID uuid, String name, String world, Vec3 position, int color) {
    }

    private static final class TrackedMember {
        private final UUID uuid;
        private String name;
        private String world;
        private int color;
        private double x;
        private double y;
        private double z;
        private double lastX;
        private double lastY;
        private double lastZ;
        private long lastUpdate;
        private long previousUpdate;

        private TrackedMember(ApolloTeamMessageParser.Member member, long now) {
            uuid = member.uuid();
            name = member.name();
            world = member.world();
            color = member.color();
            x = lastX = member.x();
            y = lastY = member.y();
            z = lastZ = member.z();
            lastUpdate = now;
        }

        private void update(ApolloTeamMessageParser.Member member, long now) {
            if (member.hasLocation()) {
                if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
                    lastX = x;
                    lastY = y;
                    lastZ = z;
                } else {
                    lastX = member.x();
                    lastY = member.y();
                    lastZ = member.z();
                }
                x = member.x();
                y = member.y();
                z = member.z();
                world = member.world();
            }
            if (!member.name().isBlank()) {
                name = member.name();
            }
            color = member.color();
            previousUpdate = lastUpdate;
            lastUpdate = now;
        }

        private MemberFrame frame(long now) {
            double progress = previousUpdate == 0L || previousUpdate == lastUpdate
                    ? 1.0
                    : Mth.clamp((double) (now - lastUpdate) / (lastUpdate - previousUpdate), 0.0, 1.0);
            return new MemberFrame(uuid, name, world,
                    new Vec3(Mth.lerp(progress, lastX, x),
                            Mth.lerp(progress, lastY, y),
                            Mth.lerp(progress, lastZ, z)), color);
        }

        private MemberSnapshot snapshot() {
            return new MemberSnapshot(uuid, name, world, x, y, z, color);
        }
    }
}
