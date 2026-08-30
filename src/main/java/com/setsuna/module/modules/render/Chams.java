package com.setsuna.module.modules.render;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.function.Function;

/**
 * Renders entities without depth testing (see-through models) and/or with a
 * coloured glow outline. The see-through effect swaps the entity render type for
 * a depth-ignoring pipeline; the glow sets the render state's outline colour.
 */
public final class Chams extends Module {

    public static final Chams INSTANCE = new Chams();

    public final BooleanSetting noDepth = add(new BooleanSetting("No Depth", true));

    public final BooleanSetting glow = add(new BooleanSetting("Glow", true));
    public final BooleanSetting self = add(new BooleanSetting("Self", true).visibleWhen(() -> glow.get()));
    public final BooleanSetting player = add(new BooleanSetting("Player", true).visibleWhen(() -> glow.get()));
    public final BooleanSetting mob = add(new BooleanSetting("Mob", true).visibleWhen(() -> glow.get()));
    public final BooleanSetting animal = add(new BooleanSetting("Animal", true).visibleWhen(() -> glow.get()));

    private Chams() {
        super("Chams", Category.RENDER);
    }

    private static final RenderPipeline ENTITY_CHAMS_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/setsuna_entity_chams")
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1100000.0f))
            .build();

    private static final Function<Identifier, RenderType> ENTITY_CHAMS_TYPE = Util.memoize(
            texture -> RenderType.create("setsuna_entity_chams", RenderSetup.builder(ENTITY_CHAMS_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    public RenderType getRenderType(Identifier texture) {
        return ENTITY_CHAMS_TYPE.apply(texture);
    }

    public boolean shouldRenderGlow(Entity entity) {
        if (!glow.get()) {
            return false;
        }
        if (entity == mc.player) {
            return self.get();
        }
        if (entity instanceof Player) {
            return player.get();
        }
        if (entity instanceof Animal) {
            return animal.get();
        }
        if (entity instanceof Monster) {
            return mob.get();
        }
        return false;
    }

    public int getGlowColor(Entity entity) {
        if (entity == mc.player) {
            return getRainbowColor();
        }
        if (entity instanceof Monster) {
            return Color.RED.getRGB();
        }
        return 0xFFFFFFFF;
    }

    private int getRainbowColor() {
        float hue = (System.currentTimeMillis() % 3000) / 3000.0f;
        return Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }
}
