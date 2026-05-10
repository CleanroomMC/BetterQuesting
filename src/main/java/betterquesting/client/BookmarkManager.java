package betterquesting.client;

import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.client.gui2.GuiQuest;
import net.minecraft.client.gui.GuiScreen;

public class BookmarkManager {
    public static BookmarkManager INSTANCE = new BookmarkManager();

    private GuiScreen parent;
    private Integer questId;

    private BookmarkManager() {}

    public void setBookmark(GuiScreenCanvas parent) {
        this.parent = parent;
        this.questId = null;
    }

    public void setBookmark(GuiScreenCanvas parent, int questId) {
        this.parent = parent;
        this.questId = questId;
    }

    public GuiScreen getBookmark() {
        if (questId == null) {
            return parent;
        }
        return new GuiQuest(parent, questId);
    }

    public void reset() {
        parent = null;
        questId = null;
    }
}
