package betterquesting.api2.client.gui.panels.content;

import java.util.List;

import org.lwjgl.opengl.GL11;

import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Displays task info over the panel.
 */
public class PanelTaskOverlay implements IGuiPanel {

    public static final float FRAME_WIDTH = 2;

    private final IGuiPanel delegate;
    private State state;
    private boolean consume;
    private String text; // Long text will be scaled.

    public PanelTaskOverlay(IGuiPanel delegate) { this.delegate = delegate; }

    public PanelTaskOverlay setState(State state) {
        this.state = state;
        return this;
    }

    public PanelTaskOverlay setConsume(boolean consume) {
        this.consume = consume;
        return this;
    }

    public PanelTaskOverlay setText(String text) {
        this.text = text;
        return this;
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        delegate.drawPanel(mx, my, partialTick);
        switch (state) {
            case INCOMPLETE -> {
                renderIncomplete();
                if (consume)
                    renderConsumeIcon(mx, my);
            }
            case COMPLETE -> {
                renderComplete(mx, my);
            }
            case IN_PROGRESS -> {
                renderInProgress();
                if (consume)
                    renderConsumeIcon(mx, my);
            }
        }
        renderText(mx, my);
    }

    // delegates

    @Override
    public IGuiRect getTransform() { return delegate.getTransform(); }

    @Override
    public void initPanel() {
        delegate.initPanel();
    }

    @Override
    public void setEnabled(boolean state) {
        delegate.setEnabled(state);
    }

    @Override
    public boolean isEnabled() { return delegate.isEnabled(); }

    @Override
    public boolean onMouseClick(int mx, int my, int button) {
        return delegate.onMouseClick(mx, my, button);
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int button) {
        return delegate.onMouseRelease(mx, my, button);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        return delegate.onMouseScroll(mx, my, scroll);
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        return delegate.onKeyTyped(c, keycode);
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        return delegate.getTooltip(mx, my);
    }

    private void renderIncomplete() {
        renderFrame(0.2f * RenderUtils.sineWave(2, 0) + 0.3f, 0, 0, 1);
    }

    private void renderComplete(int mx, int my) {
        renderFrame(0.2f, 1, 0, 1);
        int size = 12;
        IGuiRect rect = getTransform();
        int x = rect.getX() + rect.getWidth() - size + 2;
        int y = rect.getY() - 2;
        int a = getTransform().contains(mx, my) ? 100 : 255;
        PresetIcon.ICON_CHECK.getTexture().drawTexture(x, y, size, size, 0, 0, new GuiColorStatic(0, 255, 0, a));
    }

    private void renderInProgress() {
        float r = 0.3f * RenderUtils.sineWave(2, 0) + 0.3f;
        float g = r;
        renderFrame(r, g, 0, 1);
    }

    private void renderFrame(float red, float green, float blue, float alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder vertexbuffer = tessellator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                            GlStateManager.SourceFactor.ONE,
                                            GlStateManager.DestFactor.ZERO);
        GlStateManager.color(red, green, blue, alpha);

        vertexbuffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        IGuiRect rect = getTransform();
        double w = FRAME_WIDTH / 2;
        vertexbuffer.pos((double) rect.getX() - w, (double) rect.getY() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() + w, (double) rect.getY() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() + w, (double) rect.getY() - w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() - w, (double) rect.getY() - w, 0.0D).endVertex();

        vertexbuffer.pos((double) rect.getX() - w, (double) rect.getY() + rect.getHeight() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() + w, (double) rect.getY() + rect.getHeight() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() + w, (double) rect.getY() + rect.getHeight() - w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() - w, (double) rect.getY() + rect.getHeight() - w, 0.0D).endVertex();

        vertexbuffer.pos((double) rect.getX() - w, (double) rect.getY() + rect.getHeight() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + w, (double) rect.getY() + rect.getHeight() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + w, (double) rect.getY() - w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() - w, (double) rect.getY() - w, 0.0D).endVertex();

        vertexbuffer.pos((double) rect.getX() + rect.getWidth() - w, (double) rect.getY() + rect.getHeight() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() + w, (double) rect.getY() + rect.getHeight() + w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() + w, (double) rect.getY() - w, 0.0D).endVertex();
        vertexbuffer.pos((double) rect.getX() + rect.getWidth() - w, (double) rect.getY() - w, 0.0D).endVertex();

        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void renderText(int mx, int my) {
        GlStateManager.enableBlend();
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        IGuiRect rect = getTransform();
        int width = fontRenderer.getStringWidth(text);
        int x = rect.getX() + rect.getWidth() - width;
        int y = rect.getY() + rect.getHeight() - fontRenderer.FONT_HEIGHT + 1;
        int a = getTransform().contains(mx, my) ? 100 : 255;
        if (x < rect.getX() + FRAME_WIDTH / 2) {
            //scale to fit
            float s = (rect.getWidth() - FRAME_WIDTH) / width;
            float new_x = rect.getX() + FRAME_WIDTH / 2;
            float new_y = y + fontRenderer.FONT_HEIGHT * (1 - s) - FRAME_WIDTH / 2;
            GlStateManager.pushMatrix();
            GlStateManager.translate(new_x, new_y, 0);
            GlStateManager.scale(s, s, s);
            fontRenderer.drawStringWithShadow(text, 0, 0, 0xFFFFFF | (a << 24));
            GlStateManager.popMatrix();
        } else {
            fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF | (a << 24));
        }
    }

    private void renderConsumeIcon(int mx, int my) {
        GlStateManager.enableBlend();
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        IGuiRect rect = getTransform();
        int x = rect.getX() + rect.getWidth() - fontRenderer.getStringWidth("C");
        int y = rect.getY() + 1;
        int a = getTransform().contains(mx, my) ? 100 : 255;
        fontRenderer.drawStringWithShadow("C", x, y, 0xFFFF00 | (a << 24));
    }

    public enum State {
        INCOMPLETE,
        COMPLETE,
        IN_PROGRESS,
    }

}
