package betterquesting.importers.ftbq.converters.tasks;

import betterquesting.api.enums.EnumLogic;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.core.BetterQuesting;
import betterquesting.importers.ftbq.FTBQQuestImporter;
import betterquesting.importers.ftbq.FTBQUtils;
import betterquesting.questing.tasks.TaskRetrieval;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class FtbqTaskItem {
    public ITask[] convertTask(NBTTagCompound nbt) {
        TaskRetrieval task = new TaskRetrieval();
        task.consume = nbt.getBoolean("consume_items"); // If the default were changed to true and this was redacted then too bad. I'm not going looking for the root file just for this task
        long count = !nbt.hasKey("count") ? 1 : nbt.getLong("count"); // Why this isn't per item I have no idea. Ask the FTBQ dev. Also not a fan of supporting stack counts in excess of 2 BILLION items.

        if (nbt.hasKey("item", 8) || nbt.hasKey("item", 10)) {
            BigItemStack item = FTBQUtils.convertItem(nbt.getTag("item"));
            long rem = count;

            while (rem > 0) {
                int split = (int) (rem % Integer.MAX_VALUE);
                item.stackSize = split;
                task.requiredItems.add(item.copy());
                rem -= split;
            }

            FTBQQuestImporter.provideQuestIcon(item);
        } else if (nbt.hasKey("items", 9)) {
            // Note: Non-NBT items in this list are stored in Compound > String because... I have no idea
            NBTTagList tagList = nbt.getTagList("items", 10);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tagItemBase = tagList.getCompoundTagAt(i);
                BigItemStack item;
                long rem = count;

                if (tagItemBase.hasKey("item", 8)) {
                    // Need to check the sub tag is a string
                    item = FTBQUtils.convertItem(tagItemBase.getTag("item"));
                } else {
                    // Tag itself is the item... probably
                    item = FTBQUtils.convertItem(tagItemBase);
                }

                while (rem > 0) {
                    int split = (int) (rem % Integer.MAX_VALUE);
                    item.stackSize = split;
                    task.requiredItems.add(item.copy());
                    rem -= split;
                }

                FTBQQuestImporter.provideQuestIcon(item);
            }
            if (tagList.tagCount() >= 2)
                task.entryLogic = EnumLogic.OR;
        } else {
            BetterQuesting.logger.error("Unable read item tag!");
        }

        return new ITask[]{task};
    }
}
