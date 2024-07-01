package betterquesting;

import net.minecraft.nbt.NBTBase;

@Deprecated
public class NBTReplaceUtil {
    public static <T extends NBTBase> T replaceStrings(T baseTag, String key, String replace) {
        return NBTUtil.replaceStrings(baseTag, key, replace);
    }
}
