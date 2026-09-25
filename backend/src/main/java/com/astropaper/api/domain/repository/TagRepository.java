package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findBySlug(String slug);

    Optional<TagEntity> findByNameIgnoreCase(String name);

    List<TagEntity> findAllByOrderByNameAsc();

    @Query(value = """
        select t.slug as slug, t.name as name, count(p.id) as postCount
        from tags t
        join post_tags pt on pt.tag_id = t.id
        join posts p on p.id = pt.post_id
        where p.status = 'PUBLISHED' and p.published_at is not null and p.published_at <= :now
        group by t.id, t.slug, t.name
        order by t.name
        """, nativeQuery = true)
    List<PublicTagCountProjection> findPublicTagCounts(@Param("now") Instant now);

    interface PublicTagCountProjection {
        String getSlug();
        String getName();
        long getPostCount();
    }
}
