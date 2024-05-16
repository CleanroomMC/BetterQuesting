package betterquesting.api2.client.gui.controls.buttons;

import betterquesting.api.misc.ICallback;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.IGuiRect;

public class PanelButtonEnum<E extends Enum<E>> extends PanelButton {

    private E stored;
    private ICallback<E> callback = null;

    public PanelButtonEnum(IGuiRect rect, int id, E value) {
        super(rect, id, value.toString());
        this.setStoredValue(value);
    }

    public PanelButtonEnum<E> setStoredValue(E value) {
        this.stored = value;
        return this;
    }

    public E getStoredValue() { return stored; }

    public PanelButtonEnum<E> setCallback(ICallback<E> callback) {
        this.callback = callback;
        return this;
    }

    public ICallback<E> getCallback() { return this.callback; }

    @Override
    public void onButtonClick() {
        @SuppressWarnings("unchecked")
        E[] values = (E[]) stored.getClass().getEnumConstants();
        stored = values[(stored.ordinal() + 1) % values.length];
        setText(stored.toString());
        if (callback != null)
            this.callback.setValue(this.getStoredValue());
    }

}
