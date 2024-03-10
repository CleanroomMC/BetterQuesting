package betterquesting.api2.client.gui.panels.lists;

import java.util.ArrayList;

import betterquesting.api2.client.gui.misc.ComparatorGuiDepth;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;

public class CanvasScrollingBuffered extends CanvasScrolling {

    private final ArrayList<IGuiPanel> buffer = new ArrayList<>();

    public CanvasScrollingBuffered(IGuiRect rect) {
        super(rect);
    }

    public void addPanelToBuffer(IGuiPanel panel) {
        if (panel != null)
            buffer.add(panel);
    }

    public void flushBuffer() {
        if (buffer.isEmpty())
            return;
        for (IGuiPanel panel : buffer) {
            if (guiPanels.contains(panel))
                continue;

            guiPanels.add(panel);
            cullingManager.addPanel(panel, true);
            panel.initPanel();
        }
        buffer.clear();

        guiPanels.sort(ComparatorGuiDepth.INSTANCE);

        this.refreshScrollBounds();
    }

}
