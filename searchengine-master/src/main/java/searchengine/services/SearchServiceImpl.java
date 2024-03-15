package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.helper.TextProcessor;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.model.*;
import java.util.*;
@Service
public class SearchServiceImpl implements SearchService{

    public static final int HIGH_FREQUENCY = 100;
    public static final int WORDS_AROUND = 3;

    private final TextProcessor textProcessor;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public SearchServiceImpl(TextProcessor textProcessor,
                             LemmaRepository lemmaRepository,
                             IndexRepository indexRepository) {
        this.textProcessor = textProcessor;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public RequestAnswer search(String query, int offset, int limit, String site) {
        List<String> queryWords = splitQueryForLemmas(query);

        if (queryWords.isEmpty()) {
            return new RequestAnswer(false, "No results");
        }

        List<Lemma> queryLemmas = findLemmasByQuery(queryWords);


        if (site != null && !site.isEmpty()) {
            filterLemmasBySite(queryLemmas, site);
        }

        if (queryLemmas.isEmpty()) {
            return new RequestAnswer(false, "No results");
        }

        filterHighFrequencyLemmas(queryLemmas);

        if (queryLemmas.isEmpty()) {
            return new RequestAnswer(false, "No results");
        }

        queryLemmas.sort(Comparator.comparing(Lemma::getFrequency).reversed());

        List<Page> pages = filterPagesByLemmas(queryLemmas);

        if (pages.isEmpty()) {
            return new RequestAnswer(false, "No results");
        }


        // Расчет абсолютной релевантности для каждой страницы и максимальной абсолютной релевантности
        HashMap<Page, Double[]> pagesRelevance = new HashMap<>();
        double maxAbsoluteRelevance = 0.0;

        for (Page page : pages) {
            Double[] relevanceValues = new Double[queryLemmas.size() + 2];
            relevanceValues[0] = 0.0;
            double absoluteRelevance = 0.0;
            for (int i = 0; i < queryLemmas.size(); i++) {
                List<Index> indexes = indexRepository.findByLemmaIdAndPageId(queryLemmas.get(i).getId(), page.getId());
                relevanceValues[i] = (double) indexes.get(0).getRank();
                absoluteRelevance += relevanceValues[i];
            }

            relevanceValues[queryLemmas.size()] = absoluteRelevance;
            pagesRelevance.put(page, relevanceValues);

            if (absoluteRelevance > maxAbsoluteRelevance) {
                maxAbsoluteRelevance = absoluteRelevance;
            }
        }

        // Расчет относительной релевантности для каждой страницы
        for (Map.Entry<Page, Double[]> entry : pagesRelevance.entrySet()) {
            Double[] relevanceValues = entry.getValue();
            relevanceValues[queryLemmas.size() + 1] = relevanceValues[queryLemmas.size()] / maxAbsoluteRelevance;
        }

        // Сортировка страниц по относительной релевантности
        List<Page> sortedPages = new ArrayList<>(pagesRelevance.keySet());
        sortedPages.sort(new PageByRelativeRelevanceComparator(pagesRelevance));

        // Формирование списка результатов поиска с учетом ограничений offset и limit
        ArrayList<SearchResult> searchResults = new ArrayList<>();
        limit = Math.min(limit, sortedPages.size());
        for(int i = offset; i < limit; i++) {
            Page page = sortedPages.get(i);
            SearchResult searchResult = new SearchResult(page.getSiteId().getUrl(),
                    page.getSiteId().getName(),
                    page.getPath(),
                    getTitle(page),
                    textProcessor.createSnippet(page.getContent(), query, WORDS_AROUND),
                    pagesRelevance.get(page)[queryLemmas.size() + 1]);
            searchResults.add(searchResult);
        }

        // Формирование ответа на запрос поиска
        return new SearchRequestAnswer(pages.size(), searchResults);

    }


    private List<Page> filterPagesByLemmas(List<Lemma> queryLemmas) {
        Set<Page> pages = new HashSet<>();
        indexRepository.findByLemmaId(queryLemmas.get(0).getId()).forEach(index -> pages.add(index.getPageId()));
        for (int i = 1; i < queryLemmas.size(); i++) {
            Iterator<Page> pageIterator = pages.iterator();
            while (pageIterator.hasNext()) {
                Page page = pageIterator.next();
                if (indexRepository.findByLemmaIdAndPageId(queryLemmas.get(i).getId(), page.getId()).isEmpty()) {
                    pageIterator.remove();
                }
            }
        }
        return new ArrayList<>(pages);
    }

    private void filterHighFrequencyLemmas(List<Lemma> queryLemmas) {
        queryLemmas.removeIf(lemma -> lemma.getFrequency() > HIGH_FREQUENCY && queryLemmas.size() > 1);
    }

    private void filterLemmasBySite(List<Lemma> queryLemmas, String site) {
        queryLemmas.removeIf(lemma -> !lemma.getSiteId().getUrl().contains(site));
    }

    private List<Lemma> findLemmasByQuery(List<String> queryWords) {
        List<Lemma> queryLemmas = new ArrayList<>();
        for (String queryWord : queryWords) {
            String lemma = textProcessor.getLemma(queryWord);
            queryLemmas.addAll(lemmaRepository.findByLemma(lemma));
            if (queryLemmas.isEmpty()) {
                return Collections.emptyList();
            }
        }
        return queryLemmas;
    }

    private List<String> splitQueryForLemmas(String query) {
        query = query.toLowerCase();
        query = textProcessor.removePunctuation(query);
        String[] split = query.split(" ");
        List<String> queryWords = new ArrayList<>(Arrays.asList(split));
        queryWords.removeIf(String::isEmpty);
        queryWords.forEach(textProcessor::getLemma);

        return queryWords;
    }

    private String getTitle(Page page) {
        String pageContent = page.getContent();
        int titleTextStartIndex = pageContent.indexOf("<title>");
        int titleTextEndIndex = pageContent.indexOf("</title>");

        if (titleTextStartIndex == -1 || titleTextEndIndex == -1) {
            return "";
        }

        titleTextStartIndex += 7;

        return pageContent.substring(titleTextStartIndex, titleTextEndIndex);
    }

    static class PageByRelativeRelevanceComparator implements Comparator<Page> {
        Map<Page, Double[]> base;

        public PageByRelativeRelevanceComparator(Map<Page, Double[]> base) {
            this.base = base;
        }

        @Override
        public int compare(Page a, Page b) {

            Double[] aRelevanceValues = base.get(a);
            Double[] bRelevanceValues = base.get(b);

            Double aRelativeRelevance = aRelevanceValues[aRelevanceValues.length -1];
            Double bRelativeRelevance = bRelevanceValues[bRelevanceValues.length - 1];


            return bRelativeRelevance.compareTo(aRelativeRelevance);
        }
    }

}
