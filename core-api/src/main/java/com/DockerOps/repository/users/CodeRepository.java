package com.DockerOps.repository.users;

import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.CodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeRepository extends JpaRepository<Code, UUID> {

    boolean existsByCode(String code);

    Optional<Code> findByCode(String code);

    List<Code> findAllByOrderByCreatedAtDesc();

    void deleteByUserAndCodeType(User user, CodeType codeType);

    void deleteByUser(User user);
}
