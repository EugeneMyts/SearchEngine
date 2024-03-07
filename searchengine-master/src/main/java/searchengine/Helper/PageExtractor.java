package searchengine.Helper;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.Repository.IndexRepository;
import searchengine.Repository.LemmaRepository;
import searchengine.Repository.PageRepository;
import searchengine.Repository.SiteRepository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveAction;

public class PageExtractor extends RecursiveAction {
    private static final String USER_AGENT = "WondererSearchBot";
    private static final String REFERRER = "http://www.google.com";
    private static final int MIN_DELAY = 50;
    private static final int MAX_DELAY = 150;

    private final String url;
    private final List<String> controlList;
    private final Page page;
    private final Site site;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final TextProcessor textProcessor;

    private Connection connection;

    public PageExtractor(String url,
                               List<String> controlList,
                               Site site,
                               PageRepository pageRepository,
                               SiteRepository siteRepository,
                               LemmaRepository lemmaRepository,
                               IndexRepository indexRepository,
                               TextProcessor textProcessor) {
        this.url = url;
        this.controlList = controlList;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.textProcessor = textProcessor;
        this.page = new Page();
    }

    @Override
    public void compute() {
        if(!IndexingServiceImpl.inProgress) {
            return;
        }

        page.setSiteId(site);
        String pageUrl = getPageUrl();
        page.setPath(pageUrl);
        Document pageAsDocument = getPageAsDocument();
        int statusCode = getStatusCode();
        page.setCode(statusCode);
        page.setContent(pageAsDocument == null ? null : pageAsDocument.toString());
        pageRepository.save(page);

        if (pageAsDocument != null) {
            Map<String, Integer> lemmas = textProcessor.countLemmas(pageAsDocument.toString());
            lemmas.forEach((value, rate) -> {
                Lemma lemma = new Lemma();
                lemma.setLemma(value);
                lemma.setSiteId(site);
                lemma = addLemmaToDB(lemma);
                createOrUpdateIndexForLemma(lemma, page, rate);
            });
        }

        updateSiteStatusTime();

        if(!IndexingServiceImpl.inProgress) {
            return;
        }

        if (statusCode != 200 || pageAsDocument == null) {
            return;
        }

        Elements linkElements = pageAsDocument.select("a");
        HashSet<String> validLinks = validateLinks(linkElements);

        for (String link : validLinks) {
            controlList.add(link);
            PageExtractor task = new PageExtractor(link, controlList, site,
                    pageRepository, siteRepository, lemmaRepository, indexRepository, textProcessor);
            task.fork();
        }
    }

    private void createOrUpdateIndexForLemma(Lemma lemma, Page page, int rate) {
        Index index = new Index();
        index.setLemmaId(lemma);
        index.setPageId(page);
        index.setRank(rate);
        lemmaRepository.save(lemma);
        indexRepository.save(index);
    }

    private Lemma addLemmaToDB(Lemma lemma) {
        List<Lemma> lemmaList = lemmaRepository.findByLemma(lemma.getLemma());

        if (lemmaList.size() == 0) {
            lemma.setFrequency(1);
            return lemmaRepository.save(lemma);
        } else {
            Lemma lemmaFromDb = lemmaList.get(0);
            lemmaFromDb.setFrequency(lemmaFromDb.getFrequency() + 1);
            return lemmaRepository.save(lemmaFromDb);
        }
    }

    private String getPageUrl() {
        String pageUrl = url.replace(site.getUrl(), "");
        pageUrl = pageUrl.isBlank() ? url : pageUrl;
        return pageUrl;
    }

    private Document getPageAsDocument() {
        connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(REFERRER);

        System.out.println("Parsing: " + url);
        Document document;

        try {
            Thread.sleep((int) ((Math.random() * MIN_DELAY) + MAX_DELAY));
            document = connection.get();
        } catch (HttpStatusException statusException) {
            page.setCode(statusException.getStatusCode());
            System.out.println(url + " - STATUS FAILURE");
            return null;
        } catch (IOException ioException) {
            System.out.println(url + " - ILLEGAL ARGUMENT");
            return null;
        } catch (InterruptedException interruptedException) {
            throw new RuntimeException(interruptedException);
        }

        return document;
    }

    private int getStatusCode() {
        int statusCode = 0;
        if (page.getCode() == 0) {
            statusCode = connection.response().statusCode();
        }
        return statusCode;
    }

    private void updateSiteStatusTime() {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    private HashSet<String> validateLinks(Elements linkElements) {
        HashSet<String> validLinks = new HashSet<>();

        for (Element link : linkElements) {
            String href = link.absUrl("href");

            if (href.contains(" ")) {
                continue;
            }


            String httpSVar = url.split("://", 2)[0];
            if (!href.startsWith(httpSVar)) {
                continue;
            } else if (controlList.contains(href)) {
                continue;
            }

            if (href.split("/").length <= url.split("/").length) {
                continue;
            }

            if (href.endsWith(".jpg")) {
                continue;
            }


            // Then check if this link is internal service
            boolean linkIsExternal = false;
            boolean linkIsInnerService = false;
            String[] split = href.split("/");
            String rootGeneralNamePart = controlList.get(0).split("/")[2];
            rootGeneralNamePart = rootGeneralNamePart.replace("www.", "");
            if (split.length > 2) {
                linkIsExternal = !split[2].equals(rootGeneralNamePart)
                        && !split[2].equals("www." + rootGeneralNamePart);
                linkIsInnerService = split[split.length - 1].contains("#");
            }

            if (!linkIsExternal && !linkIsInnerService) {
                validLinks.add(href);
            }

        }
        return validLinks;
    }
}
