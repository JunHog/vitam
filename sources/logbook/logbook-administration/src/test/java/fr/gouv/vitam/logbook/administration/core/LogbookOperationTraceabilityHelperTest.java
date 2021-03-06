package fr.gouv.vitam.logbook.administration.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.MongoCursor;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.security.merkletree.MerkleTreeAlgo;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.logbook.common.model.TraceabilityFile;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.logbook.common.traceability.LogbookTraceabilityHelper;
import fr.gouv.vitam.logbook.operations.api.LogbookOperations;
import org.apache.commons.codec.binary.Base64;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class LogbookOperationTraceabilityHelperTest {

    private static final String LOGBOOK_OPERATION_WITH_TOKEN = "/logbookOperationWithToken.json";
    public static final Integer OPERATION_TRACEABILITY_TEMPORIZATION_DELAY = 300;
    private static final String FILE_NAME = "0_operations_20171031_151118.zip";
    private static final String LOGBOOK_OPERATION_START_DATE = "2017-10-31T15:11:15.405";
    private static LocalDateTime LOGBOOK_OPERATION_EVENT_DATE;

    private static final String LAST_OPERATION_HASH =
        "AQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQBAgMEAQIDBAECAwQ=";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @BeforeClass
    public static void init() throws ParseException {
        LOGBOOK_OPERATION_EVENT_DATE = LocalDateUtil.fromDate(LocalDateUtil.getDate("2017-10-31T15:11:18.569"));
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_timestamp_token() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        InputStream stream = getClass().getResourceAsStream(LOGBOOK_OPERATION_WITH_TOKEN);
        JsonNode jsonNode = JsonHandler.getFromInputStream(stream);
        LogbookOperation logbookOperation = new LogbookOperation(jsonNode);

        // When
        byte[] token = helper.extractTimestampToken(logbookOperation);

        // Then
        assertThat(Base64.encodeBase64String(token)).isEqualTo(LAST_OPERATION_HASH);
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_startDate_from_last_event() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        InputStream stream = getClass().getResourceAsStream(LOGBOOK_OPERATION_WITH_TOKEN);
        JsonNode jsonNode = JsonHandler.getFromInputStream(stream);
        LogbookOperation logbookOperation = new LogbookOperation(jsonNode);

        given(logbookOperations.findLastTraceabilityOperationOK()).willReturn(logbookOperation);

        // When
        LocalDateTime beforeInit = LocalDateUtil.now();
        helper.initialize();
        LocalDateTime afterInit = LocalDateUtil.now();

        // Then
        assertThat(helper.getTraceabilityStartDate()).isEqualTo(LocalDateUtil
            .getFormattedDateForMongo(LOGBOOK_OPERATION_EVENT_DATE));
        assertThat(LocalDateUtil.parseMongoFormattedDate(helper.getTraceabilityEndDate()))
            .isAfterOrEqualTo(beforeInit.minusSeconds(OPERATION_TRACEABILITY_TEMPORIZATION_DELAY))
            .isBeforeOrEqualTo(afterInit.minusSeconds(OPERATION_TRACEABILITY_TEMPORIZATION_DELAY));
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_startDate_from_no_event() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        given(logbookOperations.findLastTraceabilityOperationOK()).willReturn(null);

        // When
        helper.initialize();

        // Then
        assertThat(helper.getTraceabilityStartDate())
            .isEqualTo(LocalDateUtil.getFormattedDateForMongo(LogbookTraceabilityHelper.INITIAL_START_DATE));
    }

    @Test
    @RunWithCustomExecutor
    public void should_correctly_save_data_and_compute_start_date_for_first_traceability() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        InputStream stream = getClass().getResourceAsStream(LOGBOOK_OPERATION_WITH_TOKEN);
        JsonNode jsonNode = JsonHandler.getFromInputStream(stream);
        LogbookOperation logbookOperation = new LogbookOperation(jsonNode);
        MongoCursor<LogbookOperation> cursor = getMongoCursorFor(logbookOperation);

        given(logbookOperations.selectOperationsByLastPersistenceDateInterval(any(), any())).willReturn(cursor);

        final MerkleTreeAlgo algo = new MerkleTreeAlgo(VitamConfiguration.getDefaultDigestType());

        File zipFile = new File(folder.newFolder(), String.format(FILE_NAME));
        TraceabilityFile file = new TraceabilityFile(zipFile);

        // When
        helper.saveDataInZip(algo, file);
        file.close();

        // Then
        assertThat(Files.size(Paths.get(zipFile.getPath()))).isEqualTo(5534L);
    }

    @Test
    @RunWithCustomExecutor
    public void should_correctly_save_data_and_not_update_startDate_for_not_first_traceability() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        InputStream stream = getClass().getResourceAsStream(LOGBOOK_OPERATION_WITH_TOKEN);
        JsonNode jsonNode = JsonHandler.getFromInputStream(stream);
        LogbookOperation logbookOperation = new LogbookOperation(jsonNode);
        MongoCursor<LogbookOperation> cursor = getMongoCursorFor(logbookOperation);

        given(logbookOperations.selectOperationsByLastPersistenceDateInterval(any(), any())).willReturn(cursor);

        final MerkleTreeAlgo algo = new MerkleTreeAlgo(VitamConfiguration.getDefaultDigestType());

        File zipFile = new File(folder.newFolder(), String.format(FILE_NAME));
        TraceabilityFile file = new TraceabilityFile(zipFile);

        // When
        helper.saveDataInZip(algo, file);
        file.close();

        // Then
        assertThat(Files.size(Paths.get(zipFile.getPath()))).isEqualTo(5534L);
    }

    @Test
    @RunWithCustomExecutor
    public void should_return_null_date_and_token_if_no_previous_logbook() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        // When
        String date = helper.getPreviousStartDate();
        byte[] token = helper.getPreviousTimestampToken();

        // Then
        assertThat(date).isNull();
        assertThat(token).isNull();
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_date_and_token_from_last_event() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        InputStream stream = getClass().getResourceAsStream(LOGBOOK_OPERATION_WITH_TOKEN);
        JsonNode jsonNode = JsonHandler.getFromInputStream(stream);
        LogbookOperation logbookOperation = new LogbookOperation(jsonNode);

        given(logbookOperations.findLastTraceabilityOperationOK()).willReturn(logbookOperation);
        helper.initialize();

        // When
        String date = helper.getPreviousStartDate();
        byte[] token = helper.getPreviousTimestampToken();

        // Then
        assertThat(date).isEqualTo(LOGBOOK_OPERATION_START_DATE);
        assertThat(Base64.encodeBase64String(token)).isEqualTo(LAST_OPERATION_HASH);
    }

    @Test
    @RunWithCustomExecutor
    public void should_extract_correctly_event_number() throws Exception {
        // Given
        LogbookOperations logbookOperations = mock(LogbookOperations.class);
        GUID guid = GUIDFactory.newOperationLogbookGUID(0);

        LogbookOperationTraceabilityHelper helper =
            new LogbookOperationTraceabilityHelper(logbookOperations, guid, OPERATION_TRACEABILITY_TEMPORIZATION_DELAY);

        InputStream stream = getClass().getResourceAsStream(LOGBOOK_OPERATION_WITH_TOKEN);
        JsonNode jsonNode = JsonHandler.getFromInputStream(stream);
        LogbookOperation logbookOperation = new LogbookOperation(jsonNode);
        MongoCursor<LogbookOperation> cursor = getMongoCursorFor(logbookOperation);

        given(logbookOperations.selectOperationsByLastPersistenceDateInterval(any(), any())).willReturn(cursor);

        final MerkleTreeAlgo algo = new MerkleTreeAlgo(VitamConfiguration.getDefaultDigestType());

        File zipFile = new File(folder.newFolder(), String.format(FILE_NAME));
        TraceabilityFile file = new TraceabilityFile(zipFile);
        helper.saveDataInZip(algo, file);

        // When
        Long size = helper.getDataSize();

        // Then
        assertThat(size).isEqualTo(1);
    }

    private MongoCursor<LogbookOperation> getMongoCursorFor(LogbookOperation logbookOperation) {
        List<LogbookOperation> logbookList = new ArrayList<LogbookOperation>();
        logbookList.add(logbookOperation);

        return getMongoCursorFor(logbookList);
    }

    private MongoCursor<LogbookOperation> getMongoCursorFor(List<LogbookOperation> logbookList) {
        return new MongoCursor<LogbookOperation>() {
            int rank = 0;
            int max = logbookList.size();
            List<LogbookOperation> list = logbookList;

            @Override
            public void close() {
                // Nothing to do
            }

            @Override
            public boolean hasNext() {
                return rank < max;
            }

            @Override
            public LogbookOperation next() {
                if (rank >= max) {
                    throw new NoSuchElementException();
                }
                final LogbookOperation operation = list.get(rank);
                rank++;
                return operation;
            }

            @Override
            public LogbookOperation tryNext() {
                if (rank >= max) {
                    return null;
                }
                final LogbookOperation operation = list.get(rank);
                rank++;
                return operation;
            }

            @Override
            public ServerCursor getServerCursor() {
                return null;
            }

            @Override
            public ServerAddress getServerAddress() {
                return null;
            }
        };
    }

}
