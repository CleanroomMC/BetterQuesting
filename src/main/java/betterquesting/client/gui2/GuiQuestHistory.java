package betterquesting.client.gui2;

import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestHistory;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.BookmarkManager;
import betterquesting.misc.QuestHistoryEntry;
import net.minecraft.client.gui.GuiScreen;

import java.util.function.Consumer;

public class GuiQuestHistory extends GuiScreenCanvas {

    private static CanvasQuestHistory.TypeFilter typeFilter = CanvasQuestHistory.TypeFilter.SHOW_ALL;
    private static CanvasQuestHistory.ClaimableFilter claimableFilter = CanvasQuestHistory.ClaimableFilter.SHOW_ALL;

    private Consumer<QuestHistoryEntry> callback;
    private CanvasQuestHistory canvasQuestHistory;
    private PanelButton repeatableFilterButton;
    private PanelButton standardFilterButton;
    private int historyScrollY;
    private boolean restoreHistoryScroll;

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
        repeatableFilterButton = new PanelButton(new GuiRectangle(0, 0, 16, 16), -1, "");
        repeatableFilterButton.setClickAction(this::cycleTypeFilter);

        standardFilterButton = new PanelButton(new GuiRectangle(18, 0, 16, 16), -1, "");
        standardFilterButton.setClickAction(this::cycleClaimableFilter);

        updateFilterButtons();
        cvInner.addPanel(repeatableFilterButton);
        cvInner.addPanel(standardFilterButton);

        // Quest History list (main content)
        canvasQuestHistory = new CanvasQuestHistory(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 20, 8, 24), 0), mc.player);
        canvasQuestHistory.setTypeFilter(typeFilter);
        canvasQuestHistory.setClaimableFilter(claimableFilter);
        canvasQuestHistory.setQuestOpenCallback(entry -> {
            saveHistoryScroll();
            acceptCallback(entry);
            BookmarkManager.INSTANCE.setBookmark(this, entry.getQuest().getID());
            mc.displayGuiScreen(BookmarkManager.INSTANCE.getBookmark());
        });
        cvInner.addPanel(canvasQuestHistory);

        PanelVScrollBar scDb = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 20, 0, 24), 0));
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

    private void cycleTypeFilter(PanelButton button) {
        typeFilter = typeFilter.next();
        applyFilterChanges();
    }

    private void cycleClaimableFilter(PanelButton button) {
        claimableFilter = claimableFilter.next();
        applyFilterChanges();
    }

    private void applyFilterChanges() {
        saveHistoryScroll();
        restoreHistoryScroll = historyScrollY > 0;
        updateFilterButtons();

        canvasQuestHistory.setTypeFilter(typeFilter);
        canvasQuestHistory.setClaimableFilter(claimableFilter);
    }

    private void updateFilterButtons() {
        repeatableFilterButton.setTooltip(typeFilter.getTooltip());
        repeatableFilterButton.setIcon(PresetIcon.ICON_REFRESH.getTexture(), typeFilter.getColor(), 0);
        standardFilterButton.setTooltip(claimableFilter.getTooltip());
        standardFilterButton.setIcon(PresetIcon.ICON_CHEST.getTexture(), claimableFilter.getColor(), 0);
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