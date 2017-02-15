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
package fr.gouv.vitam.storage.offers.common.rest;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.jhades.JHades;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.restassured.RestAssured;

import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.storage.engine.common.StorageConstants;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.ObjectInit;

/**
 * DefaultOfferResource Test
 */
public class DefaultOfferResourceTest {

    private static final String WORKSPACE_OFFER_CONF = "storage-default-offer.conf";
    private static File newWorkspaceOfferConf;

    private static final String REST_URI = "/offer/v1";
    private static int serverPort = 8784;
    private static JunitHelper junitHelper;
    private static final String OBJECTS_URI = "/objects";
    private static final String OBJECT_TYPE_URI = "/{type}";
    private static final String CHECK_URI = "/check";
    private static final String OBJECT_ID_URI = "/{id}";
    private static final String STATUS_URI = "/status";
    private static final String UNIT_CODE = "UNIT";
    private static final String OBJECT_CODE = "OBJECT";
    private static final String METADATA = "/metadatas";

    private static final String DEFAULT_STORAGE_CONF = "default-storage.conf";
    private static final String ARCHIVE_FILE_TXT = "archivefile.txt";

    private static final ObjectMapper OBJECT_MAPPER;
    private static DefaultOfferApplication application;


    static {

        OBJECT_MAPPER = new ObjectMapper(new JsonFactory());
        OBJECT_MAPPER.disable(SerializationFeature.INDENT_OUTPUT);
    }

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(DefaultOfferResourceTest.class);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Identify overlapping in particular jsr311
        new JHades().overlappingJarsReport();

        junitHelper = JunitHelper.getInstance();
        serverPort = junitHelper.findAvailablePort();

        RestAssured.port = serverPort;
        RestAssured.basePath = REST_URI;

        final File workspaceOffer = PropertiesUtils.findFile(WORKSPACE_OFFER_CONF);
        final StorageConfiguration realWorkspaceOffer =
            PropertiesUtils.readYaml(workspaceOffer, StorageConfiguration.class);
        newWorkspaceOfferConf = File.createTempFile("test", WORKSPACE_OFFER_CONF, workspaceOffer.getParentFile());
        PropertiesUtils.writeYaml(newWorkspaceOfferConf, realWorkspaceOffer);

        try {
            application = new DefaultOfferApplication(WORKSPACE_OFFER_CONF);
            application.start();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
            throw new IllegalStateException(
                "Cannot start the Wokspace Offer Application Server", e);
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        LOGGER.debug("Ending tests");
        try {
            application.stop();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
        }
        // junitHelper.releasePort(serverPort);
    }

    @After
    public void deleteExistingFiles() throws Exception {
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        // delete directories recursively
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/unit_1")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/unit_2")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/object_0")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/object_1")));
        // for skipped test (putObjectChunkTest)
        // FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/1")));
    }

    @Test
    public void getCapacityTestBadRequest() {
        given().get(OBJECTS_URI + "/" + UNIT_CODE).then().statusCode(400);
    }

    @Test
    public void getCapacityTestOk() {
        // create tenant
        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        given().header(GlobalDataRest.X_TENANT_ID, 1)
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1").then().statusCode(201);
        // test
        given().header(GlobalDataRest.X_TENANT_ID, 1)
            .when().get(OBJECTS_URI + "/" + UNIT_CODE).then().statusCode(200);
    }

    @Test
    public void getObjectTestPreconditionFailed() {
        // no tenant id
        given().get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(412);

    }

    @Test
    public void getObjectTestNotFound() {
        // not found
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
    }


    @Test
    public void getObjectTestOK() throws Exception {

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        // found
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
    }

    @Test
    public void getObjectChunkTestOK() throws Exception {

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        with().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            final FileChannel fc = in.getChannel();
            final ByteBuffer bb = ByteBuffer.allocate(1024);

            byte[] bytes;
            int read = fc.read(bb);
            while (read >= 0) {
                bb.flip();
                if (fc.position() == fc.size()) {
                    bytes = new byte[read];
                    bb.get(bytes, 0, read);
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        assertNotNull(inChunk);
                        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND,
                            StorageConstants.COMMAND_END)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1");
                    }
                } else {
                    bytes = bb.array();
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        // assertNotNull(inChunk);
                        with().header(GlobalDataRest.X_TENANT_ID, "1")
                            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1");
                    }
                }
                bb.clear();
                read = fc.read(bb);
            }
        }

        // found
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1");
    }

    @Test
    public void postObjectsTest() throws Exception {
        final String guid = GUIDFactory.newGUID().toString();
        // no tenant id
        given().contentType(MediaType.APPLICATION_JSON).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then()
            .statusCode(400);

        // no command
        given().header(GlobalDataRest.X_TENANT_ID, "2").contentType(MediaType.APPLICATION_JSON).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" +
                guid)
            .then().statusCode(400);

        // no ObjectInit, command != INIT
        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
            .contentType(MediaType.APPLICATION_JSON).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then().statusCode(400);

        // no ObjectInit
        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then()
            .statusCode(400);

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        assertNotNull(objectInit);

        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then().statusCode(201);

        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/unit_2");
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
    }


    @Test
    public void putObjectTest() throws Exception {
        // no tenant id
        given().contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then()
            .statusCode(400);

        // No command
        given().header(GlobalDataRest.X_TENANT_ID, "2").contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1")
            .then().statusCode(400);

        // Bad command
        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then()
            .statusCode(400);

        // No INIT
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            // try only with one chunk
            final byte[] bytes = new byte[1024];
            in.read(bytes);
            try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                assertNotNull(inChunk);
                given().header(GlobalDataRest.X_TENANT_ID, "1")
                    .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                    .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then()
                    .statusCode(500);
            }
        }

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1").then().statusCode(201);
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            given().header(GlobalDataRest.X_TENANT_ID, "2")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then()
                .statusCode(201);
        }
        // check
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/unit_2");
        assertNotNull(container);
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
        final File object = new File(container.getAbsolutePath(), "/id1");
        assertNotNull(object);
        assertTrue(object.exists());
        assertFalse(object.isDirectory());

        assertTrue(com.google.common.io.Files.equal(PropertiesUtils.findFile(ARCHIVE_FILE_TXT), object));
    }

    // TODO activate when chunk mode is done in {@see DefaultOfferService} method createObject
    @Test
    @Ignore
    public void putObjectChunkTest() throws Exception {
        // no tenant id
        given().contentType(MediaType.APPLICATION_OCTET_STREAM).when().put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
            .statusCode(400);

        // No command
        given().header(GlobalDataRest.X_TENANT_ID, "2").contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI +
                OBJECT_ID_URI, "id1")
            .then().statusCode(400);

        // Bad command
        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).when().put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
            .statusCode(400);

        // No INIT
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            // try only with one chunk
            final byte[] bytes = new byte[1024];
            in.read(bytes);
            try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                assertNotNull(inChunk);
                given().header(GlobalDataRest.X_TENANT_ID, "2")
                    .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                    .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
                    .statusCode(500);
            }
        }

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        given().header(GlobalDataRest.X_TENANT_ID, "2")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1").then().statusCode(201);
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            final FileChannel fc = in.getChannel();
            final ByteBuffer bb = ByteBuffer.allocate(1024);

            byte[] bytes;
            int read = fc.read(bb);
            while (read >= 0) {
                bb.flip();
                if (fc.position() == fc.size()) {
                    bytes = new byte[read];
                    bb.get(bytes, 0, read);
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        assertNotNull(inChunk);
                        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND,
                            StorageConstants.COMMAND_END)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
                            .statusCode(201);
                    }
                } else {
                    bytes = bb.array();
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        assertNotNull(inChunk);
                        given().header(GlobalDataRest.X_TENANT_ID, "2")
                            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
                            .statusCode(201);
                    }
                }
                bb.clear();
                read = fc.read(bb);
            }
        }
        // check
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/1" + this);
        assertNotNull(container);
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
        final File folder = new File(container.getAbsolutePath(), "/" + DataCategory.OBJECT.getFolder());
        assertNotNull(folder);
        assertTrue(folder.exists());
        assertTrue(folder.isDirectory());
        final File object = new File(folder.getAbsolutePath(), "id1");
        assertNotNull(object);
        assertTrue(object.exists());
        assertFalse(object.isDirectory());

        assertTrue(com.google.common.io.Files.equal(PropertiesUtils.findFile(ARCHIVE_FILE_TXT), object));
    }

    @Test
    public void headObjectTest() throws Exception {
        // no tenant id
        given().head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // no object
        given().header(GlobalDataRest.X_TENANT_ID, 2).and()
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then()
            .statusCode(404);

        // object
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/unit_1");

        container.mkdir();
        final File object = new File(container.getAbsolutePath(), "/id1");
        object.createNewFile();
        given().header(GlobalDataRest.X_TENANT_ID, 1).and()
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then()
            .statusCode(204);
    }


    @Test
    public void deleteObjectTestNotExisting() {
        // no object found -> 404
        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);
    }

    @Test
    public void deleteObjectTestBadRequests() {
        // bad requests (missing headers) -> 400
        given().header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST, "digest")
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

        // unknwonw digest algorithm -> 500
        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, "wrongTypeShouldTriggerAnInternalServerError")
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(500);
    }


    @Test
    public void deleteObjectTest() throws Exception {
        // init object
        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        final File testFile = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        Digest digest = Digest.digest(testFile, VitamConfiguration.getDefaultDigestType());

        // object is found, creation worked
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        // wrong digest -> no object found -> 404
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_DIGEST, "fakeDigest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);

        // object is found, delete has failed, for sure
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        // wrong digest algorithm -> no object found -> 404
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_DIGEST, digest.toString())
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getSecurityDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);

        // object is found, delete has failed, for sure
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        String responseAsJson =
            "{\"id\":\"" + "id1" + "\",\"status\":\"" + Response.Status.OK.toString() + "\"}";
        // good combo digest algorithm + digest -> object found and deleted
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_DIGEST, digest.toString())
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(200)
            .body(Matchers.equalTo(responseAsJson));

        // lets check that we cant find the object again, meaning we re sure that the object has been deleted
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

    }


    @Test
    public void statusTest() {
        given().get(STATUS_URI).then().statusCode(Status.NO_CONTENT.getStatusCode());
    }


    @Test
    public void checkObjectTestNotExisting() {
        // no object -> 500
        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST, "digest").header(GlobalDataRest.X_DIGEST_ALGORITHM, "digestType")
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI + CHECK_URI, OBJECT_CODE, "id1").then().statusCode(500);
    }

    @Test
    public void checkObjectTestBadRequests() {
        given().header(GlobalDataRest.X_DIGEST, "digest").header(GlobalDataRest.X_DIGEST_ALGORITHM, "digestType")
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI + CHECK_URI, OBJECT_CODE, "id1").then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST, "digest")
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI + CHECK_URI, OBJECT_CODE, "id1").then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, "digestType")
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI + CHECK_URI, OBJECT_CODE, "id1").then().statusCode(400);
    }

    @Test
    public void checkObjectTest() throws Exception {

        // init object
        final ObjectInit objectInit = new ObjectInit();
        Digest digest = null;
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        final File testFile = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        digest = Digest.digest(testFile, VitamConfiguration.getDefaultDigestType());

        String responseFalseAsString = "{\"objectVerification\":false}";

        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_DIGEST, "fakeDigest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI + CHECK_URI, OBJECT_CODE, "id1").then().statusCode(200)
            .body(Matchers.equalTo(responseFalseAsString));

        String responsetrueAsString = "{\"objectVerification\":true}";

        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_DIGEST, digest.toString())
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI + CHECK_URI, OBJECT_CODE, "id1").then().statusCode(200)
            .body(Matchers.equalTo(responsetrueAsString));

    }



    @Test
    public void countObjectsTestKOBadRequest() throws FileNotFoundException, IOException {
        // test
        given()
            .contentType(MediaType.APPLICATION_JSON)
            .when().get(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/count").then()
            .statusCode(400);
    }

    @Test
    public void countObjectsTestKONotFound() throws FileNotFoundException, IOException {
        // test
        given().header(GlobalDataRest.X_TENANT_ID, "0")
            .contentType(MediaType.APPLICATION_JSON)
            .when().get(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/count").then()
            .statusCode(404);
    }

    @Test
    public void countObjectsTestOK() throws FileNotFoundException, IOException {

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        with().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + "/" + DataCategory.UNIT.name() + OBJECT_ID_URI, "id1");
        }

        // test
        given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .when().get(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/count").then()
            .statusCode(200);
    }
    
    @Test
    public void getObjectMetadataOK() throws FileNotFoundException, IOException{
        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        with().header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectInit).when().post(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + "/" + DataCategory.UNIT.name() + OBJECT_ID_URI, "id1");
        }
        // test
        given().header(GlobalDataRest.X_TENANT_ID, 1)
            .when().get(OBJECTS_URI + "/" + UNIT_CODE  + "/" + "id1" + METADATA).then().statusCode(200);
    }

}
