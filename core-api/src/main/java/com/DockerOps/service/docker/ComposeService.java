package com.DockerOps.service.docker;

import com.DockerOps.dto.container.ContainerVolumeDTO;
import com.DockerOps.dto.request.PortMappingDTO;
import com.DockerOps.dto.request.SecretDraftDTO;
import com.DockerOps.dto.response.ComposeServiceDTO;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ComposeService {

    @SuppressWarnings("unchecked")
    public List<ComposeServiceDTO> parse(Path composeFile, Path contextDir) {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(composeFile)) {
            Object loaded = new Yaml().load(in);
            root = loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Object servicesObj = root.get("services");
        if (!(servicesObj instanceof Map)) {
            return List.of();
        }
        Map<String, Object> servicesMap = (Map<String, Object>) servicesObj;

        List<ComposeServiceDTO> result = new ArrayList<>();
        for (var entry : servicesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            result.add(parseService(entry.getKey(), (Map<String, Object>) entry.getValue(), contextDir));
        }
        return result;
    }

    private ComposeServiceDTO parseService(String name, Map<String, Object> spec, Path contextDir) {
        String image = spec.get("image") instanceof String s ? s : null;
        String buildSubdir = null;
        boolean supported = true;
        String unsupportedReason = null;

        Object buildObj = spec.get("build");
        if (buildObj != null) {
            String rawContext = null;
            if (buildObj instanceof String bs) {
                rawContext = bs;
            } else if (buildObj instanceof Map<?, ?> bm && bm.get("context") instanceof String cs) {
                rawContext = cs;
            }
            if (rawContext == null) {
                supported = false;
                unsupportedReason = "Unrecognized 'build' definition";
            } else {
                Path resolved = contextDir.resolve(rawContext).normalize();
                if (!resolved.startsWith(contextDir.normalize())) {
                    supported = false;
                    unsupportedReason = "Build context escapes the uploaded project";
                } else {
                    buildSubdir = rawContext;
                }
            }
        }
        if (image == null && buildSubdir == null && supported) {
            supported = false;
            unsupportedReason = "No image or build context defined";
        }

        List<PortMappingDTO> ports = new ArrayList<>();
        if (spec.get("ports") instanceof List<?> rawPorts) {
            for (Object p : rawPorts) {
                if (p instanceof String ps) {
                    PortMappingDTO mapping = parsePort(ps);
                    if (mapping != null) ports.add(mapping);
                }
            }
        }

        List<ContainerVolumeDTO> volumes = new ArrayList<>();
        if (spec.get("volumes") instanceof List<?> rawVolumes) {
            for (Object v : rawVolumes) {
                if (!(v instanceof String vs)) continue;
                String[] parts = vs.split(":", 3);
                if (parts.length < 2) continue;
                String source = parts[0];
                String target = parts[1];
                if (source.startsWith("/") || source.startsWith(".")) {
                    supported = false;
                    unsupportedReason = "Host bind mounts aren't supported (volume '" + vs + "')";
                    continue;
                }
                boolean readOnly = parts.length > 2 && parts[2].contains("ro");
                volumes.add(new ContainerVolumeDTO(source, target, readOnly));
            }
        }

        List<String> dependsOn = new ArrayList<>();
        Object dependsObj = spec.get("depends_on");
        if (dependsObj instanceof List<?> list) {
            for (Object d : list) if (d instanceof String ds) dependsOn.add(ds);
        } else if (dependsObj instanceof Map<?, ?> map) {
            for (Object k : map.keySet()) if (k instanceof String ks) dependsOn.add(ks);
        }

        String restartPolicy = spec.get("restart") instanceof String rs ? mapRestartPolicy(rs) : "no";

        List<SecretDraftDTO> secrets = new ArrayList<>();
        Object envObj = spec.get("environment");
        if (envObj instanceof List<?> list) {
            for (Object e : list) {
                if (!(e instanceof String es)) continue;
                int idx = es.indexOf('=');
                if (idx > 0) {
                    secrets.add(new SecretDraftDTO(es.substring(0, idx), es.substring(idx + 1)));
                } else {
                    secrets.add(new SecretDraftDTO(es, ""));
                }
            }
        } else if (envObj instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                secrets.add(new SecretDraftDTO(String.valueOf(e.getKey()), e.getValue() != null ? String.valueOf(e.getValue()) : ""));
            }
        }

        return new ComposeServiceDTO(name, image, buildSubdir, ports, volumes, dependsOn, restartPolicy, secrets, supported, unsupportedReason);
    }

    private PortMappingDTO parsePort(String raw) {
        String protocol = "tcp";
        String portsPart = raw;
        int slash = raw.indexOf('/');
        if (slash > 0) {
            protocol = raw.substring(slash + 1);
            portsPart = raw.substring(0, slash);
        }
        String[] parts = portsPart.split(":");
        if (parts.length != 2) return null;
        try {
            int hostPort = Integer.parseInt(parts[0].trim());
            int containerPort = Integer.parseInt(parts[1].trim());
            return new PortMappingDTO(hostPort, containerPort, protocol);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String mapRestartPolicy(String raw) {
        return switch (raw) {
            case "always", "unless-stopped", "on-failure" -> raw;
            default -> "no";
        };
    }

    public List<String> topologicalOrder(List<ComposeServiceDTO> services, Set<String> included) {
        Map<String, ComposeServiceDTO> byName = new HashMap<>();
        for (ComposeServiceDTO s : services) byName.put(s.name(), s);

        Map<String, Set<String>> remainingDeps = new HashMap<>();
        for (String name : included) {
            Set<String> deps = new HashSet<>(byName.get(name).dependsOn());
            deps.retainAll(included);
            remainingDeps.put(name, deps);
        }

        List<String> order = new ArrayList<>();
        Deque<String> ready = new ArrayDeque<>();
        for (var entry : remainingDeps.entrySet()) {
            if (entry.getValue().isEmpty()) ready.add(entry.getKey());
        }
        while (!ready.isEmpty()) {
            String current = ready.poll();
            order.add(current);
            for (var entry : remainingDeps.entrySet()) {
                if (order.contains(entry.getKey()) || ready.contains(entry.getKey())) continue;
                if (entry.getValue().remove(current) && entry.getValue().isEmpty()) {
                    ready.add(entry.getKey());
                }
            }
        }
        if (order.size() != included.size()) {
            Set<String> unresolved = new HashSet<>(included);
            unresolved.removeAll(order);
            throw new IllegalArgumentException("Circular dependency detected among services: " + String.join(", ", unresolved));
        }
        return order;
    }

    public void validateIncluded(List<ComposeServiceDTO> services, Set<String> included) {
        Map<String, ComposeServiceDTO> byName = new HashMap<>();
        for (ComposeServiceDTO s : services) byName.put(s.name(), s);
        for (String name : included) {
            ComposeServiceDTO service = byName.get(name);
            if (service == null) {
                throw new IllegalArgumentException("Unknown service: " + name);
            }
            for (String dep : service.dependsOn()) {
                if (!included.contains(dep)) {
                    throw new IllegalArgumentException("Service '" + name + "' depends on '" + dep + "', which is excluded from this deployment.");
                }
            }
        }
    }
}
