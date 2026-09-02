package com.kiki.video.api.observability;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class AppInfoContributor implements InfoContributor {

    private final Environment environment;
    private final Optional<BuildProperties> buildProperties;
    private final Optional<GitProperties> gitProperties;

    public AppInfoContributor(
            Environment environment,
            Optional<BuildProperties> buildProperties,
            Optional<GitProperties> gitProperties
    ) {
        this.environment = environment;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, String> app = new LinkedHashMap<>();
        app.put("version", resolveVersion());
        app.put("commit", resolveCommit());
        builder.withDetail("app", app);
    }

    private String resolveVersion() {
        String fromEnv = environment.getProperty("APP_VERSION");
        if (hasText(fromEnv)) {
            return fromEnv;
        }
        return buildProperties.map(BuildProperties::getVersion).filter(AppInfoContributor::hasText).orElse("0.0.1-SNAPSHOT");
    }

    private String resolveCommit() {
        String fromEnv = environment.getProperty("GIT_COMMIT");
        if (hasText(fromEnv)) {
            return shorten(fromEnv);
        }
        return gitProperties.map(GitProperties::getShortCommitId).filter(AppInfoContributor::hasText).orElse("unknown");
    }

    private static String shorten(String commit) {
        return commit.length() > 12 ? commit.substring(0, 12) : commit;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
