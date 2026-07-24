package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativeBannerScrollEffectTest {

    @Test
    void fadeScalePreservesPreviousVisualBoundsWithoutRelayout() {
        double baseHeight = 128;
        for (double scrollPixels : new double[]{0, 48, 180, 640, 1500, 2400}) {
            double offset = NativeBannerScrollEffect.parallaxOffset(scrollPixels);
            double scale = NativeBannerScrollEffect.fadeScale(offset, baseHeight);

            double transformedTop = visualTop(baseHeight, scale, offset / 2.0);
            double transformedBottom = visualBottom(baseHeight, scale, offset / 2.0);

            assertEquals(-baseHeight, transformedTop, 0.0001);
            assertEquals(offset, transformedBottom, 0.0001);
        }
    }

    @Test
    void parallaxOffsetIsCapped() {
        assertEquals(
                NativeBannerScrollEffect.parallaxOffset(1500),
                NativeBannerScrollEffect.parallaxOffset(20_000),
                0.0001
        );
    }

    private static double visualTop(double baseHeight, double scale, double translateY) {
        return -baseHeight + baseHeight / 2.0 - baseHeight * scale / 2.0 + translateY;
    }

    private static double visualBottom(double baseHeight, double scale, double translateY) {
        return -baseHeight + baseHeight / 2.0 + baseHeight * scale / 2.0 + translateY;
    }
}
