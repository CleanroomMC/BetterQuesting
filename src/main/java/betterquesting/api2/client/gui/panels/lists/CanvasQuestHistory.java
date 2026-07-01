package betterquesting.api2.client.gui.panels.lists;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api2.client.gui.controls.PanelButtonCustom;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.misc.QuestHistoryEntry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class CanvasQuestHistory extends CanvasSearch<QuestHistoryEntry, QuestHistoryEntry> {
    private List<QuestHistoryEntry> historyList;
    private Consumer<QuestHistoryEntry> questOpenCallback;
    private final EntityPlayer player;
    private TypeFilter typeFilter = TypeFilter.SHOW_ALL;
    private ClaimableFilter claimableFilter = ClaimableFilter.SHOW_ALL;

    public CanvasQuestHistory(IGuiRect rect, EntityPlayer player) {
        super(rect);
        this.player = player;
    }

    @Override
    protected Iterator<QuestHistoryEntry> getIterator() {
        if (historyList == null) {
            historyList = collectHistory();
        }

        return historyList.iterator();
    }

    private List<QuestHistoryEntry> collectHistory() {
        Map<Integer, QuestHistoryEntry> historyEntries = new Int2ObjectOpenHashMap<>();
        UUID questingUUID = QuestingAPI.getQuestingUUID(player);

        for (DBEntry<IQuestLine> questLine : QuestLineDatabase.INSTANCE.getEntries()) {
            for (DBEntry<IQuestLineEntry> questLineEntry : questLine.getValue().getEntries()) {
                int questId = questLineEntry.getID();
                if (historyEntries.containsKey(questId)) {
                    continue;
                }

                IQuest quest = QuestDatabase.INSTANCE.getValue(questId);
                if (quest == null) {
                    continue;
                }

                NBTTagCompound completionInfo = quest.getCompletionInfo(questingUUID);
                if (completionInfo == null) {
                    continue;
                }

                DBEntry<IQuest> questEntry = new DBEntry<>(questId, quest);
                long timestamp = quest.getLastCompletedAt(questingUUID);
                if (timestamp <= 0) {
                    continue;
                }

                boolean repeatable = quest.getProperty(NativeProps.REPEAT_TIME) >= 0;
                boolean pendingRewards = quest.canClaimBasically(player);
                historyEntries.put(questId, new QuestHistoryEntry(questEntry, questLine, timestamp, repeatable, pendingRewards));
            }
        }

        List<QuestHistoryEntry> sortedEntries = new ArrayList<>(historyEntries.values());
        sortedEntries.sort(Comparator.comparingLong(QuestHistoryEntry::getCompletionTimestamp)
                                        .reversed()
                                        .thenComparingInt(entry -> entry.getQuest().getID()));
        return sortedEntries;
    }

    @Override
    protected void queryMatches(QuestHistoryEntry entry, String query, ArrayDeque<QuestHistoryEntry> results) {
        if (claimableFilter == ClaimableFilter.SHOW_PENDING_REWARDS && !entry.hasPendingRewards()) {
            return;
        }

        if (typeFilter == TypeFilter.ONLY_REPEATABLE && !entry.isRepeatable()) {
            return;
        } else if (typeFilter == TypeFilter.NON_REPEATABLE && entry.isRepeatable()) {
            return;
        }

        results.add(entry);
    }

    @Override
    protected boolean addResult(QuestHistoryEntry entry, int index, int cachedWidth) {
        GuiRectangle buttonRect = new GuiRectangle(0, index * 32, cachedWidth, 32, 0);
        PanelButtonCustom buttonContainer = new PanelButtonCustom(buttonRect, 2);
        buttonContainer.setCallback(panelButtonCustom -> {
            if (questOpenCallback != null) {
                questOpenCallback.accept(entry);
            }
        });
        this.addPanel(buttonContainer);

        GuiRectangle questButtonRect = new GuiRectangle(2, 2, 28, 28);
        PanelButtonQuest questButton = new PanelButtonQuest(questButtonRect, 0, "", entry.getQuest());
        questButton.setCallback(value -> {
            if (questOpenCallback != null) {
                questOpenCallback.accept(entry);
            }
        });
        buttonContainer.addPanel(questButton);

        int repeatableLabelWidth = entry.isRepeatable() ? 96 : 0;
        int questNameWidth = Math.max(0, cachedWidth - 36 - repeatableLabelWidth - (entry.isRepeatable() ? 8 : 0));
        GuiRectangle questNameRect = new GuiRectangle(36, 6, questNameWidth, 12);
        String questNameStr = entry.getQuest().getValue().getProperty(NativeProps.NAME);
        PanelTextBox questName = new PanelTextBox(questNameRect, QuestTranslation.translate(questNameStr));
        buttonContainer.addPanel(questName);

        if (entry.isRepeatable()) {
            GuiRectangle repeatableRect = new GuiRectangle(cachedWidth - repeatableLabelWidth - 4, 6, repeatableLabelWidth, 12);
            PanelTextBox repeatableLabel = new PanelTextBox(repeatableRect, QuestTranslation.translate("betterquesting.gui.history.repeatable"));
            repeatableLabel.setAlignment(2);
            repeatableLabel.setColor(PresetColor.QUEST_LINE_COMPLETE.getColor());
            buttonContainer.addPanel(repeatableLabel);
        }

        GuiRectangle timestampRect = new GuiRectangle(36, 20, cachedWidth - 36, 10);
        PanelTextBox timestamp = new PanelTextBox(timestampRect, getHistoryDetails(entry));
        timestamp.setColor(PresetColor.TEXT_AUX_0.getColor());
        buttonContainer.addPanel(timestamp);
        return true;
    }

    private String getHistoryDetails(QuestHistoryEntry entry) {
        String timestamp = formatTimestamp(entry.getCompletionTimestamp());

        if (entry.hasPendingRewards()) {
            return QuestTranslation.translate("betterquesting.gui.history.pending_rewards_at", timestamp);
        }

        if (!entry.isRepeatable()) {
            return QuestTranslation.translate("betterquesting.gui.history.completed_at", timestamp);
        }

        return QuestTranslation.translate("betterquesting.gui.history.last_completed_at", timestamp);
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "-";
        }

        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                         .format(new Date(timestamp));
    }

    public void setQuestOpenCallback(Consumer<QuestHistoryEntry> questOpenCallback) {
        this.questOpenCallback = questOpenCallback;
    }

    public void setTypeFilter(TypeFilter typeFilter) {
        if (this.typeFilter == typeFilter) {
            return;
        }

        this.typeFilter = typeFilter;
        refreshSearch();
        updatePanelScroll();
    }

    public void setClaimableFilter(ClaimableFilter claimableFilter) {
        if (this.claimableFilter == claimableFilter) {
            return;
        }

        this.claimableFilter = claimableFilter;
        refreshSearch();
        updatePanelScroll();
    }


    public enum TypeFilter {
        SHOW_ALL("betterquesting.gui.history.filter.type.all", null),
        ONLY_REPEATABLE("betterquesting.gui.history.filter.type.only", PresetColor.QUEST_ICON_REPEATABLE.getColor()),
        NON_REPEATABLE("betterquesting.gui.history.filter.type.hide", PresetColor.QUEST_ICON_COMPLETE.getColor());

        private static final TypeFilter[] VALUES = values();

        private final String translationKey;
        private final IGuiColor color;

        TypeFilter(String translationKey, IGuiColor color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public List<String> getTooltip() {
            List<String> list = new ArrayList<>();
            list.add(QuestTranslation.translate("betterquesting.gui.history.filter.type"));
            list.add("");
            for (var value : VALUES) {
                if (value == this) list.add(TextFormatting.YELLOW + QuestTranslation.translate(translationKey));
                else list.add(TextFormatting.DARK_GRAY + QuestTranslation.translate(value.translationKey));
            }
            return list;
        }

        public IGuiColor getColor() {
            return color;
        }

        public TypeFilter next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    public enum ClaimableFilter {
        SHOW_ALL("betterquesting.gui.history.filter.claimable.all", null),
        SHOW_PENDING_REWARDS("betterquesting.gui.history.filter.claimable.with_rewards", PresetColor.QUEST_ICON_PENDING.getColor());

        private static final ClaimableFilter[] VALUES = values();

        private final String translationKey;
        private final IGuiColor color;

        ClaimableFilter(String translationKey, IGuiColor color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public List<String> getTooltip() {
            List<String> list = new ArrayList<>();
            list.add(QuestTranslation.translate("betterquesting.gui.history.filter.claimable"));
            list.add("");
            for (var value : VALUES) {
                if (value == this) list.add(TextFormatting.YELLOW + QuestTranslation.translate(translationKey));
                else list.add(TextFormatting.DARK_GRAY + QuestTranslation.translate(value.translationKey));
            }
            return list;
        }

        public IGuiColor getColor() {
            return color;
        }

        public ClaimableFilter next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }
}