package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import searchengine.model.Site;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<Site> sites;

    public boolean contains(searchengine.model.Site site) {
        for (Site s : sites) {
            if (s.getUrl().equals(site.getUrl())) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(String url) {
        for (Site s : sites) {
            if (urlsAreEqual(url, s.getUrl())) {
                return true;
            }
        }

        return false;
    }

    public Site getSiteByURL(String url) {
        for (Site s : sites) {
            if (urlsAreEqual(url, s.getUrl())) {
                return s;
            }
        }
        return null;
    }

    private boolean urlsAreEqual(String url1, String url2) {
        if (url1.startsWith("http")) {
            url1 = url1.split("//", 2)[1];
        }

        if (url2.startsWith("http")) {
            url2 = url2.split("//", 2)[1];
        }

        return url1.equals(url2);
    }

}
