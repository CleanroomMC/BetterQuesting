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
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.misc.QuestHistoryEntry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class CanvasQuestHistory extends CanvasSearch<QuestHistoryEntry, QuestHistoryEntry> {
    private List<QuestHistoryEntry> historyList;
    private Consumer<QuestHistoryEntry> questOpenCallback;
    private final EntityPlayer player;
    private final UUID questingUUID;

    public CanvasQuestHistory(IGuiRect rect, EntityPlayer player) {
        super(rect);
        this.player = player;
        this.questingUUID = QuestingAPI.getQuestingUUID(player);
    }

    @Override
    protected Iterator<QuestHistoryEntry> getIterator() {
        if (historyList == null) {
            historyList = collectHistory();
        }

        return historyList.iterator();
    }

    private List<QuestHistoryEntry> collectHistory() {
        Map<Integer, QuestHistoryEntry> historyEntries = new HashMap<>();

        for (DBEntry<IQuestLine> questLine : QuestLineDatabase.INSTANCE.getEntries()) {
            if (questLine.getValue() == null) {
                continue;
            }

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
                long timestamp = completionInfo.getLong("timestamp");
                historyEntries.put(questId, new QuestHistoryEntry(questEntry, questLine, timestamp));
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

        GuiRectangle questNameRect = new GuiRectangle(36, 6, cachedWidth - 36, 12);
        String questNameStr = entry.getQuest().getValue().getProperty(NativeProps.NAME);
        PanelTextBox questName = new PanelTextBox(questNameRect, QuestTranslation.translate(questNameStr));
        buttonContainer.addPanel(questName);

        GuiRectangle timestampRect = new GuiRectangle(36, 20, cachedWidth - 36, 10);
        PanelTextBox timestamp = new PanelTextBox(timestampRect, formatTimestamp(entry.getCompletionTimestamp()));
        timestamp.setColor(PresetColor.TEXT_AUX_0.getColor());
        buttonContainer.addPanel(timestamp);
        return true;
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
}