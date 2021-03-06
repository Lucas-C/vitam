/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.database.api.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Iterators;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.WriteModel;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.api.VitamRepository;
import fr.gouv.vitam.common.database.api.VitamRepositoryStatus;
import fr.gouv.vitam.common.database.builder.request.configuration.GlobalDatas;
import fr.gouv.vitam.common.database.server.elasticsearch.model.ElasticsearchCollections;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.BsonHelper;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import org.apache.commons.collections.CollectionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ScrollableHitSource;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * Implementation for Elasticsearch
 */
public class VitamElasticsearchRepository implements VitamRepository {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(VitamElasticsearchRepository.class);
    /**
     * Identifier
     */
    public static final String IDENTIFIER = "Identifier";
    private static final String ALL_PARAMS_REQUIRED = "All params are required";
    private static final String BULK_REQ_FAIL_WITH_ERROR = "Bulk Request failure with error: ";
    private static final String EV_DET_DATA = "evDetData";
    private static final String RIGHT_STATE_ID = "rightsStatementIdentifier";
    private static final String AG_ID_EXT = "agIdExt";
    private static final String EVENTS = "events";
    private static final String NOT_IMPLEMENTED_YET = "Not implemented yet";


    private RestHighLevelClient client;
    private String indexName;
    private boolean indexByTenant;

    /**
     * VitamElasticsearchRepository Constructor
     *
     * @param client the es client
     * @param indexName the name of the index
     * @param indexByTenant specifies if the index is for a specific tenant or not
     */
    public VitamElasticsearchRepository(RestHighLevelClient client, String indexName, boolean indexByTenant) {
        this.client = client;
        this.indexName = indexName;
        this.indexByTenant = indexByTenant;
    }

    @Override
    public void save(Document document) throws DatabaseException {
        saveOrUpdate(document);
    }

    @Override
    public VitamRepositoryStatus saveOrUpdate(Document document) throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, document);
        Document internalDocument = new Document(document);
        Integer tenant = internalDocument.getInteger(VitamDocument.TENANT_ID);
        String id = (String) internalDocument.remove(VitamDocument.ID);
        Object score = internalDocument.remove(VitamDocument.SCORE);
        try {
            final String source = BsonHelper.stringify(internalDocument);

            String index = indexName;
            if (indexByTenant) {
                index = index + "_" + tenant;
            }
            IndexRequest request = new IndexRequest(index)
                .id(id)
                .source(source, XContentType.JSON)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .timeout(TimeValue.timeValueMillis(VitamConfiguration.getElasticSearchTimeoutWaitRequestInMilliseconds()))
                .opType(DocWriteRequest.OpType.INDEX);

            IndexResponse indexResponse;
            try {
                indexResponse = client.index(request, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new DatabaseException(e);
            }

            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                return VitamRepositoryStatus.CREATED;
            } else if (indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                return VitamRepositoryStatus.UPDATED;
            }

            return handleFailures(indexResponse);

        } finally {
            internalDocument.put(VitamDocument.ID, id);
            if (Objects.nonNull(score)) {
                internalDocument.put(VitamDocument.SCORE, score);
            }
        }
    }

    private VitamRepositoryStatus handleFailures(DocWriteResponse indexResponse) throws DatabaseException {
        String error = null;
        ReplicationResponse.ShardInfo shardInfo = indexResponse.getShardInfo();
        if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
            error = String
                .format("Exception occurred : total shards %s, successful shard.", shardInfo.getTotal(),
                    shardInfo.getSuccessful());
        }
        if (shardInfo.getFailed() > 0) {
            StringBuilder failures = new StringBuilder();
            for (ReplicationResponse.ShardInfo.Failure failure : shardInfo.getFailures()) {
                String reason = failure.reason();
                failures.append(reason).append(" ; ");
            }
            error = String.format("Exception occurred caused by : %s", failures.toString());
        }


        if (null == error) {
            error =
                String.format("Insert Documents Exception caused by : %s", indexResponse.getResult().toString());
        }

        LOGGER.error(error);
        throw new DatabaseException(error);
    }

    @Override
    public void save(List<Document> documents) throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, documents);

        if (documents.isEmpty()) {
            return;
        }

        BulkRequest bulkRequest = new BulkRequest();

        documents.forEach(document -> {
            Document internalDocument = new Document(document);
            Integer tenant = internalDocument.getInteger(VitamDocument.TENANT_ID);
            String id = (String) internalDocument.remove(VitamDocument.ID);
            internalDocument.remove(VitamDocument.SCORE);
            internalDocument.remove("_unused");

            final String source = BsonHelper.stringify(internalDocument);

            String index = indexName;
            if (indexByTenant) {
                index = index + "_" + tenant;
            }

            bulkRequest.add(new IndexRequest(index)
                .id(id)
                .source(source, XContentType.JSON));
        });

        if (bulkRequest.numberOfActions() != 0) {
            final BulkResponse bulkResponse;
            try {
                bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new DatabaseException(e);
            }

            if (bulkResponse.hasFailures()) {
                LOGGER.error(BULK_REQ_FAIL_WITH_ERROR + bulkResponse.buildFailureMessage());
                throw new DatabaseException(bulkResponse.buildFailureMessage());
            }
        }
    }

    public void save(ElasticsearchCollections elasticsearchCollections, List<Document> documents)
        throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, documents);
        BulkRequest bulkRequest = new BulkRequest();

        documents.forEach(document -> {
            Document internalDocument = new Document(document);
            Integer tenant = internalDocument.getInteger(VitamDocument.TENANT_ID);
            String id = (String) internalDocument.remove(VitamDocument.ID);
            internalDocument.remove(VitamDocument.SCORE);
            internalDocument.remove("_unused");

            String index = indexName;
            if (indexByTenant) {
                index = index + "_" + tenant;
            }

            switch (elasticsearchCollections) {
                case OPERATION:
                    transformDataForElastic(internalDocument);
                    break;
            }

            final String source = BsonHelper.stringify(internalDocument);

            bulkRequest.add(new IndexRequest(index)
                .id(id)
                .source(source, XContentType.JSON));
        });

        if (bulkRequest.numberOfActions() != 0) {
            final BulkResponse bulkResponse;
            try {
                bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new DatabaseException(e);
            }

            if (bulkResponse.hasFailures()) {
                LOGGER.error(BULK_REQ_FAIL_WITH_ERROR + bulkResponse.buildFailureMessage());
                throw new DatabaseException(bulkResponse.buildFailureMessage());
            }
        }
    }

    // TODO : very ugly here, should be generic
    private void transformDataForElastic(Document vitamDocument) {
        if (vitamDocument.get(EV_DET_DATA) != null) {
            String evDetDataString = (String) vitamDocument.get(EV_DET_DATA);
            LOGGER.debug(evDetDataString);
            try {
                JsonNode evDetData = JsonHandler.getFromString(evDetDataString);
                vitamDocument.remove(EV_DET_DATA);
                vitamDocument.put(EV_DET_DATA, evDetData);
            } catch (InvalidParseOperationException e) {
                LOGGER.warn("EvDetData is not a json compatible field", e);
                throw new RuntimeException(e);
            }
        }
        if (vitamDocument.get(AG_ID_EXT) != null) {
            String agidExt = (String) vitamDocument.get(AG_ID_EXT);
            LOGGER.debug(agidExt);
            try {
                JsonNode agidExtNode = JsonHandler.getFromString(agidExt);
                vitamDocument.remove(AG_ID_EXT);
                vitamDocument.put(AG_ID_EXT, agidExtNode);
            } catch (InvalidParseOperationException e) {
                LOGGER.warn("agidExtNode is not a json compatible field", e);
            }
        }
        if (vitamDocument.get(RIGHT_STATE_ID) != null) {
            String rightsStatementIdentifier =
                (String) vitamDocument.get(RIGHT_STATE_ID);
            LOGGER.debug(rightsStatementIdentifier);
            try {
                JsonNode rightsStatementIdentifierNode = JsonHandler.getFromString(rightsStatementIdentifier);
                vitamDocument.remove(RIGHT_STATE_ID);
                vitamDocument.put(RIGHT_STATE_ID,
                    rightsStatementIdentifierNode);
            } catch (InvalidParseOperationException e) {
                LOGGER.warn("rightsStatementIdentifier is not a json compatible field", e);
            }
        }
        List<Document> eventDocuments = (List<Document>) vitamDocument.get(EVENTS);
        if (eventDocuments != null) {
            for (Document eventDocument : eventDocuments) {
                if (eventDocument.getString(EV_DET_DATA) != null) {
                    String eventEvDetDataString =
                        eventDocument.getString(EV_DET_DATA);
                    Document eventEvDetDataDocument = Document.parse(eventEvDetDataString);
                    eventDocument.remove(EV_DET_DATA);
                    eventDocument.put(EV_DET_DATA, eventEvDetDataDocument);
                }
                if (eventDocument.getString(RIGHT_STATE_ID) != null) {
                    String eventrightsStatementIdentifier =
                        eventDocument.getString(RIGHT_STATE_ID);
                    Document eventEvDetDataDocument = Document.parse(eventrightsStatementIdentifier);
                    eventDocument.remove(RIGHT_STATE_ID);
                    eventDocument.put(RIGHT_STATE_ID, eventEvDetDataDocument);
                }
                if (eventDocument.getString(AG_ID_EXT) != null) {
                    String eventagIdExt =
                        eventDocument.getString(AG_ID_EXT);
                    Document eventEvDetDataDocument = Document.parse(eventagIdExt);
                    eventDocument.remove(AG_ID_EXT);
                    eventDocument.put(AG_ID_EXT, eventEvDetDataDocument);
                }
            }
        }
        vitamDocument.remove(EVENTS);
        vitamDocument.put(EVENTS, eventDocuments);
    }

    @Override
    public void saveOrUpdate(List<Document> documents) throws DatabaseException {
        save(documents);
    }

    @Override
    public void update(List<WriteModel<Document>> updates) throws DatabaseException {
        // Not implement yet
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_YET);
    }

    @Override
    public FindIterable<Document> findDocuments(Collection<String> ids, Bson projection) {
        // Not implement yet
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_YET);
    }

    @Override
    public void remove(String id, Integer tenant) throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, id);

        String index = indexName;
        if (indexByTenant) {
            index = index + "_" + tenant;
        }

        DeleteRequest request = new DeleteRequest(index)
            .id(id)
            .timeout(TimeValue.timeValueMillis(VitamConfiguration.getElasticSearchTimeoutWaitRequestInMilliseconds()))
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        DeleteResponse deleteResponse;
        try {
            deleteResponse = client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException | ElasticsearchException e) {
            if (e instanceof ElasticsearchException &&
                ((ElasticsearchException) e).status() == RestStatus.NOT_FOUND) {
                //Nothing to do
                return;
            }

            throw new DatabaseException(e);
        }


        DocWriteResponse.Result result = deleteResponse.getResult();
        switch (result) {
            case DELETED:
            case NOT_FOUND:
                break;
            default:
                handleFailures(deleteResponse);
        }

    }

    @Override
    public long remove(Bson query) throws DatabaseException {
        return 0;
    }

    @Override
    public void removeByNameAndTenant(String name, Integer tenant) throws DatabaseException {
        throw new DatabaseException("removeByNameAndTenant not implemented for Elasticsearch repository");
    }

    @Override
    public long purge(Integer tenant) throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, tenant);

        String index = indexName;
        if (indexByTenant) {
            index = index + "_" + tenant;
        }

        QueryBuilder qb = termQuery(VitamDocument.TENANT_ID, tenant);

        return handlePurge(client, index, qb);
    }

    public static long handlePurge(RestHighLevelClient client, String index, QueryBuilder qb)
        throws DatabaseException {
        try {
            DeleteByQueryRequest request = new DeleteByQueryRequest(index);
            request.setConflicts("proceed");
            request.setQuery(qb);
            request.setBatchSize(VitamConfiguration.getMaxElasticsearchBulk());
            request
                .setScroll(TimeValue.timeValueMillis(VitamConfiguration.getElasticSearchScrollTimeoutInMilliseconds()));
            request.setTimeout(TimeValue.timeValueMillis(
                VitamConfiguration.getElasticSearchTimeoutWaitRequestInMilliseconds()));
            request.setRefresh(true);

            BulkByScrollResponse bulkResponse =
                client.deleteByQuery(request, RequestOptions.DEFAULT);

            TimeValue timeTaken = bulkResponse.getTook();
            boolean timedOut = bulkResponse.isTimedOut();
            long totalDocs = bulkResponse.getTotal();
            long deletedDocs = bulkResponse.getDeleted();
            long batches = bulkResponse.getBatches();
            long noops = bulkResponse.getNoops();
            long versionConflicts = bulkResponse.getVersionConflicts();
            long bulkRetries = bulkResponse.getBulkRetries();
            long searchRetries = bulkResponse.getSearchRetries();
            TimeValue throttledMillis = bulkResponse.getStatus().getThrottled();
            TimeValue throttledUntilMillis =
                bulkResponse.getStatus().getThrottledUntil();

            LOGGER.debug(
                "Purge : timeTaken (" + timeTaken + "), timedOut (" + timedOut + "), totalDocs (" + totalDocs + ")," +
                    " deletedDocs (" + deletedDocs + "), batches (" + batches + "), noops (" + noops +
                    "), versionConflicts (" + versionConflicts + ")" +
                    "bulkRetries (" + bulkRetries + "), searchRetries (" + searchRetries + "),  throttledMillis(" +
                    throttledMillis + "), throttledUntilMillis(" + throttledUntilMillis + ")");

            List<ScrollableHitSource.SearchFailure> searchFailures = bulkResponse.getSearchFailures();
            if (CollectionUtils.isNotEmpty(searchFailures)) {
                throw new DatabaseException("ES purge errors : in search phase");
            }

            List<BulkItemResponse.Failure> bulkFailures = bulkResponse.getBulkFailures();
            if (CollectionUtils.isNotEmpty(bulkFailures)) {
                throw new DatabaseException("ES purge errors : in bulk phase");
            }

            return bulkResponse.getDeleted();
        } catch (IOException e) {
            throw new DatabaseException("Purge Exception", e);
        }
    }


    @Override
    public long purge() throws DatabaseException {
        String index = indexName;
        QueryBuilder qb = matchAllQuery();
        return handlePurge(client, index, qb);
    }

    @Override
    public Optional<Document> getByID(String id, Integer tenant) throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, id);

        String index = indexName;
        if (indexByTenant) {
            index = index + "_" + tenant;
        }

        GetRequest request = new GetRequest(index)
            .id(id)
            .fetchSourceContext(FetchSourceContext.FETCH_SOURCE);
        GetResponse response;
        try {
            response = client.get(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
        if (response.isExists()) {
            try {
                return Optional.of(JsonHandler.getFromString(response.getSourceAsString(), Document.class));
            } catch (InvalidParseOperationException e) {
                throw new DatabaseException(e);
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Document> findByIdentifierAndTenant(String identifier, Integer tenant)
        throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED, tenant);

        try {
            String index = indexName;
            if (indexByTenant) {
                index = index + "_" + tenant;
            }
            QueryBuilder qb = boolQuery().must(termQuery(IDENTIFIER, identifier))
                .must(termQuery(VitamDocument.TENANT_ID, tenant));

            return handleSearch(index, qb);
        } catch (IOException e) {
            throw new DatabaseException("Search by identifier and tenant exception", e);
        }

    }

    private Optional<Document> handleSearch(String index, QueryBuilder qb) throws IOException, DatabaseException {
        SearchResponse search = search(index, qb);

        for (SearchHit hit : search.getHits().getHits()) {
            try {
                return Optional.of(JsonHandler.getFromString(hit.getSourceAsString(), Document.class));
            } catch (InvalidParseOperationException e) {
                throw new DatabaseException(e);
            }
        }

        return Optional.empty();
    }

    public SearchResponse search(String index, QueryBuilder qb) throws IOException {
        SearchSourceBuilder searchSourceBuilder =
            SearchSourceBuilder.searchSource().query(qb).size(GlobalDatas.LIMIT_LOAD)
                .sort(FieldSortBuilder.DOC_FIELD_NAME, SortOrder.ASC);

        SearchRequest searchRequest = new SearchRequest()
            .indices(index)
            .scroll(new TimeValue(60000))
            .searchType(SearchType.DFS_QUERY_THEN_FETCH)
            .source(searchSourceBuilder);

        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    @Override
    public Optional<Document> findByIdentifier(String identifier)
        throws DatabaseException {
        ParametersChecker.checkParameter(ALL_PARAMS_REQUIRED);

        String index = indexName;


        try {
            return handleSearch(index, termQuery(IDENTIFIER, identifier));
        } catch (IOException e) {
            throw new DatabaseException("Search by identifier exception", e);
        }

    }

    @Override
    public FindIterable<Document> findByFieldsDocuments(Map<String, String> fields, int mongoBatchSize,
        Integer tenant) {
        // Not implement yet
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_YET);
    }

    @Override
    public FindIterable<Document> findDocuments(int mongoBatchSize, Integer tenant) {
        // Not implement yet
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_YET);
    }

    @Override
    public FindIterable<Document> findDocuments(int mongoBatchSize) {
        // Not implement yet
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_YET);
    }

    @Override
    public FindIterable<Document> findDocuments(Bson query, int mongoBatchSize) {
        // Not implement yet
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_YET);
    }

    @Override
    public void delete(List<String> ids, int tenant) throws DatabaseException {

        String index = indexName;
        if (indexByTenant) {
            index = index + "_" + tenant;
        }

        Iterator<List<String>> idIterator =
            Iterators.partition(ids.iterator(), VitamConfiguration.getMaxElasticsearchBulk());

        while (idIterator.hasNext()) {

            BulkRequest bulkRequest = new BulkRequest();
            for (String id : idIterator.next()) {
                bulkRequest.add(new DeleteRequest(index.toLowerCase(), id));
            }

            WriteRequest.RefreshPolicy refreshPolicy = idIterator.hasNext() ?
                WriteRequest.RefreshPolicy.NONE :
                WriteRequest.RefreshPolicy.IMMEDIATE;

            bulkRequest.setRefreshPolicy(refreshPolicy);

            if (bulkRequest.numberOfActions() != 0) {
                BulkResponse bulkResponse;
                try {
                    bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    throw new DatabaseException("Bulk delete exception", e);
                }

                if (bulkResponse.hasFailures()) {
                    throw new DatabaseException("ES delete in error: " + bulkResponse.buildFailureMessage());

                }
            }
        }
    }
}
