package searchengine.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

import java.util.List;
@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findByPath(String path);

    @Transactional
    long deleteByPath(String path);

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM page")
    int pagesTotal();

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM `page` WHERE `page`.site_id = ?1")
    int countBySiteId(long siteId);
}