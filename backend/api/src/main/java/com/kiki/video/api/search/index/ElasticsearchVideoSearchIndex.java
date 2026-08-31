package com.kiki.video.api.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.search.dto.VideoSearchSort;
import com.kiki.video.api.search.highlight.HighlightParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchVideoSearchIndex implements VideoSearchIndex {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchVideoSearchIndex.class);
    private static final int MAX_RESULT_WINDOW = 10_000;

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;

    public ElasticsearchVideoSearchIndex(ElasticsearchClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void ensureWritableIndex() {
        try {
            String alias = properties.videoIndexAlias();
            String versioned = properties.videoIndexVersion();
            boolean aliasExists = existsAlias(alias);
            boolean versionedExists = existsIndex(versioned);
            if (!aliasExists && !versionedExists) {
                createIndex(versioned);
                addAlias(versioned, alias);
                log.info("created search index {} with alias {}", versioned, alias);
                return;
            }
            if (versionedExists && !aliasExists) {
                addAlias(versioned, alias);
                log.info("added search alias {} -> {}", alias, versioned);
            }
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to initialize the search index", ex);
        }
    }

    public String createRebuildIndex() {
        String name = properties.videoIndexVersion() + "-" + Instant.now().toEpochMilli();
        try {
            createIndex(name);
            return name;
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to create rebuild index", ex);
        }
    }

    public void switchAlias(String newIndex) {
        try {
            String alias = properties.videoIndexAlias();
            Set<String> current = aliasIndices(alias);
            client.indices().updateAliases(u -> {
                for (String oldIndex : current) {
                    if (!oldIndex.equals(newIndex)) {
                        u.actions(a -> a.remove(r -> r.index(oldIndex).alias(alias)));
                    }
                }
                u.actions(a -> a.add(ad -> ad.index(newIndex).alias(alias)));
                return u;
            });
            for (String oldIndex : current) {
                if (!oldIndex.equals(newIndex) && existsIndex(oldIndex)) {
                    client.indices().delete(d -> d.index(oldIndex));
                }
            }
            log.info("search alias {} now points at {}", alias, newIndex);
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to switch search alias", ex);
        }
    }

    @Override
    public void upsert(VideoSearchDocument document) {
        try {
            ensureWritableIndex();
            client.index(i -> i
                    .index(properties.videoIndexAlias())
                    .id(String.valueOf(document.videoId()))
                    .document(document)
            );
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to index video " + document.videoId(), ex);
        }
    }

    @Override
    public void delete(long videoId) {
        try {
            ensureWritableIndex();
            client.delete(d -> d
                    .index(properties.videoIndexAlias())
                    .id(String.valueOf(videoId))
            );
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to delete video " + videoId + " from search", ex);
        }
    }

    @Override
    public BulkIndexResult bulkUpsert(String indexName, List<VideoSearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return new BulkIndexResult(0, 0);
        }
        try {
            BulkResponse response = client.bulk(b -> {
                for (VideoSearchDocument document : documents) {
                    b.operations(op -> op.index(idx -> idx
                            .index(indexName)
                            .id(String.valueOf(document.videoId()))
                            .document(document)
                    ));
                }
                return b;
            });
            int failed = 0;
            if (response.errors()) {
                failed = (int) response.items().stream().filter(item -> item.error() != null).count();
            }
            return new BulkIndexResult(documents.size() - failed, failed);
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to bulk index search documents", ex);
        }
    }

    @Override
    public VideoSearchHits search(VideoSearchQuery query) {
        try {
            ensureWritableIndex();
            int from = query.page() * query.size();
            if (from >= MAX_RESULT_WINDOW) {
                throw new SearchIndexException("Search page is too deep for from/size pagination");
            }
            SearchResponse<VideoSearchDocument> response = client.search(s -> {
                s.index(properties.videoIndexAlias())
                        .from(from)
                        .size(query.size())
                        .trackTotalHits(t -> t.enabled(true))
                        .query(buildQuery(query))
                        .highlight(h -> h
                                .preTags(HighlightParser.PRE)
                                .postTags(HighlightParser.POST)
                                .fields("title", f -> f.numberOfFragments(0))
                                .fields("description", f -> f.fragmentSize(160).numberOfFragments(1))
                                .fields("ownerUsername", f -> f.numberOfFragments(0))
                                .fields("ownerDisplayName", f -> f.numberOfFragments(0))
                        );
                if (query.sort() == VideoSearchSort.NEWEST) {
                    s.sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)));
                } else if (query.sort() == VideoSearchSort.OLDEST) {
                    s.sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Asc)));
                }
                return s;
            }, VideoSearchDocument.class);

            List<VideoSearchHits.Hit> items = new ArrayList<>();
            for (Hit<VideoSearchDocument> hit : response.hits().hits()) {
                if (hit.source() == null) {
                    continue;
                }
                Map<String, List<String>> fragments = new LinkedHashMap<>();
                if (hit.highlight() != null) {
                    hit.highlight().forEach(fragments::put);
                }
                items.add(new VideoSearchHits.Hit(hit.source(), fragments));
            }
            long total = response.hits().total() == null ? items.size() : response.hits().total().value();
            return new VideoSearchHits(List.copyOf(items), total, response.took());
        } catch (SearchIndexException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new SearchIndexException("Search is temporarily unavailable", ex);
        }
    }

    @Override
    public void refresh() {
        try {
            if (existsAlias(properties.videoIndexAlias())) {
                client.indices().refresh(r -> r.index(properties.videoIndexAlias()));
            }
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to refresh the search index", ex);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return client.ping().value();
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public long count() {
        try {
            if (!existsAlias(properties.videoIndexAlias())) {
                return 0;
            }
            return client.count(c -> c.index(properties.videoIndexAlias())).count();
        } catch (IOException ex) {
            throw new SearchIndexException("Unable to count search documents", ex);
        }
    }

    private Query buildQuery(VideoSearchQuery query) {
        Query textQuery = Query.of(q -> q.multiMatch(m -> m
                .query(query.q())
                .fields("title^4", "ownerUsername^2", "ownerDisplayName^2", "description")
                .fuzziness("AUTO")
                .prefixLength(1)
                .maxExpansions(50)
                .type(TextQueryType.BestFields)
        ));
        Query exactTitle = Query.of(q -> q.term(t -> t
                .field("title.keyword")
                .value(query.q())
                .boost(6.0f)
        ));
        return Query.of(q -> q.bool(b -> {
            b.must(textQuery);
            b.should(exactTitle);
            applyFilters(b, query);
            return b;
        }));
    }

    private static void applyFilters(BoolQuery.Builder bool, VideoSearchQuery query) {
        if (query.ownerId() != null) {
            bool.filter(Query.of(q -> q.term(t -> t.field("ownerId").value(query.ownerId()))));
        }
        if (query.processingStatus() != null && !query.processingStatus().isBlank()) {
            bool.filter(Query.of(q -> q.term(t -> t.field("processingStatus").value(query.processingStatus()))));
        }
        if (query.createdAfter() != null || query.createdBefore() != null) {
            bool.filter(dateRange(query.createdAfter(), query.createdBefore()));
        }
    }

    private static Query dateRange(Instant createdAfter, Instant createdBefore) {
        return Query.of(q -> q.range(r -> r.date(d -> {
            d.field("createdAt");
            if (createdAfter != null) {
                d.gte(createdAfter.toString());
            }
            if (createdBefore != null) {
                d.lte(createdBefore.toString());
            }
            return d;
        })));
    }

    private void createIndex(String name) throws IOException {
        try (InputStream mapping = getClass().getResourceAsStream("/elasticsearch/kiki-videos-v1.json")) {
            if (mapping == null) {
                throw new SearchIndexException("Search index mapping resource is missing");
            }
            client.indices().create(c -> c.index(name).withJson(mapping));
        }
    }

    private void addAlias(String index, String alias) throws IOException {
        client.indices().updateAliases(u -> u.actions(a -> a.add(ad -> ad.index(index).alias(alias))));
    }

    private boolean existsIndex(String name) throws IOException {
        return client.indices().exists(e -> e.index(name)).value();
    }

    private boolean existsAlias(String alias) throws IOException {
        return client.indices().existsAlias(e -> e.name(alias)).value();
    }

    private Set<String> aliasIndices(String alias) throws IOException {
        if (!existsAlias(alias)) {
            return Set.of();
        }
        GetAliasResponse response = client.indices().getAlias(g -> g.name(alias));
        return response.result().keySet();
    }
}
