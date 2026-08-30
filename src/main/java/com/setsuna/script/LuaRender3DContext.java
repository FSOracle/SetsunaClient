package com.setsuna.script;

import com.setsuna.Setsuna;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.util.render.Render3DUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Per-frame world-space drawing surface passed to a module's render3d callback. */
final class LuaRender3DContext {

    private static final int MAX_DRAWS = 2048;
    private static final double MAX_BOX_SIDE = 512.0;

    private final Render3DEvent event;
    private final LuaTable api = new LuaTable();
    private int draws;

    LuaRender3DContext(Render3DEvent event) {
        this.event = event;
        install();
    }

    LuaTable api() {
        return api;
    }

    private void install() {
        if (Setsuna.mc().gameRenderer != null) {
            var camera = Setsuna.mc().gameRenderer.getMainCamera();
            LuaTable snapshot = new LuaTable();
            snapshot.set("x", camera.position().x);
            snapshot.set("y", camera.position().y);
            snapshot.set("z", camera.position().z);
            snapshot.set("yaw", camera.yRot());
            snapshot.set("pitch", camera.xRot());
            api.set("camera", snapshot);
        } else {
            api.set("camera", LuaValue.NIL);
        }
        api.set("filled_box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            AABB box = box(args, first);
            int color = LuaApiSupport.color(args.arg(first + 6));
            draw();
            Render3DUtils.drawFilledBox(box, color);
            return LuaValue.NONE;
        }));
        api.set("outline_box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            AABB box = box(args, first);
            int color = LuaApiSupport.color(args.arg(first + 6));
            float thickness = thickness(args.arg(first + 7));
            draw();
            Render3DUtils.drawOutlineBox(event.getPoseStack(), box, color, thickness);
            return LuaValue.NONE;
        }));
        api.set("box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            AABB box = box(args, first);
            LuaValue fill = args.arg(first + 6);
            LuaValue outline = args.arg(first + 7);
            float thickness = thickness(args.arg(first + 8));
            draw();
            drawBox(box, fill, outline, thickness);
            return LuaValue.NONE;
        }));
        api.set("gradient_box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            AABB box = box(args, first);
            int bottom = LuaApiSupport.color(args.arg(first + 6));
            int top = LuaApiSupport.color(args.arg(first + 7));
            LuaValue outline = args.arg(first + 8);
            float thickness = thickness(args.arg(first + 9));
            draw();
            Render3DUtils.drawFilledFadeBox(box, bottom, top);
            if (!outline.isnil()) {
                Render3DUtils.drawOutlineBox(event.getPoseStack(), box,
                        LuaApiSupport.color(outline), thickness);
            }
            return LuaValue.NONE;
        }));
        api.set("block_box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            int x = args.arg(first).checkint();
            int y = args.arg(first + 1).checkint();
            int z = args.arg(first + 2).checkint();
            validateBlockPosition(x, y, z);
            LuaValue fill = args.arg(first + 3);
            LuaValue outline = args.arg(first + 4);
            float thickness = thickness(args.arg(first + 5));
            draw();
            drawBox(new AABB(new BlockPos(x, y, z)), fill, outline, thickness);
            return LuaValue.NONE;
        }));
        api.set("entity_box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Entity entity = LuaEntityApi.find(args.arg(first).checkint());
            if (entity == null) return LuaValue.FALSE;
            LuaValue fill = args.arg(first + 1);
            LuaValue outline = args.arg(first + 2);
            float thickness = thickness(args.arg(first + 3));
            draw();
            drawBox(entity.getBoundingBox(), fill, outline, thickness);
            return LuaValue.TRUE;
        }));
        api.set("entity_gradient_box", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Entity entity = LuaEntityApi.find(args.arg(first).checkint());
            if (entity == null) return LuaValue.FALSE;
            int bottom = LuaApiSupport.color(args.arg(first + 1));
            int top = LuaApiSupport.color(args.arg(first + 2));
            LuaValue outline = args.arg(first + 3);
            float thickness = thickness(args.arg(first + 4));
            draw();
            Render3DUtils.drawFilledFadeBox(entity.getBoundingBox(), bottom, top);
            if (!outline.isnil()) {
                Render3DUtils.drawOutlineBox(event.getPoseStack(), entity.getBoundingBox(),
                        LuaApiSupport.color(outline), thickness);
            }
            return LuaValue.TRUE;
        }));
        api.set("line", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Vec3 from = vector(args, first);
            Vec3 to = vector(args, first + 3);
            int color = LuaApiSupport.color(args.arg(first + 6));
            float thickness = thickness(args.arg(first + 7));
            draw();
            Render3DUtils.drawLine(event.getPoseStack(), from, to, color, thickness);
            return LuaValue.NONE;
        }));
        api.set("tracer", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Entity entity = LuaEntityApi.find(args.arg(first).checkint());
            if (entity == null || Setsuna.mc().gameRenderer == null) return LuaValue.FALSE;
            int color = LuaApiSupport.color(args.arg(first + 1));
            float thickness = thickness(args.arg(first + 2));
            Vec3 from = Setsuna.mc().gameRenderer.getMainCamera().position();
            Vec3 to = entity.getBoundingBox().getCenter();
            draw();
            Render3DUtils.drawLine(event.getPoseStack(), from, to, color, thickness);
            return LuaValue.TRUE;
        }));
    }

    private void drawBox(AABB box, LuaValue fill, LuaValue outline, float thickness) {
        if (fill.isnil() && outline.isnil()) {
            throw new LuaError("box requires a fill color, an outline color, or both");
        }
        if (!fill.isnil()) Render3DUtils.drawFilledBox(box, LuaApiSupport.color(fill));
        if (!outline.isnil()) {
            Render3DUtils.drawOutlineBox(event.getPoseStack(), box,
                    LuaApiSupport.color(outline), thickness);
        }
    }

    private void draw() {
        requireContext();
        if (++draws > MAX_DRAWS) {
            throw new LuaError("3D draw limit exceeded for one callback");
        }
    }

    private static AABB box(Varargs args, int first) {
        double x1 = worldCoordinate(args.arg(first), "min_x");
        double y1 = worldCoordinate(args.arg(first + 1), "min_y");
        double z1 = worldCoordinate(args.arg(first + 2), "min_z");
        double x2 = worldCoordinate(args.arg(first + 3), "max_x");
        double y2 = worldCoordinate(args.arg(first + 4), "max_y");
        double z2 = worldCoordinate(args.arg(first + 5), "max_z");
        if (Math.abs(x2 - x1) > MAX_BOX_SIDE || Math.abs(y2 - y1) > MAX_BOX_SIDE
                || Math.abs(z2 - z1) > MAX_BOX_SIDE) {
            throw new LuaError("box sides may not exceed " + (int) MAX_BOX_SIDE + " blocks");
        }
        return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    private static Vec3 vector(Varargs args, int first) {
        return new Vec3(
                worldCoordinate(args.arg(first), "x"),
                worldCoordinate(args.arg(first + 1), "y"),
                worldCoordinate(args.arg(first + 2), "z"));
    }

    private static float thickness(LuaValue value) {
        return value.isnil() ? 1.5F
                : LuaApiSupport.boundedFloat(value, "thickness", 0.1F, 16.0F);
    }

    private static void requireContext() {
        LuaExecutionGuard.requireEvent("render3d");
    }

    private static double worldCoordinate(LuaValue value, String name) {
        double coordinate = LuaApiSupport.finiteDouble(value, name);
        if (Math.abs(coordinate) > 30_000_000.0) {
            throw new LuaError(name + " is outside the supported world range");
        }
        return coordinate;
    }

    private static void validateBlockPosition(int x, int y, int z) {
        if (Math.abs((long) x) > 30_000_000L || Math.abs((long) z) > 30_000_000L
                || y < -4096 || y > 4096) {
            throw new LuaError("block position is outside the supported world range");
        }
    }
}
