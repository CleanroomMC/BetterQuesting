package betterquesting.api.properties;

/**
 * A listener to run when a property's value changes.
 * @param <T> The property type
 */
@FunctionalInterface
public interface IPropertyListener<T> {
    void propertyChanged(IPropertyType<T> prop, T newValue);
}
