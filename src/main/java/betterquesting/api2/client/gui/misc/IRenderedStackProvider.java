package betterquesting.api2.client.gui.misc;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

public interface IRenderedStackProvider {
    @Nonnull
    ItemStack getRenderedStack();

    void setRenderedStack(@Nonnull ItemStack stack);

    default void resetRenderedStack() {
        setRenderedStack(ItemStack.EMPTY);
    }
}
