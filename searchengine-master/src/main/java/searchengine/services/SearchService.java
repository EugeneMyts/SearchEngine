package searchengine.services;

import searchengine.model.RequestAnswer;

public interface SearchService {

    RequestAnswer search(String query, int offset, int limit, String site);
}
