package com.DockerOps.repository.apps;

import com.DockerOps.model.apps.App;
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
public interface AppRepository extends JpaRepository<App, UUID> {

    List<App> findByStack_Id(UUID stackId);
    Optional<App> findByContainerName(String containerName);
    Optional<App> findByIdAndStack_Id(UUID id, UUID stackId);
    boolean existsByName(String name);

    @Query("select a from App a join fetch a.stack where a.containerName = :containerName")
    Optional<App> findByContainerNameWithStack(@Param("containerName") String containerName);

    @Modifying
    @Query("update App a set a.appOwner = :newOwner where a.appOwner = :oldOwner")
    void reassignOwner(@Param("oldOwner") User oldOwner, @Param("newOwner") User newOwner);
}
