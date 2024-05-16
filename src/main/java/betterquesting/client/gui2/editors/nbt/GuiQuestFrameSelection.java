package betterquesting.client.gui2.editors.nbt;

import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector4f;

import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.enums.EnumFrameType;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api.misc.ICallback;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestFrame;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.resources.textures.ItemTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import net.minecraft.client.gui.GuiScreen;

@SuppressWarnings("WeakerAccess")
public class GuiQuestFrameSelection extends GuiScreenCanvas implements IPEventListener, IVolatileScreen {

    private final ICallback<EnumFrameType> callback;
    private final BigItemStack itemStack;
    private final PanelButtonStorage<?>[] questStateButtons = new PanelButtonStorage<?>[EnumQuestState.values().length];
    private EnumFrameType frameType;
    private EnumQuestState questState = EnumQuestState.LOCKED;

    private PanelQuest itemPreview;
    private CanvasQuestFrame cvQuestFrame;

    public GuiQuestFrameSelection(GuiScreen parent, EnumFrameType frameType, BigItemStack itemStack, ICallback<EnumFrameType> callback) {
        super(parent);
        this.frameType = frameType;
        this.itemStack = itemStack;
        this.callback = callback;
    }

    public void initPanel() {
        super.initPanel();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
                                                         PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0), 0, QuestTranslation.translate("gui.done")));

        PanelTextBox txTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0),
                                                QuestTranslation.translate("betterquesting.title.select_item")).setAlignment(1);
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);

        // === LEFT PANEL ===

        CanvasEmpty cvLeft = new CanvasEmpty(new GuiTransform(new Vector4f(0F, 0F, 0.2F, 1F), new GuiPadding(16, 32, 8, 8), 0));
        cvBackground.addPanel(cvLeft);

        PanelTextBox txSelection = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 0, 0, -16), 0),
                                                    QuestTranslation.translate("betterquesting.gui.selection"));
        txSelection.setColor(PresetColor.TEXT_MAIN.getColor());
        cvLeft.addPanel(txSelection);

        itemPreview = new PanelQuest(new GuiTransform(GuiAlign.TOP_LEFT, 0, 16, 36, 36, 0), 99, itemStack, frameType, questState);
        cvLeft.addPanel(itemPreview);

        EnumQuestState[] values = EnumQuestState.values();
        for (int i = 0; i < values.length; i++) {
            EnumQuestState value = values[i];
            PanelButtonStorage<EnumQuestState> btn = new PanelButtonStorage<>(new GuiTransform(GuiAlign.TOP_LEFT, 10, 64 + i * 16, 80, 16, 0),
                                                                              2,
                                                                              value.toString(),
                                                                              value);
            cvLeft.addPanel(btn);
            questStateButtons[i] = btn;
        }
        updateButtonState();

        // === RIGHT PANEL ===

        CanvasEmpty cvRight = new CanvasEmpty(new GuiTransform(new Vector4f(0.3F, 0F, 1F, 1F), new GuiPadding(8, 32, 16, 32), 0));
        cvBackground.addPanel(cvRight);

        cvQuestFrame = new CanvasQuestFrame(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 16, 8, 0), 0), 1);
        cvRight.addPanel(cvQuestFrame);

        PanelVScrollBar scEdit = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 16, 0, 0), 0));
        cvQuestFrame.setScrollDriverY(scEdit);
        cvRight.addPanel(scEdit);

    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    @SuppressWarnings("unchecked")
    private void onButtonPress(PEventButton event) {
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 0) // Exit
        {
            if (callback != null) {
                callback.setValue(frameType);
            }

            mc.displayGuiScreen(this.parent);
        } else if (btn.getButtonID() == 1 && btn instanceof PanelButtonStorage) {
            EnumFrameType tmp = ((PanelButtonStorage<EnumFrameType>) btn).getStoredValue();

            if (tmp != null) {
                frameType = tmp;
                itemPreview.setFrameType(frameType);
            }
        } else if (btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) {
            EnumQuestState tmp = ((PanelButtonStorage<EnumQuestState>) btn).getStoredValue();

            if (tmp != null) {
                questState = tmp;
                itemPreview.setQuestState(questState);
                cvQuestFrame.setQuestState(questState);
                updateButtonState();
            }
        }
    }

    private void updateButtonState() {
        for (PanelButtonStorage<?> btn : questStateButtons) {
            btn.setActive(btn.getStoredValue() != questState);
        }
    }

    private static class PanelQuest extends PanelButton {

        private EnumFrameType frameType;
        private EnumQuestState questState;

        public PanelQuest(IGuiRect rect, int id, BigItemStack itemStack, EnumFrameType frameType, EnumQuestState questState) {
            super(rect, id, "");
            this.frameType = frameType;
            this.questState = questState;

            updateTexture();
            setIcon(new ItemTexture(itemStack, false, true), 6);
            setActive(true);
        }

        public void setFrameType(EnumFrameType frameType) {
            this.frameType = frameType;
            updateTexture();
        }

        public void setQuestState(EnumQuestState questState) {
            this.questState = questState;
            updateTexture();
        }

        private void updateTexture() {
            IGuiTexture texture = new GuiTextureColored(PresetTexture.getExtraQuestFrameTexture(frameType, questState), questState.getColor());
            setTextures(texture, texture, texture);
        }

    }

}
