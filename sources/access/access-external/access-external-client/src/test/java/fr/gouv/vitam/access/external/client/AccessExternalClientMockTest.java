package fr.gouv.vitam.access.external.client;

import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.external.client.configuration.SecureClientConfiguration;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.elimination.EliminationRequestBody;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class AccessExternalClientMockTest {

    final String queryDsql =
        "{ \"$query\" : [ { \"$eq\": { \"title\" : \"test\" } } ], " +
            " \"$filter\": { \"$orderby\": \"#id\" }, " +
            " \"$projection\" : { \"$fields\" : { \"#id\": 1, \"title\" : 2, \"transacdate\": 1 } } " +
            " }";
    final String queryDsqlForGot =
        "{ \"$query\" : [ { \"$exists\": \"#id\" } ], " +
            " \"$filter\": { \"$orderby\": \"#id\" }, " +
            " \"$projection\" : { } " +
            " }";
    final String ID = "identifier1";
    final String USAGE = "usage";
    final String CONTRACT = "contract";
    final int VERSION = 1;
    final int TENANT_ID = 0;
    AccessExternalClient client;

    @Before
    public void givenMockConfExistWhenAccessExternalCreateMockedClientThenReturnOK() {
        AccessExternalClientFactory.changeMode((SecureClientConfiguration)null);
        client = AccessExternalClientFactory.getInstance().getClient();
        assertNotNull(client);
    }

    @Test
    public void givenMockConfExistWhenAccessExternalSelectUnitsThenReturnResult()
        throws Exception {
        assertNotNull(client.selectUnits(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
            JsonHandler.getFromString(queryDsql)));
    }

    @Test
    public void givenMockConfExistWhenAccessExternalSelectUnitsWithInheritedRulesThenReturnResult() {
        assertThatThrownBy(
            () -> client.selectUnitsWithInheritedRules(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
                JsonHandler.getFromString(queryDsql)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void givenMockConfExistWhenAccessExternalSelectUnitbyIDThenReturnResult()
        throws Exception {
        assertNotNull(client.selectUnitbyId(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
            JsonHandler.getFromString(queryDsql), ID));
    }

    @Test
    public void givenMockConfExistWhenAccessExternalUpdateUnitbyIDThenReturnResult()
        throws Exception {
        assertNotNull(client.updateUnitbyId(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
            JsonHandler.getFromString(queryDsql), ID));
    }

    @Test
    public void givenMockConfExistWhenAccessExternalSelectObjectbyIDThenReturnResult()
        throws Exception {
        assertNotNull(client.selectObjectMetadatasByUnitId(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
            JsonHandler.getFromString(queryDsql), ID));
    }

    @Test
    public void givenMockConfExistWhenAccessExternal_selectOperationLogbook_ThenRetururnResult()
        throws Exception {
        assertNotNull(client.selectOperations(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
            JsonHandler.getFromString(queryDsql)));
    }

    @Test
    public void givenMockConfExistWhenAccessExternal_selectOperationbyIdLogbook_ThenRetururnResult()
        throws Exception {
        assertNotNull(client.selectOperationbyId(new VitamContext(TENANT_ID).setAccessContract(CONTRACT), ID,
            JsonHandler.getFromString(queryDsql)));
    }

    @Test
    public void givenMockConfExistWhenAccessExternal_selectUnitLifeCycleByIdLogbook_ThenRetururnResult()
        throws Exception {
        assertNotNull(client.selectUnitLifeCycleById(new VitamContext(TENANT_ID).setAccessContract(CONTRACT), ID,
            JsonHandler.getFromString(queryDsql)));
    }

    @Test
    public void givenMockConfExistWhenAccessExternal_selectObjectGroupLifeCycleByIdLogbook_ThenRetururnResult()
        throws Exception {
        assertNotNull(client.selectObjectGroupLifeCycleById(new VitamContext(TENANT_ID).setAccessContract(CONTRACT), ID,
            JsonHandler.getFromString(queryDsql)));
    }

    @Test
    public void givenMockConfExistWhenAccessExternalSelectObjectsThenReturnResult()
        throws Exception {
        assertNotNull(client.selectObjects(new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
            JsonHandler.getFromString(queryDsqlForGot)));
    }

    @Test
    public void givenMockConfExistWhenAccessExternalStartEliminationAnalysisThenReturnResult() {
        assertThatThrownBy(() -> client.startEliminationAnalysis(
            new VitamContext(TENANT_ID).setAccessContract(CONTRACT), mock(EliminationRequestBody.class)))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
