package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.legendwatch.CraftTracker;
import com.setsuna.util.legendwatch.LegendChatParser;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

/**
 * Tracks legendary-item crafts and kills announced in Hypskyblock-style chat,
 * so name tags / tab can annotate players with the legendaries they hold. The
 * parsing runs off inbound chat packets; the visible annotations are produced by
 * {@link com.setsuna.util.legendwatch.LegendSuffixUtil} where name tags render.
 */
public final class LegendWatch extends Module {

    public static final LegendWatch INSTANCE = new LegendWatch();

    public enum MidasDisplayMode {
        SharpnessEstimate,
        KillCount,
        Off
    }

    public final BooleanSetting icons = add(new BooleanSetting("Icons", true));
    public final BooleanSetting transparentIcons = add(new BooleanSetting("Transparent Icons", true)
            .visibleWhen(() -> icons.get()));
    public final BooleanSetting predicted = add(new BooleanSetting("Predicted", true));
    public final BooleanSetting vanillaNameTags = add(new BooleanSetting("Vanilla Name Tags", true));
    public final BooleanSetting customNameTags = add(new BooleanSetting("Name Tags", true));
    public final BooleanSetting tabList = add(new BooleanSetting("Tab List", true));
    public final BooleanSetting geraldTracking = add(new BooleanSetting("Gerald Tracking", true));
    public final EnumSetting<MidasDisplayMode> midasDisplay = add(new EnumSetting<>("Midas Display", MidasDisplayMode.SharpnessEstimate));
    public final ButtonSetting clear = add(new ButtonSetting("Clear Records", CraftTracker::onMatchReset));

    private LegendWatch() {
        super("Legend Watch", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        CraftTracker.onMatchReset();
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            if (!packet.overlay()) {
                LegendChatParser.handleMessage(packet.content().getString());
            }
            return;
        }

        if (event.getPacket() instanceof ClientboundDisguisedChatPacket packet) {
            LegendChatParser.handleMessage(packet.message().getString());
            return;
        }

        if (event.getPacket() instanceof ClientboundPlayerChatPacket packet) {
            String message = packet.unsignedContent() != null ? packet.unsignedContent().getString() : packet.body().content();
            LegendChatParser.handleMessage(message);
        }
    }

    public boolean iconsEnabled() {
        return icons.get();
    }

    public boolean transparentIconsEnabled() {
        return transparentIcons.get();
    }

    public boolean predictedEnabled() {
        return predicted.get();
    }

    public boolean vanillaNameTagsEnabled() {
        return vanillaNameTags.get();
    }

    public boolean customNameTagsEnabled() {
        return customNameTags.get();
    }

    public boolean tabListEnabled() {
        return tabList.get();
    }

    public boolean geraldTrackingEnabled() {
        return geraldTracking.get();
    }

    public MidasDisplayMode midasDisplayMode() {
        return midasDisplay.get();
    }
}
