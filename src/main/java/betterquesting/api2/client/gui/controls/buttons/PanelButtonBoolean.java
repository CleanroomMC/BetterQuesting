package betterquesting.api2.client.gui.controls.buttons;

import betterquesting.api.misc.ICallback;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.IGuiRect;

public class PanelButtonBoolean extends PanelButton {

    private boolean stored;
    private ICallback<Boolean> callback = null;

    public PanelButtonBoolean(IGuiRect rect, int id, boolean value) {
        super(rect, id, Boolean.toString(value));
        this.setStoredValue(value);
    }

    public PanelButtonBoolean setStoredValue(boolean value) {
        this.stored = value;
        return this;
    }

    public boolean getStoredValue() { return stored; }

    public PanelButtonBoolean setCallback(ICallback<Boolean> callback) {
        this.callback = callback;
        return this;
    }

    public ICallback<Boolean> getCallback() { return this.callback; }

    @Override
    public void onButtonClick() {
        stored = !stored;
        setText(Boolean.toString(stored));
        if (callback != null)
            this.callback.setValue(this.getStoredValue());
    }

}
