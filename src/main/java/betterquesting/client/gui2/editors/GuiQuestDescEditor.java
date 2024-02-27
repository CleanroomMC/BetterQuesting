package betterquesting.client.gui2.editors;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import com.google.common.collect.Lists;

import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterString;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import net.minecraft.util.text.TextFormatting;

public class GuiQuestDescEditor extends GuiScreenCanvas implements IPEventListener, IVolatileScreen {

    private final int questID;
    private final IQuest quest;
    private final String beforeName;
    private final String beforeDesc;
    private String name; //TODO: Add this to GUI
    private PanelTextField<String> description;
    private PanelButton close;
    private @Nullable TextEditorFrame window;

    public GuiQuestDescEditor(GuiQuestEditor parent, int questID, IQuest quest) {
        super(parent);
        this.questID = questID;
        this.quest = quest;
        beforeName = quest.getProperty(NativeProps.NAME);
        beforeDesc = quest.getProperty(NativeProps.DESC);
        TextEditorFrame window = TextEditorFrame.get(questID);
        if (window != null) {
            this.window = window;
            window.setGui(this);
            window.toFront();
            window.requestFocus();
            //Allow just-closing(this screen)
            if (close != null)
                close.setEnabled(true);
            name = window.getName();
        } else {
            name = beforeName;
        }
    }

    /**
     * Show the editor window.
     */
    public void showWindow() {
        if (window == null)
            window = TextEditorFrame.getOrCreate(questID, beforeName, name, description.getRawText());
        window.setGui(this);
        window.toFront();
        window.requestFocus();
        //Allow just-closing(this screen)
        if (close != null)
            close.setEnabled(true);
    }

    /**
     * Close the editor window.
     */
    public void removeWindow() {
        if (window == null)
            return;
        window.close();
        window = null;
        //Disallow just-closing(this screen)
        if (close != null)
            close.setEnabled(false);
    }

    /**
     * Close the screen.
     */
    public void close() {
        mc.displayGuiScreen(this.parent);
    }

    /**
     * Save the name and the desc, and close the screen and the window.
     */
    public void saveAndClose() {
        quest.setProperty(NativeProps.NAME, name.trim());
        quest.setProperty(NativeProps.DESC, description.getRawText());
        GuiQuestEditor.sendChanges(questID);
        removeWindow();
        close();
    }

    public void cancel() {
        removeWindow();
        close();
    }

    /**
     * Insert the text to the quest name.
     *
     * @param offset     The (beginning) offset to insert.
     * @param insertText The text to insert.
     */
    public void insertName(int offset, String insertText) {
        String text = name;
        name = text.substring(0, offset) + insertText + text.substring(offset);
    }

    /**
     * Remove the range from the quest name.
     *
     * @param offset The (beginning) offset to remove.
     * @param length The characters num to remove.
     */
    public void removeName(int offset, int length) {
        String text = name;
        name = text.substring(0, offset) + text.substring(offset + length);
    }

    /**
     * Insert the text to the quest description.
     *
     * @param offset     The (beginning) offset to insert.
     * @param insertText The text to insert.
     */
    public void insertDesc(int offset, String insertText) {
        String text = description.getRawText();
        description.setText(text.substring(0, offset) + insertText + text.substring(offset));
    }

    /**
     * Remove the range from the quest description.
     *
     * @param offset The (beginning) offset to remove.
     * @param length The characters num to remove.
     */
    public void removeDesc(int offset, int length) {
        String text = description.getRawText();
        description.setText(text.substring(0, offset) + text.substring(offset + length));
    }

    @Override
    public void initPanel() {
        super.initPanel();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
                                                         PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        close = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -40, -16, 80, 16, 0),
                                0,
                                QuestTranslation.translate("betterquesting.btn.edit_name_desc.just_close"));
        close.setTooltip(Lists.newArrayList(QuestTranslation.translate("betterquesting.tooltip.edit_name_desc.just_close_screen")));
        close.setEnabled(window != null);
        cvBackground.addPanel(close);
        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 0 + 20, -16, 80, 16, 0), 1, QuestTranslation.translate("gui.cancel")));
        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_RIGHT, -80 - 20, -16, 80, 16, 0), 2, QuestTranslation.translate("gui.done")));

        PanelTextBox txTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0),
                                                QuestTranslation.translate("betterquesting.title.edit_quest")).setAlignment(1);
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);

        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.TOP_RIGHT, -100 - 10, 8, 100, 16, 0),
                                              3,
                                              QuestTranslation.translate("betterquesting.btn.edit_name_desc.open_window")));

        description = new PanelTextField<>(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(124, 32, 24, 32), 0),
                                           description != null ? description.getRawText() : window != null ? window.getDesc() : beforeDesc,
                                           FieldFilterString.INSTANCE);
        cvBackground.addPanel(description);
        description.setMaxLength(Integer.MAX_VALUE);
        description.enableWrapping(true);
        description.lockFocus(true);
        description.setCallback(this::updateWindowDesc);

        CanvasScrolling cvFormatList = new CanvasScrolling(new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(16, 32, -116, 32), 0));
        cvBackground.addPanel(cvFormatList);

        TextFormatting[] tfValues = TextFormatting.values();
        for (int i = 0; i < tfValues.length; i++) {
            cvFormatList.addPanel(new PanelButtonStorage<>(new GuiRectangle(0, i * 16, 100, 16), 10, tfValues[i].getFriendlyName(), tfValues[i].toString()));
        }

        PanelVScrollBar scFormatScroll = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(0, 0, -8, 0), 0));
        cvBackground.addPanel(scFormatScroll);
        scFormatScroll.getTransform().setParent(cvFormatList.getTransform());
        cvFormatList.setScrollDriverY(scFormatScroll);
        scFormatScroll.setActive(cvFormatList.getScrollBounds().getHeight() > 0);
    }

    @Override
    public void onGuiClosed() {
        if (window != null)
            window.setGui(null);
        super.onGuiClosed();
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

        switch (btn.getButtonID()) {
            case 0 -> {
                //Close
                if (window == null) {
                    // Why come here?
                    // The button is disabled!
                    cancel();
                } else {
                    close();
                }
            }
            case 1 -> {
                //Cancel
                cancel();
            }
            case 2 -> {
                //Done
                saveAndClose();
            }
            case 3 -> {
                showWindow();
            }
            case 10 -> {
                if (btn instanceof PanelButtonStorage) {
                    String format = ((PanelButtonStorage<String>) btn).getStoredValue();
                    description.writeText(format);
                }
            }
        }
    }

    private void updateWindowDesc(String value) {
        if (window != null)
            window.setDesc(value);
    }

}
