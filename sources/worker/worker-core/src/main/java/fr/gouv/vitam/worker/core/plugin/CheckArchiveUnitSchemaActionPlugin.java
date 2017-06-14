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
package fr.gouv.vitam.worker.core.plugin;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.common.utils.IngestWorkflowConstants;
import fr.gouv.vitam.worker.common.utils.SchemaValidationStatus;
import fr.gouv.vitam.worker.common.utils.SchemaValidationUtils;
import fr.gouv.vitam.worker.common.utils.SchemaValidationStatus.SchemaValidationStatusEnum;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * CheckArchiveUnitSchema Plugin.<br>
 *
 */

public class CheckArchiveUnitSchemaActionPlugin extends ActionHandler {
    private static final String WORKSPACE_SERVER_ERROR = "Workspace Server Error";
    
    private static final String SCHEMA_ERROR = "Json validation couldn't be done";

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(CheckArchiveUnitSchemaActionPlugin.class);

    private static final String CHECK_UNIT_SCHEMA_TASK_ID = "CHECK_UNIT_SCHEMA";

    private HandlerIO handlerIO;

    private static final String NOT_AU_JSON_VALID = "NOT_AU_JSON_VALID";
    private static final String NOT_JSON_FILE = "NOT_JSON_FILE";

    /**
     * Empty constructor UnitsRulesComputePlugin
     *
     */
    public CheckArchiveUnitSchemaActionPlugin() {
        // Empty
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler) {
        handlerIO = handler;
        final ItemStatus itemStatus = new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID);
        SchemaValidationStatus schemaValidationStatus;
        try {
            schemaValidationStatus = checkAUJsonAgainstSchema(params, itemStatus);
            switch (schemaValidationStatus.getValidationStatus()) {
                case VALID:
                    itemStatus.increment(StatusCode.OK);
                    return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID,
                        itemStatus);
                case NOT_AU_JSON_VALID:
                    itemStatus.setItemId(NOT_AU_JSON_VALID);
                    itemStatus.increment(StatusCode.KO);
                    itemStatus.setEvDetailData(schemaValidationStatus.getValidationMessage());
                    return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID,
                        itemStatus);
                case NOT_JSON_FILE:
                    itemStatus.setItemId(NOT_JSON_FILE);
                    itemStatus.increment(StatusCode.KO);
                    itemStatus.setEvDetailData(schemaValidationStatus.getValidationMessage());
                    return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID,
                        itemStatus);
            }
        } catch (final ProcessingException e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.FATAL);
        }
        return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID, itemStatus);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing to check
    }


    private SchemaValidationStatus checkAUJsonAgainstSchema(WorkerParameters params, ItemStatus itemStatus)
        throws ProcessingException {
        final String objectName = params.getObjectName();

        try (InputStream archiveUnitToJson =
            handlerIO.getInputStreamFromWorkspace(IngestWorkflowConstants.ARCHIVE_UNIT_FOLDER + "/" + objectName)) {
            SchemaValidationUtils validator = new SchemaValidationUtils();
            JsonNode archiveUnit = JsonHandler.getFromInputStream(archiveUnitToJson);
            return validator.validateUnit(archiveUnit);
        } catch (final InvalidParseOperationException e) {
            LOGGER.error("File couldnt be converted into json", e);
            return new SchemaValidationStatus("File is not a valid json file",
                SchemaValidationStatusEnum.NOT_JSON_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException |
            IOException e) {
            LOGGER.error(WORKSPACE_SERVER_ERROR);
            throw new ProcessingException(e);
        } catch (com.github.fge.jsonschema.core.exceptions.ProcessingException e) {
            LOGGER.error(SCHEMA_ERROR);
            throw new ProcessingException(e);
        }
    }


}
