package betterquesting.api.properties;

import betterquesting.api.enums.EnumFrameType;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.basic.PropertyTypeBoolean;
import betterquesting.api.properties.basic.PropertyTypeEnum;
import betterquesting.api.properties.basic.PropertyTypeFloat;
import betterquesting.api.properties.basic.PropertyTypeInteger;
import betterquesting.api.properties.basic.PropertyTypeItemStack;
import betterquesting.api.properties.basic.PropertyTypeString;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.BigItemStack;
import betterquesting.core.ModReference;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;

// TODO: SPLIT THIS DAMN FILE UP. It's already too big and it needs to be divided up per-purpose

/**
 * List of native properties used in BetterQuesting
 */
public class NativeProps {

    public static final IPropertyType<String> NAME = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "name"), "untitled.name");
    public static final IPropertyType<String> DESC = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "desc"), "untitled.desc");

    @Deprecated
    public static final IPropertyType<Boolean> MAIN = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "isMain"), false);
    public static final IPropertyType<Boolean> GLOBAL = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "isGlobal"), false);
    public static final IPropertyType<Boolean> GLOBAL_SHARE = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "globalShare"), false);
    public static final IPropertyType<Boolean> SILENT = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "isSilent"), false);
    public static final IPropertyType<Boolean> AUTO_CLAIM = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "autoClaim"), false);
    public static final IPropertyType<Boolean> LOCKED_PROGRESS = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "lockedProgress"), false);
    public static final IPropertyType<Boolean> SIMULTANEOUS = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "simultaneous"), false);
    public static final IPropertyType<Boolean> IGNORES_VIEW_MODE = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "ignoresView"), false);

    public static final IPropertyType<EnumQuestVisibility> VISIBILITY = new PropertyTypeEnum<>(new ResourceLocation(ModReference.MODID, "visibility"), findVisibility());
    public static final IPropertyType<EnumLogic> LOGIC_TASK = new PropertyTypeEnum<>(new ResourceLocation(ModReference.MODID, "taskLogic"), EnumLogic.AND);
    public static final IPropertyType<EnumLogic> LOGIC_QUEST = new PropertyTypeEnum<>(new ResourceLocation(ModReference.MODID, "questLogic"), EnumLogic.AND);

    public static final IPropertyType<Integer> REPEAT_TIME = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "repeatTime"), -1);
    public static final IPropertyType<Boolean> REPEAT_REL = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "repeat_relative"), true);

    public static final IPropertyType<String> SOUND_UNLOCK = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "snd_unlock"), "minecraft:ui.button.click");
    public static final IPropertyType<String> SOUND_UPDATE = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "snd_update"), "minecraft:entity.player.levelup");
    public static final IPropertyType<String> SOUND_COMPLETE = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "snd_complete"), "minecraft:entity.player.levelup");

    public static final IPropertyType<BigItemStack> ICON = new PropertyTypeItemStack(new ResourceLocation(ModReference.MODID, "icon"), new BigItemStack(Items.NETHER_STAR));
    public static final IPropertyType<EnumFrameType> FRAME = new PropertyTypeEnum<>(new ResourceLocation(ModReference.MODID, "frame"), EnumFrameType.DEFAULT);

    public static final IPropertyType<String> BG_IMAGE = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "bg_image"), "");
    public static final IPropertyType<Integer> BG_SIZE = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "bg_size"), 256);

    public static final IPropertyType<Boolean> PARTY_ENABLE = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "party_enable"), true);

    public static final IPropertyType<Boolean> HARDCORE = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "hardcore"), false);
    public static final IPropertyType<Boolean> EDIT_MODE = new PropertyTypeBoolean(new ResourceLocation(ModReference.MODID, "editMode"), true);
    public static final IPropertyType<Integer> LIVES = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "lives"), 1);
    public static final IPropertyType<Integer> LIVES_DEF = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "livesDef"), 3);
    public static final IPropertyType<Integer> LIVES_MAX = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "livesMax"), 10);

    public static final IPropertyType<String> HOME_IMAGE = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "home_image"), new ResourceLocation(ModReference.MODID, "textures/gui/default_title.png").toString());
    public static final IPropertyType<Float> HOME_ANC_X = new PropertyTypeFloat(new ResourceLocation(ModReference.MODID, "home_anchor_x"), 0.5F);
    public static final IPropertyType<Float> HOME_ANC_Y = new PropertyTypeFloat(new ResourceLocation(ModReference.MODID, "home_anchor_y"), 0F);
    public static final IPropertyType<Integer> HOME_OFF_X = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "home_offset_x"), -128);
    public static final IPropertyType<Integer> HOME_OFF_Y = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "home_offset_y"), 0);

    public static final IPropertyType<Integer> PACK_VER = new PropertyTypeInteger(new ResourceLocation(ModReference.MODID, "pack_version"), 0);
    public static final IPropertyType<String> PACK_NAME = new PropertyTypeString(new ResourceLocation(ModReference.MODID, "pack_name"), "");

    private static EnumQuestVisibility findVisibility() {
        String visibility = BQ_Settings.defaultVisibility;
        for (EnumQuestVisibility enumVisibility : EnumQuestVisibility.values()) {
            if (enumVisibility.toString().equals(visibility)) {
                return enumVisibility;
            }
        }

        return EnumQuestVisibility.NORMAL;
    }

}
