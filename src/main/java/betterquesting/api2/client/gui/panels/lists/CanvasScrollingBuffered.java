package betterquesting.api2.client.gui.panels.lists;

import java.util.ArrayList;
import java.util.List;

import betterquesting.api2.client.gui.misc.ComparatorGuiDepth;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;

public class CanvasScrollingBuffered extends CanvasScrolling {

    private final List<IGuiPanel> buffer = new ArrayList<>();
    private boolean requiresSorting;

    public CanvasScrollingBuffered(IGuiRect rect) {
        super(rect);
    }

    protected void addPanelToBuffer(IGuiPanel panel) {
        if (panel != null)
            buffer.add(panel);
    }

    public void flushBuffer() {
        if (buffer.isEmpty())
            return;

        guiPanels.addAll(buffer);
        for (IGuiPanel panel : buffer) {
            if (requiresSorting || panel.getTransform().getDepth() != guiPanels.get(0).getTransform().getDepth()) {
                requiresSorting = true;
            }
            cullingManager.addPanel(panel, true);
            panel.initPanel();
        }
        buffer.clear();

        if (requiresSorting) {
            guiPanels.sort(ComparatorGuiDepth.INSTANCE);
        }

        this.refreshScrollBounds();
    }

    @Override
    public void addCulledPanel(IGuiPanel panel, boolean useCulling)
    {
        if (panel == null || guiPanels.contains(panel)) return;

        guiPanels.add(panel);
        if (requiresSorting || panel.getTransform().getDepth() != guiPanels.get(0).getTransform().getDepth()) {
            requiresSorting = true;
            guiPanels.sort(ComparatorGuiDepth.INSTANCE);
        }

        cullingManager.addPanel(panel, useCulling);

        panel.initPanel();

        this.refreshScrollBounds();
    }

    @Override
    public void resetCanvas()
    {
        super.resetCanvas();
        requiresSorting = false;
    }
}
