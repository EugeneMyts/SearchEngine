package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Setter
@Getter
@Entity(name = "site")
@NoArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public Site(String url, String name) {
        this.url = url;
        this.name = name;

        updateStatusAndTime(Status.INDEXING);
    }

    public void updateStatusAndTime(Status status) {
        this.status = status;
        statusTime = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Site site = (Site) o;
        return id == site.id && Objects.equals(url, site.url) && Objects.equals(name, site.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url, name);
    }
}