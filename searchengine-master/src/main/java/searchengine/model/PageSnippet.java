package searchengine.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageSnippet {

    private String url;
    private String title;
    private String snippet;
    private double relevance;

    public PageSnippet(String url, String title, String snippet, double relevance) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }
}
