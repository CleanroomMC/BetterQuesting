package betterquesting.api.enums;

public enum EnumQuestVisibility {
    HIDDEN,
    UNLOCKED,
    NORMAL,
    COMPLETED,
    CHAIN,
    ALWAYS;

    public String getTooltip(EnumQuestVisibility vis) {
        return String.format("betterquesting.btn.show.%s", vis.toString().toLowerCase());
    }
}
