package com.setsuna.module.modules.render;

/** Kill-effect visuals, fired immediately when the client attacks a valid target. */
public final class HitEffect extends KillEffect {

    public static final HitEffect INSTANCE = new HitEffect();

    private HitEffect() {
        super("Hit Effect");
    }

    @Override
    protected boolean firesOnHit() {
        return true;
    }
}
