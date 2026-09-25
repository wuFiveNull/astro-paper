package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
}
