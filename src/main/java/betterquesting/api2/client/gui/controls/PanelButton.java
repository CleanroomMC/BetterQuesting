package betterquesting.api2.client.gui.controls;

import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.storage.INBTSaveLoad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.function.Consumer;

public class PanelButton implements IPanelButton, IGuiPanel, INBTSaveLoad<NBTTagCompound> {
    private final IGuiRect transform;
    private boolean enabled = true;
    private boolean hovered = false;

    private final IGuiTexture[] texStates = new IGuiTexture[3];
    private IGuiColor[] colStates = new IGuiColor[]{new GuiColorStatic(128, 128, 128, 255), new GuiColorStatic(255, 255, 255, 255), new GuiColorStatic(16777120)};
    private IGuiTexture texIcon = null;
    private String txtIcon = null;
    private IGuiColor colIcon = null;
    private int icoPadding = 0;
    private int iconAlign = 1;
    private List<String> tooltip = null;
    private boolean txtShadow = true;
    private String btnText;
    private int textAlign = 1;
    private boolean isActive = true;
    private final int btnID;

    private boolean pendingRelease = false;

    private Consumer<PanelButton> clickAction = null;

    public PanelButton(IGuiRect rect, int id, String txt) {
        this.transform = rect;
        this.btnText = txt;
        this.btnID = id;

        this.setTextures(PresetTexture.BTN_NORMAL_0.getTexture(), PresetTexture.BTN_NORMAL_1.getTexture(), PresetTexture.BTN_NORMAL_2.getTexture());
        this.setTextHighlight(PresetColor.BTN_DISABLED.getColor(), PresetColor.BTN_IDLE.getColor(), PresetColor.BTN_HOVER.getColor());
    }

    public PanelButton setClickAction(Consumer<PanelButton> action) {
        this.clickAction = action;
        return this;
    }

    public PanelButton setTextHighlight(IGuiColor disabled, IGuiColor idle, IGuiColor hover) {
        this.colStates[0] = disabled;
        this.colStates[1] = idle;
        this.colStates[2] = hover;
        return this;
    }

    public PanelButton setTextShadow(boolean enabled) {
        this.txtShadow = enabled;
        return this;
    }

    public PanelButton setTextAlignment(int align) {
        this.textAlign = MathHelper.clamp(align, 0, 2);
        return this;
    }

    public PanelButton setTextures(IGuiTexture disabled, IGuiTexture idle, IGuiTexture hover) {
        this.texStates[0] = disabled;
        this.texStates[1] = idle;
        this.texStates[2] = hover;
        return this;
    }

    public PanelButton setIcon(IGuiTexture icon) {
        return this.setIcon(icon, 0);
    }

    public PanelButton setIcon(IGuiTexture icon, int padding) {
        return setIcon(icon, null, padding);
    }

    public PanelButton setIcon(IGuiTexture icon, IGuiColor color, int padding) {
        this.texIcon = icon;
        this.txtIcon = null;
        this.colIcon = color;
        this.icoPadding = padding * 2;
        return this;
    }

    public PanelButton setIconText(String icon) {
        return this.setIconText(icon, 0);
    }

    public PanelButton setIconText(String icon, int padding) {
        return this.setIconText(icon, null, padding);
    }

    public PanelButton setIconText(String icon, IGuiColor color, int padding) {
        this.texIcon = null;
        this.txtIcon = icon;
        this.colIcon = color;
        this.icoPadding = padding * 2;
        return this;
    }

    public PanelButton setIconAlignment(int align) {
        this.iconAlign = MathHelper.clamp(align, 0, 2);
        return this;
    }

    public PanelButton setTooltip(List<String> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public void setText(String text) {
        this.btnText = text;
    }

    public String getText() {
        return this.btnText;
    }

    @Override
    public int getButtonID() {
        return this.btnID;
    }

    @Override
    public boolean isActive() {
        return this.isActive;
    }

    @Override
    public void setActive(boolean state) {
        this.isActive = state;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean state) {
        this.enabled = state;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public void setHovered(boolean state) {
        this.hovered = state;
    }

    @Override
    public IGuiRect getTransform() {
        return transform;
    }

    @Override
    public void initPanel() {
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        IGuiRect bounds = this.getTransform();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        GlStateManager.pushMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        this.setHovered(bounds.contains(mx, my));
        int curState = !isActive() ? 0 : (isHovered() ? 2 : 1);

        if (curState == 2 && pendingRelease && Mouse.isButtonDown(0)) {
            curState = 0;
        }

        IGuiTexture t = texStates[curState];

        if (t != null) // Support for text or icon only buttons in one or more states.
        {
            t.drawTexture(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), 0F, partialTick);
        }

        int isz = getIconWidth(font, bounds);

        if (texIcon != null) {

            if (isz > 0) {
                int iconX = bounds.getX() + getIconOffset(bounds.getWidth(), isz, icoPadding / 2, iconAlign);
                int iconY = bounds.getY() + (bounds.getHeight() / 2) - (isz / 2);

                if (colIcon != null) {
                    texIcon.drawTexture(iconX, iconY, isz, isz, 0F, partialTick, colIcon);
                } else {
                    texIcon.drawTexture(iconX, iconY, isz, isz, 0F, partialTick);
                }
            }
        } else if (txtIcon != null && txtIcon.length() > 0 && isz > 0) {
            int iconX = bounds.getX() + getIconOffset(bounds.getWidth(), isz, icoPadding / 2, iconAlign);
            int iconY = getTextOffsetY(bounds);
            int iconColor = colIcon != null ? colIcon.getRGB() : colStates[curState].getRGB();

            font.drawString(txtIcon, iconX, iconY, iconColor, false);
        }

        if (btnText != null && btnText.length() > 0) {
            int textX = bounds.getX();
            int textWidth = bounds.getWidth();

            if (isz > 0) {
                int textInset = isz + (icoPadding / 2) + 2;

                if (iconAlign == 0) {
                    textX += textInset;
                    textWidth -= textInset;
                } else if (iconAlign == 2) {
                    textWidth -= textInset;
                }
            }

            if (textWidth > 0) {
                float offset = getTextOffset(font, btnText, textWidth, textAlign);
                font.drawString(btnText, textX + offset, getTextOffsetY(bounds), colStates[curState].getRGB(), txtShadow);
            }
        }

        GlStateManager.popMatrix();
    }

    private int getIconWidth(FontRenderer font, IGuiRect bounds) {
        if (texIcon != null) {
            return Math.max(0, Math.min(bounds.getHeight() - icoPadding, bounds.getWidth() - icoPadding));
        }

        if (txtIcon == null || txtIcon.length() <= 0) {
            return 0;
        }

        return Math.max(0, Math.min(RenderUtils.getStringWidth(txtIcon, font), bounds.getWidth() - icoPadding));
    }

    private static int getTextOffsetY(IGuiRect bounds) {
        return bounds.getY() + bounds.getHeight() / 2 - 4;
    }



    private static int getIconOffset(int width, int size, int padding, int align) {
        switch (align) {
            case 0:
                return padding;
            case 2:
                return width - size - padding;
            default:
                return (width / 2) - (size / 2);
        }
    }

    private static float getTextOffset(FontRenderer font, String text, int width, int align) {
        switch (align) {
            case 0:
                return 4;
            case 2:
                return width - RenderUtils.getStringWidth(text, font) / 2F - 4;
            default:
                return Math.floorDiv(width, 2) - RenderUtils.getStringWidth(text, font) / 2F;
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        pendingRelease = isActive() && (click == 0 || click == 1) && isHovered();

        return (click == 0 || click == 1) && isHovered();
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        if (!pendingRelease) {
            return false;
        }

        pendingRelease = false;

        boolean clicked = isActive() && isHovered() && (click == 1 || (click == 0 && !PEventBroadcaster.INSTANCE.postEvent(new PEventButton(this))));

        if (clicked) {
            Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (click == 0) onButtonClick();
            else if (click == 1) onRightButtonClick();
        }

        return clicked;
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        return false;
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        if (isHovered()) {
            return tooltip;
        }

        return null;
    }

    @Override
    public void onButtonClick() {
        if (clickAction != null) clickAction.accept(this);
    }

    public void onRightButtonClick() {
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        // TODO: Fix me
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {

    }
}
