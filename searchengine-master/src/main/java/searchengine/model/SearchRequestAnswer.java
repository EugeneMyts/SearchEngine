package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
public class SearchRequestAnswer extends RequestAnswer {
    private int count;
    private ArrayList<SearchResult> data;

    public SearchRequestAnswer(int count, ArrayList<SearchResult> data) {
        super(true);
        this.count = count;
        this.data = data;
    }

    public void addSearchResult(SearchResult searchResult) {
        data.add(searchResult);
    }
}
