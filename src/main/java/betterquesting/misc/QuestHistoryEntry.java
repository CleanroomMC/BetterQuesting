package betterquesting.misc;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api2.storage.DBEntry;

public class QuestHistoryEntry extends QuestSearchEntry {
    private final long completionTimestamp;
    private final boolean repeatable;
    private final boolean pendingRewards;

    public QuestHistoryEntry(DBEntry<IQuest> quest, DBEntry<IQuestLine> questLineEntry, long completionTimestamp, boolean repeatable, boolean pendingRewards) {
        super(quest, questLineEntry);
        this.completionTimestamp = completionTimestamp;
        this.repeatable = repeatable;
        this.pendingRewards = pendingRewards;
    }

    public long getCompletionTimestamp() {
        return completionTimestamp;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public boolean hasPendingRewards() {
        return pendingRewards;
    }
}