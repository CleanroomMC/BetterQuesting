package betterquesting.api.properties;

public interface IPropertyListener<T> {
    void propertyChanged(IPropertyType<T> prop, T newValue);
}
