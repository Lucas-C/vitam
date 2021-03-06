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
package fr.gouv.vitam.security.internal.filter;

import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.BasicAuthModel;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.functional.administration.common.server.AdminManagementConfiguration;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enpoint authentication filter tests.
 */
@RunWithCustomExecutor
public class EndpointAuthenticationFilterTest {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(EndpointAuthenticationFilterTest.class);

    @ClassRule
    public static RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AdminManagementConfiguration configuration;

    @Mock
    private ContainerRequestContext containerRequestContext;

    @Captor
    private ArgumentCaptor<ContainerRequestContext> contextArgumentCaptor;

    @InjectMocks
    @Spy
    private EndpointAuthenticationFilter instance;


    public void setup(String user, String password) {

        // Instanciate Vitam configuration credentials.
        List<BasicAuthModel> basicAuthConfig = Arrays.asList(new BasicAuthModel(user, password));

        // mock admin basic authentication informations.
        when(configuration.getAdminBasicAuth())
            .thenReturn(basicAuthConfig);
    }

    @Test
    @RunWithCustomExecutor
    public void testBasicAuthentication_Success() throws Exception {
        setup("adminUserName", "adminPassword");

        // Encode to Base64 format of (adminUserName:adminPassword) -> Basic YWRtaW5Vc2VyTmFtZTphZG1pblBhc3N3b3Jk
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic YWRtaW5Vc2VyTmFtZTphZG1pblBhc3N3b3Jk");

        // mock context headers.
        when(containerRequestContext.getHeaders())
            .thenReturn(headers);

        LOGGER.debug(String.format("headers informations : %s", headers));
        instance.filter(containerRequestContext);

        // verify number of the filter method call and the value of the parameter.
        verify(instance, times(1))
            .filter(contextArgumentCaptor.capture());
        Assert.assertEquals(headers, contextArgumentCaptor.getValue().getHeaders());
    }

    @Test
    @RunWithCustomExecutor
    public void testBasicAuthentication_case_sensitive() throws Exception {
        setup("adminuserName", "adminpassword");

        // Encode to Base64 format of (adminUserName:adminPassword) -> Basic YWRtaW5Vc2VyTmFtZTphZG1pblBhc3N3b3Jk
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic YWRtaW5Vc2VyTmFtZTphZG1pblBhc3N3b3Jk");

        // mock context headers.
        when(containerRequestContext.getHeaders())
            .thenReturn(headers);

        // verify type and message of the thrown Exception.
        assertThatThrownBy(() -> instance.filter(containerRequestContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VitamAuthentication failed: Wrong credentials");
    }

    @Test
    @RunWithCustomExecutor
    public void testBasicAuthenticationFailed_WrongCredentials() throws Exception {

        setup("adminUserName", "adminPassword");

        // Wrong Encode to Base64 format of (adminUserName:adminPassword)
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.AUTHORIZATION.toString(), "Basic YWRtaW5Vc2VyTmFtZTphZG1pblBhc3N3bXXX");
        LOGGER.debug(String.format("headers informations : %s", headers));

        // mock context headers.
        when(containerRequestContext.getHeaders())
            .thenReturn(headers);

        // verify type and message of the thrown Exception.
        assertThatThrownBy(() -> instance.filter(containerRequestContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VitamAuthentication failed: Wrong credentials");
    }

    @Test
    @RunWithCustomExecutor
    public void testBasicAuthenticationFailed_MissingInfos() throws Exception {

        setup("adminUserName", "adminPassword");
        // Wrong Encode to Base64 format of (adminUserName:adminPassword)
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.AUTHORIZATION.toString(), "XXX YWRtaW5Vc2VyTmFtZTphZG1pblBhc3N3bXXX");
        LOGGER.debug(String.format("headers informations : %s", headers));

        // mock context headers.
        when(containerRequestContext.getHeaders())
            .thenReturn(headers);

        // verify type and message of the thrown Exception.
        assertThatThrownBy(() -> instance.filter(containerRequestContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VitamAuthentication failed: VitamAuthentication informations are missing.");
    }

}
