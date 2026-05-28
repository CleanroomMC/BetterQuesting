package betterquesting.misc;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api2.storage.DBEntry;

public class QuestHistoryEntry extends QuestSearchEntry {
    private final long completionTimestamp;

    public QuestHistoryEntry(DBEntry<IQuest> quest, DBEntry<IQuestLine> questLineEntry, long completionTimestamp) {
        super(quest, questLineEntry);
        this.completionTimestamp = completionTimestamp;
    }

    public long getCompletionTimestamp() {
        return completionTimestamp;
    }
}