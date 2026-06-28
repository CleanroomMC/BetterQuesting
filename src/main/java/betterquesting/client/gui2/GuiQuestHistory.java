package betterquesting.client.gui2;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
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
import betterquesting.handlers.ConfigHandler;
import betterquesting.misc.QuestHistoryEntry;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Configuration;
import org.lwjgl.util.vector.Vector4f;

import java.util.function.Consumer;

public class GuiQuestHistory extends GuiScreenCanvas {

    private Consumer<QuestHistoryEntry> callback;
    private CanvasQuestHistory canvasQuestHistory;
    private PanelButton repeatableFilterButton;
    private PanelButton standardFilterButton;
    private int historyScrollY;
    private boolean restoreHistoryScroll;
    private CanvasQuestHistory.RepeatableFilter repeatableFilter = CanvasQuestHistory.RepeatableFilter.fromName(BQ_Settings.historyRepeatableFilter);
    private CanvasQuestHistory.StandardFilter standardFilter = CanvasQuestHistory.StandardFilter.fromName(BQ_Settings.historyStandardFilter);

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

        // Exit
        PanelButton btnExit = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, new GuiPadding(-100, -16, -100, 0), 0), 0, QuestTranslation.translate("gui.back"));
        btnExit.setClickAction(b -> mc.displayGuiScreen(parent));
        cvInner.addPanel(btnExit);

        // Title
        PanelTextBox txtTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 2, 0, -16), 0), QuestTranslation.translate("betterquesting.gui.history"))
            .setAlignment(1)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        cvInner.addPanel(txtTitle);

        // Filter buttons
        repeatableFilterButton = createFilterButton(new Vector4f(0F, 0F, 0.5F, 0F), new GuiPadding(4, 20, 2, -36), this::cycleRepeatableFilter);
        standardFilterButton = createFilterButton(new Vector4f(0.5F, 0F, 1F, 0F), new GuiPadding(2, 20, 4, -36), this::cycleStandardFilter);
        repeatableFilterButton.setIconText(QuestTranslation.translate("betterquesting.gui.history.repeatables"), PresetColor.TEXT_AUX_1.getColor(), 4);
        standardFilterButton.setIconText(QuestTranslation.translate("betterquesting.gui.history.standard"), PresetColor.TEXT_AUX_1.getColor(), 4);
        updateFilterButtons();
        cvInner.addPanel(repeatableFilterButton);
        cvInner.addPanel(standardFilterButton);

        // Quest History list (main content)
        canvasQuestHistory = new CanvasQuestHistory(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 40, 8, 24), 0), mc.player);
        canvasQuestHistory.setRepeatableFilter(repeatableFilter);
        canvasQuestHistory.setStandardFilter(standardFilter);
        canvasQuestHistory.setQuestOpenCallback(entry -> {
            saveHistoryScroll();
            acceptCallback(entry);
            BookmarkManager.INSTANCE.setBookmark(this, entry.getQuest().getID());
            mc.displayGuiScreen(BookmarkManager.INSTANCE.getBookmark());
        });
        cvInner.addPanel(canvasQuestHistory);

        PanelVScrollBar scDb = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 40, 0, 24), 0));
        cvInner.addPanel(scDb);
        canvasQuestHistory.setScrollDriverY(scDb);
        restoreHistoryScroll = historyScrollY > 0;
    }

    public void setCallback(Consumer<QuestHistoryEntry> callback) {
        this.callback = callback;
    }

    private void acceptCallback(QuestHistoryEntry entry) {
        if (callback != null) {
            callback.accept(entry);
        }
    }

    private PanelButton createFilterButton(Vector4f anchor, GuiPadding padding, Consumer<PanelButton> clickAction) {
        PanelButton filterButton = new PanelButton(new GuiTransform(anchor, padding, 0), -1, "");
        filterButton.setClickAction(clickAction);
        filterButton.setTextures(PresetTexture.BTN_CLEAN_0.getTexture(), PresetTexture.BTN_CLEAN_1.getTexture(), PresetTexture.BTN_CLEAN_2.getTexture());
        filterButton.setIconAlignment(0);
        filterButton.setTextShadow(false);
        filterButton.setTextAlignment(1);
        return filterButton;
    }

    private void cycleRepeatableFilter(PanelButton button) {
        repeatableFilter = repeatableFilter.next();
        applyFilterChanges();
    }

    private void cycleStandardFilter(PanelButton button) {
        standardFilter = standardFilter.next();
        applyFilterChanges();
    }

    private void applyFilterChanges() {
        BQ_Settings.historyRepeatableFilter = repeatableFilter.name();
        BQ_Settings.historyStandardFilter = standardFilter.name();
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "History Repeatable Filter", "SHOW_ALL").set(BQ_Settings.historyRepeatableFilter);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "History Standard Filter", "SHOW_ALL").set(BQ_Settings.historyStandardFilter);
        ConfigHandler.config.save();

        saveHistoryScroll();
        restoreHistoryScroll = historyScrollY > 0;
        updateFilterButtons();

        canvasQuestHistory.setRepeatableFilter(repeatableFilter);
        canvasQuestHistory.setStandardFilter(standardFilter);
    }

    private void updateFilterButtons() {
        updateRepeatableFilterButton();
        updateStandardFilterButton();
    }

    private void updateRepeatableFilterButton() {
        updateFilterButton(repeatableFilterButton,
                           QuestTranslation.translate(repeatableFilter.getTranslationKey()),
                           getRepeatableFilterColor());
    }

    private void updateStandardFilterButton() {
        updateFilterButton(standardFilterButton,
                           QuestTranslation.translate(standardFilter.getTranslationKey()),
                           getStandardFilterColor());
    }

    private void updateFilterButton(PanelButton button, String state, IGuiColor color) {
        button.setText(state);
        button.setTextHighlight(PresetColor.BTN_DISABLED.getColor(), color, color);
    }

    private IGuiColor getRepeatableFilterColor() {
        switch (repeatableFilter) {
            case HIDE:
                return PresetColor.QUEST_LINE_LOCKED.getColor();
            case SHOW_PENDING_REWARDS:
                return PresetColor.QUEST_ICON_PENDING_STATIC.getColor();
            default:
                return PresetColor.QUEST_LINE_COMPLETE.getColor();
        }
    }

    private IGuiColor getStandardFilterColor() {
        if (standardFilter == CanvasQuestHistory.StandardFilter.SHOW_PENDING_REWARDS) {
            return PresetColor.QUEST_ICON_PENDING_STATIC.getColor();
        }

        return PresetColor.QUEST_LINE_COMPLETE.getColor();
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