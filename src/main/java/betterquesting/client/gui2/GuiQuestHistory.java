package betterquesting.client.gui2;

import betterquesting.api.storage.BQ_Settings;
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
import betterquesting.handlers.ConfigHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Configuration;

import java.util.function.Consumer;

public class GuiQuestHistory extends GuiScreenCanvas {
    private Consumer<QuestHistoryEntry> callback;
    private CanvasQuestHistory canvasQuestHistory;
    private PanelButton repeatableFilterButton;
    private int historyScrollY;
    private boolean restoreHistoryScroll;
    private CanvasQuestHistory.RepeatableFilter repeatableFilter = CanvasQuestHistory.RepeatableFilter.fromName(BQ_Settings.historyRepeatableFilter);

    public GuiQuestHistory(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initPanel() {
        super.initPanel();
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        CanvasEmpty cvInner = new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 8, 8), 0));
        cvBackground.addPanel(cvInner);

        createExitButton(cvInner);

        PanelTextBox txtTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 0, 0, -16), 0), QuestTranslation.translate("betterquesting.gui.history"))
            .setAlignment(1)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        cvInner.addPanel(txtTitle);

        createRepeatableFilterButton(cvInner);

        canvasQuestHistory = new CanvasQuestHistory(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 32, 8, 24), 0), mc.player);
        canvasQuestHistory.setRepeatableFilter(repeatableFilter);
        canvasQuestHistory.setQuestOpenCallback(entry -> {
            saveHistoryScroll();
            acceptCallback(entry);
            BookmarkManager.INSTANCE.setBookmark(this, entry.getQuest().getID());
            mc.displayGuiScreen(BookmarkManager.INSTANCE.getBookmark());
        });
        cvInner.addPanel(canvasQuestHistory);

        PanelVScrollBar scDb = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 32, 0, 24), 0));
        cvInner.addPanel(scDb);
        canvasQuestHistory.setScrollDriverY(scDb);
        restoreHistoryScroll = historyScrollY > 0;
    }

    private void createRepeatableFilterButton(CanvasEmpty cvInner) {
        repeatableFilterButton = new PanelButton(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 8, -32), 0), 1, "");
        repeatableFilterButton.setClickAction(button -> cycleRepeatableFilter());
        repeatableFilterButton.setTextures(PresetTexture.BTN_ALT_0.getTexture(), PresetTexture.BTN_ALT_1.getTexture(), PresetTexture.BTN_ALT_2.getTexture());
        repeatableFilterButton.setTextShadow(false);
        // Same color for normal and hover; the button's background should be enough to indicate interactivity
        repeatableFilterButton.setTextHighlight(PresetColor.BTN_DISABLED.getColor(), PresetColor.TEXT_AUX_0.getColor(), PresetColor.TEXT_AUX_0.getColor());

        updateRepeatableFilterButton();
        cvInner.addPanel(repeatableFilterButton);
    }

    private void createExitButton(CanvasEmpty cvInner) {
        PanelButton btnExit = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, new GuiPadding(-100, -16, -100, 0), 0), 0, QuestTranslation.translate("gui.back"));
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

    private void cycleRepeatableFilter() {
        repeatableFilter = repeatableFilter.next();
        BQ_Settings.historyRepeatableFilter = repeatableFilter.name();
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "History Repeatable Filter", "SHOW_ALL").set(BQ_Settings.historyRepeatableFilter);
        ConfigHandler.config.save();

        saveHistoryScroll();
        restoreHistoryScroll = historyScrollY > 0;
        updateRepeatableFilterButton();

        canvasQuestHistory.setRepeatableFilter(repeatableFilter);
    }

    private void updateRepeatableFilterButton() {
        String filterLabel = QuestTranslation.translate(repeatableFilter.getTranslationKey());
        if (repeatableFilterButton != null) {
            repeatableFilterButton.setText(QuestTranslation.translate("betterquesting.gui.history.repeatable_filter", filterLabel));
        }
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        super.drawPanel(mx, my, partialTick);
        restoreHistoryScroll();
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        try {
            return super.onMouseRelease(mx, my, click);
        } finally {
            saveHistoryScroll();
        }
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        try {
            return super.onMouseScroll(mx, my, scroll);
        } finally {
            saveHistoryScroll();
        }
    }

    // History entries are buffered into the list, so keep reapplying until the scroll can be restored.
    private void restoreHistoryScroll() {
        if (!restoreHistoryScroll || canvasQuestHistory == null) {
            return;
        }

        if (canvasQuestHistory.isSearching()) {
            return;
        }

        canvasQuestHistory.setScrollY(historyScrollY);
        canvasQuestHistory.updatePanelScroll();
        restoreHistoryScroll = false;
    }

    // Save the scroll position so it can be restored later
    // (e.g. when opening a quest and returning back to the history list)
    private void saveHistoryScroll() {
        if (canvasQuestHistory == null) {
            return;
        }

        // Keep the pending restore target intact while the list is rebuilding its filtered results.
        if (restoreHistoryScroll) {
            return;
        }

        historyScrollY = canvasQuestHistory.getScrollY();
    }
}