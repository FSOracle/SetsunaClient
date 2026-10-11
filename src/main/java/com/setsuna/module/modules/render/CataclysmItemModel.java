package com.setsuna.module.modules.render;

import com.google.gson.JsonObject;
import com.setsuna.render.CataclysmModelDefinition;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.util.HashMap;
import java.util.Map;

/** Pose changes are applied by the deferred model renderer using the captured frame. */
final class CataclysmItemModel extends Model<CataclysmItemModel.Frame> {
    record Frame(float age, float pull, boolean using) { }

    private final String id;
    private final Map<String, ModelPart> bones = new HashMap<>();

    CataclysmItemModel(String id, CataclysmModelDefinition.Baked baked, JsonObject source) {
        super(baked.root(), RenderTypes::entityCutout);
        this.id = id;
        source.getAsJsonArray("bones").forEach(element -> {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (!name.equals("setsuna_geometry")) {
                bones.put(name, baked.bones().get(bone.get("id").getAsString()));
            }
        });
    }

    @Override
    public void setupAnim(Frame frame) {
        resetPose();
        if (frame == null) frame = new Frame(0, 0, false);
        switch (id) {
            case "cursed_bow", "wrath_of_the_desert" -> animateBow(frame);
            case "meat_shredder" -> {
                part("core").z += (float) Math.cos(frame.age) + 1;
                part("core2").z += (float) Math.cos(frame.age + Math.PI) + 1;
                part("saw").xRot += frame.age * 0.75F;
            }
            case "laser_gatling" -> {
                part("core").y += (float) Math.cos(frame.age) + 1;
                part("core2").y += (float) Math.cos(frame.age + Math.PI) + 1;
                float open = frame.using ? frame.age : 0;
                part("gatling").zRot -= open * 0.75F;
                part("root").z += (float) Math.cos(open * 2) + 1;
            }
            default -> { }
        }
    }

    private void animateBow(Frame frame) {
        float pull = Math.clamp(frame.pull, 0, 1);
        part("bow_string").z += pull * 9;
        part("string2").xRot += (float) Math.toRadians(pull * 25);
        part("string").xRot += (float) Math.toRadians(pull * -25);
        part("arm1").xRot += (float) Math.toRadians(pull * -15);
        part("arm2").xRot += (float) Math.toRadians(pull * 15);
        for (int index = 1; index <= 3; index++) scaleGeometry("arrow_pivot" + index, pull * 1.2F);
        if (id.equals("wrath_of_the_desert")) {
            for (String prefix : new String[]{"first", "second", "third"}) {
                ModelPart outer = part(prefix + "_sand3");
                ModelPart middle = part(prefix + "_sand2");
                ModelPart inner = part(prefix + "_sand1");
                outer.zRot += frame.age * 0.7F;
                middle.zRot += -outer.zRot + frame.age * 0.5F;
                inner.zRot += -outer.zRot - middle.zRot + frame.age * 0.3F;
                for (int index = 1; index <= 3; index++) {
                    scaleGeometry(prefix + "_sand" + index, pull * 1.2F);
                }
            }
        }
    }

    ModelPart part(String name) {
        ModelPart part = bones.get(name);
        if (part == null) throw new IllegalArgumentException("Missing " + id + " bone " + name);
        return part;
    }

    private void scaleGeometry(String name, float scale) {
        ModelPart geometry = part(name).getChild("setsuna_geometry");
        geometry.visible = scale > 0.0001F;
        geometry.xScale = geometry.yScale = geometry.zScale = Math.max(0.0001F, scale);
    }
}
