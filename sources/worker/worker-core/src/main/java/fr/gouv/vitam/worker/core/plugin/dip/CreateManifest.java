/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 * <p>
 * contact.vitam@culture.gouv.fr
 * <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 * <p>
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 * <p>
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 * <p>
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 * <p>
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.worker.core.plugin.dip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.query.InQuery;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.parser.query.ParserTokens;
import fr.gouv.vitam.common.database.parser.request.multiple.SelectParserMultiple;
import fr.gouv.vitam.common.database.utils.ScrollSpliterator;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.dip.DipExportRequest;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.worker.core.plugin.ScrollSpliteratorHelper;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static com.google.common.collect.Iterables.partition;
import static fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper.id;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTION.FIELDS;
import static fr.gouv.vitam.common.database.builder.request.configuration.GlobalDatas.LIMIT_LOAD;
import static fr.gouv.vitam.common.database.parser.query.ParserTokens.PROJECTIONARGS.ID;
import static fr.gouv.vitam.common.database.parser.query.ParserTokens.PROJECTIONARGS.OBJECT;
import static fr.gouv.vitam.common.database.parser.query.ParserTokens.PROJECTIONARGS.ORIGINATING_AGENCY;
import static fr.gouv.vitam.common.database.parser.query.ParserTokens.PROJECTIONARGS.UNITUPS;
import static fr.gouv.vitam.common.database.parser.request.GlobalDatasParser.DEFAULT_SCROLL_TIMEOUT;
import static fr.gouv.vitam.common.model.dip.DipExportRequest.DIP_REQUEST_FILE_NAME;

/**
 * create manifest and put in on workspace
 */
public class CreateManifest extends ActionHandler {
    static final int MANIFEST_XML_RANK = 0;
    static final int GUID_TO_INFO_RANK = 1;
    static final int BINARIES_RANK = 2;

    private static final String CREATE_MANIFEST = "CREATE_MANIFEST";
    private static final int MAX_ELEMENT_IN_QUERY = 1000;

    private MetaDataClientFactory metaDataClientFactory;
    private ObjectNode projection;

    /**
     * constructor use for plugin instantiation
     */
    public CreateManifest() {
        this(MetaDataClientFactory.getInstance());
    }

    @VisibleForTesting
    CreateManifest(MetaDataClientFactory metaDataClientFactory) {
        this.metaDataClientFactory = metaDataClientFactory;

        ObjectNode fields = JsonHandler.createObjectNode();
        fields.put(UNITUPS.exactToken(), 1);
        fields.put(ID.exactToken(), 1);
        fields.put(ORIGINATING_AGENCY.exactToken(), 1);
        fields.put(OBJECT.exactToken(), 1);

        this.projection = JsonHandler.createObjectNode();
        this.projection.set(FIELDS.exactToken(), fields);
    }

    @Override
    public ItemStatus execute(WorkerParameters param, HandlerIO handlerIO) throws ProcessingException {

        final ItemStatus itemStatus = new ItemStatus(CREATE_MANIFEST);
        File manifestFile = handlerIO.getNewLocalFile(handlerIO.getOutput(MANIFEST_XML_RANK).getPath());

        try (MetaDataClient client = metaDataClientFactory.getClient();
            OutputStream outputStream = new FileOutputStream(manifestFile);
            ManifestBuilder manifestBuilder = new ManifestBuilder(outputStream)) {

            DipExportRequest exportRequest = JsonHandler
                .getFromJsonNode(handlerIO.getJsonFromWorkspace(DIP_REQUEST_FILE_NAME), DipExportRequest.class);

            switch (exportRequest.getExportType()) {
                case ArchiveDeliveryRequestReply:
                case ArchiveTransfer:
                    // Validate request
                    manifestBuilder.validate(exportRequest.getExportType(), exportRequest.getExportRequestParameters());
                    break;
            }


            // Write manifest first line information
            manifestBuilder.startDocument(param.getContainerName(), exportRequest.getExportType(),
                exportRequest.getExportRequestParameters());


            ListMultimap<String, String> multimap = ArrayListMultimap.create();
            Set<String> originatingAgencies = new HashSet<>();
            String originatingAgency =
                VitamConfiguration.getDefaultOriginatingAgencyForExport(
                    ParameterHelper.getTenantParameter());
            Map<String, String> ogs = new HashMap<>();

            SelectParserMultiple parser = new SelectParserMultiple();
            parser.parse(exportRequest.getDslRequest());

            SelectMultiQuery request = parser.getRequest();
            request.setProjection(projection);

            ScrollSpliterator<JsonNode> scrollRequest = ScrollSpliteratorHelper
                .createUnitScrollSplitIterator(client, request);

            StreamSupport.stream(scrollRequest, false)
                .forEach(item -> createGraph(multimap, originatingAgencies, ogs, item));

            if (checkNumberOfUnit(itemStatus, scrollRequest.estimateSize())) {
                return new ItemStatus(CREATE_MANIFEST).setItemsStatus(CREATE_MANIFEST, itemStatus);
            }

            if (originatingAgencies.size() == 1) {
                originatingAgency = Iterables.getOnlyElement(originatingAgencies);
            }

            manifestBuilder.startDataObjectPackage();

            Select select = new Select();

            Map<String, JsonNode> idBinaryWithFileName = new HashMap<>();
            boolean exportWithLogBookLFC = exportRequest.isExportWithLogBookLFC();
            Set<String> dataObjectVersions = Objects.nonNull(exportRequest.getDataObjectVersionToExport())
                ? exportRequest.getDataObjectVersionToExport().getDataObjectVersions()
                : Collections.emptySet();

            Iterable<List<Entry<String, String>>> partitions = partition(ogs.entrySet(), MAX_ELEMENT_IN_QUERY);
            for (List<Entry<String, String>> partition : partitions) {

                ListMultimap<String, String> unitsForObjectGroupId = partition.stream()
                    .collect(
                        ArrayListMultimap::create,
                        (map, entry) -> map.put(entry.getValue(), entry.getKey()),
                        (list1, list2) -> list1.putAll(list2)
                    );

                InQuery in = QueryHelper.in(id(), partition.stream().map(Entry::getValue).toArray(String[]::new));

                select.setQuery(in);
                JsonNode response = client.selectObjectGroups(select.getFinalSelect());
                ArrayNode objects = (ArrayNode) response.get("$results");

                for (JsonNode object : objects) {
                    List<String> linkedUnits = unitsForObjectGroupId.get(
                        object.get(ParserTokens.PROJECTIONARGS.ID.exactToken()).textValue());
                    for (String linkedUnit : linkedUnits) {
                        idBinaryWithFileName.putAll(
                            manifestBuilder.writeGOT(object, linkedUnit, dataObjectVersions, exportWithLogBookLFC));
                    }
                }
            }

            storeBinaryInformationOnWorkspace(handlerIO, idBinaryWithFileName);

            SelectParserMultiple initialQueryParser = new SelectParserMultiple();
            initialQueryParser.parse(exportRequest.getDslRequest());

            scrollRequest = new ScrollSpliterator<>(initialQueryParser.getRequest(),
                query -> {
                    try {
                        JsonNode node = client.selectUnits(query.getFinalSelect());
                        return RequestResponseOK.getFromJsonNode(node);
                    } catch (MetaDataExecutionException | MetaDataDocumentSizeException | MetaDataClientServerException | InvalidParseOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }, DEFAULT_SCROLL_TIMEOUT, LIMIT_LOAD);

            manifestBuilder.startDescriptiveMetadata();
            StreamSupport.stream(scrollRequest, false)
                .forEach(result -> {
                    try {
                        manifestBuilder.writeArchiveUnit(result, multimap, ogs, exportWithLogBookLFC);
                    } catch (JsonProcessingException | JAXBException | DatatypeConfigurationException | ProcessingException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
            manifestBuilder.endDescriptiveMetadata();

            switch (exportRequest.getExportType()) {
                case ArchiveTransfer:
                    originatingAgency = exportRequest.getExportRequestParameters().getOriginatingAgencyIdentifier();
                    break;
                case ArchiveDeliveryRequestReply:
                    if (Strings.isNullOrEmpty(originatingAgency)) {
                        originatingAgency = exportRequest.getExportRequestParameters().getOriginatingAgencyIdentifier();
                    }
                    break;
            }

            manifestBuilder
                .writeManagementMetadata(originatingAgency,
                    exportRequest.getExportRequestParameters().getSubmissionAgencyIdentifier());

            manifestBuilder.endDataObjectPackage();

            switch (exportRequest.getExportType()) {
                case ArchiveDeliveryRequestReply:
                case ArchiveTransfer:
                    manifestBuilder
                        .writeFooter(exportRequest.getExportType(), exportRequest.getExportRequestParameters());
                    break;
            }

            manifestBuilder.closeManifest();

            handlerIO.addOutputResult(MANIFEST_XML_RANK, manifestFile, true, false);

            itemStatus.increment(StatusCode.OK);

        } catch (ExportException e) {
            itemStatus.increment(StatusCode.KO);
            ObjectNode infoNode = JsonHandler.createObjectNode();
            infoNode.put("Reason", e.getMessage());
            String evDetData = JsonHandler.unprettyPrint(infoNode);
            itemStatus.setEvDetailData(evDetData);
        } catch (IOException | MetaDataExecutionException | InvalidCreateOperationException | MetaDataClientServerException | XMLStreamException | JAXBException | MetaDataDocumentSizeException | InvalidParseOperationException e) {
            throw new ProcessingException(e);
        }

        return new ItemStatus(CREATE_MANIFEST).setItemsStatus(CREATE_MANIFEST, itemStatus);
    }

    private boolean checkNumberOfUnit(ItemStatus itemStatus, long total) {
        if (total == 0) {
            itemStatus.increment(StatusCode.KO);
            ObjectNode infoNode = JsonHandler.createObjectNode();
            infoNode.put("Reason", "the DSL query has no result");
            String evdev = JsonHandler.unprettyPrint(infoNode);
            itemStatus.setEvDetailData(evdev);
            return true;
        }
        return false;
    }

    private void createGraph(ListMultimap<String, String> multimap, Set<String> originatingAgencies,
        Map<String, String> ogs, JsonNode result) {
        String archiveUnitId = result.get(id()).asText();
        ArrayNode nodes = (ArrayNode) result.get(VitamFieldsHelper.unitups());
        for (JsonNode node : nodes) {
            multimap.put(node.asText(), archiveUnitId);
        }
        Optional<JsonNode> originatingAgency = Optional.ofNullable(result.get(VitamFieldsHelper.originatingAgency()));
        originatingAgency.ifPresent(jsonNode -> originatingAgencies.add(jsonNode.asText()));
        JsonNode objectIdNode = result.get(VitamFieldsHelper.object());
        if (objectIdNode != null) {
            ogs.put(archiveUnitId, objectIdNode.asText());
        }
    }

    private void storeBinaryInformationOnWorkspace(HandlerIO handlerIO, Map<String, JsonNode> maps)
        throws ProcessingException {
        File guidToInfo = handlerIO.getNewLocalFile(handlerIO.getOutput(GUID_TO_INFO_RANK).getPath());
        File binaryListFile = handlerIO.getNewLocalFile(handlerIO.getOutput(BINARIES_RANK).getPath());

        try {
            JsonHandler.writeAsFile(maps, guidToInfo);
            JsonHandler.writeAsFile(maps.keySet(), binaryListFile);
        } catch (InvalidParseOperationException e) {
            throw new ProcessingException(e);
        }

        // put file in workspace
        handlerIO.addOutputResult(GUID_TO_INFO_RANK, guidToInfo, true, false);
        handlerIO.addOutputResult(BINARIES_RANK, binaryListFile, true, false);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // TODO: add check on file listUnit.json.
    }

}
