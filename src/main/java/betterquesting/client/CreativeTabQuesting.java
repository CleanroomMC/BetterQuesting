package betterquesting.client;

import betterquesting.core.BetterQuesting;
import betterquesting.core.ModReference;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class CreativeTabQuesting extends CreativeTabs {
    private ItemStack tabStack;

    public CreativeTabQuesting() {
        super(ModReference.MODID);
    }

    @Override
    public ItemStack createIcon() {
        if (tabStack == null) {
            this.tabStack = new ItemStack(BetterQuesting.extraLife);
        }

        return tabStack;
    }
}
