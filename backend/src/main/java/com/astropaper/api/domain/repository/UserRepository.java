package com.astropaper.api.domain.repository;

import com.astropaper.api.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select distinct user from UserEntity user left join fetch user.roles role left join fetch role.permissions where user.id = :id")
    Optional<UserEntity> findByIdWithAuthorization(@Param("id") Long id);

    @Query("select count(distinct user.id) from UserEntity user join user.roles role where role.code = 'ADMIN' and user.status = 'ACTIVE'")
    long countActiveAdministrators();
}
