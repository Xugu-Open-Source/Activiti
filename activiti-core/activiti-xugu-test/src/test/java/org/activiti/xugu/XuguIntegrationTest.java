/*
 * Copyright 2010-2020 Alfresco Software, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.xugu;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Live XuGu integration tests for Activiti 7.7.0.
 *
 * <p>Opt-in only: run with {@code -Pxugu-test}. Connection settings come from
 * system properties / environment variables — never from committed lab hosts.
 *
 * <ul>
 *   <li>{@code xugu.jdbc.url} / {@code XUGU_JDBC_URL}
 *   <li>{@code xugu.jdbc.user} / {@code XUGU_JDBC_USER}
 *   <li>{@code xugu.jdbc.password} / {@code XUGU_JDBC_PASSWORD}
 *   <li>{@code xugu.jdbc.driver} / {@code XUGU_JDBC_DRIVER} (default {@code com.xugu.cloudjdbc.Driver})
 * </ul>
 */
public class XuguIntegrationTest {

    private static final String DEFAULT_DRIVER = "com.xugu.cloudjdbc.Driver";
    private static final String DEFAULT_URL = "jdbc:xugu://127.0.0.1:5138/ACTIVITI_TEST";

    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;
    private static String jdbcDriver;

    private ProcessEngine processEngine;

    @BeforeClass
    public static void resolveConnectionSettings() {
        jdbcUrl = firstNonBlank(
                System.getenv("XUGU_JDBC_URL"),
                System.getProperty("xugu.jdbc.url"),
                DEFAULT_URL);
        jdbcUser = firstNonBlank(
                System.getenv("XUGU_JDBC_USER"),
                System.getProperty("xugu.jdbc.user"),
                "SYSDBA");
        jdbcPassword = firstNonBlank(
                System.getenv("XUGU_JDBC_PASSWORD"),
                System.getProperty("xugu.jdbc.password"),
                "SYSDBA");
        jdbcDriver = firstNonBlank(
                System.getenv("XUGU_JDBC_DRIVER"),
                System.getProperty("xugu.jdbc.driver"),
                DEFAULT_DRIVER);
    }

    @Before
    public void setup() {
        ensureTestDatabase();
        try {
            ProcessEngineConfiguration config = ProcessEngineConfiguration
                    .createStandaloneProcessEngineConfiguration()
                    .setJdbcUrl(jdbcUrl)
                    .setJdbcDriver(jdbcDriver)
                    .setJdbcUsername(jdbcUser)
                    .setJdbcPassword(jdbcPassword)
                    .setDatabaseType("xugu")
                    .setDatabaseSchemaUpdate("drop-create")
                    .setHistory("full")
                    .setAsyncExecutorActivate(false);
            processEngine = config.buildProcessEngine();
            System.err.println("XUGU_ENGINE_CONNECTED");
        } catch (RuntimeException ex) {
            // Missing driver or unreachable DB must not be reported as a green suite.
            Assume.assumeNoException(
                    "XuGu live DB unavailable (url=" + sanitizeUrl(jdbcUrl) + "): " + ex.getMessage(),
                    ex);
        }
    }

    @After
    public void tearDown() {
        if (processEngine != null) {
            processEngine.close();
            processEngine = null;
        }
    }

    @Test
    public void testEngineStartsAndSchemaCreated() {
        assertNotNull("Process engine should be created", processEngine);
        assertNotNull("Repository service should be available", processEngine.getRepositoryService());
        assertEquals("7.1.0-M6",
                processEngine.getManagementService().getProperties().get("schema.version"));
    }

    @Test
    public void testDatabaseTypeIsXugu() {
        String dbType = processEngine.getProcessEngineConfiguration().getDatabaseType();
        assertEquals("xugu", dbType);
    }

    @Test
    public void testDeployAndStartSimpleProcess() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .deploy();
        assertNotNull(dep);

        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("xuguTestProcess");
        assertNotNull(pi.getId());

        TaskService taskService = processEngine.getTaskService();
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
        assertNotNull(task);
        assertEquals("Confirm XuGu works", task.getName());

        taskService.complete(task.getId());
        assertEquals(0, runtimeService.createProcessInstanceQuery().count());

        repoService.deleteDeployment(dep.getId(), true);
    }

    @Test
    public void testProcessVariablesRoundTrip() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .deploy();

        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("stringVar", "hello-xugu");
        vars.put("longVar", 42L);
        vars.put("doubleVar", 3.14d);
        vars.put("boolVar", Boolean.TRUE);

        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("xuguTestProcess", vars);

        assertEquals("hello-xugu", runtimeService.getVariable(pi.getId(), "stringVar"));
        assertEquals(42L, runtimeService.getVariable(pi.getId(), "longVar"));
        assertEquals(3.14d, runtimeService.getVariable(pi.getId(), "doubleVar"));
        assertEquals(Boolean.TRUE, runtimeService.getVariable(pi.getId(), "boolVar"));

        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pi.getId())
                .singleResult();
        processEngine.getTaskService().complete(task.getId());
        repoService.deleteDeployment(dep.getId(), true);
    }

    @Test
    public void testPaginationQuery() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep1 = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .name("dep-page-1")
                .deploy();
        Deployment dep2 = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .name("dep-page-2")
                .deploy();

        long count = repoService.createDeploymentQuery().count();
        assertTrue(count >= 2);

        List<Deployment> page = repoService.createDeploymentQuery()
                .orderByDeploymentName()
                .asc()
                .listPage(0, 1);
        assertNotNull(page);
        assertEquals(1, page.size());

        repoService.deleteDeployment(dep1.getId(), true);
        repoService.deleteDeployment(dep2.getId(), true);
    }

    @Test
    public void testNullSortingInQuery() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .deploy();

        List<Deployment> deployments = repoService.createDeploymentQuery()
                .orderByDeploymentId()
                .desc()
                .list();
        assertNotNull(deployments);
        assertFalse(deployments.isEmpty());

        repoService.deleteDeployment(dep.getId(), true);
    }

    @Test
    public void testHistoricProcessQuery() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .deploy();

        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("xuguTestProcess");

        TaskService taskService = processEngine.getTaskService();
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
        taskService.complete(task.getId());

        HistoryService historyService = processEngine.getHistoryService();
        assertEquals(1, historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .list()
                .size());
        assertEquals(1, historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(pi.getId())
                .finished()
                .count());

        repoService.deleteDeployment(dep.getId(), true);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static void ensureTestDatabase() {
        if (jdbcUrl == null) return;
        String adminUrl = jdbcUrl.replaceAll("/[^/]+$", "/SYSTEM");
        String dbName = jdbcUrl.replaceAll(".*/", "");
        try (Connection conn = DriverManager.getConnection(adminUrl, jdbcUser, jdbcPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        } catch (SQLException ignored) {
            // database already exists or admin connection unavailable
        }
    }

    private static String sanitizeUrl(String url) {
        if (url == null) {
            return "<null>";
        }
        // Avoid leaking credentials if someone embeds userinfo in the URL.
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int at = url.indexOf('@', scheme + 3);
        if (at > 0) {
            return url.substring(0, scheme + 3) + "***@" + url.substring(at + 1);
        }
        return url;
    }
}
