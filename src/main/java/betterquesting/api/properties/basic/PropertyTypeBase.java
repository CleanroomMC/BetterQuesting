package betterquesting.api.properties.basic;

import java.util.ArrayList;
import java.util.List;

import betterquesting.api.properties.IPropertyListener;
import betterquesting.api.properties.IPropertyType;
import net.minecraft.util.ResourceLocation;

public abstract class PropertyTypeBase<T> implements IPropertyType<T> {
    private final ResourceLocation key;
    private final T def;
    private final List<IPropertyListener<T>> listeners = new ArrayList<>();

    public PropertyTypeBase(ResourceLocation key, T def) {
        this.key = key;
        this.def = def;
    }

    @Override
    public ResourceLocation getKey() {
        return key;
    }

    @Override
    public T getDefault() {
        return def;
    }

    @Override
    public void addListener(IPropertyListener<T> listener) {
        listeners.add(listener);
    }

    @Override
    public void notifyListeners(T newValue) {
        for (var listener : listeners) {
            listener.propertyChanged(this, newValue);
        }
    }
}
