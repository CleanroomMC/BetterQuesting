package betterquesting.api2.client.gui.panels.lists;

import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import java.util.function.Function;

public class CanvasScrollingNameValue extends CanvasScrolling {

    private int rowNum = 0;

    public CanvasScrollingNameValue(IGuiRect rect) {
        super(rect);
    }

    public void addPanel(String name, Function<GuiRectangle, IGuiPanel> panelFactory) {
        int width = getTransform().getWidth();
        int lw = (int) (width / 3F);
        int rw = width - lw; // Width on right side (rounds up to account for rounding errors lost on left side)
        PanelTextBox namePanel = new PanelTextBox(new GuiRectangle(0, rowNum * 16 + 4, lw - 8, 12, 0), name).setAlignment(2);
        namePanel.setColor(PresetColor.TEXT_MAIN.getColor());
        addPanel(namePanel);
        GuiRectangle rect = new GuiRectangle(lw, rowNum * 16, rw - 32, 16);
        addPanel(panelFactory.apply(rect));
        rowNum++;
    }

}
