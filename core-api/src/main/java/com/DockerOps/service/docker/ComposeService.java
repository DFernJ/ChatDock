package com.DockerOps.service.docker;

import com.DockerOps.dto.container.ContainerVolumeDTO;
import com.DockerOps.dto.request.HealthcheckDTO;
import com.DockerOps.dto.request.PortMappingDTO;
import com.DockerOps.dto.request.SecretDraftDTO;
import com.DockerOps.dto.response.ComposeNetworkDTO;
import com.DockerOps.dto.response.ComposeParseResultDTO;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComposeService {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)(h|ms|m|s)");
    private static final Pattern VAR_REFERENCE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?:[:-][^}]*)?}|\\$([A-Za-z_][A-Za-z0-9_]*)");

    @SuppressWarnings("unchecked")
    public ComposeParseResultDTO parse(Path composeFile, Path contextDir) {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(composeFile)) {
            Object loaded = new Yaml().load(in);
            root = loaded instanceof Map ? (Map<String, Object>) loaded : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<ComposeNetworkDTO> networks = parseNetworks(root.get("networks"));

        Object servicesObj = root.get("services");
        if (!(servicesObj instanceof Map)) {
            return new ComposeParseResultDTO(List.of(), networks);
        }
        Map<String, Object> servicesMap = (Map<String, Object>) servicesObj;

        List<ComposeServiceDTO> result = new ArrayList<>();
        for (var entry : servicesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            result.add(parseService(entry.getKey(), (Map<String, Object>) entry.getValue(), contextDir, networks));
        }
        return new ComposeParseResultDTO(result, networks);
    }

    @SuppressWarnings("unchecked")
    private List<ComposeNetworkDTO> parseNetworks(Object networksObj) {
        List<ComposeNetworkDTO> networks = new ArrayList<>();
        if (!(networksObj instanceof Map<?, ?> map)) return networks;
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) continue;
            Map<String, Object> spec = entry.getValue() instanceof Map ? (Map<String, Object>) entry.getValue() : Map.of();
            String name = spec.get("name") instanceof String s ? s : null;
            String driver = spec.get("driver") instanceof String d ? d : "bridge";
            Object externalObj = spec.get("external");
            boolean external = Boolean.TRUE.equals(externalObj) || externalObj instanceof Map;
            if (name == null && externalObj instanceof Map<?, ?> extMap && extMap.get("name") instanceof String extName) {
                name = extName;
            }
            networks.add(new ComposeNetworkDTO(key, name, driver, external));
        }
        return networks;
    }

    @SuppressWarnings("unchecked")
    private ComposeServiceDTO parseService(String name, Map<String, Object> spec, Path contextDir, List<ComposeNetworkDTO> declaredNetworks) {
        String image = spec.get("image") instanceof String s ? s : null;
        String buildSubdir = null;
        String dockerfile = null;
        boolean supported = true;
        String unsupportedReason = null;

        Object buildObj = spec.get("build");
        if (buildObj != null) {
            String rawContext = null;
            if (buildObj instanceof String bs) {
                rawContext = bs;
            } else if (buildObj instanceof Map<?, ?> bm) {
                if (bm.get("context") instanceof String cs) rawContext = cs;
                if (bm.get("dockerfile") instanceof String df) dockerfile = df;
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

        ParsedRestartPolicy restartPolicy = spec.get("restart") instanceof String rs
                ? parseRestartPolicy(rs)
                : new ParsedRestartPolicy("no", null);

        List<SecretDraftDTO> secrets = new ArrayList<>();
        Object envObj = spec.get("environment");
        if (envObj instanceof List<?> list) {
            for (Object e : list) {
                if (!(e instanceof String es)) continue;
                int idx = es.indexOf('=');
                if (idx > 0) {
                    secrets.add(toSecretDraft(es.substring(0, idx), es.substring(idx + 1)));
                } else {
                    secrets.add(toSecretDraft(es, ""));
                }
            }
        } else if (envObj instanceof Map<?, ?> map) {
            for (var e : map.entrySet()) {
                secrets.add(toSecretDraft(String.valueOf(e.getKey()), e.getValue() != null ? String.valueOf(e.getValue()) : ""));
            }
        }

        List<String> networks = new ArrayList<>();
        Object networksObj = spec.get("networks");
        if (networksObj instanceof List<?> list) {
            for (Object n : list) if (n instanceof String ns) networks.add(ns);
        } else if (networksObj instanceof Map<?, ?> map) {
            for (Object k : map.keySet()) if (k instanceof String ks) networks.add(ks);
        }
        if (!networks.isEmpty()) {
            Set<String> declaredKeys = new HashSet<>();
            for (ComposeNetworkDTO net : declaredNetworks) declaredKeys.add(net.key());
            for (String netKey : networks) {
                if (!declaredKeys.contains(netKey)) {
                    supported = false;
                    unsupportedReason = "References undeclared network '" + netKey + "'";
                }
            }
        }

        List<String> command = parseShellOrList(spec.get("command"));
        List<String> entrypoint = parseShellOrList(spec.get("entrypoint"));
        HealthcheckDTO healthcheck = parseHealthcheck(spec.get("healthcheck"));

        Set<String> secretNames = new HashSet<>();
        for (SecretDraftDTO secret : secrets) secretNames.add(secret.name());
        Set<String> referencedVars = new HashSet<>();
        collectReferencedVarNames(command, referencedVars);
        collectReferencedVarNames(entrypoint, referencedVars);
        for (String varName : referencedVars) {
            if (secretNames.add(varName)) {
                secrets.add(new SecretDraftDTO(varName, ""));
            }
        }

        return new ComposeServiceDTO(name, image, buildSubdir, dockerfile, ports, volumes, dependsOn,
                restartPolicy.name(), restartPolicy.maxRetryCount(), secrets, networks, command, entrypoint,
                healthcheck, supported, unsupportedReason);
    }

    private SecretDraftDTO toSecretDraft(String name, String rawValue) {
        if (rawValue != null && VAR_REFERENCE.matcher(rawValue).find()) {
            return new SecretDraftDTO(name, "");
        }
        return new SecretDraftDTO(name, rawValue);
    }

    private void collectReferencedVarNames(List<String> tokens, Set<String> names) {
        if (tokens == null) return;
        for (String token : tokens) {
            if (token == null) continue;
            Matcher m = VAR_REFERENCE.matcher(token);
            while (m.find()) {
                names.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
    }

    private List<String> parseShellOrList(Object raw) {
        if (raw instanceof String s && !s.isBlank()) {
            return List.of("sh", "-c", s);
        }
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) if (o instanceof String s) result.add(s);
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private HealthcheckDTO parseHealthcheck(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) return null;
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (Boolean.TRUE.equals(map.get("disable"))) {
            return new HealthcheckDTO(false, null, null, null, null, null);
        }

        Object testObj = map.get("test");
        String command = null;
        if (testObj instanceof String s) {
            command = s;
        } else if (testObj instanceof List<?> list && !list.isEmpty()) {
            int start = 0;
            if (list.get(0) instanceof String first) {
                if ("NONE".equals(first)) {
                    return new HealthcheckDTO(false, null, null, null, null, null);
                }
                if ("CMD".equals(first) || "CMD-SHELL".equals(first)) {
                    start = 1;
                }
            }
            List<String> parts = new ArrayList<>();
            for (int i = start; i < list.size(); i++) {
                if (list.get(i) instanceof String part) parts.add(part);
            }
            command = String.join(" ", parts);
        }
        if (command == null || command.isBlank()) return null;

        Integer interval = parseDurationSeconds(map.get("interval"));
        Integer timeout = parseDurationSeconds(map.get("timeout"));
        Integer startPeriod = parseDurationSeconds(map.get("start_period"));
        Integer retries = map.get("retries") instanceof Number n ? n.intValue() : null;
        return new HealthcheckDTO(true, command, interval, timeout, retries, startPeriod);
    }

    private Integer parseDurationSeconds(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (!(raw instanceof String s) || s.isBlank()) return null;
        Matcher matcher = DURATION_PATTERN.matcher(s.trim());
        boolean matchedAny = false;
        long totalMillis = 0;
        while (matcher.find()) {
            matchedAny = true;
            long value = Long.parseLong(matcher.group(1));
            totalMillis += switch (matcher.group(2)) {
                case "h" -> value * 3_600_000L;
                case "m" -> value * 60_000L;
                case "s" -> value * 1_000L;
                case "ms" -> value;
                default -> 0L;
            };
        }
        if (!matchedAny) return null;
        return (int) Math.max(1, totalMillis / 1000);
    }

    private record ParsedRestartPolicy(String name, Integer maxRetryCount) {}

    private ParsedRestartPolicy parseRestartPolicy(String raw) {
        String name = raw;
        Integer retries = null;
        int colon = raw.indexOf(':');
        if (colon > 0) {
            name = raw.substring(0, colon);
            try {
                retries = Integer.parseInt(raw.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return switch (name) {
            case "always", "unless-stopped" -> new ParsedRestartPolicy(name, null);
            case "on-failure" -> new ParsedRestartPolicy(name, retries);
            default -> new ParsedRestartPolicy("no", null);
        };
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
        if (parts.length < 2) return null;
        try {
            int hostPort = Integer.parseInt(parts[parts.length - 2].trim());
            int containerPort = Integer.parseInt(parts[parts.length - 1].trim());
            return new PortMappingDTO(hostPort, containerPort, protocol);
        } catch (NumberFormatException e) {
            return null;
        }
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
