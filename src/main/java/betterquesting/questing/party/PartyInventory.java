package betterquesting.questing.party;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A snapshot of a party's inventories, accumulating counts of all item stacks.
 */
public class PartyInventory {
    /** The main player's collected inventory, keys are stacks packed by {@link RecipeItemHelper#pack(ItemStack)}. */
    private final Int2ObjectMap<List<IndexedItemStack>> playerStacks;
    /** The collapsed inventory of all party members, keys are stacks packed by {@link RecipeItemHelper#pack(ItemStack)}. */
    private final Int2ObjectMap<List<IndexedItemStack>> partyStacks;
    /** The main player's collected fluid containers. */
    private final List<IndexedFluidContainer> playerFluidContainers;
    /** The collected fluid containers of all party members. */
    private final List<IndexedFluidContainer> partyFluidContainers;

    public PartyInventory(EntityPlayer mainPlayer, List<EntityPlayer> party) {
        // Player inventories should usually all be the same size
        int maxSize = mainPlayer.inventory.mainInventory.size() * party.size();
        this.playerStacks = new Int2ObjectOpenHashMap<>(maxSize);
        this.playerFluidContainers = new ArrayList<>();
        if (party.size() <= 1) {
            this.partyStacks = this.playerStacks;
            this.partyFluidContainers = this.playerFluidContainers;
        } else {
            this.partyStacks = new Int2ObjectOpenHashMap<>(maxSize);
            this.partyFluidContainers = new ArrayList<>();
        }

        for (EntityPlayer player : party) {
            NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
            for (int i = 0; i < mainInventory.size(); i++) {
                ItemStack stack = mainInventory.get(i);
                if (stack.isEmpty()) continue;

                IndexedItemStack indexedStack;
                IndexedFluidContainer indexedFluidContainer;
                if (stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                    indexedStack = indexedFluidContainer = new IndexedFluidContainer(stack, i, player.inventory);
                } else {
                    indexedStack = new IndexedItemStack(stack, i, player.inventory);
                    indexedFluidContainer = null;
                }

                int itemHash = RecipeItemHelper.pack(stack);
                if (player == mainPlayer) {
                    var subStacks = playerStacks.get(itemHash);
                    if (subStacks == null) {
                        subStacks = new ArrayList<>();
                        playerStacks.put(itemHash, subStacks);
                    }
                    subStacks.add(indexedStack);
                    if (indexedFluidContainer != null) {
                        playerFluidContainers.add(indexedFluidContainer);
                    }
                }
                // Don't put duplicates in solo parties
                if (playerStacks != partyStacks) {
                    var subStacks = partyStacks.get(itemHash);
                    if (subStacks == null) {
                        subStacks = new ArrayList<>();
                        partyStacks.put(itemHash, subStacks);
                    }
                    subStacks.add(indexedStack);
                    if (indexedFluidContainer != null) {
                        partyFluidContainers.add(indexedFluidContainer);
                    }
                }
            }
        }
    }

    /**
     * Get the combined count of an item stack in the party's inventory.
     * @param req the requirement stack
     * @param taskConsumes if true, will only count from the main player's inventory
     * @param ignoreNBT if matching should ignore NBT
     * @param partialMatch if partially matching NBT is allowed
     * @return the total amount of the stack the party has
     */
    public ItemMatchContext getItemCountFor(BigItemStack req, boolean taskConsumes, boolean ignoreNBT, boolean partialMatch) {
        var gatheredStacks = taskConsumes ? playerStacks : partyStacks;

        List<IndexedItemStack> subStacks = null;
        if (req.hasOreDict()) {
            // Stacks matched by any ore-dict item
            IntList oreHashes = req.getOreIngredient().getValidItemStacksPacked();

            // for-each causes autoboxing, suppress
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < oreHashes.size(); i++) {
                subStacks = gatheredStacks.get(oreHashes.get(i));
                if (subStacks != null) break;
            }
        } else {
            // Stacks matched by base item
            subStacks = gatheredStacks.get(req.getHashKey());
        }
        if (subStacks == null || subStacks.isEmpty()) {
            return ItemMatchContext.EMPTY;
        }

        // Collect matched stacks and total count
        var matchedStacks = new ArrayList<IndexedItemStack>();
        int count = 0;
        for (var indexedStack : subStacks) {
            ItemStack stack = indexedStack.stack;
            if (ItemComparison.BigStackMatch(req, stack, ignoreNBT, partialMatch)) {
                matchedStacks.add(indexedStack);
                count += indexedStack.count;
            }
        }
        return matchedStacks.isEmpty() ? ItemMatchContext.EMPTY : new ItemMatchContext(count, matchedStacks);
    }

    /**
     * Get all fluid handlers from the party's inventory that can handle the given fluid stack.
     * @param req the required fluid stack
     * @param taskConsumes if true, will only search the main player's inventory
     * @param ignoreNBT if matching should ignore NBT
     * @return the context wrapping the max fluid amount available and applicable fluid handlers
     */
    public FluidMatchContext getFluidHandlersFor(FluidStack req, boolean taskConsumes, boolean ignoreNBT) {
        var gatheredHandlers = taskConsumes ? playerFluidContainers : partyFluidContainers;
        if (gatheredHandlers.isEmpty()) {
            return FluidMatchContext.EMPTY;
        }

        int amount = 0;
        var handlers = new ArrayList<IndexedFluidContainer>();
        for (var indexedHandler : gatheredHandlers) {
            // Even though we're simulating, make a defensive copy
            FluidStack toDrain = req.copy();
            if (ignoreNBT) {
                toDrain.tag = null;
            }

            // Simulate the drain
            var handler = indexedHandler.handler(true);
            if (handler == null) continue;
            FluidStack drainable = handler.drain(toDrain, false);
            if (drainable == null || drainable.amount <= 0) continue;

            // The handler is for a single stack, so multiply the actual stack count
            drainable.amount *= indexedHandler.count;
            amount += indexedHandler.getAvailableAmount(drainable);
            handlers.add(indexedHandler);
        }
        return handlers.isEmpty() ? FluidMatchContext.EMPTY : new FluidMatchContext(amount, handlers);
    }

    /**
     * Resets the cached counts for the party's stacks.
     * Call this after any call to {@link ItemMatchContext#shrink(int)}.
     * @param taskConsumes if true, will only reset the cached stacks for the main player
     */
    public void resetItemCounts(boolean taskConsumes) {
        var stacksToReset = taskConsumes ? playerStacks : partyStacks;
        for (var iStacks : stacksToReset.values()) {
            for (var iStack : iStacks) {
                iStack.resetCount();
            }
        }
    }

    /**
     * The fluid variant of {@link #resetItemCounts(boolean)}.
     * Call this after any call to {@link FluidMatchContext#shrink(FluidStack, int)}.
     * @param taskConsumes if true, will only reset the cached fluid amounts for the main player
     */
    public void resetFluidAmounts(boolean taskConsumes) {
        var handlersToReset = taskConsumes ? playerFluidContainers : partyFluidContainers;
        for (var iHandler : handlersToReset) {
            iHandler.resetAmounts();
        }
    }

    public static class IndexedItemStack {
        /** The actual stack belonging to a player's inventory */
        protected final ItemStack stack;
        /**
         * The cached stack count.
         * This should be used over the direct stack count, in order to support split stack detection within one task.
         */
        protected int count;
        /** The slot index of the stack */
        protected final int slot;
        /** The player inventory this stack belongs to */
        protected final InventoryPlayer sourceInv;

        private IndexedItemStack(ItemStack stack, int slot, InventoryPlayer sourceInv) {
            this.stack = stack;
            this.slot = slot;
            this.sourceInv = sourceInv;
            this.count = stack.getCount();
        }

        public int slot() {
            return slot;
        }

        private void shrink(int amount) {
            count -= amount;
        }

        /** Resync the cached count to the actual stack's count. Call this at the end of task detection if needed. */
        protected void resetCount() {
            count = stack.getCount();
        }

        @Override
        public String toString() {
            return "IndexedItemStack[" +
                    "stack=" + stack + ", " +
                    "count=" + count + ", " +
                    "slot=" + slot + ", " +
                    "sourceInv=" + sourceInv + ']';
        }
    }

    @Desugar
    public record ItemMatchContext(int availableAmount, List<IndexedItemStack> indexedItemStacks) {
        public static final ItemMatchContext EMPTY = new ItemMatchContext(0, Collections.emptyList());

        /**
         * Shrink a total amount from the indexed stacks.
         * Call this for any detection of this item, even if it wasn't actually consumed.
         * @param amount the amount to shrink by
         */
        public void shrink(int amount) {
            int remaining = amount;
            for (var iStack : indexedItemStacks) {
                int amountShrunk = Math.min(iStack.count, remaining);
                iStack.shrink(amountShrunk);
                remaining -= amountShrunk;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }

    /**
     * A fluid container belonging to a certain player's inventory
     */
    public static final class IndexedFluidContainer extends IndexedItemStack {
        /** A fluid handler only to be used for simulated drains. */
        private IFluidHandlerItem simulatedHandler;
        /**
         * A list of fluid stacks and their amounts that were matched for this fluid container.
         * This should always be empty at the start of any fluid task detection (the caller is responsible to reset).
         */
        @Nonnull
        private final List<FluidStack> cachedFluidAmounts;

        /**
         * @param container   the fluid container
         * @param slot      the associated container's slot in the inventory
         * @param sourceInv the player's inventory this fluid handler belongs to
         */
        public IndexedFluidContainer(@Nonnull ItemStack container, int slot, InventoryPlayer sourceInv) {
            super(container, slot, sourceInv);
            cachedFluidAmounts = new ArrayList<>();
        }

        @Nullable
        public IFluidHandlerItem handler(boolean simulated) {
            ItemStack source;
            if (simulated) {
                if (simulatedHandler == null) {
                    source = determineHandlerSource();
                    simulatedHandler = source.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                }
                return simulatedHandler;
            }
            source = determineHandlerSource();
            return source.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        }

        private ItemStack determineHandlerSource() {
            ItemStack containerCopy;
            if (stack.getCount() == 1) {
                containerCopy = stack;
            } else {
                // Some IFluidHandlerItems require the stack count to be 1.
                containerCopy = ItemHandlerHelper.copyStackWithSize(stack, 1);
            }
            return containerCopy;
        }

        /** The stack count of the fluid container. */
        public int stackCount() {
            return count;
        }

        /**
         * Update the inventory and cached item stack with the fluid handler's result.
         * Must be called after any real drains.
         * Similar to {@link net.minecraftforge.fluids.FluidUtil#tryEmptyContainerAndStow}.
         *
         * @param handler the fluid handler that the actual drain happened with
         * @param itemsToConsume the number of containers to consume from the stack
         * @param taskConsumes if true, will only update the main player's cached stacks
         * @see net.minecraftforge.fluids.FluidUtil#getFluidHandler(ItemStack) the contract this fulfills
         */
        public void updateFluidContainer(IFluidHandlerItem handler, int itemsToConsume, boolean taskConsumes) {
            // Consume tasks for parties aren't currently supported.
            if (!taskConsumes) return;

            ItemStack container = handler.getContainer();
            if (container != this.stack) {
                // Consume the appropriate amount
                stack.shrink(itemsToConsume);
                // Then stow the resulting containers
                if (!container.isEmpty()) {
                    container.setCount(itemsToConsume);
                    ItemHandlerHelper.giveItemToPlayer(sourceInv.player, container);
                }
                this.resetCount();
            }
        }

        /**
         * Get the extractable amount of the given fluid stack from this fluid handler.
         */
        private int getAvailableAmount(FluidStack fluidIn) {
            FluidStack cached = null;
            // Look for the cached amount
            for (var fluid : cachedFluidAmounts) {
                if (fluid.isFluidEqual(fluidIn)) {
                    cached = fluid;
                    break;
                }
            }
            // Cache if not found
            if (cached == null) {
                cached = fluidIn;
                cachedFluidAmounts.add(cached);
            }

            return cached.amount;
        }

        private void shrink(FluidStack fluidToMatch, int amount) {
            for (var fluid : cachedFluidAmounts) {
                if (fluid.isFluidEqual(fluidToMatch)) fluid.amount -= amount;
            }
        }

        /** Reset the cached fluid amounts. Call this at the end of task detection if needed. */
        private void resetAmounts() {
            cachedFluidAmounts.clear();
        }

        @Override
        public String toString() {
            return "IndexedFluidHandler[" +
                    "stack=" + stack + ", " +
                    "count=" + count + ", " +
                    "slot=" + slot + ", " +
                    "sourceInv=" + sourceInv +
                    "handler=" + simulatedHandler + ", " +
                    "cachedFluidAmounts=" + cachedFluidAmounts + ']';
        }
    }

    /**
     * Fluid stack context with available fluid amount and the matched slots of the applicable fluid handlers
     * @param drainableAmount total amount of the fluid that can be drained
     * @param indexedFluidContainers applicable fluid handlers
     */
    @Desugar
    public record FluidMatchContext(int drainableAmount, List<IndexedFluidContainer> indexedFluidContainers) {
        public static final FluidMatchContext EMPTY = new FluidMatchContext(0, Collections.emptyList());

        /**
         * Shrink a total amount of a given fluid from the indexed handlers.
         * Call this for any detection of this fluid, even if it wasn't actually consumed.
         * @param reqFluid the required fluid stack, only used for its fluid and tag
         * @param amount the amount to shrink by
         */
        public void shrink(FluidStack reqFluid, int amount) {
            int remaining = amount;
            FluidStack fluid = new FluidStack(reqFluid.getFluid(), amount, reqFluid.tag);
            for (var iFluidContainer : indexedFluidContainers) {
                int amountShrunk = Math.min(iFluidContainer.getAvailableAmount(fluid), remaining);
                iFluidContainer.shrink(fluid, amountShrunk);
                remaining -= amountShrunk;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }
}
