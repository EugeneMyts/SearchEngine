package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {
    @Transactional
    void deleteByUrl(String url);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM site")
    int sitesTotal();

    @Query(nativeQuery = true,
            value = "SELECT * FROM site")
    List<Site> findAll();
}
