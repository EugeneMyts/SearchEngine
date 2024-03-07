package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.Repository.LemmaRepository;
import searchengine.Repository.PageRepository;
import searchengine.Repository.SiteRepository;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    private final Random random = new Random();
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        int totalSites = siteRepository.sitesTotal();
        total.setSites(totalSites);
        int totalPages = pageRepository.pagesTotal();
        total.setPages(totalPages);
        int totalLemmas = lemmaRepository.lemmasTotal();
        total.setLemmas(totalLemmas);

        total.setIndexing(IndexingServiceImpl.inProgress);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = siteRepository.findAll();
        for (Site site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int siteId = site.getId();
            int pages = pageRepository.countBySiteId(siteId);
            item.setPages(pages);
            int lemmas = lemmaRepository.countBySiteId(siteId);
            item.setLemmas(lemmas);
            item.setStatus(site.getStatus().toString());
            item.setError(site.getLastError());
            item.setStatusTime(ZonedDateTime.of(site.getStatusTime(), ZoneId.systemDefault())
                    .toInstant().toEpochMilli());
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;


    }

}
