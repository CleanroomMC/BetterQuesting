package betterquesting.api2.client.gui.resources.colors;

import betterquesting.api.utils.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;

public class GuiColorPulse implements IGuiColor {

    private final IGuiColor c1;
    private final IGuiColor c2;

    private final double period;
    private final float phase;

    public GuiColorPulse(int color1, int color2, double period, float phase) {
        this(new GuiColorStatic(color1), new GuiColorStatic(color2), period, phase);
    }

    public GuiColorPulse(IGuiColor color1, IGuiColor color2, double period, float phase) {
        this.c1 = color1;
        this.c2 = color2;

        this.period = period;
        this.phase = phase;
    }

    @Override
    public int getRGB() {
        float blend = RenderUtils.sineWave(period, phase);
        // Return interpolated color
        return RenderUtils.lerpRGB(c1.getRGB(), c2.getRGB(), blend);
    }

    @Override
    public float getRed() { return (getRGB() >> 16 & 255) / 255F; }

    @Override
    public float getGreen() { return (getRGB() >> 8 & 255) / 255F; }

    @Override
    public float getBlue() { return (getRGB() & 255) / 255F; }

    @Override
    public float getAlpha() { return (getRGB() >> 24 & 255) / 255F; }

    @Override
    public void applyGlColor() {
        int color = getRGB();
        float a = (float) (color >> 24 & 255) / 255F;
        float r = (float) (color >> 16 & 255) / 255F;
        float g = (float) (color >> 8 & 255) / 255F;
        float b = (float) (color & 255) / 255F;
        GlStateManager.color(r, g, b, a);
    }

}
