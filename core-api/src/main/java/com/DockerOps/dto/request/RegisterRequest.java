package com.DockerOps.dto.request;

import com.DockerOps.model.users.enums.UserAuthRole;
import com.DockerOps.model.users.enums.UserPermissions;

public record RegisterRequest(String username, String email, String password, UserAuthRole userAuthRole, UserPermissions userPermissions) {
}
