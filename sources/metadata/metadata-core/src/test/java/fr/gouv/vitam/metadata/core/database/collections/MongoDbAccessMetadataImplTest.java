/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
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
 *******************************************************************************/
package fr.gouv.vitam.metadata.core.database.collections;

import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.ARCHIVE_UNIT;
import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.BINARY_OBJECT;
import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.BINARY_OBJECTS_SIZE;
import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.CREATION_DATE;
import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.OBJECT_GROUP;
import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.ORIGINATING_AGENCY;
import static fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic.TENANT;
import static fr.gouv.vitam.metadata.core.database.collections.MetadataCollections.OBJECTGROUP;
import static fr.gouv.vitam.metadata.core.database.collections.MetadataCollections.UNIT;
import static java.util.Locale.US;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mongodb.client.MongoCollection;
import fr.gouv.vitam.common.database.api.VitamRepositoryFactory;
import fr.gouv.vitam.common.database.api.impl.VitamElasticsearchRepository;
import fr.gouv.vitam.common.database.api.impl.VitamMongoRepository;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.elasticsearch.ElasticsearchRule;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.FacetBucket;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.server.AccessionRegisterSymbolic;
import fr.gouv.vitam.metadata.api.model.ObjectGroupPerOriginatingAgency;
import fr.gouv.vitam.metadata.core.MetaDataImpl;
import org.assertj.core.util.Lists;
import org.bson.Document;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ParsedValueCount;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.powermock.api.mockito.PowerMockito;

public class MongoDbAccessMetadataImplTest {

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    private static final String DEFAULT_MONGO =
        "AccessionRegisterDetail\n" + "AccessionRegisterSummary\n" + "Unit\n" + "ObjectGroup\n" +
            "Unit Document{{v=2, key=Document{{_id=1}}, name=_id_, ns=Vitam-DB.Unit}}\n" +
            "Unit Document{{v=2, key=Document{{_id=hashed}}, name=_id_hashed, ns=Vitam-DB.Unit}}\n" +
            "ObjectGroup Document{{v=2, key=Document{{_id=1}}, name=_id_, ns=Vitam-DB.ObjectGroup}}\n" +
            "ObjectGroup Document{{v=2, key=Document{{_id=hashed}}, name=_id_hashed, ns=Vitam-DB.ObjectGroup}}\n";

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    @ClassRule
    public static MongoRule mongoRule =
        new MongoRule(MongoDbAccessMetadataImpl.getMongoClientOptions(), "Vitam-DB",
            MetadataCollections.UNIT.getName(),
            MetadataCollections.OBJECTGROUP.getName());

    @ClassRule
    public static ElasticsearchRule elasticsearchRule =
        new ElasticsearchRule(org.assertj.core.util.Files.newTemporaryFolder(),
            MetadataCollections.UNIT.getName().toLowerCase() + "_0",
            MetadataCollections.UNIT.getName().toLowerCase() + "_1",
            MetadataCollections.OBJECTGROUP.getName().toLowerCase() + "_0",
            MetadataCollections.OBJECTGROUP.getName().toLowerCase() + "_1"
        );

    static final List<Integer> tenantList = Arrays.asList(0);
    private static ElasticsearchAccessMetadata esClient;


    static MongoDbAccessMetadataImpl mongoDbAccess;


    @BeforeClass
    public static void setupOne() throws IOException, VitamException {


        final List<ElasticsearchNode> nodes = new ArrayList<>();
        nodes.add(new ElasticsearchNode("localhost", elasticsearchRule.getTcpPort()));

        esClient = new ElasticsearchAccessMetadata(elasticsearchRule.getClusterName(), nodes);

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        mongoRule.handleAfter();
        elasticsearchRule.handleAfter();
    }

    @Test
    public void givenMongoDbAccessConstructorWhenCreateWithRecreateThenAddDefaultCollections() {
        mongoDbAccess =
            new MongoDbAccessMetadataImpl(mongoRule.getMongoClient(), mongoRule.getMongoDatabase().getName(), true,
                esClient, tenantList);
        assertThat(mongoDbAccess.getInfo()).isEqualTo(DEFAULT_MONGO);
        assertThat(MetadataCollections.UNIT.getName()).isEqualTo("Unit");
        assertThat(MetadataCollections.OBJECTGROUP.getName()).isEqualTo("ObjectGroup");
        assertThat(MongoDbAccessMetadataImpl.getUnitSize()).isEqualTo(0);
        assertThat(MongoDbAccessMetadataImpl.getObjectGroupSize()).isEqualTo(0);
    }

    @Test
    public void givenMongoDbAccessConstructorWhenCreateWithoutRecreateThenAddNothing() {
        mongoDbAccess =
            new MongoDbAccessMetadataImpl(mongoRule.getMongoClient(), mongoRule.getMongoDatabase().getName(), false,
                esClient, tenantList);
        assertEquals(DEFAULT_MONGO, mongoDbAccess.getInfo());
    }

    @Test
    public void givenMongoDbAccessWhenFlushOnDisKThenDoNothing() {
        mongoDbAccess =
            new MongoDbAccessMetadataImpl(mongoRule.getMongoClient(), mongoRule.getMongoDatabase().getName(), false,
                esClient, tenantList);
        mongoDbAccess.flushOnDisk();
    }

    @Test
    @RunWithCustomExecutor
    public void should_aggregate_unit_per_operation_id_and_originating_agency() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(0);

        mongoDbAccess =
            new MongoDbAccessMetadataImpl(mongoRule.getMongoClient(), mongoRule.getMongoDatabase().getName(), false,
                esClient, tenantList);

        // Given
        final MetaDataImpl metaData = new MetaDataImpl(mongoDbAccess);

        final String operationId = "1234";
        ArrayList<Document> units = Lists.newArrayList(
            new Document("_id", "1")
                .append("_tenant", 0)
                .append("_ops", Arrays.asList(operationId))
                .append("_opi", Arrays.asList(operationId))
                .append("_sp", "sp2")
                .append("_max", 1)
                .append("_sps", Arrays.asList("sp1", "sp2")),
            new Document("_id", "2")
                .append("_tenant", 0)
                .append("_ops", Arrays.asList(operationId))
                .append("_opi", Arrays.asList(operationId))
                .append("_sp", "sp1")
                .append("_max", 1)
                .append("_sps", Arrays.asList("sp1")),

            new Document("_id", "5")
                .append("_tenant", 0)
                .append("_ops", Arrays.asList(operationId))
                .append("_opi", Arrays.asList(operationId))
                .append("_sp", "sp1")
                .append("_max", 1)
                .append("_sps", Arrays.asList("sp1")),
            new Document("_id", "4")
                .append("_tenant", 0)
                .append("_ops", Arrays.asList(operationId))
                .append("_opi", Arrays.asList(operationId))
                .append("_sp", "sp1")
                .append("_max", 1)
                .append("_unitType", "HOLDING_UNIT")
                .append("_sps", Arrays.asList("sp1")),
            new Document("_id", "3")
                .append("_tenant", 0)
                .append("_ops", Arrays.asList("otherOperationId"))
                .append("_max", 1)
                .append("_sp", "sp1")
                .append("_opi", Arrays.asList("otherOperationId"))
                .append("_sps", Arrays.asList("sp2")));

        VitamRepositoryFactory factory = VitamRepositoryFactory.get();
        VitamMongoRepository mongo = factory.getVitamMongoRepository(MetadataCollections.UNIT.getVitamCollection());
        mongo.save(units);

        VitamElasticsearchRepository es =
            VitamRepositoryFactory.get().getVitamESRepository(MetadataCollections.UNIT.getVitamCollection());
        es.save(units);

        // When
        List<FacetBucket> documents =
            metaData.selectOwnAccessionRegisterOnUnitByOperationId(operationId);

        // Then
        assertThat(documents).containsExactlyInAnyOrder(new FacetBucket("sp1", 2),
            new FacetBucket("sp2", 1));

    }

    @Test
    @RunWithCustomExecutor
    public void should_aggregate_object_group_per_operation_id_and_originating_agency() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(0);

        mongoDbAccess =
            new MongoDbAccessMetadataImpl(mongoRule.getMongoClient(), mongoRule.getMongoDatabase().getName(), false,
                esClient, tenantList);

        // Given
        final MongoCollection objectGroup = MetadataCollections.OBJECTGROUP.getCollection();

        final MetaDataImpl metaData = new MetaDataImpl(mongoDbAccess);

        final String operationId = "aedqaaaaacgbcaacaar3kak4tr2o3wqaaaaq";
        objectGroup.insertOne(new ObjectGroup(JsonHandler.getFromInputStream(getClass().getResourceAsStream(
            "/object_sp1_1.json"))));
        objectGroup.insertOne(new ObjectGroup(JsonHandler.getFromInputStream(getClass().getResourceAsStream(
            "/object_sp1_sp2_2.json"))));
        objectGroup.insertOne(
            new ObjectGroup(JsonHandler.getFromInputStream(getClass().getResourceAsStream("/object_sp2.json"))));
        objectGroup.insertOne(new ObjectGroup(JsonHandler.getFromInputStream(getClass().getResourceAsStream(
            "/object_sp2_4.json"))));
        objectGroup.insertOne(new ObjectGroup(JsonHandler.getFromInputStream(getClass().getResourceAsStream(
            "/object_other_operation_id.json"))));
        // When
        List<ObjectGroupPerOriginatingAgency> documents =
            metaData.selectOwnAccessionRegisterOnObjectGroupByOperationId(operationId);

        // Then

        assertThat(documents).extracting("operation", "agency", "numberOfObject", "numberOfGOT",
            "size")
            .contains(tuple("aedqaaaaacgbcaacaar3kak4tr2o3wqaaaaq", "sp1", 3L, 1l, 200l),
                tuple("aedqaaaaacgbcaacaar3kak4tr2o3wqaaaaq", "sp2", 6l, 3l, 380l));

    }

    @Test
    public void should_select_accession_register_symbolic() throws Exception {
        // Given
        ElasticsearchAccessMetadata client = PowerMockito.mock(ElasticsearchAccessMetadata.class);

        SearchResponse archiveUnitResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 3,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 3\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );
        SearchResponse objectGroupResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 3,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 2\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 88209\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 2\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 88209\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );

        given(client.basicSearch(eq(UNIT), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(archiveUnitResponse);
        given(client.basicSearch(eq(OBJECTGROUP), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(objectGroupResponse);

        final MetaDataImpl metaData = new MetaDataImpl(
            new MongoDbAccessMetadataImpl(
                mongoRule.getMongoClient(),
                mongoRule.getMongoDatabase().getName(),
                true,
                client,
                tenantList
            )
        );

        // When
        List<Document> accessionRegisterSymbolics = metaData.createAccessionRegisterSymbolic(0);

        // Then
        assertThat(accessionRegisterSymbolics).hasSize(1);
    }

    @Test
    public void should_fill_all_accession_register_symbolic_information() throws IOException {
        // Given
        ElasticsearchAccessMetadata client = PowerMockito.mock(ElasticsearchAccessMetadata.class);

        SearchResponse archiveUnitResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 3,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 3\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );
        SearchResponse objectGroupResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 3,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 3,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 88209\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 2\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier1\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 88209\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 2\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );

        given(client.basicSearch(eq(UNIT), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(archiveUnitResponse);
        given(client.basicSearch(eq(OBJECTGROUP), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(objectGroupResponse);

        final MetaDataImpl metaData = new MetaDataImpl(
            new MongoDbAccessMetadataImpl(
                mongoRule.getMongoClient(),
                mongoRule.getMongoDatabase().getName(),
                true,
                client,
                tenantList
            )
        );

        // When
        Optional<AccessionRegisterSymbolic> first = metaData.createAccessionRegisterSymbolic(0)
            .stream()
            .map(a -> (AccessionRegisterSymbolic) a)
            .findFirst();

        // Then
        assertThat(first).map(a -> a.getInteger(TENANT)).hasValue(0);
        assertThat(first).map(a -> a.getString(ORIGINATING_AGENCY)).hasValue("Identifier0");
        assertThat(first).map(a -> a.getDouble(BINARY_OBJECTS_SIZE)).hasValue(88209.0);
        assertThat(first).map(a -> a.getLong(ARCHIVE_UNIT)).hasValue(2L);
        assertThat(first).map(a -> a.getLong(OBJECT_GROUP)).hasValue(3L);
        assertThat(first).map(a -> a.getLong(BINARY_OBJECT)).hasValue(2L);
        assertThat(first).map(a -> a.getString(CREATION_DATE)).isNotEmpty();
    }

    @Test
    public void should_subtracts_sp_count_to_sis_in_order_to_have_number_of_symbolic_link() throws IOException {
        // Given
        ElasticsearchAccessMetadata client = PowerMockito.mock(ElasticsearchAccessMetadata.class);

        long numberOfOriginatingAgencies = 12;
        long numberOfOriginatingAgency = 1;
        SearchResponse archiveUnitResponse = searchResult(
            String.format(
                    "{\n" +
                            "  \"took\": 6,\n" +
                            "  \"timed_out\": false,\n" +
                            "  \"_shards\": {\n" +
                            "    \"total\": 1,\n" +
                            "    \"successful\": 1,\n" +
                            "    \"skipped\": 0,\n" +
                            "    \"failed\": 0\n" +
                            "  },\n" +
                            "  \"aggregations\": {\n" +
                            "    \"sterms#originatingAgencies\": {\n" +
                            "      \"doc_count_error_upper_bound\": 0,\n" +
                            "      \"sum_other_doc_count\": 0,\n" +
                            "      \"buckets\": [\n" +
                            "        {\n" +
                            "          \"key\": \"Identifier0\",\n" +
                            "          \"doc_count\": %d,\n" +
                            "          \"nested#nestedVersions\": {\n" +
                            "            \"doc_count\": 3\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    },\n" +
                            "    \"sterms#originatingAgency\": {\n" +
                            "      \"doc_count_error_upper_bound\": 0,\n" +
                            "      \"sum_other_doc_count\": 0,\n" +
                            "      \"buckets\": [\n" +
                            "        {\n" +
                            "          \"key\": \"Identifier0\",\n" +
                            "          \"doc_count\": %d,\n" +
                            "          \"nested#nestedVersions\": {\n" +
                            "            \"doc_count\": 1\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    }\n" +
                            "  }\n" +
                            "}",
                numberOfOriginatingAgencies, numberOfOriginatingAgency)
        );
        SearchResponse objectGroupResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 3,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 88209\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 2\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier1\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 88209\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 2\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );

        given(client.basicSearch(eq(UNIT), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(archiveUnitResponse);
        given(client.basicSearch(eq(OBJECTGROUP), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(objectGroupResponse);

        final MetaDataImpl metaData = new MetaDataImpl(
            new MongoDbAccessMetadataImpl(
                mongoRule.getMongoClient(),
                mongoRule.getMongoDatabase().getName(),
                true,
                client,
                tenantList
            )
        );

        // When
        Optional<AccessionRegisterSymbolic> first = metaData.createAccessionRegisterSymbolic(0)
            .stream()
            .map(a -> (AccessionRegisterSymbolic) a)
            .findFirst();

        // Then
        assertThat(first).map(a -> a.getLong(ARCHIVE_UNIT)).hasValue(numberOfOriginatingAgencies - numberOfOriginatingAgency);
    }

    @Test
    public void should_add_number_of_binaries_and_binaries_total_size_to_related_accession_register_when_object_group_counted() throws IOException {
        // Given
        ElasticsearchAccessMetadata client = PowerMockito.mock(ElasticsearchAccessMetadata.class);

        SearchResponse archiveUnitResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 3,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 3\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );
        double binarySize = 88209;
        long binaryCount = 2;
        long objectGroupCountAll = 3;
        long objectGroupCountThis = 1;

        SearchResponse objectGroupResponse = searchResult(
            String.format(US,
                    "{\n" +
                            "  \"took\": 6,\n" +
                            "  \"timed_out\": false,\n" +
                            "  \"_shards\": {\n" +
                            "    \"total\": 1,\n" +
                            "    \"successful\": 1,\n" +
                            "    \"skipped\": 0,\n" +
                            "    \"failed\": 0\n" +
                            "  },\n" +
                            "  \"aggregations\": {\n" +
                            "    \"sterms#originatingAgencies\": {\n" +
                            "      \"doc_count_error_upper_bound\": 0,\n" +
                            "      \"sum_other_doc_count\": 0,\n" +
                            "      \"buckets\": [\n" +
                            "        {\n" +
                            "          \"key\": \"Identifier0\",\n" +
                            "          \"doc_count\": %d,\n" +
                            "          \"nested#nestedVersions\": {\n" +
                            "            \"doc_count\": 1,\n" +
                            "            \"value_count#binaryObjectCount\": {\n" +
                            "              \"value\": %d\n" +
                            "            },\n" +
                            "            \"sum#binaryObjectSize\": {\n" +
                            "              \"value\": %f\n" +
                            "            }\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    },\n" +
                            "    \"sterms#originatingAgency\": {\n" +
                            "      \"doc_count_error_upper_bound\": 0,\n" +
                            "      \"sum_other_doc_count\": 0,\n" +
                            "      \"buckets\": [\n" +
                            "        {\n" +
                            "          \"key\": \"Identifier1\",\n" +
                            "          \"doc_count\": %d,\n" +
                            "          \"nested#nestedVersions\": {\n" +
                            "            \"doc_count\": 1,\n" +
                            "            \"value_count#binaryObjectCount\": {\n" +
                            "              \"value\": %d\n" +
                            "            },\n" +
                            "            \"sum#binaryObjectSize\": {\n" +
                            "              \"value\": %f\n" +
                            "            }\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    }\n" +
                            "  }\n" +
                            "}",
                objectGroupCountAll, binaryCount, binarySize, objectGroupCountThis, binaryCount, binarySize)
        );

        given(client.basicSearch(eq(UNIT), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(archiveUnitResponse);
        given(client.basicSearch(eq(OBJECTGROUP), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(objectGroupResponse);

        final MetaDataImpl metaData = new MetaDataImpl(
            new MongoDbAccessMetadataImpl(
                mongoRule.getMongoClient(),
                mongoRule.getMongoDatabase().getName(),
                true,
                client,
                tenantList
            )
        );

        // When
        Optional<AccessionRegisterSymbolic> first = metaData.createAccessionRegisterSymbolic(0)
            .stream()
            .map(a -> (AccessionRegisterSymbolic) a)
            .findFirst();

        // Then
        assertThat(first).map(a -> a.getDouble(BINARY_OBJECTS_SIZE)).hasValue(binarySize);
        assertThat(first).map(a -> a.getLong(OBJECT_GROUP)).hasValue(3L);
        assertThat(first).map(a -> a.getLong(BINARY_OBJECT)).hasValue(objectGroupCountAll - objectGroupCountThis);
    }

    @Test
    public void should_add_zero_binaries_and_zero_binaries_total_size_to_related_accession_register_when_object_group_NOT_counted()
        throws IOException {
        // Given
        ElasticsearchAccessMetadata client = PowerMockito.mock(ElasticsearchAccessMetadata.class);

        SearchResponse archiveUnitResponse = searchResult(
            "{\n" +
                    "  \"took\": 6,\n" +
                    "  \"timed_out\": false,\n" +
                    "  \"_shards\": {\n" +
                    "    \"total\": 1,\n" +
                    "    \"successful\": 1,\n" +
                    "    \"skipped\": 0,\n" +
                    "    \"failed\": 0\n" +
                    "  },\n" +
                    "  \"aggregations\": {\n" +
                    "    \"sterms#originatingAgencies\": {\n" +
                    "      \"doc_count_error_upper_bound\": 0,\n" +
                    "      \"sum_other_doc_count\": 0,\n" +
                    "      \"buckets\": [\n" +
                    "        {\n" +
                    "          \"key\": \"Identifier0\",\n" +
                    "          \"doc_count\": 3,\n" +
                    "          \"nested#nestedVersions\": {\n" +
                    "            \"doc_count\": 3\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    },\n" +
                    "    \"sterms#originatingAgency\": {\n" +
                    "      \"doc_count_error_upper_bound\": 0,\n" +
                    "      \"sum_other_doc_count\": 0,\n" +
                    "      \"buckets\": [\n" +
                    "        {\n" +
                    "          \"key\": \"Identifier0\",\n" +
                    "          \"doc_count\": 1,\n" +
                    "          \"nested#nestedVersions\": {\n" +
                    "            \"doc_count\": 1\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
        );
        double binarySize = 0;
        long binaryCount = 0;
        long objectGroupCountAll = 1;
        long objectGroupCountThis = 1;

        SearchResponse objectGroupResponse = searchResult(
            String.format(US,
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": %d,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": %d\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": %.2f\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": %d,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": %d\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": %.2f\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                objectGroupCountAll, binaryCount, binarySize, objectGroupCountThis, binaryCount, binarySize)
        );

        given(client.basicSearch(eq(UNIT), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(archiveUnitResponse);
        given(client.basicSearch(eq(OBJECTGROUP), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(objectGroupResponse);

        final MetaDataImpl metaData = new MetaDataImpl(
            new MongoDbAccessMetadataImpl(
                mongoRule.getMongoClient(),
                mongoRule.getMongoDatabase().getName(),
                true,
                client,
                tenantList
            )
        );

        // When
        Optional<AccessionRegisterSymbolic> first = metaData.createAccessionRegisterSymbolic(0)
            .stream()
            .map(a -> (AccessionRegisterSymbolic) a)
            .findFirst();

        // Then
        assertThat(first).map(a -> a.getDouble(BINARY_OBJECTS_SIZE)).hasValue(0D);
        assertThat(first).map(a -> a.getLong(OBJECT_GROUP)).hasValue(0L);
        assertThat(first).map(a -> a.getLong(BINARY_OBJECT)).hasValue(0L);
    }

    @Test
    public void should_NOT_created_new_accession_register_with_object_group_information_when_no_related_accession_register() throws IOException {
        // Given
        ElasticsearchAccessMetadata client = PowerMockito.mock(ElasticsearchAccessMetadata.class);

        SearchResponse archiveUnitResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );
        SearchResponse objectGroupResponse = searchResult(
                "{\n" +
                        "  \"took\": 6,\n" +
                        "  \"timed_out\": false,\n" +
                        "  \"_shards\": {\n" +
                        "    \"total\": 1,\n" +
                        "    \"successful\": 1,\n" +
                        "    \"skipped\": 0,\n" +
                        "    \"failed\": 0\n" +
                        "  },\n" +
                        "  \"aggregations\": {\n" +
                        "    \"sterms#originatingAgencies\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 88209\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 2\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    },\n" +
                        "    \"sterms#originatingAgency\": {\n" +
                        "      \"doc_count_error_upper_bound\": 0,\n" +
                        "      \"sum_other_doc_count\": 0,\n" +
                        "      \"buckets\": [\n" +
                        "        {\n" +
                        "          \"key\": \"Identifier0\",\n" +
                        "          \"doc_count\": 1,\n" +
                        "          \"nested#nestedVersions\": {\n" +
                        "            \"doc_count\": 1,\n" +
                        "            \"sum#binaryObjectSize\": {\n" +
                        "              \"value\": 88209\n" +
                        "            },\n" +
                        "            \"value_count#binaryObjectCount\": {\n" +
                        "              \"value\": 2\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
        );

        given(client.basicSearch(eq(UNIT), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(archiveUnitResponse);
        given(client.basicSearch(eq(OBJECTGROUP), eq(0), anyListOf(AggregationBuilder.class), any(QueryBuilder.class)))
            .willReturn(objectGroupResponse);

        final MetaDataImpl metaData = new MetaDataImpl(
            new MongoDbAccessMetadataImpl(
                mongoRule.getMongoClient(),
                mongoRule.getMongoDatabase().getName(),
                true,
                client,
                tenantList
            )
        );

        // When
        Optional<AccessionRegisterSymbolic> first = metaData.createAccessionRegisterSymbolic(0)
            .stream()
            .map(a -> (AccessionRegisterSymbolic) a)
            .findFirst();

        // Then
        assertThat(first).isEmpty();
    }

    private List<NamedXContentRegistry.Entry> getDefaultNamedXContents() {
        Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();

        map.put(StringTerms.NAME, (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
        map.put(SumAggregationBuilder.NAME, (p, c) -> ParsedSum.fromXContent(p, (String) c));
        map.put(ValueCountAggregationBuilder.NAME, (p, c) -> ParsedValueCount.fromXContent(p, (String) c));
        map.put(NestedAggregationBuilder.NAME, (p, c) -> ParsedNested.fromXContent(p, (String) c));

        return map.entrySet()
            .stream()
            .map(entry -> new NamedXContentRegistry.Entry(Aggregation.class, new ParseField(entry.getKey()),
                entry.getValue()))
            .collect(Collectors.toList());
    }

    private SearchResponse searchResult(String content) throws IOException {
        NamedXContentRegistry registry = new NamedXContentRegistry(getDefaultNamedXContents());
        return SearchResponse.fromXContent(JsonXContent.jsonXContent.createParser(registry, content));
    }
}
