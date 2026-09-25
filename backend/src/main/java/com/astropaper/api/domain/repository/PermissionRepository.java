package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findByCode(String code);

    List<PermissionEntity> findAllByCodeIn(Collection<String> codes);

    List<PermissionEntity> findAllByOrderByCodeAsc();
}
