package com.setsuna.module.modules.render;

import com.setsuna.Setsuna;
import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.util.legendwatch.LegendSuffixUtil;
import com.setsuna.util.player.HealthDetectionUtils;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a name + health tag above every visible player, scaled by distance.
 * <p>
 * Positions are projected in the 3D pass and the tags themselves are drawn as a
 * 2D overlay through the shared Skija canvas. Team flags are resolved from the
 * private-use characters embedded in the tab-list display name.
 */
public final class NameTags extends Module {

    private static final Color NAME_COLOR = new Color(255, 255, 255, 235);
    private static final Color FRIEND_COLOR = new Color(20, 255, 20, 235);
    private static final Identifier TEAM_FLAGS = Identifier.fromNamespaceAndPath(
            Setsuna.MOD_ID, "textures/nametags/flags.png");
    private static final Identifier TEAM_FLAGS_ACCESSIBLE = Identifier.fromNamespaceAndPath(
            Setsuna.MOD_ID, "textures/nametags/flags_accessibility.png");
    private static final Identifier TEAM_FLAG_GENERIC = Identifier.fromNamespaceAndPath(
            Setsuna.MOD_ID, "textures/nametags/flag_generic.png");
    private static final char[] TAB_FLAG_CHARS = {
            '\uE237', '\uE238', '\uE239', '\uE23A', '\uE23B', '\uE23C', '\uE23D',
            '\uE23E', '\uE23F', '\uE240', '\uE241', '\uE342', '\uE38D'
    };
    private static final char[] TAB_ACCESSIBLE_FLAG_CHARS = {
            '\uE391', '\uE392', '\uE393', '\uE394', '\uE395', '\uE396', '\uE397',
            '\uE398', '\uE399', '\uE39A', '\uE39B', '\uE39C', '\uE39D'
    };
    private static final char TAB_GENERIC_FLAG_CHAR = '\uE405';
    private static final int FLAG_ROWS = 13;
    private static final float FLAG_SIZE = 10.0F;
    private static final float FLAG_GAP = 2.0F;
    private static final float TAG_HEIGHT = 11.0F;
    private static final float TAG_OFFSET = 4.0F;
    private static final Paint FLAG_PAINT = new Paint().setAntiAlias(false);

    public static final NameTags INSTANCE = new NameTags();

    public final DoubleSetting range = add(new DoubleSetting("Range", 64.0, 4.0, 128.0, 1.0));
    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.3, 3.0, 0.1));
    public final DoubleSetting heightOffset = add(new DoubleSetting("Height Offset", 0.15, -0.5, 1.0, 0.05));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color", new Color(0, 0, 0, 130)));
    public final BooleanSetting vanillaNameTags = add(new BooleanSetting("Vanilla Name Tags", false));
    public final BooleanSetting showSelf = add(new BooleanSetting("Show Self", false));
    public final BooleanSetting teamFlags = add(new BooleanSetting("Team Flags", true));
    public final BooleanSetting accessibleFlags = add(new BooleanSetting("Accessible Flags", false))
            .visibleWhen(teamFlags::get);

    private final List<TagDrawData> drawList = new ArrayList<>();

    private record TagDrawData(String name, int nameColor, String health, int healthColor, TabFlag flag,
                               float centerX, float topY, float scale) {
    }

    private NameTags() {
        super("Name Tags", Category.RENDER);
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            return;
        }
        drawList.clear();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistanceSq = range.get() * range.get();
        float baseScale = scale.get().floatValue();
        double guiScale = mc.getWindow().getGuiScale();

        for (Player target : mc.level.players()) {
            if (!target.isAlive() || target.isSpectator()) {
                continue;
            }
            if (mc.options.getCameraType().isFirstPerson() && target == mc.player) {
                continue;
            }
            if (target == mc.player && !showSelf.get()) {
                continue;
            }
            if (mc.player.distanceToSqr(target) > maxDistanceSq) {
                continue;
            }

            Vector4d projected = WorldToScreen.getEntityPositionsOn2D(target, partialTick);
            if (projected == null) {
                continue;
            }
            float projectedHeight = (float) Math.max(1.0, projected.w - projected.y);
            float renderScale = baseScale * Mth.clamp(projectedHeight / 36.0f, 0.55f, 2.2f);

            Vec3 current = WorldToScreen.interpolate(target, partialTick);
            Vector3f head = WorldToScreen.getWorldPositionToScreen(
                    current.add(0.0, heightOffset.get() + target.getEyeHeight(), 0.0));
            if (head.z > 1.0f || head.z < 0.0f) {
                continue;
            }

            String username = target.getName().getString();
            String name = username + LegendSuffixUtil.getPlainSuffix(username);
            float health = getDisplayHealth(target);
            String healthText = String.format(Locale.ROOT, "[%.1f HP]", health);
            int healthColor = (health < 10.0f ? new Color(255, 214, 64, 240) : new Color(120, 255, 120, 240)).getRGB();
            boolean friend = FriendManager.INSTANCE.isFriend(username);
            int nameColor = TeamColorUtils.getNameColor(target, friend ? FRIEND_COLOR : NAME_COLOR).getRGB();

            drawList.add(new TagDrawData(name, nameColor, healthText, healthColor, getTabFlag(target),
                    (float) (head.x / guiScale), (float) (head.y / guiScale), renderScale));
        }
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (drawList.isEmpty()) {
            return;
        }
        Canvas canvas = event.canvas();
        int bg = backgroundColor.argb();

        for (TagDrawData d : drawList) {
            canvas.save();
            canvas.translate(d.centerX(), d.topY());
            canvas.scale(d.scale(), d.scale());

            float pad = 3.0f;
            float gap = 4.0f;
            float boxHeight = TAG_HEIGHT;
            float nameWidth = SkijaUi.textWidth(d.name());
            float healthWidth = SkijaUi.textWidth(d.health());
            float flagWidth = d.flag() == null ? 0.0F : FLAG_SIZE + FLAG_GAP;
            float contentWidth = flagWidth + nameWidth + gap + healthWidth;
            float boxWidth = contentWidth + pad * 2.0f;
            float left = -boxWidth / 2.0f;
            float top = -boxHeight - TAG_OFFSET;
            float contentX = left + pad;

            SkijaUi.fill(canvas, left, top, boxWidth, boxHeight, bg);
            if (d.flag() != null) {
                drawFlag(canvas, d.flag(), contentX, top + (boxHeight - FLAG_SIZE) * 0.5F, FLAG_SIZE);
                contentX += FLAG_SIZE + FLAG_GAP;
            }
            SkijaUi.text(canvas, d.name(), contentX, top, boxHeight, d.nameColor());
            SkijaUi.text(canvas, d.health(), contentX + nameWidth + gap, top, boxHeight, d.healthColor());

            canvas.restore();
        }
    }

    private float getDisplayHealth(Player player) {
        return HealthDetectionUtils.getHealth(player);
    }

    private TabFlag getTabFlag(Player player) {
        if (!teamFlags.get() || mc.getConnection() == null) {
            return null;
        }

        TabFlag flag = getTabFlag(mc.getConnection().getPlayerInfo(player.getUUID()));
        if (flag != null) {
            return flag;
        }

        flag = getTabFlag(mc.getConnection().getPlayerInfoIgnoreCase(player.getGameProfile().name()));
        return flag != null ? flag : getTabFlag(player.getDisplayName());
    }

    private TabFlag getTabFlag(PlayerInfo info) {
        if (info == null) {
            return null;
        }

        TabFlag flag = getTabFlag(info.getTabListDisplayName());
        return flag != null ? flag : getTabFlag(Component.literal(info.getProfile().name()));
    }

    private TabFlag getTabFlag(Component component) {
        if (component == null) {
            return null;
        }

        String text = component.getString();
        for (int i = 0; i < TAB_FLAG_CHARS.length; i++) {
            if (text.indexOf(TAB_FLAG_CHARS[i]) >= 0) {
                return new TabFlag(accessibleFlags.get() ? TEAM_FLAGS_ACCESSIBLE : TEAM_FLAGS, i);
            }
        }
        for (int i = 0; i < TAB_ACCESSIBLE_FLAG_CHARS.length; i++) {
            if (text.indexOf(TAB_ACCESSIBLE_FLAG_CHARS[i]) >= 0) {
                return new TabFlag(TEAM_FLAGS_ACCESSIBLE, i);
            }
        }
        return text.indexOf(TAB_GENERIC_FLAG_CHAR) >= 0 ? new TabFlag(TEAM_FLAG_GENERIC, 0) : null;
    }

    private void drawFlag(Canvas canvas, TabFlag flag, float x, float y, float size) {
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(flag.texture())) {
            if (borrowed == null) {
                return;
            }

            float textureWidth = borrowed.image().getWidth();
            float textureHeight = borrowed.image().getHeight();
            float rowHeight = flag.texture().equals(TEAM_FLAG_GENERIC)
                    ? textureHeight
                    : textureHeight / FLAG_ROWS;
            float sourceTop = flag.index() * rowHeight;
            Rect source = Rect.makeLTRB(0.0F, sourceTop, textureWidth, sourceTop + rowHeight);
            Rect destination = Rect.makeXYWH(x, y, size, size);
            canvas.drawImageRect(borrowed.image(), source, destination,
                    SamplingMode.DEFAULT, FLAG_PAINT, true);
        } catch (Throwable ignored) {
            // Keep the name tag visible if a resource pack removes or replaces the atlas.
        }
    }

    private record TabFlag(Identifier texture, int index) {
    }

}
