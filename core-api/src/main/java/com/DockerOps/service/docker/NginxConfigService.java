package com.DockerOps.service.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class NginxConfigService {

    private static final String TEMPLATE_RESOURCE = "/templates/nginx-imported-container.conf.template";

    @Autowired
    private DockerClient dockerClient;

    @Value("${app.cloudflare.base-domain}")
    private String baseDomain;
    @Value("${app.nginx.apps-conf-dir}")
    private String appsConfDir;
    @Value("${app.nginx.container-name}")
    private String nginxContainerName;
    @Value("${app.nginx.max-body-size}")
    private String maxBodySize;
    @Value("${app.nginx.reload-timeout-seconds}")
    private long reloadTimeoutSeconds;

    private volatile String template;

    public void writeConfig(String subdomain, String containerName, int port) {
        String content = loadTemplate()
                .replace("{{ app-name }}", containerName)
                .replace("{{ app-port }}", String.valueOf(port))
                .replace("{{ subdomain }}", subdomain)
                .replace("{{ base-domain }}", baseDomain)
                .replace("{{ app-max-body-size }}", maxBodySize);
        try {
            Files.createDirectories(appsConfDirPath());
            Files.writeString(configPath(subdomain), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reload();
    }

    public void removeConfig(String subdomain) {
        try {
            Files.deleteIfExists(configPath(subdomain));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reload();
    }

    private Path appsConfDirPath() {
        return Path.of(appsConfDir);
    }

    private Path configPath(String subdomain) {
        return appsConfDirPath().resolve(subdomain + ".conf");
    }

    private String loadTemplate() {
        String loaded = template;
        if (loaded == null) {
            try (InputStream in = getClass().getResourceAsStream(TEMPLATE_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled Nginx template: " + TEMPLATE_RESOURCE);
                }
                loaded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                template = loaded;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return loaded;
    }

    private void reload() {
        List<Container> matches = dockerClient.listContainersCmd()
                .withNameFilter(List.of(nginxContainerName))
                .exec();
        if (matches.isEmpty()) {
            throw new IllegalStateException("Reverse proxy container not found, cannot reload Nginx");
        }
        String reverseProxyId = matches.get(0).getId();
        String execId = dockerClient.execCreateCmd(reverseProxyId)
                .withCmd("nginx", "-s", "reload")
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec()
                .getId();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(execId)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                output.write(frame.getPayload());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    })
                    .awaitCompletion(reloadTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while reloading Nginx", e);
        }

        Long exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCodeLong();
        if (exitCode == null || exitCode != 0) {
            throw new IllegalStateException("Nginx reload failed: " + output.toString(StandardCharsets.UTF_8).trim());
        }
    }
}
