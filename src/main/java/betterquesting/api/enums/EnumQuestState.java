package betterquesting.api.enums;

import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.themes.presets.PresetColor;

public enum EnumQuestState {

    LOCKED,
    UNLOCKED,
    UNCLAIMED,
    COMPLETED,
    REPEATABLE;

    public IGuiColor getColor() {
        switch (this) {
            case LOCKED -> {
                return PresetColor.QUEST_ICON_LOCKED.getColor();
            }
            case UNLOCKED -> {
                return PresetColor.QUEST_ICON_UNLOCKED.getColor();
            }
            case UNCLAIMED -> {
                return PresetColor.QUEST_ICON_PENDING.getColor();
            }
            case COMPLETED -> {
                return PresetColor.QUEST_ICON_COMPLETE.getColor();
            }
            case REPEATABLE -> {
                return PresetColor.QUEST_ICON_REPEATABLE.getColor();
            }
            default -> throw new IllegalStateException("Unexpected value: " + this);
        }
    }

}
