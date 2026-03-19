package betterquesting.client.gui2.editors;

import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterString;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.panels.lists.CanvasSearch;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.registry.IFactoryData;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.storage.IDatabaseNBT;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.editors.nbt.GuiNbtEditor;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.tasks.TaskRegistry;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.util.vector.Vector4f;

public class GuiTaskEditor extends GuiScreenCanvas implements IPEventListener, IVolatileScreen, INeedsRefresh {

    private CanvasScrolling qtList;

    private IQuest quest;
    private final int qID;
    @Nullable // Not null when tasks are reordered.
    private Int2IntMap taskIndexBeforeReorder = null; // currentIndex -> oldIndex

    public GuiTaskEditor(GuiScreen parent, IQuest quest) {
        super(parent);

        this.quest = quest;
        this.qID = QuestDatabase.INSTANCE.getID(quest);
    }

    @Override
    public void refreshGui() {
        quest = QuestDatabase.INSTANCE.getValue(qID);

        if (quest == null) {
            mc.displayGuiScreen(this.parent);
            return;
        }

        refreshTasks();
    }

    @Override
    public void initPanel() {
        super.initPanel();

        if (qID < 0) {
            mc.displayGuiScreen(this.parent);
            return;
        }

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
                PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        PanelTextBox panTxt = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0),
                QuestTranslation.translate("betterquesting.title.edit_tasks")).setAlignment(1);
        panTxt.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(panTxt);

        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0), 0, QuestTranslation.translate("gui.back")));

        CanvasSearch<IFactoryData<ITask, NBTTagCompound>, IFactoryData<ITask, NBTTagCompound>> cvRegSearch = new CanvasSearch<IFactoryData<ITask, NBTTagCompound>, IFactoryData<ITask, NBTTagCompound>>((new GuiTransform(GuiAlign.HALF_RIGHT,
                new GuiPadding(8,
                        48,
                        24,
                        32),
                0))) {

            @Override
            protected Iterator<IFactoryData<ITask, NBTTagCompound>> getIterator() {
                List<IFactoryData<ITask, NBTTagCompound>> list = TaskRegistry.INSTANCE.getAll();
                list.sort(Comparator.comparing(o -> o.getRegistryName().toString().toLowerCase()));
                return list.iterator();
            }

            @Override
            protected void queryMatches(IFactoryData<ITask, NBTTagCompound> value, String query, ArrayDeque<IFactoryData<ITask, NBTTagCompound>> results) {
                if (value.getRegistryName().toString().toLowerCase().contains(query.toLowerCase()))
                    results.add(value);
            }

            @Override
            protected boolean addResult(IFactoryData<ITask, NBTTagCompound> entry, int index, int cachedWidth) {
                this.addPanel(new PanelButtonStorage<>(new GuiRectangle(0, index * 16, cachedWidth, 16, 0), 1, entry.getRegistryName().toString(), entry));
                return true;
            }

        };
        cvBackground.addPanel(cvRegSearch);

        PanelVScrollBar scReg = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-24, 48, 16, 32), 0));
        cvBackground.addPanel(scReg);
        cvRegSearch.setScrollDriverY(scReg);

        PanelTextField<String> tfSearch = new PanelTextField<>(new GuiTransform(new Vector4f(0.5F, 0F, 1F, 0F), new GuiPadding(8, 32, 16, -48), 0),
                "",
                FieldFilterString.INSTANCE);
        tfSearch.setCallback(cvRegSearch::setSearchFilter);
        tfSearch.setWatermark("Search...");
        cvBackground.addPanel(tfSearch);

        qtList = new CanvasScrolling(new GuiTransform(GuiAlign.HALF_LEFT, new GuiPadding(16, 32, 16, 32), 0));
        cvBackground.addPanel(qtList);

        PanelVScrollBar scTsk = new PanelVScrollBar(new GuiTransform(new Vector4f(0.5F, 0F, 0.5F, 1F), new GuiPadding(-16, 32, 8, 32), 0));
        cvBackground.addPanel(scTsk);
        qtList.setScrollDriverY(scTsk);

        // === DIVIDERS ===

        IGuiRect ls0 = new GuiTransform(GuiAlign.TOP_CENTER, 0, 32, 0, 0, 0);
        ls0.setParent(cvBackground.getTransform());
        IGuiRect le0 = new GuiTransform(GuiAlign.BOTTOM_CENTER, 0, -32, 0, 0, 0);
        le0.setParent(cvBackground.getTransform());
        PanelLine paLine0 = new PanelLine(ls0, le0, PresetLine.GUI_DIVIDER.getLine(), 1, PresetColor.GUI_DIVIDER.getColor(), 1);
        cvBackground.addPanel(paLine0);

        refreshTasks();
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    @SuppressWarnings("unchecked")
    private void onButtonPress(PEventButton event) {
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 0) // Exit
        {
            mc.displayGuiScreen(this.parent);
        } else if (btn.getButtonID() == 1 && btn instanceof PanelButtonStorage) // Add
        {
            IFactoryData<ITask, NBTTagCompound> fact = ((PanelButtonStorage<IFactoryData<ITask, NBTTagCompound>>) btn).getStoredValue();
            quest.getTasks().add(quest.getTasks().nextID(), fact.createNew());

            SendChanges();
        } else if (btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) // Remove
        {
            ITask task = ((PanelButtonStorage<ITask>) btn).getStoredValue();

            if (quest.getTasks().removeValue(task)) {
                SendChanges();
            }
        } else if (btn.getButtonID() == 3 && btn instanceof PanelButtonStorage) // Edit
        {
            ITask task = ((PanelButtonStorage<ITask>) btn).getStoredValue();
            GuiScreen editor = task.getTaskEditor(this, new DBEntry<>(qID, quest));

            if (editor != null) {
                mc.displayGuiScreen(editor);
            } else {
                mc.displayGuiScreen(new GuiNbtEditor(this, task.writeToNBT(new NBTTagCompound(), false), value -> {
                    task.readFromNBT(value);
                    SendChanges();
                }));
            }
        } else if (btn.getButtonID() == 4 && btn instanceof PanelButtonStorage) // Up
        {
            ITask task = ((PanelButtonStorage<ITask>) btn).getStoredValue();
            int idx = quest.getTasks().getID(task);
            reorder(idx, false);
        } else if (btn.getButtonID() == 5 && btn instanceof PanelButtonStorage) // Down
        {
            ITask task = ((PanelButtonStorage<ITask>) btn).getStoredValue();
            int idx = quest.getTasks().getID(task);
            reorder(idx, true);
        }
    }

    private void refreshTasks() {
        List<DBEntry<ITask>> dbTsk = quest.getTasks().getEntries();

        qtList.resetCanvas();
        int w = qtList.getTransform().getWidth();

        for (int i = 0; i < dbTsk.size(); i++) {
            ITask task = dbTsk.get(i).getValue();
            int index = taskIndexBeforeReorder == null ? i : taskIndexBeforeReorder.get(i);
            qtList.addPanel(new PanelButtonStorage<>(new GuiRectangle(0, i * 16, w - 48, 16, 0),
                    3,
                    (index + 1) + ". " + QuestTranslation.translate(task.getUnlocalisedName()),
                    task));

            PanelButton btnUp = new PanelButtonStorage<>(new GuiRectangle(w - 48, i * 16, 16, 16, 0), 4, "", task).setIcon(PresetIcon.ICON_UP.getTexture());
            btnUp.setActive(i > 0);
            qtList.addPanel(btnUp);
            PanelButton btnDown = new PanelButtonStorage<>(new GuiRectangle(w - 32, i * 16, 16, 16, 0), 5, "", task).setIcon(PresetIcon.ICON_DOWN.getTexture());
            btnDown.setActive(i < dbTsk.size() - 1);
            qtList.addPanel(btnDown);

            qtList.addPanel(new PanelButtonStorage<>(new GuiRectangle(w - 16, i * 16, 16, 16, 0),
                    2,
                    "" + TextFormatting.RED + TextFormatting.BOLD + "x",
                    task));
        }
    }

    private void SendChanges() {
        NBTTagCompound payload = new NBTTagCompound();
        NBTTagList dataList = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("questID", qID);
        entry.setTag("config", quest.writeToNBT(new NBTTagCompound(), true));
        dataList.appendTag(entry);
        payload.setTag("data", dataList);
        payload.setInteger("action", 0);
        NetQuestEdit.sendEdit(payload);
    }

    private void reorder(int index, boolean down) {

        int size = quest.getTasks().size();

        int indexFrom = (index + (down ? 1 : -1) + size) % size;

        IDatabaseNBT<ITask, NBTTagList, NBTTagList> tasks = quest.getTasks();

        if (taskIndexBeforeReorder == null) {
            taskIndexBeforeReorder = new Int2IntOpenHashMap();
            for (DBEntry<ITask> entry : tasks.getEntries()) {
                taskIndexBeforeReorder.put(entry.getID(), entry.getID());
            }
        }

        ITask task = tasks.getValue(index);
        ITask task2 = tasks.getValue(indexFrom);
        tasks.removeID(index);
        tasks.removeID(indexFrom);
        tasks.add(index, task2);
        tasks.add(indexFrom, task);

        int tmp = taskIndexBeforeReorder.get(index);
        taskIndexBeforeReorder.put(index, taskIndexBeforeReorder.get(indexFrom));
        taskIndexBeforeReorder.put(indexFrom, tmp);

        SendChanges();
    }

}
