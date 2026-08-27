package com.DockerOps.repository.apps;

import com.DockerOps.model.apps.AppStack;
import com.DockerOps.model.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppStackRepository extends JpaRepository<AppStack, UUID> {

    Optional<AppStack> findByStackNameIgnoreCase(String stackName);

    @Modifying
    @Query("update AppStack s set s.owner = :newOwner where s.owner = :oldOwner")
    void reassignOwner(@Param("oldOwner") User oldOwner, @Param("newOwner") User newOwner);
}
