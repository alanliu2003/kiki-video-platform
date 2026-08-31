package com.kiki.video.api.support;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public final class ElasticsearchTestContainer {

    public static final String IMAGE = "elasticsearch:8.17.10";

    @SuppressWarnings("resource")
    public static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse(IMAGE)
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(3));

    static {
        ELASTICSEARCH.start();
    }

    private ElasticsearchTestContainer() {
    }

    public static String url() {
        return "http://" + ELASTICSEARCH.getHttpHostAddress();
    }
}
