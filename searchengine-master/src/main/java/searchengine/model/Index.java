package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Getter
@Setter
@Entity(name = "`index`")
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page pageId;

    @ManyToOne
    @JoinColumn(name = "lemma_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lemma lemmaId;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
