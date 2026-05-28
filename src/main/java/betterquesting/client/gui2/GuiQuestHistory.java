package betterquesting.client.gui2;

import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestHistory;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.BookmarkManager;
import betterquesting.misc.QuestHistoryEntry;
import net.minecraft.client.gui.GuiScreen;

import java.util.function.Consumer;

public class GuiQuestHistory extends GuiScreenCanvas {
    private Consumer<QuestHistoryEntry> callback;

    public GuiQuestHistory(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initPanel() {
        super.initPanel();
        GuiTransform bgTransform = new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0);
        CanvasTextured cvBackground = new CanvasTextured(bgTransform, PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        CanvasEmpty cvInner = new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 8, 8), 0));
        cvBackground.addPanel(cvInner);

        createExitButton(cvInner);

        GuiTransform titleTransform = new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 0, 0, -16), 0);
        String title = QuestTranslation.translate("betterquesting.gui.history");
        PanelTextBox txtTitle = new PanelTextBox(titleTransform, title)
            .setAlignment(1)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        cvInner.addPanel(txtTitle);

        GuiTransform listTransform = new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 16, 8, 24), 0);
        CanvasQuestHistory canvasQuestHistory = new CanvasQuestHistory(listTransform, mc.player);
        canvasQuestHistory.setQuestOpenCallback(entry -> {
            acceptCallback(entry);
            BookmarkManager.INSTANCE.setBookmark(this, entry.getQuest().getID());
            mc.displayGuiScreen(BookmarkManager.INSTANCE.getBookmark());
        });
        cvInner.addPanel(canvasQuestHistory);

        GuiTransform scTransform = new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 16, 0, 24), 0);
        PanelVScrollBar scDb = new PanelVScrollBar(scTransform);
        cvInner.addPanel(scDb);
        canvasQuestHistory.setScrollDriverY(scDb);
    }

    private void createExitButton(CanvasEmpty cvInner) {
        GuiTransform btnTransform = new GuiTransform(GuiAlign.BOTTOM_CENTER, new GuiPadding(-100, -16, -100, 0), 0);
        PanelButton btnExit = new PanelButton(btnTransform, 0, QuestTranslation.translate("gui.back"));
        btnExit.setClickAction(b -> mc.displayGuiScreen(parent));
        cvInner.addPanel(btnExit);
    }

    public void setCallback(Consumer<QuestHistoryEntry> callback) {
        this.callback = callback;
    }

    private void acceptCallback(QuestHistoryEntry entry) {
        if (callback != null) {
            callback.accept(entry);
        }
    }
}