package betterquesting.api2.client.gui.resources.textures;

import org.lwjgl.opengl.GL11;

import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;

public class RotatingTexture implements IGuiTexture {

    private static final IGuiColor defColor = new GuiColorStatic(255, 255, 255, 255);

    private final ResourceLocation texture;
    private final IGuiRect texBounds;
    private boolean maintainAspect = false;
    private float period;

    public RotatingTexture(ResourceLocation texture, IGuiRect bounds) {
        this.texture = texture;
        this.texBounds = bounds;
        setPeriod(1);
    }

    public RotatingTexture maintainAspect(boolean enable) {
        this.maintainAspect = enable;
        return this;
    }

    public void setPeriod(float period) { this.period = period; }

    @Override
    public void drawTexture(int x, int y, int width, int height, float zLevel, float partialTick) {
        drawTexture(x, y, width, height, zLevel, partialTick, defColor);
    }

    @Override
    public void drawTexture(int x, int y, int width, int height, float zLevel, float partialTick, IGuiColor color) {
        if (width <= 0 || height <= 0)
            return;

        GlStateManager.pushMatrix();

        float sx = (float) width / (float) texBounds.getWidth();
        float sy = (float) height / (float) texBounds.getHeight();

        if (maintainAspect) {
            float sa = Math.min(sx, sy);
            float dx = (sx - sa) * texBounds.getWidth() / 2F;
            float dy = (sy - sa) * texBounds.getHeight() / 2F;
            sx = sa;
            sy = sa;
            GlStateManager.translate(x + dx, y + dy, 0F);
        } else {
            GlStateManager.translate(x, y, 0F);
        }

        GlStateManager.scale(sx, sy, 1F);
        float w2 = texBounds.getWidth() * 0.5f;
        float h2 = texBounds.getHeight() * 0.5f;
        GlStateManager.translate(w2, h2, 0);
        GlStateManager.rotate(getAngle(period), 0, 0, 1);
        GlStateManager.translate(-w2, -h2, 0);
        color.applyGlColor();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Minecraft.getMinecraft().renderEngine.bindTexture(texture);
        GuiUtils.drawTexturedModalRect(0, 0, texBounds.getX(), texBounds.getY(), texBounds.getWidth(), texBounds.getHeight(), zLevel);

        GlStateManager.popMatrix();
    }

    @Override
    public ResourceLocation getTexture() { return this.texture; }

    @Override
    public IGuiRect getBounds() { return this.texBounds; }

    private static float getAngle(float period) {
        float phase = 0;
        // Period in milliseconds
        double pms = 1000D * period;
        // Current period time
        double time = System.currentTimeMillis() % pms;
        // Shift current time by phase, wrap value and scale between 0.0 - 1.0
        time = (time + (pms * phase)) % pms / pms;
        return (float) time * 360;
    }

}
