package com.setsuna.script;

import com.setsuna.Setsuna;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Locale;

/** Converts Minecraft entities into immutable, primitive-only Lua snapshots. */
final class LuaEntityApi {

    private LuaEntityApi() {
    }

    static Entity find(int id) {
        return Setsuna.mc().level == null ? null : Setsuna.mc().level.getEntity(id);
    }

    static LuaValue snapshot(Entity entity) {
        if (entity == null) return LuaValue.NIL;

        LuaTable result = new LuaTable();
        result.set("id", entity.getId());
        result.set("uuid", entity.getUUID().toString());
        result.set("name", bounded(entity.getName().getString(), 256));
        result.set("type", typeId(entity));
        result.set("x", entity.getX());
        result.set("y", entity.getY());
        result.set("z", entity.getZ());
        result.set("yaw", entity.getYRot());
        result.set("pitch", entity.getXRot());
        result.set("width", entity.getBbWidth());
        result.set("height", entity.getBbHeight());
        result.set("alive", LuaValue.valueOf(entity.isAlive()));
        result.set("invisible", LuaValue.valueOf(entity.isInvisible()));
        result.set("on_ground", LuaValue.valueOf(entity.onGround()));
        result.set("player", LuaValue.valueOf(entity instanceof Player));
        result.set("living", LuaValue.valueOf(entity instanceof LivingEntity));
        result.set("mob", LuaValue.valueOf(entity instanceof Monster));
        result.set("animal", LuaValue.valueOf(entity instanceof Animal));

        Vec3 velocity = entity.getDeltaMovement();
        result.set("velocity", vector(velocity.x, velocity.y, velocity.z));
        result.set("box", box(entity.getBoundingBox()));

        if (entity instanceof LivingEntity living) {
            result.set("health", living.getHealth());
            result.set("max_health", living.getMaxHealth());
            result.set("armor", living.getArmorValue());
        }

        Player local = Setsuna.mc().player;
        if (local != null) {
            result.set("local_player", LuaValue.valueOf(entity == local));
            result.set("distance", Math.sqrt(local.distanceToSqr(entity)));
            result.set("visible", LuaValue.valueOf(entity == local || local.hasLineOfSight(entity)));
        } else {
            result.set("local_player", LuaValue.FALSE);
            result.set("distance", LuaValue.NIL);
            result.set("visible", LuaValue.FALSE);
        }
        return result;
    }

    static String typeId(Entity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? "unknown" : id.toString();
    }

    static boolean matches(Entity entity, String rawFilter) {
        String filter = rawFilter == null ? "all" : rawFilter.trim().toLowerCase(Locale.ROOT);
        return switch (filter) {
            case "", "all" -> true;
            case "player", "players" -> entity instanceof Player;
            case "living" -> entity instanceof LivingEntity;
            case "mob", "mobs", "monster", "monsters" -> entity instanceof Monster;
            case "animal", "animals" -> entity instanceof Animal;
            default -> {
                String type = typeId(entity).toLowerCase(Locale.ROOT);
                yield type.equals(filter) || type.equals("minecraft:" + filter);
            }
        };
    }

    private static LuaTable vector(double x, double y, double z) {
        LuaTable table = new LuaTable();
        table.set("x", x);
        table.set("y", y);
        table.set("z", z);
        return table;
    }

    private static LuaTable box(AABB box) {
        LuaTable table = new LuaTable();
        table.set("min_x", box.minX);
        table.set("min_y", box.minY);
        table.set("min_z", box.minZ);
        table.set("max_x", box.maxX);
        table.set("max_y", box.maxY);
        table.set("max_z", box.maxZ);
        return table;
    }

    private static String bounded(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
