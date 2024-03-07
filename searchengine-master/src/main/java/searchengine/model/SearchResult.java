package searchengine.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchResult {

    private String site;
    private String siteName;
    private String url;
    private String title;
    private String snippet;
    private double relevance;

    public SearchResult(String site, String siteName, String url, String title, String snippet, double relevance) {
        this.site = site;
        this.siteName = siteName;
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }
}
