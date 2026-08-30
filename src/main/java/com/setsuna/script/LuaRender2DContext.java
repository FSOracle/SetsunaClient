package com.setsuna.script;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.render.SkijaUi;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.types.Rect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Per-frame Skija drawing surface passed to a module's render2d callback. */
final class LuaRender2DContext {

    private static final int MAX_DRAWS = 4096;
    private static final int MAX_CLIP_DEPTH = 32;

    private final Render2DEvent event;
    private final LuaTable api = new LuaTable();
    private int draws;
    private int clipDepth;

    LuaRender2DContext(Render2DEvent event) {
        this.event = event;
        install();
    }

    LuaTable api() {
        return api;
    }

    private void install() {
        api.set("width", event.width());
        api.set("height", event.height());
        api.set("scale", event.guiScale());
        LuaTable cursor = LuaInputApi.cursorSnapshot();
        api.set("mouse_x", cursor.get("x"));
        api.set("mouse_y", cursor.get("y"));
        api.set("mouse_grabbed", cursor.get("grabbed"));
        api.set("rect", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Bounds bounds = bounds(args, first);
            draw();
            SkijaUi.fill(event.canvas(), bounds.x, bounds.y, bounds.width, bounds.height,
                    LuaApiSupport.color(args.arg(first + 4)));
            return LuaValue.NONE;
        }));
        api.set("rounded_rect", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Bounds bounds = bounds(args, first);
            float radius = LuaApiSupport.boundedFloat(args.arg(first + 4), "radius", 0.0F, 4096.0F);
            draw();
            SkijaUi.rounded(event.canvas(), bounds.x, bounds.y, bounds.width, bounds.height,
                    radius, LuaApiSupport.color(args.arg(first + 5)));
            return LuaValue.NONE;
        }));
        api.set("outline_rect", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Bounds bounds = bounds(args, first);
            int color = LuaApiSupport.color(args.arg(first + 4));
            float thickness = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 5), "thickness", 1.0F, 0.1F, 128.0F);
            float radius = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 6), "radius", 0.0F, 0.0F, 4096.0F);
            draw();
            SkijaUi.outline(event.canvas(), bounds.x, bounds.y, bounds.width, bounds.height,
                    radius, thickness, color);
            return LuaValue.NONE;
        }));
        api.set("gradient_rect", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Bounds bounds = bounds(args, first);
            int start = LuaApiSupport.color(args.arg(first + 4));
            int end = LuaApiSupport.color(args.arg(first + 5));
            boolean vertical = args.arg(first + 6).optboolean(true);
            float radius = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 7), "radius", 0.0F, 0.0F, 4096.0F);
            draw();
            SkijaUi.gradient(event.canvas(), bounds.x, bounds.y, bounds.width, bounds.height,
                    start, end, vertical, radius);
            return LuaValue.NONE;
        }));
        api.set("circle", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            float centerX = coordinate(args.arg(first), "center_x");
            float centerY = coordinate(args.arg(first + 1), "center_y");
            float radius = LuaApiSupport.boundedFloat(
                    args.arg(first + 2), "radius", 0.0F, 100_000.0F);
            int color = LuaApiSupport.color(args.arg(first + 3));
            draw();
            SkijaUi.rounded(event.canvas(), centerX - radius, centerY - radius,
                    radius * 2.0F, radius * 2.0F, radius, color);
            return LuaValue.NONE;
        }));
        api.set("circle_outline", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            float centerX = coordinate(args.arg(first), "center_x");
            float centerY = coordinate(args.arg(first + 1), "center_y");
            float radius = LuaApiSupport.boundedFloat(
                    args.arg(first + 2), "radius", 0.0F, 100_000.0F);
            int color = LuaApiSupport.color(args.arg(first + 3));
            float thickness = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 4), "thickness", 1.0F, 0.1F, 128.0F);
            draw();
            SkijaUi.outline(event.canvas(), centerX - radius, centerY - radius,
                    radius * 2.0F, radius * 2.0F, radius, thickness, color);
            return LuaValue.NONE;
        }));
        api.set("line", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            float x1 = coordinate(args.arg(first), "x1");
            float y1 = coordinate(args.arg(first + 1), "y1");
            float x2 = coordinate(args.arg(first + 2), "x2");
            float y2 = coordinate(args.arg(first + 3), "y2");
            int color = LuaApiSupport.color(args.arg(first + 4));
            float thickness = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 5), "thickness", 1.0F, 0.1F, 128.0F);
            draw();
            SkijaUi.line(event.canvas(), x1, y1, x2, y2, thickness, color);
            return LuaValue.NONE;
        }));
        api.set("text", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            String text = LuaApiSupport.boundedString(args.arg(first), "text", 4096);
            float x = coordinate(args.arg(first + 1), "x");
            float y = coordinate(args.arg(first + 2), "y");
            int color = LuaApiSupport.color(args.arg(first + 3));
            float size = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 4), "size", 14.0F, 1.0F, 256.0F);
            boolean shadow = args.arg(first + 5).optboolean(true);
            draw();
            float height = size * 1.25F;
            if (shadow) SkijaUi.textShadow(event.canvas(), text, x, y, height, color, size);
            else SkijaUi.text(event.canvas(), text, x, y, height, color, size);
            return LuaValue.NONE;
        }));
        api.set("text_width", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            String text = LuaApiSupport.boundedString(args.arg(first), "text", 4096);
            float size = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 1), "size", 14.0F, 1.0F, 256.0F);
            return LuaValue.valueOf(SkijaUi.textWidth(text, size));
        }));
        api.set("bold_text", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            String text = LuaApiSupport.boundedString(args.arg(first), "text", 4096);
            float x = coordinate(args.arg(first + 1), "x");
            float y = coordinate(args.arg(first + 2), "y");
            int color = LuaApiSupport.color(args.arg(first + 3));
            float size = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 4), "size", 14.0F, 1.0F, 256.0F);
            boolean shadow = args.arg(first + 5).optboolean(true);
            draw();
            float height = size * 1.25F;
            if (shadow) SkijaUi.boldTextShadow(event.canvas(), text, x, y, height, color, size);
            else SkijaUi.boldText(event.canvas(), text, x, y, height, color, size);
            return LuaValue.NONE;
        }));
        api.set("bold_text_width", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            String text = LuaApiSupport.boundedString(args.arg(first), "text", 4096);
            float size = LuaApiSupport.optionalBoundedFloat(
                    args.arg(first + 1), "size", 14.0F, 1.0F, 256.0F);
            return LuaValue.valueOf(SkijaUi.boldTextWidth(text, size));
        }));
        api.set("clip", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Bounds bounds = bounds(args, first);
            LuaValue callback = args.arg(first + 4).checkfunction();
            if (++clipDepth > MAX_CLIP_DEPTH) {
                clipDepth--;
                throw new LuaError("2D clip nesting limit exceeded");
            }
            event.canvas().save();
            try {
                event.canvas().clipRect(Rect.makeXYWH(
                        bounds.x, bounds.y, bounds.width, bounds.height),
                        ClipMode.INTERSECT, true);
                callback.call();
            } finally {
                event.canvas().restore();
                clipDepth--;
            }
            return LuaValue.NONE;
        }));
        api.set("world_to_screen", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            double x = LuaApiSupport.finiteDouble(args.arg(first), "x");
            double y = LuaApiSupport.finiteDouble(args.arg(first + 1), "y");
            double z = LuaApiSupport.finiteDouble(args.arg(first + 2), "z");
            return project(new Vec3(x, y, z));
        }));
        api.set("entity_bounds", LuaApiSupport.method(api, (args, first) -> {
            requireContext();
            Entity entity = LuaEntityApi.find(args.arg(first).checkint());
            if (entity == null) return LuaValue.NIL;
            double padding = LuaApiSupport.boundedDouble(
                    args.arg(first + 1).isnil() ? LuaValue.ZERO : args.arg(first + 1),
                    "padding", 0.0, 16.0);
            return project(entity.getBoundingBox().inflate(padding));
        }));
    }

    private LuaValue project(Vec3 position) {
        Vector3f projected = WorldToScreen.getWorldPositionToScreen(position);
        if (!validProjection(projected)) return LuaValue.NIL;
        float screenX = (float) (projected.x / event.guiScale());
        float screenY = (float) (projected.y / event.guiScale());
        LuaTable result = new LuaTable();
        result.set("x", screenX);
        result.set("y", screenY);
        result.set("depth", projected.z);
        result.set("visible", LuaValue.valueOf(screenX >= 0.0F && screenY >= 0.0F
                && screenX <= event.width() && screenY <= event.height()));
        return result;
    }

    private LuaValue project(AABB box) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vector3f projected = WorldToScreen.getWorldPositionToScreen(new Vec3(x, y, z));
                    if (!validProjection(projected)) return LuaValue.NIL;
                    float screenX = (float) (projected.x / event.guiScale());
                    float screenY = (float) (projected.y / event.guiScale());
                    minX = Math.min(minX, screenX);
                    minY = Math.min(minY, screenY);
                    maxX = Math.max(maxX, screenX);
                    maxY = Math.max(maxY, screenY);
                }
            }
        }
        LuaTable result = new LuaTable();
        result.set("x", minX);
        result.set("y", minY);
        result.set("width", maxX - minX);
        result.set("height", maxY - minY);
        result.set("min_x", minX);
        result.set("min_y", minY);
        result.set("max_x", maxX);
        result.set("max_y", maxY);
        result.set("visible", LuaValue.valueOf(
                maxX >= 0.0F && maxY >= 0.0F
                        && minX <= event.width() && minY <= event.height()));
        return result;
    }

    private static boolean validProjection(Vector3f projected) {
        return Float.isFinite(projected.x) && Float.isFinite(projected.y)
                && Float.isFinite(projected.z)
                && projected.z >= 0.0F && projected.z <= 1.0F;
    }

    private void draw() {
        requireContext();
        if (++draws > MAX_DRAWS) {
            throw new org.luaj.vm2.LuaError("2D draw limit exceeded for one callback");
        }
    }

    private static Bounds bounds(org.luaj.vm2.Varargs args, int first) {
        float x = coordinate(args.arg(first), "x");
        float y = coordinate(args.arg(first + 1), "y");
        float width = LuaApiSupport.boundedFloat(args.arg(first + 2), "width", -100_000.0F, 100_000.0F);
        float height = LuaApiSupport.boundedFloat(args.arg(first + 3), "height", -100_000.0F, 100_000.0F);
        if (width < 0.0F) {
            x += width;
            width = -width;
        }
        if (height < 0.0F) {
            y += height;
            height = -height;
        }
        return new Bounds(x, y, width, height);
    }

    private static float coordinate(LuaValue value, String name) {
        return LuaApiSupport.boundedFloat(value, name, -1_000_000.0F, 1_000_000.0F);
    }

    private static void requireContext() {
        LuaExecutionGuard.requireEvent("render2d");
    }

    private record Bounds(float x, float y, float width, float height) {
    }
}
