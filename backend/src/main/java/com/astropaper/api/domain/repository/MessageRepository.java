package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @EntityGraph(attributePaths = "user")
    Page<MessageEntity> findAllByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<MessageEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<MessageEntity> findWithUserById(Long id);
}
