package fr.gouv.vitam.access.external.client;

import java.io.InputStream;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.access.external.api.AccessExtAPI;
import fr.gouv.vitam.access.external.api.AdminCollections;
import fr.gouv.vitam.access.external.common.exception.AccessExternalClientException;
import fr.gouv.vitam.access.external.common.exception.AccessExternalClientNotFoundException;
import fr.gouv.vitam.access.external.common.exception.AccessExternalClientServerException;
import fr.gouv.vitam.access.external.common.exception.AccessExternalNotFoundException;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.AccessUnauthorizedException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.exception.VitamClientInternalException;
import fr.gouv.vitam.common.external.client.DefaultClient;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.AccessionRegisterSummaryModel;
import fr.gouv.vitam.common.model.administration.ContextModel;
import fr.gouv.vitam.common.model.administration.FileFormatModel;
import fr.gouv.vitam.common.model.administration.FileRulesModel;
import fr.gouv.vitam.common.model.administration.IngestContractModel;
import fr.gouv.vitam.common.model.administration.ProfileModel;
import fr.gouv.vitam.logbook.common.client.ErrorMessage;

/**
 * Rest client implementation for Access External
 */
public class AdminExternalClientRest extends DefaultClient implements AdminExternalClient {

    private static final String ADMIN_EXTERNAL_MODULE = "AdminExternalModule";

    private static final String ACCESS_EXTERNAL_MODULE = "AccessExternalModule";

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AdminExternalClientRest.class);

    private static final String URI_NOT_FOUND = "URI not found";
    private static final String UPDATE_ACCESS_CONTRACT = AccessExtAPI.ACCESS_CONTRACT_API_UPDATE + "/";
    private static final String UPDATE_INGEST_CONTRACT = AccessExtAPI.ENTRY_CONTRACT_API_UPDATE + "/";
    private static final String UPDATE_CONTEXT = AccessExtAPI.CONTEXTS_API_UPDATE + "/";
    private static final String LOGBOOK_CHECK = AccessExtAPI.TRACEABILITY_API + "/check";



    AdminExternalClientRest(AdminExternalClientFactory factory) {
        super(factory);
    }

    @Override
    public Response checkDocuments(AdminCollections documentType, InputStream stream, Integer tenantId)
        throws VitamClientException {

        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        try {
            return performRequest(HttpMethod.PUT, documentType.getName(), headers,
                stream, MediaType.APPLICATION_OCTET_STREAM_TYPE,
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        } catch (final VitamClientInternalException e) {
            LOGGER.error("VitamClientInternalException: ", e);
            throw new VitamClientException(e);
        }
    }

    @Override
    public Status createDocuments(AdminCollections documentType, InputStream stream, String filename, Integer tenantId)
        throws AccessExternalClientException {
        Response response = null;
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        headers.add(GlobalDataRest.X_FILENAME, filename);
        try {
            response = performRequest(HttpMethod.POST, documentType.getName(), headers,
                stream, MediaType.APPLICATION_OCTET_STREAM_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new AccessExternalClientNotFoundException(URI_NOT_FOUND);
            }
            if (response.getStatus() == Status.FORBIDDEN.getStatusCode()) {
                throw new AccessExternalClientException(JsonHandler.unprettyPrint(response.readEntity(String.class)));
            }
            final Status status = Status.fromStatusCode(response.getStatus());
            return status;
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<FileFormatModel> findFormats(JsonNode select, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.FORMATS, select, tenantId, contractName, FileFormatModel.class);
    }

    @Override
    public RequestResponse<FileRulesModel> findRules(JsonNode select, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.RULES, select, tenantId, contractName, FileRulesModel.class);
    }

    @Override
    public RequestResponse<IngestContractModel> findIngestContracts(JsonNode select, Integer tenantId,
        String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.ENTRY_CONTRACTS, select, tenantId, contractName,
            IngestContractModel.class);
    }

    @Override
    public RequestResponse<AccessContractModel> findAccessContracts(JsonNode select, Integer tenantId,
        String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.ACCESS_CONTRACTS, select, tenantId, contractName,
            AccessContractModel.class);
    }

    @Override
    public RequestResponse<ContextModel> findContexts(JsonNode select, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.CONTEXTS, select, tenantId, contractName,
            ContextModel.class);
    }

    @Override
    public RequestResponse<ProfileModel> findProfiles(JsonNode select, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.PROFILE, select, tenantId, contractName,
            ProfileModel.class);
    }

    @Override
    public RequestResponse<AccessionRegisterSummaryModel> findAccessionRegister(JsonNode select, Integer tenantId,
        String contractName)
        throws VitamClientException {
        return internalFindDocuments(AdminCollections.ACCESSION_REGISTERS, select, tenantId, contractName,
            AccessionRegisterSummaryModel.class);
    }


    private RequestResponse internalFindDocuments(AdminCollections documentType, JsonNode select, Integer tenantId,
        String contractName, Class clazz)
        throws VitamClientException {
        Response response = null;
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        if (contractName != null) {
            headers.add(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractName);
        }

        try {
            response = performRequest(HttpMethod.GET, documentType.getName(), headers,
                select, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, clazz);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (final VitamClientException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new VitamClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse getAccessionRegisterDetail(String id, JsonNode query, Integer tenantId, String contractName)
        throws InvalidParseOperationException, AccessExternalClientServerException,
        AccessExternalClientNotFoundException {
        Response response = null;
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_HTTP_METHOD_OVERRIDE, HttpMethod.GET);
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        headers.add(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractName);

        try {
            response = performRequest(HttpMethod.POST,
                AccessExtAPI.ACCESSION_REGISTERS_API + "/" + id + "/" +
                    AccessExtAPI.ACCESSION_REGISTERS_DETAIL,
                headers,
                query, MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE, false);

            RequestResponse requestResponse = RequestResponse.parseFromResponse(response);
            if (requestResponse.isOk()) {
                return requestResponse;
            } else {
                final VitamError vitamError =
                    new VitamError(VitamCode.ACCESS_EXTERNAL_GET_ACCESSION_REGISTER_DETAIL_ERROR.getItem())
                        .setMessage(VitamCode.ACCESS_EXTERNAL_GET_ACCESSION_REGISTER_DETAIL_ERROR.getMessage())
                        .setState(StatusCode.KO.name())
                        .setContext(ACCESS_EXTERNAL_MODULE)
                        .setDescription(VitamCode.ACCESS_EXTERNAL_GET_ACCESSION_REGISTER_DETAIL_ERROR.getMessage());

                if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                    return vitamError.setHttpCode(Status.UNAUTHORIZED.getStatusCode())
                        .setDescription(
                            VitamCode.ACCESS_EXTERNAL_GET_ACCESSION_REGISTER_DETAIL_ERROR.getMessage() + " Cause : " +
                                Status.UNAUTHORIZED.getReasonPhrase());
                } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    return vitamError.setHttpCode(Status.NOT_FOUND.getStatusCode())
                        .setDescription(
                            VitamCode.ACCESS_EXTERNAL_GET_ACCESSION_REGISTER_DETAIL_ERROR.getMessage() + " Cause : " +
                                Status.NOT_FOUND.getReasonPhrase());
                } else if (response.getStatus() == Status.PRECONDITION_FAILED.getStatusCode()) {
                    return vitamError.setHttpCode(Status.PRECONDITION_FAILED.getStatusCode())
                        .setDescription(
                            VitamCode.ACCESS_EXTERNAL_GET_ACCESSION_REGISTER_DETAIL_ERROR.getMessage() + " Cause : " +
                                Status.PRECONDITION_FAILED.getReasonPhrase());
                } else {
                    return requestResponse;
                }
            }
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }


    @Override
    public RequestResponse importContracts(InputStream contracts, Integer tenantId, AdminCollections collection)
        throws AccessExternalClientException {
        // FIXME : should be replaced by createDocuments
        ParametersChecker.checkParameter("The input contracts json is mandatory", contracts, collection);
        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        try {
            response = performRequest(HttpMethod.POST, collection.getName(), headers,
                contracts, MediaType.APPLICATION_OCTET_STREAM_TYPE,
                MediaType.APPLICATION_JSON_TYPE);


            // FIXME quick fix for response OK, adapt response for all response types
            if (response.getStatus() == Response.Status.OK.getStatusCode() ||
                response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                return new RequestResponseOK().setHttpCode(Status.OK.getStatusCode());
            } else {
                return RequestResponse.parseFromResponse(response);
            }
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse updateAccessContract(String id, JsonNode queryDsl, Integer tenantId)
        throws AccessExternalClientException {
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        Response response = null;
        try {
            response = performRequest(HttpMethod.PUT, UPDATE_ACCESS_CONTRACT + id, headers,
                queryDsl, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
            return RequestResponse.parseFromResponse(response);
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse updateIngestContract(String id, JsonNode queryDsl, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientException {
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        Response response = null;
        try {
            response = performRequest(HttpMethod.PUT, UPDATE_INGEST_CONTRACT + id, headers,
                queryDsl, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
            return RequestResponse.parseFromResponse(response);
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse createProfiles(InputStream profiles, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientException {
        ParametersChecker.checkParameter("The input profile json is mandatory", profiles, AdminCollections.PROFILE);
        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        try {
            response = performRequest(HttpMethod.POST, AdminCollections.PROFILE.getName(), headers,
                profiles, MediaType.APPLICATION_OCTET_STREAM_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
            return RequestResponse.parseFromResponse(response);
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse importProfileFile(String profileMetadataId, InputStream profile, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientException {
        ParametersChecker.checkParameter("The input profile stream is mandatory", profile, AdminCollections.PROFILE);
        ParametersChecker.checkParameter(profileMetadataId, "The profile id is mandatory");
        Response response = null;
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        try {
            response =
                performRequest(HttpMethod.PUT, AdminCollections.PROFILE.getName() + "/" + profileMetadataId, headers,
                    profile, MediaType.APPLICATION_OCTET_STREAM_TYPE,
                    MediaType.APPLICATION_JSON_TYPE);
            return RequestResponse.parseFromResponse(response);
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public Response downloadProfileFile(String profileMetadataId, Integer tenantId)
        throws AccessExternalClientException,
        AccessExternalNotFoundException {
        ParametersChecker.checkParameter("Profile is is required", profileMetadataId);


        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        Response response = null;

        Status status = Status.BAD_REQUEST;
        try {
            response =
                performRequest(HttpMethod.GET, AdminCollections.PROFILE.getName() + "/" + profileMetadataId, headers,
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            status = Status.fromStatusCode(response.getStatus());
            switch (status) {
                case OK:
                    return response;
                default: {
                    String msgErr = "Error while download profile file : " + profileMetadataId;
                    final RequestResponse requestResponse = RequestResponse.parseFromResponse(response);
                    if (!requestResponse.isOk()) {
                        VitamError error = (VitamError) requestResponse;
                        msgErr = error.getDescription();
                    }
                    throw new AccessExternalNotFoundException(msgErr);
                }
            }

        } catch (final VitamClientInternalException e) {
            throw new AccessExternalClientException(INTERNAL_SERVER_ERROR, e); // access-common
        } finally {
            if (status != Status.OK) {
                consumeAnyEntityAndClose(response);
            }
        }
    }

    @Override
    public RequestResponse importContexts(InputStream contexts, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientServerException {
        Response response = null;
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        try {
            response = performRequest(HttpMethod.POST, AdminCollections.CONTEXTS.getName(), headers,
                contexts, MediaType.APPLICATION_OCTET_STREAM_TYPE,
                MediaType.APPLICATION_JSON_TYPE);

            if (response.getStatus() == Response.Status.OK.getStatusCode() ||
                response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                return new RequestResponseOK().setHttpCode(Status.OK.getStatusCode());
            } else {
                return RequestResponse.parseFromResponse(response);
            }

        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse updateContext(String id, JsonNode queryDsl, Integer tenantId)
        throws AccessExternalClientException {
        Response response = null;
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        try {
            response = performRequest(HttpMethod.PUT, UPDATE_CONTEXT + id, headers,
                queryDsl, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
            return RequestResponse.parseFromResponse(response);
        } catch (final VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse checkTraceabilityOperation(JsonNode query, Integer tenantId, String contractName)
        throws AccessExternalClientServerException, AccessUnauthorizedException {
        Response response = null;
        try {
            final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
            headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
            headers.add(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractName);

            response = performRequest(HttpMethod.POST, LOGBOOK_CHECK, headers, query, MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
            final Status status = Status.fromStatusCode(response.getStatus());

            RequestResponse requestResponse = RequestResponse.parseFromResponse(response);
            if (requestResponse.isOk()) {
                return requestResponse;
            } else {
                final VitamError vitamError =
                    new VitamError(VitamCode.ACCESS_EXTERNAL_CHECK_TRACEABILITY_OPERATION_ERROR.getItem())
                        .setMessage(VitamCode.ACCESS_EXTERNAL_CHECK_TRACEABILITY_OPERATION_ERROR.getMessage())
                        .setContext(ACCESS_EXTERNAL_MODULE)
                        .setDescription(
                            VitamCode.ACCESS_EXTERNAL_CHECK_TRACEABILITY_OPERATION_ERROR.getMessage() + " Cause : " +
                                ((VitamError) requestResponse).getDescription());

                switch (status) {
                    case OK:
                        return requestResponse;
                    case UNAUTHORIZED:
                        return vitamError.setHttpCode(Status.UNAUTHORIZED.getStatusCode())
                            .setDescription(VitamCode.ACCESS_EXTERNAL_CHECK_TRACEABILITY_OPERATION_ERROR.getMessage() +
                                " Cause : " +
                                Status.UNAUTHORIZED.getReasonPhrase());
                    default:
                        LOGGER
                            .error(
                                "checks operation tracebility is " + status.name() + ":" + vitamError.getDescription());
                        return vitamError.setHttpCode(status.getStatusCode());
                }
            }

        } catch (VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public Response downloadTraceabilityOperationFile(String operationId, Integer tenantId, String contractName)
        throws AccessExternalClientServerException, AccessUnauthorizedException {
        Response response = null;
        try {
            final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
            headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
            headers.add(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractName);

            response = performRequest(HttpMethod.GET, AccessExtAPI.TRACEABILITY_API + "/" + operationId, headers, null,
                null, MediaType.APPLICATION_OCTET_STREAM_TYPE);

            final Status status = Status.fromStatusCode(response.getStatus());
            switch (status) {
                case OK:
                    return response;
                case UNAUTHORIZED:
                    throw new AccessUnauthorizedException(status.getReasonPhrase());
                default:
                    LOGGER.error("checks operation tracebility is " + status.name() + ":" + status.getReasonPhrase());
                    throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage());
            }
        } catch (VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            if (response != null && response.getStatus() != Status.OK.getStatusCode()) {
                consumeAnyEntityAndClose(response);
            }
        }
    }

    @Override
    public Status launchAudit(JsonNode auditOption, Integer tenantId, String contractName)
        throws AccessExternalClientServerException {
        Response response = null;

        try {
            final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
            headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
            headers.add(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractName);

            response = performRequest(HttpMethod.POST, AccessExtAPI.AUDITS_API, headers, auditOption,
                MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE);
        } catch (VitamClientInternalException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new AccessExternalClientServerException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            if (response != null && response.getStatus() != Status.OK.getStatusCode()) {
                consumeAnyEntityAndClose(response);
            }
        }
        return Status.fromStatusCode(response.getStatus());
    }

    private RequestResponse internalFindDocumentById(AdminCollections documentType, String documentId, Integer tenantId,
        String contractName, Class clazz)
        throws VitamClientException {
        Response response = null;
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(GlobalDataRest.X_TENANT_ID, tenantId);
        if (contractName != null) {
            headers.add(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractName);
        }
        try {
            response = performRequest(HttpMethod.GET, documentType.getName() + "/" + documentId, headers,
                null, null, MediaType.APPLICATION_JSON_TYPE, false);
            return RequestResponse.parseFromResponse(response, clazz);

        } catch (IllegalStateException e) {
            LOGGER.error("Could not parse server response ", e);
            throw createExceptionFromResponse(response);
        } catch (final VitamClientException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            throw new VitamClientException(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
        } finally {
            consumeAnyEntityAndClose(response);
        }
    }

    @Override
    public RequestResponse<FileFormatModel> findFormatById(String formatId, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocumentById(AdminCollections.FORMATS, formatId, tenantId, contractName, FileFormatModel.class);
    }

    @Override
    public RequestResponse<FileRulesModel> findRuleById(String ruleId, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocumentById(AdminCollections.RULES, ruleId, tenantId, contractName, FileRulesModel.class);
    }

    @Override
    public RequestResponse<IngestContractModel> findIngestContractById(String contractId, Integer tenantId,
        String contractName) throws VitamClientException {
        return internalFindDocumentById(AdminCollections.ENTRY_CONTRACTS, contractId, tenantId, contractName, IngestContractModel.class);
    }

    @Override
    public RequestResponse<AccessContractModel> findAccessContractById(String contractId, Integer tenantId,
        String contractName) throws VitamClientException {
        return internalFindDocumentById(AdminCollections.ACCESS_CONTRACTS, contractId, tenantId, contractName, AccessContractModel.class);
    }

    @Override
    public RequestResponse<ContextModel> findContextById(String contextId, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocumentById(AdminCollections.CONTEXTS, contextId, tenantId, contractName, ContextModel.class);
    }

    @Override
    public RequestResponse<ProfileModel> findProfileById(String profileId, Integer tenantId, String contractName)
        throws VitamClientException {
        return internalFindDocumentById(AdminCollections.PROFILE, profileId, tenantId, contractName, ProfileModel.class);
    }

    @Override
    public RequestResponse<AccessionRegisterSummaryModel> findAccessionRegisterById(String accessionRegisterId,
        Integer tenantId, String contractName) throws VitamClientException {
        return internalFindDocumentById(AdminCollections.ACCESSION_REGISTERS, accessionRegisterId, tenantId, contractName, AccessionRegisterSummaryModel.class);
    }

}
