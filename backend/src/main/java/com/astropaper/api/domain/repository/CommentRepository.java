package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.CommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    @EntityGraph(attributePaths = {"user", "parent"})
    Page<CommentEntity> findAllByPostIdAndStatus(Long postId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "post"})
    Page<CommentEntity> findAllByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "post"})
    Page<CommentEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "post"})
    Optional<CommentEntity> findWithModerationDetailsById(Long id);

    Optional<CommentEntity> findByIdAndPostIdAndStatus(Long id, Long postId, String status);
}
