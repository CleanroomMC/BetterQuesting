package betterquesting.client.gui2.tasks;

import java.util.List;
import java.util.UUID;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelFluidSlot;
import betterquesting.api2.client.gui.panels.content.PanelTaskOverlay;
import betterquesting.api2.client.gui.panels.content.PanelTaskOverlay.State;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.TaskFluid;
import mezz.jei.Internal;
import mezz.jei.api.recipe.IFocus.Mode;
import mezz.jei.gui.Focus;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional.Method;

public class PanelTaskFluid extends CanvasMinimum {

    public static final int MAX_SLOT_SIZE = PanelTaskRetrieval.MAX_SLOT_SIZE;
    public static final int MIN_SLOT_SIZE = PanelTaskRetrieval.MIN_SLOT_SIZE;
    public static final int SLOT_PADDING = PanelTaskRetrieval.SLOT_PADDING;
    private final IGuiRect initialRect;
    private final TaskFluid task;

    public PanelTaskFluid(IGuiRect rect, TaskFluid task) {
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

        if (task.isFold() && task.requiredFluids.size() > 1) {
            int x = firstIconOffset;
            int y = 0;
            int slotSize = MAX_SLOT_SIZE;
            int canvasWidth = initialRect.getWidth();
            canvasWidth -= 8; //scrollbar width
            canvasWidth -= (int) PanelTaskOverlay.FRAME_WIDTH / 2;
            if (firstIconOffset + task.requiredFluids.size() * (slotSize + SLOT_PADDING) >= canvasWidth) {
                // Fit it into the (parent) canvas
                slotSize = (int) Math.max(MIN_SLOT_SIZE, (float) (canvasWidth - firstIconOffset) / task.requiredFluids.size() - SLOT_PADDING);
            }

            PanelButton foldArrow = new PanelButton(new GuiRectangle(0, 0, 8, 8, 0), -1, "");
            foldArrow.setIcon(PresetIcon.ICON_RIGHT.getTexture());
            foldArrow.setClickAction(b -> setFold(false));
            this.addPanel(foldArrow);

            for (int i = 0; i < task.requiredFluids.size(); i++) {
                if (x + slotSize > canvasWidth) {
                    x = firstIconOffset;
                    y += slotSize + SLOT_PADDING;
                }
                FluidStack stack = task.requiredFluids.get(i);
                boolean completed = isComplete || progress[i] >= stack.amount;

                PanelFluidSlot slot = createPanelFluidSlot(new GuiRectangle(x, y, slotSize, slotSize, 0), stack);
                if (BetterQuesting.hasJEI)
                    slot.setCallback(this::lookupRecipe);
                PanelTaskOverlay overlay = new PanelTaskOverlay(slot);
                if (completed)
                    overlay.setState(State.COMPLETE);
                else if (progress[i] > 0)
                    overlay.setState(State.IN_PROGRESS);
                else
                    overlay.setState(State.INCOMPLETE);
                if (!completed)
                    overlay.setText(progress[i] + "/" + stack.amount);
                if (task.consume)
                    overlay.setConsume(true);
                this.addPanel(overlay);
                x += slotSize + SLOT_PADDING;
            }
        } else {
            if (task.requiredFluids.size() > 1) {
                PanelButton foldArrow = new PanelButton(new GuiRectangle(0, 0, 8, 8, 0), -1, "");
                foldArrow.setIcon(PresetIcon.ICON_DOWN.getTexture());
                foldArrow.setClickAction(b -> setFold(true));
                this.addPanel(foldArrow);
            }

            for (int i = 0; i < task.requiredFluids.size(); i++) {
                FluidStack stack = task.requiredFluids.get(i);
                boolean completed = isComplete || progress[i] >= stack.amount;

                PanelFluidSlot slot = createPanelFluidSlot(new GuiRectangle(firstIconOffset,
                                                                            i * (MAX_SLOT_SIZE + SLOT_PADDING),
                                                                            MAX_SLOT_SIZE,
                                                                            MAX_SLOT_SIZE,
                                                                            0), stack);
                if (BetterQuesting.hasJEI)
                    slot.setCallback(this::lookupRecipe);
                PanelTaskOverlay overlay = new PanelTaskOverlay(slot);
                if (completed)
                    overlay.setState(State.COMPLETE);
                else if (progress[i] > 0)
                    overlay.setState(State.IN_PROGRESS);
                else
                    overlay.setState(State.INCOMPLETE);
                if (!completed)
                    overlay.setText(progress[i] + "/" + stack.amount);
                if (task.consume)
                    overlay.setConsume(true);
                this.addPanel(overlay);

                StringBuilder sb = new StringBuilder();

                sb.append(stack.getLocalizedName()).append("\n");
                sb.append(progress[i]).append("/").append(stack.amount).append("mB\n");

                if (progress[i] >= stack.amount || isComplete) {
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

    @Method(modid = "jei")
    private void lookupRecipe(FluidStack fluid) {
        if (fluid == null || Internal.getRuntime() == null)
            return;
        Internal.getRuntime().getRecipesGui().show(new Focus<>(Mode.OUTPUT, fluid));
    }

    private PanelFluidSlot createPanelFluidSlot(GuiRectangle rect, FluidStack stack) {
        return new PanelFluidSlot(rect, -1, stack, false) {

            @Override
            public PanelButton setTooltip(List<String> tooltip) {
                if (tooltip != null && task.consume) {
                    tooltip.add(TextFormatting.RED + "[" + QuestTranslation.translate("bq_standard.tooltip.consume") + "]");
                }
                return super.setTooltip(tooltip);
            }

        };
    }

    private void setFold(boolean fold) {
        task.setFold(fold);
    }

}
