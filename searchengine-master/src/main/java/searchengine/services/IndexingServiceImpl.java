package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.Helper.PageExtractor;
import searchengine.Helper.TextProcessor;
import searchengine.Repository.IndexRepository;
import searchengine.Repository.LemmaRepository;
import searchengine.Repository.PageRepository;
import searchengine.Repository.SiteRepository;
import searchengine.config.SitesList;
import searchengine.model.*;
import org.apache.logging.log4j.Logger;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private static final Logger LOGGER = LogManager.getLogger(IndexingServiceImpl.class);
    private static final int TERMINATION_AWAIT_TIME_HOURS = 24;
    public static volatile boolean inProgress;
    private static volatile boolean isStopped = false;
    private final SitesList sites;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private TextProcessor textProcessor;
    private ForkJoinPool pool;

    @Override
    public RequestAnswer startIndexing() {

        if (inProgress) {
            return new RequestAnswer(false, "Indexing is already started");
        } else {
            inProgress = true;
            new Thread(this::parseSites).start();
            return new RequestAnswer(true);
        }
    }

    private RequestAnswer parseSites() {
        for (Site site : sites.getSites()) {
            siteRepository.deleteByUrl(site.getUrl());
        }

        for (Site site : sites.getSites()) {
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            Site siteSaved = siteRepository.save(site);

            List<String> controlList = new ArrayList<>();
            controlList = Collections.synchronizedList(controlList);
            controlList.add(site.getUrl());

            PageExtractor parser = new PageExtractor(site.getUrl(), controlList, siteSaved,
                    pageRepository, siteRepository, lemmaRepository, indexRepository, textProcessor);
            pool = new ForkJoinPool();
            pool.execute(parser);
            pool.shutdown();

            awaitPoolTermination();

            if (isStopped) {
                isStopped = false;
                return new RequestAnswer(false, "Indexing stopped by user");
            }

            siteSaved.setStatus(Status.INDEXED);
            siteSaved.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteSaved);
        }

        System.out.println("Parsing complete");

        inProgress = false;
        return new RequestAnswer(true);
    }

    private void awaitPoolTermination() {
        try {
            if (!pool.awaitTermination(TERMINATION_AWAIT_TIME_HOURS, TimeUnit.HOURS)) {
                awaitPoolTermination();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public RequestAnswer stopIndexing() {
        if (!inProgress) {
            return new RequestAnswer(false, "Indexing is not started");
        } else {
            inProgress = false;
            isStopped = true;
        }

        pool.shutdownNow();

        List<Site> siteList = siteRepository.findAll();
        for (Site site : siteList) {
            if (site.getStatus() == Status.INDEXING) {
                site.updateStatusAndTime(Status.FAILED);
                site.setLastError("Indexing stopped by user");
                siteRepository.save(site);
            }
        }

        return new RequestAnswer(true);
    }

    public RequestAnswer indexPage(String url) {
        url = URLDecoder.decode(url, StandardCharsets.UTF_8);
        url = url.split("=")[1];
        String rootUrl = getSiteURL(url);

        if (!sites.contains(rootUrl)) {
            return new RequestAnswer(false, "Данная страница находится за пределами сайтов," +
                    " указанных в конфигурационном файле");
        }

        Site site = getActualSite(rootUrl);
        Status statusBeforeIndexing = site.getStatus() == null ? Status.FAILED : site.getStatus();
        site.updateStatusAndTime(Status.INDEXING);
        siteRepository.save(site);

        String pagePath = "/" + url.split("/", 4)[3];
        List<Page> pageList = pageRepository.findByPath(pagePath);
        if (pageList.size() > 0) {
            updateLemmasFrequency(pageList.get(0));
            pageRepository.deleteByPath(pagePath);
        }

        ArrayList<String> controlList = new ArrayList<>();
        inProgress = true;
        PageExtractor parser = new PageExtractor(url, controlList, site,
                pageRepository, siteRepository, lemmaRepository, indexRepository, textProcessor);
        parser.compute();
        inProgress = false;
        parser.quietlyComplete();
        parser.join();

        site.updateStatusAndTime(statusBeforeIndexing);
        siteRepository.save(site);

        System.out.println("Page indexed");

        return new RequestAnswer(true);
    }

    private void updateLemmasFrequency(Page page) {
        List<Index> indexesOfPage = indexRepository.findByPageId(page.getId());
        indexesOfPage.forEach(index -> {
            Lemma indexLemma = index.getLemmaId();
            if (indexLemma.getFrequency() == 1) {
                lemmaRepository.delete(indexLemma);
            } else {
                indexLemma.setFrequency(indexLemma.getFrequency() - 1);
                lemmaRepository.save(indexLemma);
            }
        });

        lemmaRepository.flush();
    }

    private Site getActualSite(String rootUrl) {
        List<Site> siteListFromDB = siteRepository.findAll();
        Site site = null;

        for (Site siteFromList : siteListFromDB) {
            if (siteFromList.getUrl().contains(rootUrl)) {
                site = siteFromList;
                break;
            }
        }

        if (site == null) {

            site = sites.getSiteByURL(rootUrl);
            site.updateStatusAndTime(Status.INDEXING);

            siteRepository.save(site);
        }

        return site;
    }

    private String getSiteURL(String url) {
        String[] split = url.split("/");
        if (split[0].startsWith("http") && split.length >= 2) {
            url = split[2];
        } else {
            url = split[0];
        }
        return url;
    }

}
