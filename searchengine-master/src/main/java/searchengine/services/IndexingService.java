package searchengine.services;

import searchengine.model.RequestAnswer;

public interface IndexingService {

    RequestAnswer startIndexing();

    RequestAnswer stopIndexing();

    RequestAnswer indexPage(String url);
}
