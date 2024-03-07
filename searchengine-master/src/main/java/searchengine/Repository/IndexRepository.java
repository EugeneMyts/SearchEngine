package searchengine.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {

    @Query(nativeQuery = true,
            value = "select * from `index` where `index`.lemma_id=?1")
    List<Index> findByLemmaId(Lemma lemma);

    @Query(nativeQuery = true,
            value = "select * from `index` where `index`.lemma_id=?1")
    List<Index> findByLemmaId(int lemmaId);

    @Query(nativeQuery = true,
            value = "select * from `index` where `index`.page_id=?1")
    List<Index> findByPageId(int pageId);

    @Query(nativeQuery = true,
            value = "select page_id from `index` where `index`.lemma_id=?1")
    List<Integer> findPageIdLemmaId(int lemmaId);

    @Query(nativeQuery = true,
            value = "SELECT * FROM search_engine.index i where i.lemma_id = :lemmaId AND i.page_id = :pageId;")
    Index findIndexWithLemmaAndPage(int lemmaId, int pageId);

    @Query(nativeQuery = true,
            value = "SELECT * FROM search_engine.`index` i where i.lemma_id = :lemmaId AND i.page_id = :pageId")
    List<Index> findByLemmaIdAndPageId(int lemmaId, int pageId);
}
