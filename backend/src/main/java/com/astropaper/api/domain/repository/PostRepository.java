package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

    Page<PostEntity> findAllByStatusAndPublishedAtLessThanEqualOrderByPublishedAtDesc(
        String status,
        Instant publishedAt,
        Pageable pageable
    );

    Optional<PostEntity> findBySlugAndStatusAndPublishedAtLessThanEqual(
        String slug,
        String status,
        Instant publishedAt
    );

    @Query(
        value = """
            select distinct p from PostEntity p
            where p.status = 'PUBLISHED'
              and p.publishedAt is not null
              and p.publishedAt <= :now
              and (:term is null
                   or lower(p.title) like lower(concat('%', :term, '%'))
                   or lower(p.description) like lower(concat('%', :term, '%'))
                   or lower(p.contentMarkdown) like lower(concat('%', :term, '%')))
            """,
        countQuery = """
            select count(p) from PostEntity p
            where p.status = 'PUBLISHED'
              and p.publishedAt is not null
              and p.publishedAt <= :now
              and (:term is null
                   or lower(p.title) like lower(concat('%', :term, '%'))
                   or lower(p.description) like lower(concat('%', :term, '%'))
                   or lower(p.contentMarkdown) like lower(concat('%', :term, '%')))
            """
    )
    Page<PostEntity> findPublicPosts(@Param("term") String term, @Param("now") Instant now, Pageable pageable);

    @Query(
        value = """
            select distinct p from PostEntity p join p.tags t
            where t.slug = :tagSlug
              and p.status = 'PUBLISHED'
              and p.publishedAt is not null
              and p.publishedAt <= :now
            """,
        countQuery = """
            select count(distinct p) from PostEntity p join p.tags t
            where t.slug = :tagSlug
              and p.status = 'PUBLISHED'
              and p.publishedAt is not null
              and p.publishedAt <= :now
            """
    )
    Page<PostEntity> findPublicPostsByTag(
        @Param("tagSlug") String tagSlug,
        @Param("now") Instant now,
        Pageable pageable
    );
}
