package betterquesting.api2.client.gui.controls;

import betterquesting.api.misc.ICallback;
import betterquesting.api2.client.gui.misc.IGuiRect;

public class PanelButtonStorage<T> extends PanelButton {
    private T stored = null;
    private ICallback<T> callback = null;

    /**
     * Creates a panel button that stores a value.
     * <p>
     *     Note: If a subclass overrides {@link #setStoredValue(T)}, make sure to call that method during construction
     *     time after calling this super constructor!
     * </p>
     */
    public PanelButtonStorage(IGuiRect rect, int id, String txt, T value) {
        super(rect, id, txt);
        setStoredRaw(value);
    }

    private void setStoredRaw(T value) {
        stored = value;
    }

    public PanelButtonStorage<T> setStoredValue(T value) {
        setStoredRaw(value);
        return this;
    }

    public T getStoredValue() {
        return stored;
    }

    public PanelButtonStorage<T> setCallback(ICallback<T> callback) {
        this.callback = callback;
        return this;
    }

    public ICallback<T> getCallback() {
        return this.callback;
    }

    @Override
    public void onButtonClick() {
        if (callback != null) this.callback.setValue(this.getStoredValue());
    }
}
