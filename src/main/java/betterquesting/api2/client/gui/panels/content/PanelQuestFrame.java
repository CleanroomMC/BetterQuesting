package betterquesting.api2.client.gui.panels.content;

import betterquesting.api.enums.EnumFrameType;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.textures.ColorTexture;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.api2.client.gui.resources.textures.LayeredTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;

public class PanelQuestFrame extends PanelButtonStorage<EnumFrameType> {

    private EnumQuestState questState = EnumQuestState.LOCKED;

    public PanelQuestFrame(IGuiRect rect, int id, EnumFrameType value) {
        super(rect, id, "", value);

        this.setTextures(PresetTexture.ITEM_FRAME.getTexture(),
                         PresetTexture.ITEM_FRAME.getTexture(),
                         new LayeredTexture(PresetTexture.ITEM_FRAME.getTexture(),
                                            new ColorTexture(PresetColor.ITEM_HIGHLIGHT.getColor(), new GuiPadding(1, 1, 1, 1))));

        setStoredValue(value);
    }

    @Override
    public PanelQuestFrame setStoredValue(EnumFrameType value) {
        super.setStoredValue(value);

        if (value == EnumFrameType.DEFAULT)
            this.setIcon(null);
        else if (value != null && questState != null)
            this.setIcon(new GuiTextureColored(PresetTexture.getExtraQuestFrameTexture(value, questState), questState.getColor()), 1);
        else
            this.setIcon(null);
        this.setTooltip(null);

        return this;
    }

    public void setQuestState(EnumQuestState questState) {
        this.questState = questState;
        EnumFrameType frameType = getStoredValue();
        if (frameType == EnumFrameType.DEFAULT)
            this.setIcon(null);
        else if (frameType != null && questState != null)
            this.setIcon(new GuiTextureColored(PresetTexture.getExtraQuestFrameTexture(frameType, questState), questState.getColor()), 1);
        else
            this.setIcon(null);
    }

    @Override
    public void onButtonClick() {
        if (getCallback() != null)
            getCallback().setValue(getStoredValue());
    }

    @Override
    public void onRightButtonClick() {
        if (getCallback() != null)
            getCallback().setValue(getStoredValue());
    }

}
