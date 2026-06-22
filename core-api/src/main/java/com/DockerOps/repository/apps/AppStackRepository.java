package com.DockerOps.repository.apps;

import com.DockerOps.model.apps.AppStack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppStackRepository extends JpaRepository<AppStack, UUID> {

    Optional<AppStack> findByStackNameIgnoreCase(String stackName);
}
