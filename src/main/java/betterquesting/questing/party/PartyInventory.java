package betterquesting.questing.party;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of a party's inventories, accumulating counts of all item stacks.
 * Take caution when modifying any ItemStacks from this class,
 * they are direct references to the actual stacks in the inventory, not a copy!
 */
public class PartyInventory {
    /** The main player's collected inventory, keys are item ids. */
    private final Int2ObjectMap<List<IndexedItemStack>> playerStacks;
    /** The collapsed inventory of all party members, keys are item ids. */
    private final Int2ObjectMap<List<IndexedItemStack>> partyStacks;
    /** The main player's collected fluid containers. */
    private final List<IndexedFluidHandler> playerFluidContainers;
    /** The collected fluid containers of all party members. */
    private final List<IndexedFluidHandler> partyFluidContainers;

    public PartyInventory(EntityPlayer mainPlayer, List<EntityPlayer> party) {
        int maxSize = party.stream().mapToInt(player -> player.inventory.mainInventory.size()).sum();
        this.playerStacks = new Int2ObjectOpenHashMap<>(maxSize);
        this.playerFluidContainers = new ArrayList<>();
        if (party.size() <= 1) {
            this.partyStacks = this.playerStacks;
            this.partyFluidContainers = this.playerFluidContainers;
        }
        else {
            this.partyStacks = new Int2ObjectOpenHashMap<>(maxSize);
            this.partyFluidContainers = new ArrayList<>();
        }

        for (EntityPlayer player : party) {
            NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
            for (int i = 0; i < mainInventory.size(); i++) {
                ItemStack stack = mainInventory.get(i);
                if (stack.isEmpty()) continue;

                var indexedStack = new IndexedItemStack(stack, i, player.inventory);
                var fluidHandler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                IndexedFluidHandler indexedFluidHandler = null;
                if (fluidHandler != null) {
                    indexedFluidHandler = new IndexedFluidHandler(fluidHandler, i, player.inventory);
                }

                int hash = getHashKey(stack);
                if (player == mainPlayer) {
                    var subStacks = playerStacks.get(hash);
                    if (subStacks == null) {
                        subStacks = new ArrayList<>();
                        playerStacks.put(hash, subStacks);
                    }
                    subStacks.add(indexedStack);
                    if (indexedFluidHandler != null) {
                        playerFluidContainers.add(indexedFluidHandler);
                    }
                }
                // Don't put duplicates in solo parties
                if (playerStacks != partyStacks) {
                    var subStacks = partyStacks.get(hash);
                    if (subStacks == null) {
                        subStacks = new ArrayList<>();
                        partyStacks.put(hash, subStacks);
                    }
                    subStacks.add(indexedStack);
                    if (indexedFluidHandler != null) {
                        partyFluidContainers.add(indexedFluidHandler);
                    }
                }
            }
        }
    }

    public static int getHashKey(ItemStack stack) {
        return Objects.requireNonNull(stack.getItem().getRegistryName()).hashCode();
    }

    /**
     * Get the combined count of an item stack in the party's inventory.
     * @param req the requirement stack
     * @param taskConsumes if true, will only count from the main player's inventory
     * @param ignoreNBT if matching should ignore NBT
     * @param partialMatch if partially matching NBT is allowed
     * @return the total amount of the stack the party has, up to the required amount
     */
    public int getItemCountFor(BigItemStack req, boolean taskConsumes, boolean ignoreNBT, boolean partialMatch) {
        var gatheredStacks = taskConsumes ? playerStacks : partyStacks;

        // The stacks matched by Item
        var subStacks = gatheredStacks.get(req.getHashKey());
        if (subStacks == null || subStacks.isEmpty()) {
            return 0;
        }

        // Collect count of the specific stack
        int count = 0;
        for (var indexedStack : subStacks) {
            ItemStack stack = indexedStack.stack;
            if (ItemComparison.BigStackMatch(req, stack, ignoreNBT, partialMatch)) {
                count += stack.getCount();
                if (count >= req.stackSize) break;
            }
        }
        return Math.min(count, req.stackSize);
    }

    /**
     * Get all fluid handlers from the party's inventory that can handle the given fluid stack.
     * @param inputStack the fluid stack
     * @param taskConsumes if true, will only search the main player's inventory
     * @param ignoreNBT if matching should ignore NBT
     * @return the context wrapping the applicable fluid handlers and max fluid amount available
     */
    public FluidMatchContext getFluidHandlersFor(FluidStack inputStack, boolean taskConsumes, boolean ignoreNBT) {
        var gatheredHandlers = taskConsumes ? playerFluidContainers : partyFluidContainers;
        if (gatheredHandlers.isEmpty()) {
            return FluidMatchContext.EMPTY;
        }

        int amount = 0;
        var handlers = new ArrayList<IndexedFluidHandler>();
        for (var indexedHandler : gatheredHandlers) {
            int numContainers = indexedHandler.handler.getContainer().getCount();
            FluidStack toDrain = inputStack.copy();
            if (ignoreNBT) {
                toDrain.tag = null;
            }
            toDrain.amount /= numContainers; // Must be a multiple of the stack size to drain evenly
            if (toDrain.amount <= 0) continue;

            // Simulate the drain
            FluidStack drained = indexedHandler.handler().drain(toDrain, false);
            if (drained == null || drained.amount <= 0) continue;

            amount += drained.amount * numContainers; // Multiply back the number of containers drained
            handlers.add(indexedHandler);
        }
        return handlers.isEmpty() ? FluidMatchContext.EMPTY : new FluidMatchContext(amount, handlers);
    }

    /**
     * Update any cached item stacks with a corresponding fluid handler. Must be called after any non-simulated drains
     * of a fluid handler from the context.
     *
     * @param context the fluid match context
     * @param taskConsumes if true, will only update the main player's cached stacks
     * @see net.minecraftforge.fluids.FluidUtil#getFluidHandler(ItemStack) the contract this fulfills
     */
    public void updateFluidContainers(FluidMatchContext context, boolean taskConsumes) {
        // Consume tasks for parties aren't currently supported.
        if (!taskConsumes) return;

        int toUpdate = 0;
        for (List<IndexedItemStack> possibleStacks : playerStacks.values()) {
            for (IndexedItemStack iStack : possibleStacks) {
                for (IndexedFluidHandler iContainerStack : context.indexedFluidHandlers()) {
                    if (iContainerStack.slot == iStack.slot && iContainerStack.sourceInv == iStack.sourceInv) {
                        ItemStack container = iContainerStack.handler.getContainer();
                        if (container == iStack.stack) break;

                        // Update the player's inventory
                        iContainerStack.sourceInv.setInventorySlotContents(iStack.slot, container);
                        // As well as the cached stack
                        iStack.updateCachedStack(container);
                        toUpdate++;
                        break;
                    }
                }
                if (toUpdate >= context.indexedFluidHandlers.size()) return;
            }
        }
    }

    private static final class IndexedItemStack {
        /** The cached stack */
        private ItemStack stack;
        /** The slot index of the stack */
        private final int slot;
        /** The player inventory this stack belongs to */
        private final InventoryPlayer sourceInv;

        private IndexedItemStack(ItemStack stack, int slot, InventoryPlayer sourceInv) {
            this.stack = stack;
            this.slot = slot;
            this.sourceInv = sourceInv;
        }

        public void updateCachedStack(ItemStack stack) {
            this.stack = stack;
        }
    }

    /**
     * A fluid handler associated with a fluid container belonging to a certain player's inventory
     * @param handler the fluid container's handler
     * @param slot the associated container's slot in the inventory
     * @param sourceInv the player's inventory this fluid handler belongs to
     */
    @Desugar
    public record IndexedFluidHandler(@Nonnull IFluidHandlerItem handler, int slot, InventoryPlayer sourceInv) {}

    /**
     * Fluid stack context with available fluid amount and the matched slots of the applicable fluid handlers
     * @param drainableAmount total amount of the fluid that can be drained
     * @param indexedFluidHandlers applicable fluid handlers
     */
    @Desugar
    public record FluidMatchContext(int drainableAmount, List<IndexedFluidHandler> indexedFluidHandlers) {
        public static final FluidMatchContext EMPTY = new FluidMatchContext(0, Collections.emptyList());
    }
}
