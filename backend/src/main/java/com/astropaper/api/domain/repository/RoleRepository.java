package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from RoleEntity role where role.code = :code")
    Optional<RoleEntity> lockByCode(@Param("code") String code);

    List<RoleEntity> findAllByCodeIn(Collection<String> codes);

    List<RoleEntity> findAllByOrderByCodeAsc();
}
