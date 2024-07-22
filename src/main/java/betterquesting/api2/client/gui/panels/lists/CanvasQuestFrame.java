package betterquesting.api2.client.gui.panels.lists;

import betterquesting.api.enums.EnumFrameType;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.panels.content.PanelQuestFrame;

public class CanvasQuestFrame extends CanvasScrolling {

    private int resultWidth = 256; // Used for organising ongoing search results even if the size changes midway

    private final int btnId;

    public CanvasQuestFrame(IGuiRect rect, int buttonId) {
        super(rect);

        this.btnId = buttonId;
    }

    public void setQuestState(EnumQuestState questState) {
        for (IGuiPanel panel : getChildren()) {
            ((PanelQuestFrame) panel).setQuestState(questState);
        }
    }

    @Override
    public void initPanel() {
        super.initPanel();
        resetCanvas();
        this.resultWidth = this.getTransform().getWidth();
        updateResults();
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        //        updateResults();
        super.drawPanel(mx, my, partialTick);
    }

    private void updateResults() {
        EnumFrameType[] values = EnumFrameType.values();
        for (int i = 0; i < values.length; i++) {
            EnumFrameType frameType = values[i];
            int x = (i % (resultWidth / 18)) * 18;
            int y = (i / (resultWidth / 18)) * 18;

            this.addPanel(new PanelQuestFrame(new GuiRectangle(x, y, 18, 18, 0), btnId, frameType));
        }

    }

}
