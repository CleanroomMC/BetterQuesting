package betterquesting.client.gui2.tasks;

import java.util.List;
import java.util.UUID;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTaskOverlay;
import betterquesting.api2.client.gui.panels.content.PanelTaskOverlay.State;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.questing.tasks.TaskRetrieval;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;

public class PanelTaskRetrieval extends CanvasMinimum {

    public static final int MAX_SLOT_SIZE = 28;
    public static final int MIN_SLOT_SIZE = 20;
    public static final int SLOT_PADDING = 4;
    private final IGuiRect initialRect;
    private final TaskRetrieval task;

    public PanelTaskRetrieval(IGuiRect rect, TaskRetrieval task) {
        super(rect);
        this.initialRect = rect;
        this.task = task;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int listW = initialRect.getWidth();

        UUID uuid = QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().player);
        int[] progress = task.getUsersProgress(uuid);
        boolean isComplete = task.isComplete(uuid);

        final int firstIconOffset = 10;

        if (task.isFold() && task.requiredItems.size() > 1) {

            int x = firstIconOffset;
            int y = 0;
            int slotSize = MAX_SLOT_SIZE;
            int canvasWidth = initialRect.getWidth();
            canvasWidth -= 8; //scrollbar width
            canvasWidth -= (int) PanelTaskOverlay.FRAME_WIDTH / 2;
            if (firstIconOffset + task.requiredItems.size() * (slotSize + SLOT_PADDING) >= canvasWidth) {
                // Fit it into the (parent) canvas
                slotSize = (int) Math.max(MIN_SLOT_SIZE, (float) (canvasWidth - firstIconOffset) / task.requiredItems.size() - SLOT_PADDING);
            }

            PanelButton foldArrow = new PanelButton(new GuiRectangle(0, 0, 8, 8, 0), -1, "");
            foldArrow.setIcon(PresetIcon.ICON_RIGHT.getTexture());
            foldArrow.setClickAction(b -> setFold(false));
            this.addPanel(foldArrow);

            for (int i = 0; i < task.requiredItems.size(); i++) {
                if (x + slotSize > canvasWidth) {
                    x = firstIconOffset;
                    y += slotSize + SLOT_PADDING;
                }
                BigItemStack stack = task.requiredItems.get(i);
                boolean completed = isComplete || progress[i] >= stack.stackSize;

                PanelItemSlot slot = createPanelItemSlot(new GuiRectangle(x, y, slotSize, slotSize, 0), stack);
                PanelTaskOverlay overlay = new PanelTaskOverlay(slot);
                if (completed)
                    overlay.setState(State.COMPLETE);
                else if (progress[i] > 0)
                    overlay.setState(State.IN_PROGRESS);
                else
                    overlay.setState(State.INCOMPLETE);
                if (!completed)
                    overlay.setText(progress[i] + "/" + stack.stackSize);
                if (task.consume)
                    overlay.setConsume(true);
                this.addPanel(overlay);
                x += slotSize + SLOT_PADDING;
            }
        } else {
            if (task.requiredItems.size() > 1) {
                PanelButton foldArrow = new PanelButton(new GuiRectangle(0, 0, 8, 8, 0), -1, "");
                foldArrow.setIcon(PresetIcon.ICON_DOWN.getTexture());
                foldArrow.setClickAction(b -> setFold(true));
                this.addPanel(foldArrow);
            }

            for (int i = 0; i < task.requiredItems.size(); i++) {
                BigItemStack stack = task.requiredItems.get(i);
                boolean completed = isComplete || progress[i] >= stack.stackSize;

                PanelItemSlot slot = createPanelItemSlot(new GuiRectangle(firstIconOffset, i * (MAX_SLOT_SIZE + SLOT_PADDING), MAX_SLOT_SIZE, MAX_SLOT_SIZE, 0),
                                                         stack);
                PanelTaskOverlay overlay = new PanelTaskOverlay(slot);
                if (completed)
                    overlay.setState(State.COMPLETE);
                else if (progress[i] > 0)
                    overlay.setState(State.IN_PROGRESS);
                else
                    overlay.setState(State.INCOMPLETE);
                if (!completed)
                    overlay.setText(progress[i] + "/" + stack.stackSize);
                if (task.consume)
                    overlay.setConsume(true);
                this.addPanel(overlay);

                StringBuilder sb = new StringBuilder();

                sb.append(stack.getBaseStack().getDisplayName());

                if (stack.hasOreDict())
                    sb.append(" (").append(stack.getOreDict()).append(")");

                sb.append("\n").append(progress[i]).append("/").append(stack.stackSize).append("\n");

                if (completed) {
                    sb.append(TextFormatting.GREEN).append(QuestTranslation.translate("betterquesting.tooltip.complete"));
                } else {
                    sb.append(TextFormatting.RED).append(QuestTranslation.translate("betterquesting.tooltip.incomplete"));
                }

                PanelTextBox text = new PanelTextBox(new GuiRectangle(firstIconOffset + (MAX_SLOT_SIZE + SLOT_PADDING),
                                                                      i * (MAX_SLOT_SIZE + SLOT_PADDING),
                                                                      listW - MAX_SLOT_SIZE,
                                                                      MAX_SLOT_SIZE,
                                                                      0), sb.toString());
                text.setColor(PresetColor.TEXT_MAIN.getColor());
                this.addPanel(text);
            }
        }
        recalculateSizes();
    }

    private PanelItemSlot createPanelItemSlot(GuiRectangle rect, BigItemStack stack) {
        return new PanelItemSlot(rect, -1, stack, false, true) {

            @Override
            public List<String> getTooltip(int mx, int my) {
                List<String> tooltip = super.getTooltip(mx, my);
                if (tooltip != null && task.consume) {
                    tooltip.add(TextFormatting.RED + "[" + QuestTranslation.translate("bq_standard.tooltip.consume") + "]");
                }
                return tooltip;
            }

        };
    }

    private void setFold(boolean fold) {
        task.setFold(fold);
    }

}
