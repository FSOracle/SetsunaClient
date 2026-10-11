package com.setsuna.module.modules.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTagTest {

    @Test
    void distanceScaleIsCappedAndFinite() {
        assertEquals(1.0F, ItemTag.distanceScale(0.0D));
        assertEquals(1.0F, ItemTag.distanceScale(4.0D));
        assertEquals(0.55F, ItemTag.distanceScale(20.0D), 0.001F);
        assertEquals(0.55F, ItemTag.distanceScale(256.0D), 0.001F);
        assertTrue(Float.isFinite(ItemTag.distanceScale(Double.NaN)));
        assertTrue(Float.isFinite(ItemTag.distanceScale(Double.POSITIVE_INFINITY)));
    }
}
