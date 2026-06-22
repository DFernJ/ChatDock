package com.DockerOps.repository.apps;

import com.DockerOps.model.apps.AppSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppSecretRepository extends JpaRepository<AppSecret, UUID> {

    List<AppSecret> findByApp_Id(UUID appId);
}
