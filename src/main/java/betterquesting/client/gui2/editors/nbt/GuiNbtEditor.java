package betterquesting.client.gui2.editors.nbt;

import org.lwjgl.input.Keyboard;

import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.misc.ICallback;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.callbacks.CallbackNBTTagString;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.editors.GuiTextEditor;
import betterquesting.client.gui2.editors.nbt.callback.NbtEntityCallback;
import betterquesting.client.gui2.editors.nbt.callback.NbtFluidCallback;
import betterquesting.client.gui2.editors.nbt.callback.NbtItemCallback;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

public class GuiNbtEditor extends GuiScreenCanvas implements IPEventListener, IVolatileScreen {

    public static GuiScreen createEditorGui(GuiScreen parent, NBTTagCompound nbt, String targetKey) {
        NBTBase entry;
        if (nbt.getId() == 10) {
            entry = nbt.getTag(targetKey);
        } else {
            throw new RuntimeException("Invalid NBT tag type!");
        }

        if (entry.getId() == 10) // Object editor
        {
            NBTTagCompound tag = (NBTTagCompound) entry;

            if (JsonHelper.isItem(tag)) {
                return new GuiItemSelection(parent, tag, new NbtItemCallback(tag));
            } else if (JsonHelper.isFluid(tag)) {
                return new GuiFluidSelection(parent, tag, new NbtFluidCallback(tag));
            } else if (JsonHelper.isEntity(tag)) {
                return new GuiEntitySelection(parent, tag, new NbtEntityCallback(tag));
            } else {
                return new GuiNbtEditor(parent, tag, null);
            }
        } else if (entry.getId() == 9) // List editor
        {
            return new GuiNbtEditor(parent, (NBTTagList) entry, null);
        } else if (entry.getId() == 8) // Text editor
        {
            return new GuiTextEditor(parent, ((NBTTagString) entry).getString(), new CallbackNBTTagString(nbt, targetKey));
        } else if (entry.getId() == 7 || entry.getId() == 11 || entry.getId() == 12) // Byte/Integer/Long array
        {
            // TODO: Add supportted editors for Byte, Integer and Long Arrays
            throw new UnsupportedOperationException("NBTTagByteArray, NBTTagIntArray and NBTTagLongArray are not currently supported yet");
        } else {
            throw new RuntimeException("Invalid NBT tag type!");
        }
    }

    public static GuiScreen createEditorGui(GuiScreen parent, NBTTagList nbt, int targetIndex) {
        if (nbt.getId() != 9)
            throw new RuntimeException("Invalid NBT tag type!");

        NBTBase entry = nbt.get(targetIndex);
        if (entry.getId() == 10) // Object editor
        {
            NBTTagCompound tag = (NBTTagCompound) entry;

            if (JsonHelper.isItem(tag)) {
                return new GuiItemSelection(parent, tag, new NbtItemCallback(tag));
            } else if (JsonHelper.isFluid(tag)) {
                return new GuiFluidSelection(parent, tag, new NbtFluidCallback(tag));
            } else if (JsonHelper.isEntity(tag)) {
                return new GuiEntitySelection(parent, tag, new NbtEntityCallback(tag));
            } else {
                return new GuiNbtEditor(parent, tag, null);
            }
        } else if (entry.getId() == 9) // List editor
        {
            return new GuiNbtEditor(parent, (NBTTagList) entry, null);
        } else if (entry.getId() == 8) // Text editor
        {
            return new GuiTextEditor(parent, ((NBTTagString) entry).getString(), new CallbackNBTTagString(nbt, targetIndex));
        } else if (entry.getId() == 7 || entry.getId() == 11 || entry.getId() == 12) // Byte/Integer/Long array
        {
            // TODO: Add supportted editors for Byte, Integer and Long Arrays
            throw new UnsupportedOperationException("NBTTagByteArray, NBTTagIntArray and NBTTagLongArray are not currently supported yet");
        } else {
            throw new RuntimeException("Invalid NBT tag type!");
        }
    }

    private final NBTBase nbt;
    private final ICallback<NBTTagCompound> comCallback;
    private final ICallback<NBTTagList> lstCallback;

    public GuiNbtEditor(GuiScreen parent, NBTTagCompound tag, ICallback<NBTTagCompound> callback) {
        super(parent);

        this.nbt = tag;
        this.comCallback = callback;
        this.lstCallback = null;
    }

    public GuiNbtEditor(GuiScreen parent, NBTTagList tag, ICallback<NBTTagList> callback) {
        super(parent);

        this.nbt = tag;
        this.comCallback = null;
        this.lstCallback = callback;
    }

    public void initPanel() {
        super.initPanel();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
                                                         PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0), 0, QuestTranslation.translate("gui.back")));

        PanelTextBox txTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0),
                                                QuestTranslation.translate(nbt.getId() == 9 ? "betterquesting.title.json_array" :
                                                        "betterquesting.title.json_object")).setAlignment(1);
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);

        PanelScrollingNBT pnEdit;
        if (nbt.getId() == 10) {
            pnEdit = new PanelScrollingNBT(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 32, 24, 32), 0), (NBTTagCompound) nbt, 1, 2, 3, 4, 5, 6);
        } else {
            pnEdit = new PanelScrollingNBT(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 32, 24, 32), 0), (NBTTagList) nbt, 1, 2, 3, 4, 5, 6);
        }
        cvBackground.addPanel(pnEdit);

        PanelVScrollBar scEdit = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-24, 32, 16, 32), 0));
        cvBackground.addPanel(scEdit);
        pnEdit.setScrollDriverY(scEdit);

        // === DECORATIVE LINES ===

        IGuiRect ls0 = new GuiTransform(GuiAlign.TOP_LEFT, 16, 32, 0, 0, 0);
        ls0.setParent(cvBackground.getTransform());
        IGuiRect le0 = new GuiTransform(GuiAlign.TOP_RIGHT, -16, 32, 0, 0, 0);
        le0.setParent(cvBackground.getTransform());
        PanelLine paLine0 = new PanelLine(ls0, le0, PresetLine.GUI_DIVIDER.getLine(), 1, PresetColor.GUI_DIVIDER.getColor(), -1);
        cvBackground.addPanel(paLine0);

        IGuiRect ls1 = new GuiTransform(GuiAlign.BOTTOM_LEFT, 16, -32, 0, 0, 0);
        ls1.setParent(cvBackground.getTransform());
        IGuiRect le1 = new GuiTransform(GuiAlign.BOTTOM_RIGHT, -16, -32, 0, 0, 0);
        le1.setParent(cvBackground.getTransform());
        PanelLine paLine1 = new PanelLine(ls1, le1, PresetLine.GUI_DIVIDER.getLine(), 1, PresetColor.GUI_DIVIDER.getColor(), -1);
        cvBackground.addPanel(paLine1);
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    private void onButtonPress(PEventButton event) {
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 0) // Exit
        {
            mc.displayGuiScreen(this.parent);

            if (nbt.getId() == 10 && comCallback != null) {
                comCallback.setValue((NBTTagCompound) nbt);
            } else if (nbt.getId() == 9 && lstCallback != null) {
                lstCallback.setValue((NBTTagList) nbt);
            }
        }
    }

}
