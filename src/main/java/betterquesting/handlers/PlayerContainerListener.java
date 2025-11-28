package betterquesting.handlers;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.questing.tasks.ITaskInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PlayerContainerListener implements IContainerListener {
    private static final Object2ObjectLinkedOpenHashMap<UUID, PlayerContainerListener> LISTEN_MAP = new Object2ObjectLinkedOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> listenersToUpdate = new Object2LongOpenHashMap<>();
    private static final long MILLIS_PER_TICK = 50L;

    public static void refreshListener(@Nonnull EntityPlayer player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        PlayerContainerListener listener = LISTEN_MAP.get(uuid);
        if (listener != null) {
            listener.setDirty(false);
            listenersToUpdate.removeLong(uuid);
        } else {
            listener = new PlayerContainerListener(player);
            LISTEN_MAP.put(uuid, listener);
        }

        try {
            player.inventoryContainer.addListener(listener);
        } catch (Exception ignored) {
        }
    }

    public static void removeListener(@Nonnull EntityPlayer player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        LISTEN_MAP.remove(uuid);
        listenersToUpdate.removeLong(uuid);
    }

    /**
     * Update all player container listeners, updating their tasks if dirty.
     */
    public static void updateListeners() {
        long now = System.currentTimeMillis();

        // Check listeners to mark dirty
        if (!listenersToUpdate.isEmpty()) {
            var itr = listenersToUpdate.object2LongEntrySet().fastIterator();
            while (itr.hasNext()) {
                Object2LongMap.Entry<UUID> entry = itr.next();
                if (now >= entry.getLongValue()) {
                    var listener = LISTEN_MAP.get(entry.getKey());
                    listener.setDirty(true);
                    itr.remove();
                }
            }
        }

        // Update players' tasks
        for (PlayerContainerListener playerContainerListener : LISTEN_MAP.values()) {
            playerContainerListener.updateTasks();
        }
    }

    private final UUID playerId;
    private boolean invChanged;

    private PlayerContainerListener(@Nonnull EntityPlayer player) {
        this.playerId = QuestingAPI.getQuestingUUID(player);
    }

    /**
     * Update quest tasks for this player if necessary.
     */
    @SuppressWarnings("ConstantValue")
    private void updateTasks() {
        if (!this.isDirty()) return;
        this.setDirty(false);

        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        EntityPlayer player = server.getPlayerList().getPlayerByUUID(this.playerId);
        if (player == null || player.inventory == null) return;

        ParticipantInfo pInfo = new ParticipantInfo(player);
        for (DBEntry<IQuest> questEntry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookupShared(pInfo)) {
            for (DBEntry<ITask> taskEntry : questEntry.getValue().getTasks().getEntries()) {
                if (taskEntry.getValue() instanceof ITaskInventory task) task.onInventoryChange(questEntry, pInfo);
            }
        }
    }

    @Override
    public void sendAllContents(@Nonnull Container container, @Nonnull NonNullList<ItemStack> changedStacks) {
        if (changedStacks.isEmpty() || changedStacks.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }
        scheduleChange();
    }

    @Override
    public void sendSlotContents(@Nonnull Container container, int i, @Nonnull ItemStack changedStack) {
        // Ignore changes outside main inventory (e.g. crafting grid and armor)
        if (i >= 9 && i <= 44 && !changedStack.isEmpty()) {
            scheduleChange();
        }
    }

    @Override
    public void sendWindowProperty(@Nonnull Container container, int i, int i1) {
    }

    @Override
    public void sendAllWindowProperties(@Nonnull Container container, @Nonnull IInventory iInventory) {
    }

    /**
     * Schedules checking this player's inventory after the configured delay.
     * Deduplicates requests to avoid scanning it multiple times per tick.
     */
    private void scheduleChange() {
        // Guarantee changes are only scheduled on the main server thread
        if (!FMLCommonHandler.instance().getMinecraftServerInstance().isCallingFromMinecraftThread()) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(this::scheduleChange);
        }

        // 0 & 1 tick delay are effectively the same as the handler runs at the start of next tick
        if (BQ_Settings.retrievalDetectionDelay <= 1) {
            setDirty(true);
        } else if (!listenersToUpdate.containsKey(playerId)) {
            listenersToUpdate.put(playerId, System.currentTimeMillis() + BQ_Settings.retrievalDetectionDelay * MILLIS_PER_TICK);
        }
    }

    private void setDirty(boolean isDirty) {
        invChanged = isDirty;
    }

    private boolean isDirty() {
        return invChanged;
    }
}
