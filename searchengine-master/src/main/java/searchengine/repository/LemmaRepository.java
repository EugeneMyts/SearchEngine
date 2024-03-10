package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Query(nativeQuery = true,
            value = "select * from lemma where lemma.lemma=?1")
    List<Lemma> findByLemma(String lemma);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM lemma l")
    int lemmasTotal();

    @Query(nativeQuery = true,
            value = "SELECT * FROM lemma")
    List<Lemma> findAll();

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM Lemma WHERE lemma.site_id = ?1")
    int countBySiteId(long siteId);
}

