package com.setsuna.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {

    @Accessor
    RenderSetup.OutlineProperty getOutlineProperty();

    @Accessor("pipeline")
    RenderPipeline setsuna$getPipeline();

    @Accessor("textures")
    Map<String, ?> setsuna$getTextureBindings();

    @Accessor("useLightmap")
    boolean setsuna$usesLightmap();

    @Accessor("useOverlay")
    boolean setsuna$usesOverlay();

    @Accessor("layeringTransform")
    LayeringTransform setsuna$getLayeringTransform();

    @Accessor("outputTarget")
    OutputTarget setsuna$getOutputTarget();

    @Accessor("textureTransform")
    TextureTransform setsuna$getTextureTransform();

    @Accessor("affectsCrumbling")
    boolean setsuna$affectsCrumbling();

    @Accessor("sortOnUpload")
    boolean setsuna$sortsOnUpload();

    @Accessor("bufferSize")
    int setsuna$getBufferSize();

    @Invoker("<init>")
    static RenderSetup setsuna$create(
            RenderPipeline pipeline,
            Map<String, ?> textures,
            boolean useLightmap,
            boolean useOverlay,
            LayeringTransform layeringTransform,
            OutputTarget outputTarget,
            TextureTransform textureTransform,
            RenderSetup.OutlineProperty outlineProperty,
            boolean affectsCrumbling,
            boolean sortOnUpload,
            int bufferSize) {
        throw new AssertionError();
    }
}
