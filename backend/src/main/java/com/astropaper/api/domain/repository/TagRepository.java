package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findBySlug(String slug);
}
