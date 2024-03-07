package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.Helper.TextProcessor;
import searchengine.Repository.IndexRepository;
import searchengine.Repository.LemmaRepository;
import searchengine.model.*;

import java.util.*;

@Service
public class SearchServiceImpl implements SearchService{

    public static final int HIGH_FREQUENCY = 100;
    public static final int WORDS_AROUND = 3;

    @Autowired
    private final TextProcessor textProcessor;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;

    public SearchServiceImpl(TextProcessor textProcessor, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
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

        List<Lemma> queryLemmas = new ArrayList<>();
        for (String queryWord : queryWords) {
            String lemma = textProcessor.getLemma(queryWord);
            queryLemmas.addAll(lemmaRepository.findByLemma(lemma));
            if (queryLemmas.isEmpty()) {
                return new RequestAnswer(false, "No results");
            }
        }

        if (site != null && !site.isEmpty()) {
            queryLemmas.removeIf(lemma -> !lemma.getSiteId().getUrl().contains(site));
        }

        if (queryLemmas.isEmpty()) {
            return new RequestAnswer(false, "No results");
        }

        Iterator<Lemma> iterator = queryLemmas.iterator();
        while (iterator.hasNext()) {
            Lemma lemma = iterator.next();
            if (lemma.getFrequency() > HIGH_FREQUENCY && queryLemmas.size() > 1) {
                iterator.remove();
            }
        }

        queryLemmas.sort(Comparator.comparing(Lemma::getFrequency).reversed());

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

        if (pages.isEmpty()) {
            return new RequestAnswer(false, "no results");
        }

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

        for (Map.Entry<Page, Double[]> entry : pagesRelevance.entrySet()) {
            Double[] relevanceValues = entry.getValue();
            relevanceValues[queryLemmas.size() + 1] = relevanceValues[queryLemmas.size()] / maxAbsoluteRelevance;
        }

        List<Page> sortedPages = new ArrayList<>(pagesRelevance.keySet());
        sortedPages.sort(new PageByRelativeRelevanceComparator(pagesRelevance));

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

        return new SearchRequestAnswer(pages.size(), searchResults);

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