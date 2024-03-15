package betterquesting;

import org.apache.commons.lang3.EnumUtils;

public class EnumUtil {

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E getEnum(String enumName, E defaultValue) {
        E value = (E) EnumUtils.getEnum(defaultValue.getClass(), enumName);
        return value != null ? value : defaultValue;
    }

}
