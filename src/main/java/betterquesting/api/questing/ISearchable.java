package betterquesting.api.questing;

import java.util.List;

public interface ISearchable {

    default List<String> getTextForSearch() {
        return null;
    }
}
