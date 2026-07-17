package com.DockerOps.repository.users;

import com.DockerOps.model.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    Optional<User> findByDiscordId(Long discordId);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByDiscordId(Long discordId);
    boolean existsByGithubId(Long githubId);
}
