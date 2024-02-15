package betterquesting.importers.ftbq;

import betterquesting.api.placeholders.PlaceholderConverter;
import betterquesting.api.utils.BigItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

public class FTBQUtils {
    public static BigItemStack convertItem(NBTBase tag) {
        if (tag instanceof NBTTagString) {
            return convertItemType1(((NBTTagString) tag).getString());
        } else if (tag instanceof NBTTagCompound) {
            return convertItemType2((NBTTagCompound) tag);
        }

        return new BigItemStack(ItemStack.EMPTY);
    }

    private static BigItemStack convertItemType1(String string) {
        String[] split = string.split(" ");
        if (split.length <= 0)
            return new BigItemStack(ItemStack.EMPTY);

        Item item = Item.REGISTRY.getObject(new ResourceLocation(split[0]));
        int count = split.length < 2 ? 1 : tryParseInt(split[1], 1);
        int meta = split.length < 3 ? 0 : tryParseInt(split[2], 0);
        NBTTagCompound tags = null;

        return PlaceholderConverter.convertItem(item, split[0], count, meta, "", tags);
    }

    private static BigItemStack convertItemType2(NBTTagCompound tag) {
        String[] split = tag.getString("id").split(" ");
        if (split.length <= 0)
            return new BigItemStack(ItemStack.EMPTY);
        if (split[0].equals("itemfilters:filter")) {
            String displayName = tag.getCompoundTag("tag").getCompoundTag("display").getString("Name");
            String filterType = tag.getCompoundTag("ForgeCaps").getCompoundTag("Parent").getString("id");
            switch (filterType) {
                case "ore": {
                    String oredict = tag.getCompoundTag("ForgeCaps").getCompoundTag("Parent").getString("data");
                    NonNullList<ItemStack> ores = OreDictionary.getOres(oredict);
                    BigItemStack bigItemStack = new BigItemStack(ores.isEmpty() ? ItemStack.EMPTY.copy() : ores.get(0));
                    bigItemStack.setOreDict(oredict);
                    bigItemStack.getBaseStack().setItemDamage(0);
                    return bigItemStack;
                }
                default:
                    throw new RuntimeException("Invalid filterType");
            }
        } else {
            Item item = Item.REGISTRY.getObject(new ResourceLocation(split[0]));
            int count = split.length < 2 ? 1 : tryParseInt(split[1], 1);
            int meta = tag.hasKey("Damage") ? tag.getInteger("Damage") : split.length < 3 ? 0 : tryParseInt(split[2], 0);
            NBTTagCompound tags = !tag.hasKey("tag", 10) ? null : tag.getCompoundTag("tag");
            return PlaceholderConverter.convertItem(item, split[0], count, meta, "", tags);
        }
    }

    private static int tryParseInt(String text, int def) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
