package com.DockerOps.service.users;

import com.DockerOps.repository.users.CodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class CodeGeneratorService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private CodeRepository codeRepository;

    public String generateUniqueCode() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            candidate = sb.toString();
        } while (codeRepository.existsByCode(candidate));
        return candidate;
    }
}
