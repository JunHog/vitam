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
package fr.gouv.vitam.batch.report.model.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.gouv.vitam.batch.report.model.PreservationStatus;
import fr.gouv.vitam.common.model.administration.ActionTypePreservation;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class PreservationReportEntry extends ReportEntry {

    public static final String ID = "_id";
    public static final String PRESERVATION_REPORT_ID = "preservationReportId";
    public static final String UNIT_ID = "unitId";
    public static final String OBJECT_GROUP_ID = "objectGroupId";
    public static final String PROCESS_ID = "processId";
    public static final String TENANT = "_tenant";
    public static final String CREATION_DATE_TIME = "creationDateTime";
    public static final String STATUS = "status";
    public static final String ACTION = "action";
    public static final String GRIFFIN_ID = "griffinId";
    public static final String SCENARIO_ID = "preservationScenarioId";
    public static final String ANALYSE_RESULT = "analyseResult";
    public static final String INPUT_OBJECT_ID = "inputObjectId";
    public static final String OUTPUT_OBJECT_ID = "outputObjectId";

    @JsonProperty(ID)
    private String preservationId;

    @JsonProperty(PRESERVATION_REPORT_ID)
    private String preservationReportId;

    @JsonProperty(PROCESS_ID)
    private String processId;

    @JsonProperty(TENANT)
    private int tenant;

    @JsonProperty(CREATION_DATE_TIME)
    private String creationDateTime;

    @JsonProperty(STATUS)
    private PreservationStatus status;

    @JsonProperty(UNIT_ID)
    private String unitId;

    @JsonProperty(OBJECT_GROUP_ID)
    private String objectGroupId;

    @JsonProperty(ACTION)
    private ActionTypePreservation action;

    @JsonProperty(ANALYSE_RESULT)
    private String analyseResult;

    @JsonProperty(INPUT_OBJECT_ID)
    private String inputObjectId;

    @JsonProperty(OUTPUT_OBJECT_ID)
    private String outputObjectId;

    @JsonProperty(GRIFFIN_ID)
    private String griffinId;

    @JsonProperty(SCENARIO_ID)
    private String preservationScenarioId;



    public PreservationReportEntry() {
        // Empty constructor for deserialization
    }

    public PreservationReportEntry(String preservationId, String processId, int tenant, String creationDateTime, PreservationStatus status,
        String unitId, String objectGroupId, ActionTypePreservation action, String analyseResult, String inputObjectId, String outputObjectId,
        String outcome, String griffinId, String preservationScenarioId) {
        super(outcome, "preservation", preservationId);
        this.preservationId = preservationId;
        this.preservationReportId = preservationId;
        this.processId = processId;
        this.tenant = tenant;
        this.creationDateTime = creationDateTime;
        this.status = status;
        this.unitId = unitId;
        this.objectGroupId = objectGroupId;
        this.action = action;
        this.analyseResult = analyseResult;
        this.inputObjectId = inputObjectId;
        this.outputObjectId = outputObjectId;
        this.griffinId = griffinId;
        this.preservationScenarioId = preservationScenarioId;
    }

    public String getPreservationId() {
        return preservationId;
    }

    public void setPreservationId(String preservationId) {
        this.preservationId = preservationId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public int getTenant() {
        return tenant;
    }

    public void setTenant(int tenant) {
        this.tenant = tenant;
    }

    public String getCreationDateTime() {
        return creationDateTime;
    }

    public void setCreationDateTime(String creationDateTime) {
        this.creationDateTime = creationDateTime;
    }

    public PreservationStatus getStatus() {
        return status;
    }

    public void setStatus(PreservationStatus status) {
        this.status = status;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }

    public String getObjectGroupId() {
        return objectGroupId;
    }

    public void setObjectGroupId(String objectGroupId) {
        this.objectGroupId = objectGroupId;
    }

    public String getInputObjectId() {
        return inputObjectId;
    }

    public void setInputObjectId(String inputObjectId) {
        this.inputObjectId = inputObjectId;
    }

    public String getOutputObjectId() {
        return outputObjectId;
    }

    public void setOutputObjectId(String outputObjectId) {
        this.outputObjectId = outputObjectId;
    }

    public ActionTypePreservation getAction() {
        return action;
    }

    public void setAction(ActionTypePreservation action) {
        this.action = action;
    }

    public String getAnalyseResult() {
        return analyseResult;
    }

    public void setAnalyseResult(String analyseResult) {
        this.analyseResult = analyseResult;
    }

    public String getGriffinId() {
        return griffinId;
    }

    public void setGriffinId(String griffinId) {
        this.griffinId = griffinId;
    }

    public void setPreservationScenarioId(String preservationScenarioId) {
        this.preservationScenarioId = preservationScenarioId;
    }

    public String getPreservationScenarioId() {
        return preservationScenarioId;
    }

    public void setPreservationReportId(String preservationReportId) {
        this.preservationReportId = preservationReportId;
    }

    public String getPreservationReportId() {
        return preservationReportId;
    }
}
