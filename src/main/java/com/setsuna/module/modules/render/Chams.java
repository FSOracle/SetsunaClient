package com.setsuna.module.modules.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.setsuna.mixin.RenderSetupAccessor;
import com.setsuna.mixin.RenderTypeAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.awt.Color;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Minecraft 26.1.2 port of Flux's two-mode player Chams renderer. */
public final class Chams extends Module {

    public enum Mode {
        NORMAL,
        COLORED
    }

    private static final Identifier COLORED_FRAGMENT_SHADER =
            Identifier.fromNamespaceAndPath("setsuna", "core/chams");
    private static final DepthStencilState COLORED_OCCLUDED_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    private static final DepthStencilState COLORED_VISIBLE_DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
    private static final AtomicInteger NORMAL_PIPELINE_SEQUENCE = new AtomicInteger();

    private static final RenderPipeline COLORED_OCCLUDED_PIPELINE =
            createColoredPipeline("colored_occluded", false, COLORED_OCCLUDED_DEPTH);
    private static final RenderPipeline COLORED_VISIBLE_PIPELINE =
            createColoredPipeline("colored_visible", false, COLORED_VISIBLE_DEPTH);
    private static final RenderPipeline COLORED_FLAT_OCCLUDED_PIPELINE =
            createColoredPipeline("colored_flat_occluded", true, COLORED_OCCLUDED_DEPTH);
    private static final RenderPipeline COLORED_FLAT_VISIBLE_PIPELINE =
            createColoredPipeline("colored_flat_visible", true, COLORED_VISIBLE_DEPTH);

    private static final Function<Identifier, RenderType> COLORED_OCCLUDED_TYPE =
            coloredTypes("setsuna_chams_colored_occluded", COLORED_OCCLUDED_PIPELINE);
    private static final Function<Identifier, RenderType> COLORED_VISIBLE_TYPE =
            coloredTypes("setsuna_chams_colored_visible", COLORED_VISIBLE_PIPELINE);
    private static final Function<Identifier, RenderType> COLORED_FLAT_OCCLUDED_TYPE =
            coloredTypes("setsuna_chams_colored_flat_occluded", COLORED_FLAT_OCCLUDED_PIPELINE);
    private static final Function<Identifier, RenderType> COLORED_FLAT_VISIBLE_TYPE =
            coloredTypes("setsuna_chams_colored_flat_visible", COLORED_FLAT_VISIBLE_PIPELINE);

    public static final Chams INSTANCE = new Chams();

    public final ColorSetting chamsColours = add(new ColorSetting("Chams", Color.RED, false));
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.NORMAL));
    public final BooleanSetting flat = add(new BooleanSetting("Flat", false));
    public final BooleanSetting teamColor = add(new BooleanSetting("TeamColor", false));
    public final IntSetting alpha = add(new IntSetting("Alpha", 170, 0, 255, 1));

    private final Map<RenderType, RenderType> normalTypes =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private Chams() {
        super("Chams", Category.RENDER);
    }

    public boolean appliesTo(Entity entity) {
        return isEnabled() && entity instanceof Player && entity != mc.player;
    }

    public boolean isNormal() {
        return mode.is(Mode.NORMAL);
    }

    public boolean isColored() {
        return mode.is(Mode.COLORED);
    }

    /** Applies Flux's glPolygonOffset(1, -1100000) to an otherwise unchanged type. */
    public RenderType getNormalRenderType(RenderType original) {
        synchronized (normalTypes) {
            return normalTypes.computeIfAbsent(original, this::createNormalRenderType);
        }
    }

    public RenderType getColoredRenderType(Identifier texture, boolean flatShading, boolean occluded) {
        if (flatShading) {
            return (occluded ? COLORED_FLAT_OCCLUDED_TYPE : COLORED_FLAT_VISIBLE_TYPE).apply(texture);
        }
        return (occluded ? COLORED_OCCLUDED_TYPE : COLORED_VISIBLE_TYPE).apply(texture);
    }

    public int getColoredArgb(Entity entity) {
        int rgb = chamsColours.get().getRGB();
        if (teamColor.get()) {
            Integer resolved = resolveTeamColor(entity);
            if (resolved != null) {
                rgb = resolved;
            }
        }
        return (alpha.get() << 24) | (rgb & 0x00FFFFFF);
    }

    private RenderType createNormalRenderType(RenderType original) {
        RenderTypeAccessor typeAccessor = (RenderTypeAccessor) (Object) original;
        RenderSetup originalSetup = typeAccessor.getState();
        RenderSetupAccessor setupAccessor = (RenderSetupAccessor) (Object) originalSetup;
        RenderPipeline originalPipeline = setupAccessor.setsuna$getPipeline();
        DepthStencilState originalDepth = originalPipeline.getDepthStencilState();
        DepthStencilState fluxDepth = new DepthStencilState(
                originalDepth.depthTest(), originalDepth.writeDepth(), 1.0F, -1100000.0F);

        Identifier location = Identifier.fromNamespaceAndPath(
                "setsuna", "pipeline/chams/normal_" + NORMAL_PIPELINE_SEQUENCE.incrementAndGet());
        RenderPipeline pipeline = RenderPipelines.register(copyPipeline(originalPipeline, location, fluxDepth));
        RenderSetup setup = RenderSetupAccessor.setsuna$create(
                pipeline,
                setupAccessor.setsuna$getTextureBindings(),
                setupAccessor.setsuna$usesLightmap(),
                setupAccessor.setsuna$usesOverlay(),
                setupAccessor.setsuna$getLayeringTransform(),
                setupAccessor.setsuna$getOutputTarget(),
                setupAccessor.setsuna$getTextureTransform(),
                setupAccessor.getOutlineProperty(),
                setupAccessor.setsuna$affectsCrumbling(),
                setupAccessor.setsuna$sortsOnUpload(),
                setupAccessor.setsuna$getBufferSize());
        return RenderType.create("setsuna_chams_normal_" + typeAccessor.getName(), setup);
    }

    private static RenderPipeline createColoredPipeline(
            String name, boolean flatShading, DepthStencilState depthState) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation("pipeline/setsuna_chams_" + name)
                .withFragmentShader(COLORED_FRAGMENT_SHADER)
                .withShaderDefine("NO_OVERLAY")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withDepthStencilState(depthState);
        builder.withShaderDefine(flatShading ? "NO_CARDINAL_LIGHTING" : "PER_FACE_LIGHTING");
        return RenderPipelines.register(builder.build());
    }

    private static Function<Identifier, RenderType> coloredTypes(String name, RenderPipeline pipeline) {
        return Util.memoize(texture -> RenderType.create(name, RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .sortOnUpload()
                .createRenderSetup()));
    }

    private static RenderPipeline copyPipeline(
            RenderPipeline original, Identifier location, DepthStencilState depthState) {
        RenderPipeline.Snippet originalState = new RenderPipeline.Snippet(
                Optional.of(original.getVertexShader()),
                Optional.of(original.getFragmentShader()),
                Optional.of(original.getShaderDefines()),
                Optional.of(original.getSamplers()),
                Optional.of(original.getUniforms()),
                Optional.of(original.getColorTargetState()),
                Optional.of(original.getDepthStencilState()),
                Optional.of(original.getPolygonMode()),
                Optional.of(original.isCull()),
                Optional.of(original.getVertexFormat()),
                Optional.of(original.getVertexFormatMode()));
        return RenderPipeline.builder(originalState)
                .withLocation(location)
                .withDepthStencilState(depthState)
                .build();
    }

    private static Integer resolveTeamColor(Entity entity) {
        for (Component component : entity.getDisplayName().toFlatList()) {
            TextColor color = component.getStyle().getColor();
            if (color != null) {
                return color.getValue();
            }
        }

        Team team = entity.getTeam();
        if (team != null) {
            ChatFormatting formatting = team.getColor();
            if (formatting != null && formatting.isColor()) {
                return formatting.getColor();
            }
        }
        return null;
    }
}
