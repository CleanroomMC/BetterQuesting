package betterquesting.questing.tasks;

import betterquesting.NBTUtil;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.editors.tasks.GuiEditTaskRetrieval;
import betterquesting.client.gui2.tasks.PanelTaskRetrieval;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.party.PartyInventory;
import betterquesting.questing.tasks.factory.FactoryTaskRetrieval;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TaskRetrieval implements ITaskInventory, IItemTask {

    private static final boolean DEFAULT_PARTIAL_MATCH = true;
    private static final boolean DEFAULT_IGNORE_NBT = false;
    private static final boolean DEFAULT_CONSUME = false;
    private static final boolean DEFAULT_GROUP_DETECT = false;
    private static final boolean DEFAULT_AUTO_CONSUME = false;
    private static final EnumLogic DEFAULT_ENTRY_LOGIC = EnumLogic.AND;

    private final Set<UUID> completeUsers = new ObjectOpenHashSet<>();
    public final NonNullList<BigItemStack> requiredItems = NonNullList.create();
    private final Map<UUID, int[]> userProgress = new Object2ObjectOpenHashMap<>();
    public boolean partialMatch = DEFAULT_PARTIAL_MATCH;
    public boolean ignoreNBT = DEFAULT_IGNORE_NBT;
    public boolean consume = DEFAULT_CONSUME;
    public boolean groupDetect = DEFAULT_GROUP_DETECT;
    public boolean autoConsume = DEFAULT_AUTO_CONSUME;
    private EnumLogic entryLogic = DEFAULT_ENTRY_LOGIC;
    private boolean resync = false;
    private boolean progressChanged = false;

    public EnumLogic getEntryLogic() {
        return entryLogic;
    }

    public void setEntryLogic(EnumLogic entryLogic) {
        this.resync = switch (entryLogic) {
            case AND, NAND, OR -> false;
            default -> true;
        };
        this.entryLogic = entryLogic;
    }

    @Override
    public String getUnlocalisedName() {
        return BetterQuesting.MODID_STD + ".task.retrieval";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskRetrieval.INSTANCE.getRegistryName();
    }

    @Override
    public boolean isComplete(UUID uuid) {
        return completeUsers.contains(uuid);
    }

    @Override
    public void setComplete(UUID uuid) {
        completeUsers.add(uuid);
    }

    @Override
    public void onInventoryChange(@Nonnull DBEntry<IQuest> quest, @Nonnull ParticipantInfo pInfo) {
        if (!consume || autoConsume) {
            detect(pInfo, quest);
        }
    }

    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (isComplete(pInfo.UUID)) {
            return;
        }
        int updatedReqs = 0;
        PartyInventory partyInv = pInfo.getPartyInventory();

        boolean taskConsumes = consume;
        // The current progress so far for the player
        int[] currentProgress;
        // For easier interfacing with the player's inventory when consuming
        IItemHandler playerInv;
        if (taskConsumes) {
            currentProgress = getUserProgress(pInfo.UUID);
            playerInv = new PlayerInvWrapper(pInfo.PLAYER.inventory);
        }
        else {
            currentProgress = null;
            playerInv = EmptyHandler.INSTANCE;
        }
        int reqSize = requiredItems.size();
        // The progress to fill based on current PartyInventory snapshot
        int[] invProgress = new int[reqSize];

        for (int reqI = 0; reqI < reqSize; reqI++) {
            BigItemStack rStack = requiredItems.get(reqI);

            // Check the party has the required stack
            var itemStackContext = partyInv.getItemCountFor(rStack, taskConsumes, ignoreNBT, partialMatch);
            if (itemStackContext == PartyInventory.ItemMatchContext.EMPTY) continue;

            int reqRemaining = rStack.stackSize;
            if (taskConsumes) {
                // Account for already consumed progress
                reqRemaining -= currentProgress[reqI];
            }

            int progressAmount = Math.min(reqRemaining, itemStackContext.availableAmount());
            progressAmount = consumeRequired(playerInv, pInfo.PLAYER, progressAmount, itemStackContext);
            if (progressAmount > 0) {
                invProgress[reqI] += progressAmount;
                // Allows the stack detection to split across multiple requirements.
                itemStackContext.shrink(progressAmount);
                updatedReqs++;
            }
        }
        // Reset counts used for split stack detection
        if (updatedReqs > 0) {
            partyInv.resetItemCounts(taskConsumes);
        }

        // Update cached progress and check completion
        if (updatedReqs > 0 || resync) {
            int[] updatedProgress = updateBulkProgress(invProgress, pInfo);
            checkAndComplete(pInfo, quest, updatedProgress);
        }
    }

    /**
     * Consume the given amount from the player's inventory if necessary.
     *
     * @param inv              the item handler wrapping the player inventory
     * @param player           the player
     * @param amountToConsume  the amount to try to consume
     * @param itemStackContext the context with matching item stacks
     * @return how much was actually consumed, or amountToConsume if this is not a consume task
     */
    private int consumeRequired(IItemHandler inv, EntityPlayer player, int amountToConsume,
                                PartyInventory.ItemMatchContext itemStackContext) {
        // Theoretically this could work in consume mode for parties but the priority order and manual submission code would need changing
        if (!consume) return amountToConsume;

        int remaining = amountToConsume;
        int totalConsumed = 0;
        // Scan each matching stack for their slot to extract from
        for (PartyInventory.IndexedItemStack iStack : itemStackContext.indexedItemStacks()) {
            int slot = iStack.slot();
            ItemStack simulated = inv.extractItem(slot, remaining, true);
            if (simulated.isEmpty()) {
                continue;
            }

            int toExtract = Math.min(simulated.getCount(), remaining);
            ItemStack extracted = inv.extractItem(slot, toExtract, false);
            if (!extracted.isEmpty()) {
                int amountExtracted = extracted.getCount();
                totalConsumed += amountExtracted;
                remaining -= amountExtracted;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (totalConsumed > 0) {
            player.openContainer.detectAndSendChanges();
        }
        return totalConsumed;
    }

    @Nonnull
    private int[] updateBulkProgress(int[] playerProgress, ParticipantInfo pInfo) {
        int[] updatedProgress = updateUserProgress(pInfo.UUID, playerProgress);
        if (!consume) {
            // Update all other party member's progress with playerProgress
            for (UUID uuid : pInfo.ALL_UUIDS) {
                if (uuid == pInfo.UUID) continue;
                updateUserProgress(uuid, playerProgress);
            }
        }
        return updatedProgress;
    }

    /**
     * Update the task progress for given user
     *
     * @param userUUID      the user's id
     * @param progressIn    the progress to merge, treated as immutable
     * @return the updated task progress
     */
    private int[] updateUserProgress(UUID userUUID, int[] progressIn) {
        return userProgress.merge(userUUID, progressIn, (existingProgress, progressToMerge) -> {
            // Somehow the existing progress doesn't reflect current requirements, only use new progress.
            if (existingProgress.length != requiredItems.size()) {
                progressChanged = true;
                return progressToMerge;
            }
            // Group-detect requires all requirements to be met within this single detection.
            if (groupDetect) {
                progressChanged = true;
                return progressToMerge;
            }

            for (int i = 0; i < existingProgress.length; i++) {
                // Skip if already fulfilled
                if (existingProgress[i] >= requiredItems.get(i).stackSize) continue;

                if (consume) {
                    // Make sure we keep the progress that has already consumed stuff before
                    existingProgress[i] += progressToMerge[i];
                    progressChanged = true;
                }
                else if (existingProgress[i] != progressToMerge[i]) {
                    // Otherwise the progressIn overwrites the current progress
                    existingProgress[i] = progressToMerge[i];
                    progressChanged = true;
                }
            }
            return existingProgress;
        });
    }

    /**
     * Check if the detected progress would complete the quest.
     * @param pInfo the participant's info
     * @param quest the quest to possibly complete
     * @param progress the updated progress
     */
    private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, int[] progress) {
        if (progressChanged) {
            progressChanged = false;
            var questID = Collections.singletonList(quest.getID());
            if (consume) {
                pInfo.markDirty(questID);
            }
            else {
                pInfo.markDirtyParty(questID);
            }
        }
        int completedReqs = 0;
        // Count up completed requirements
        for (int i = 0; i < progress.length; i++) {
            if (progress[i] >= requiredItems.get(i).stackSize) {
                completedReqs++;
            }
        }
        if (!entryLogic.getResult(completedReqs, requiredItems.size())) {
            return;
        }

        if (consume) {
            setComplete(pInfo.UUID);
        }
        else {
            pInfo.ALL_UUIDS.forEach(this::setComplete);
        }
    }

    /**
     * Check if the given progress for a single player would complete the quest.
     * @param uuid the UUID of the player
     * @param progress the updated progress
     */
    private void checkAndComplete(UUID uuid, int[] progress) {
        int completedReqs = 0;
        // Count up completed requirements
        for (int i = 0; i < progress.length; i++) {
            if (progress[i] >= requiredItems.get(i).stackSize) {
                completedReqs++;
            }
        }
        if (!entryLogic.getResult(completedReqs, requiredItems.size())) {
            return;
        }

        setComplete(uuid);
    }

    @Nonnull
    public int[] getUserProgress(UUID uuidIn) {
        return userProgress.compute(uuidIn, (uuid, progress) ->
                progress == null || progress.length != requiredItems.size() ? new int[requiredItems.size()] : progress);
    }

    /* NBT handling */

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        NBTUtil.setBoolean(nbt, "partialMatch", partialMatch, DEFAULT_PARTIAL_MATCH, reduce);
        NBTUtil.setBoolean(nbt, "ignoreNBT", ignoreNBT, DEFAULT_IGNORE_NBT, reduce);
        NBTUtil.setBoolean(nbt, "consume", consume, DEFAULT_CONSUME, reduce);
        NBTUtil.setBoolean(nbt, "groupDetect", groupDetect, DEFAULT_GROUP_DETECT, reduce);
        NBTUtil.setBoolean(nbt, "autoConsume", autoConsume, DEFAULT_AUTO_CONSUME, reduce);
        NBTUtil.setString(nbt, "entryLogic", entryLogic.name(), DEFAULT_ENTRY_LOGIC.name(), reduce);

        NBTTagList itemArray = new NBTTagList();
        for (BigItemStack stack : this.requiredItems) {
            itemArray.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound(), reduce));
        }
        nbt.setTag("requiredItems", itemArray);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        partialMatch = NBTUtil.getBoolean(nbt, "partialMatch", DEFAULT_PARTIAL_MATCH);
        ignoreNBT = NBTUtil.getBoolean(nbt, "ignoreNBT", DEFAULT_IGNORE_NBT);
        consume = NBTUtil.getBoolean(nbt, "consume", DEFAULT_CONSUME);
        groupDetect = NBTUtil.getBoolean(nbt, "groupDetect", DEFAULT_GROUP_DETECT);
        autoConsume = NBTUtil.getBoolean(nbt, "autoConsume", DEFAULT_AUTO_CONSUME);
        setEntryLogic(NBTUtil.getEnum(nbt, "entryLogic", EnumLogic.class, true, DEFAULT_ENTRY_LOGIC));

        requiredItems.clear();
        NBTTagList iList = nbt.getTagList("requiredItems", 10);
        for (int i = 0; i < iList.tagCount(); i++) {
            requiredItems.add(JsonHelper.JsonToItemStack(iList.getCompoundTagAt(i)));
        }
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) {
            completeUsers.clear();
            userProgress.clear();
        }

        NBTTagList cList = nbt.getTagList("completeUsers", 8);
        for (int i = 0; i < cList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }

        NBTTagList pList = nbt.getTagList("userProgress", 10);
        for (int n = 0; n < pList.tagCount(); n++) {
            try {
                NBTTagCompound pTag = pList.getCompoundTagAt(n);
                UUID uuid = UUID.fromString(pTag.getString("uuid"));

                int[] data = new int[requiredItems.size()];
                NBTTagList dNbt = pTag.getTagList("data", 3);
                for (int i = 0; i < data.length && i < dNbt.tagCount(); i++) // TODO: Change this to an int array. This is dumb...
                {
                    data[i] = dNbt.getIntAt(i);
                }

                userProgress.put(uuid, data);
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load user progress for task", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        NBTTagList jArray = new NBTTagList();
        NBTTagList progArray = new NBTTagList();

        if (users != null) {
            users.forEach((uuid) -> {
                if (completeUsers.contains(uuid)) {
                    jArray.appendTag(new NBTTagString(uuid.toString()));
                }

                int[] data = userProgress.get(uuid);
                if (data != null) {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                    for (int i : data) {
                        pArray.appendTag(new NBTTagInt(i));
                    }
                    pJson.setTag("data", pArray);
                    progArray.appendTag(pJson);
                }
            });
        } else {
            // Ensure consistent order when writing
            TreeSet<UUID> sortedCompleteUsers = new TreeSet<>(completeUsers);
            TreeMap<UUID, int[]> sortedProgress = new TreeMap<>(userProgress);

            sortedCompleteUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));

            sortedProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
                pJson.setString("uuid", uuid.toString());
                NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                for (int i : data) {
                    pArray.appendTag(new NBTTagInt(i));
                }
                pJson.setTag("data", pArray);
                progArray.appendTag(pJson);
            });
        }

        nbt.setTag("completeUsers", jArray);
        nbt.setTag("userProgress", progArray);

        return nbt;
    }

    @Override
    public void resetUser(@Nullable UUID uuid) {
        if (uuid == null) {
            completeUsers.clear();
            userProgress.clear();
        } else {
            completeUsers.remove(uuid);
            userProgress.remove(uuid);
        }
    }

    @Override
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskRetrieval(rect, this);
    }

    @Override
    public boolean canAcceptItem(UUID owner, DBEntry<IQuest> quest, ItemStack stack) {
        if (owner == null || stack == null || stack.isEmpty() || !consume || isComplete(owner) || requiredItems.size() <= 0) {
            return false;
        }

        int[] progress = getUserProgress(owner);

        for (int j = 0; j < requiredItems.size(); j++) {
            BigItemStack rStack = requiredItems.get(j);

            if (progress[j] >= rStack.stackSize)
                continue;

            if (ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch) || ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack submitItem(UUID owner, DBEntry<IQuest> quest, ItemStack input) {
        if (owner == null || input.isEmpty() || !consume || isComplete(owner))
            return input;

        ItemStack stack = input.copy();

        // Direct reference to value in userProgress
        int[] progress = getUserProgress(owner);
        boolean updated = false;

        for (int j = 0; j < requiredItems.size(); j++) {
            if (stack.isEmpty())
                break;

            BigItemStack rStack = requiredItems.get(j);

            if (progress[j] >= rStack.stackSize)
                continue;

            int remaining = rStack.stackSize - progress[j];

            if (ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch) || ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch)) {
                int removed = Math.min(stack.getCount(), remaining);
                stack.shrink(removed);
                progress[j] += removed;
                updated = true;
                if (stack.isEmpty())
                    break;
            }
        }

        if (updated) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(owner);

            if (player != null) {
                checkAndComplete(new ParticipantInfo(player), quest, progress);
            } else {
                checkAndComplete(owner, progress);
            }
        }

        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return new GuiEditTaskRetrieval(parent, quest, this);
    }

    @Override
    public List<String> getTextForSearch() {
        List<String> texts = new ArrayList<>();
        for (BigItemStack bigStack : requiredItems) {
            ItemStack stack = bigStack.getBaseStack();
            texts.add(stack.getDisplayName());
            if (bigStack.hasOreDict()) {
                texts.add(bigStack.getOreDict());
            }
        }
        return texts;
    }
}
