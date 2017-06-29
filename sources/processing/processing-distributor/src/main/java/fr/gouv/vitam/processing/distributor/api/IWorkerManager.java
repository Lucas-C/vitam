/*
 *  Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *  <p>
 *  contact.vitam@culture.gouv.fr
 *  <p>
 *  This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 *  high volumetry securely and efficiently.
 *  <p>
 *  This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 *  software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 *  circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *  <p>
 *  As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 *  users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 *  successive licensors have only limited liability.
 *  <p>
 *  In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 *  developing or reproducing the software by the user in light of its specific status of free software, that may mean
 *  that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 *  experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 *  software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 *  to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *  <p>
 *  The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 *  accept its terms.
 */
package fr.gouv.vitam.processing.distributor.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.functional.administration.client.model.IngestContractModel;
import fr.gouv.vitam.processing.common.exception.ProcessingBadRequestException;
import fr.gouv.vitam.processing.common.exception.WorkerAlreadyExistsException;
import fr.gouv.vitam.processing.common.exception.WorkerFamilyNotFoundException;
import fr.gouv.vitam.processing.common.exception.WorkerNotFoundException;
import fr.gouv.vitam.processing.common.model.WorkerBean;
import fr.gouv.vitam.processing.common.model.WorkerRemoteConfiguration;
import fr.gouv.vitam.processing.distributor.v2.WorkerFamilyManager;
import fr.gouv.vitam.worker.client.WorkerClient;
import fr.gouv.vitam.worker.client.WorkerClientConfiguration;
import fr.gouv.vitam.worker.client.WorkerClientFactory;

import java.io.File;
import java.util.List;

/**
 * Manage the parallelism calls to worker in the same distributor
 */
public interface IWorkerManager {

    VitamLogger LOGGER = VitamLoggerFactory.getInstance(IWorkerManager.class);
    String WORKKER_DB_PATH = "worker.db";
    File WORKKER_DB_FILE = PropertiesUtils.fileFromDataFolder(WORKKER_DB_PATH);

    /**
     * Do the initialization
     * Load worker from worker.db
     */
    default void initialize() {
        if (WORKKER_DB_FILE.exists()) {
            loadWorkerList(WORKKER_DB_FILE);
        } else {
            LOGGER.warn("No worker list serialization file : " + WORKKER_DB_FILE.getName());
        }
    }

    /**
     * To load a registered worker list
     *
     * @param registerWorkerFile
     */
    default void loadWorkerList(File registerWorkerFile) {
        // Load the list of worker from database
        // for now it is a file content json data
        try {
            List<WorkerBean>  workerBeans = JsonHandler.getFromFileAsTypeRefence(registerWorkerFile, new TypeReference<List<WorkerBean>>() {});
            for (WorkerBean workerBean : workerBeans) {
                String workerId = workerBean.getWorkerId();
                String familyId = workerBean.getFamily();
                // Ignore if the familyId or the workerId is null
                if (familyId == null || workerId == null) {
                    // Mandatory argument missing : Continue with the next worker
                    continue;
                }
                WorkerRemoteConfiguration config = workerBean.getConfiguration();
                if (checkStatusWorker(config.getServerHost(), config.getServerPort())) {
                    try {
                        registerWorker(workerBean);
                    } catch (WorkerAlreadyExistsException e) {
                        // This case should almost never happened as we are in the initialization
                        LOGGER.error("Worker already exists during the initialization", e);
                    }
                }
            }
        } catch (InvalidParseOperationException e) {
            LOGGER.error("Cannot load worker list from database.", e);
            // Ignore the rest of the file
            return;
        }

        // load to the list of WORKERS_LIST
        marshallToDB();
    }

    /**
     * Check status
     *
     * @param serverHost
     * @param serverPort
     * @return
     */
    default boolean checkStatusWorker(String serverHost, int serverPort) {
        WorkerClientConfiguration workerClientConfiguration =
            new WorkerClientConfiguration(serverHost, serverPort);
        WorkerClientFactory.changeMode(workerClientConfiguration);
        WorkerClient workerClient = WorkerClientFactory.getInstance(workerClientConfiguration).getClient();
        try {
            workerClient.checkStatus();
            return true;
        } catch (Exception e) {
            LOGGER.error("Worker server [" + serverHost + ":" + serverPort + "] is not active.", e);
            return false;
        }
    }

    /**
     * To register a worker in the processing
     *
     * @param familyId          : family of this worker
     * @param workerId          : ID of the worker
     * @param workerInformation : Worker Json representation
     * @throws WorkerAlreadyExistsException   : when the worker is already registered
     * @throws ProcessingBadRequestException  if cannot register worker to family
     * @throws InvalidParseOperationException if worker description is not well-formed
     */
    void registerWorker(String familyId, String workerId, String workerInformation)
        throws WorkerAlreadyExistsException, ProcessingBadRequestException, InvalidParseOperationException;

    void registerWorker(WorkerBean workerBean) throws WorkerAlreadyExistsException;

    /**
     * To unregister a worker in the processing
     *
     * @param familyId : family of this worker
     * @param workerId : ID of the worker
     * @throws WorkerFamilyNotFoundException : when the family is unknown
     * @throws WorkerNotFoundException       : when the ID of the worker is unknown in the family
     * @throws InterruptedException          if error in stopping thread
     */
    void unregisterWorker(String familyId, String workerId)
        throws WorkerFamilyNotFoundException, WorkerNotFoundException, InterruptedException;

    void marshallToDB();

    WorkerFamilyManager findWorkerBy(String workerFamily);
}
