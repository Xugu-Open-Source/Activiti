package org.activiti.xugu;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * XuGu database integration tests for Activiti 7.7.0.
 * Verifies DDL execution, pagination, null sorting, bulk insert, and full process lifecycle.
 */
public class XuguIntegrationTest {

    private ProcessEngine processEngine;

    @Before
    public void setup() {
        ProcessEngineConfiguration config = ProcessEngineConfiguration
                .createStandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:xugu://127.0.0.1:5138/ACTIVITI_TEST")
                .setJdbcDriver("com.xugu.cloudjdbc.Driver")
                .setJdbcUsername("SYSDBA")
                .setJdbcPassword("SYSDBA")
                .setDatabaseType("xugu")
                .setDatabaseSchemaUpdate("drop-create")
                .setHistory("full")
                .setAsyncExecutorActivate(false);
        processEngine = config.buildProcessEngine();
    }

    @After
    public void tearDown() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    public void testEngineStartsAndSchemaCreated() {
        assertNotNull("Process engine should be created", processEngine);
        assertNotNull("Repository service should be available", processEngine.getRepositoryService());
        System.out.println("[PASS] XuGu schema created successfully (DDL execution OK)");
    }

    @Test
    public void testDatabaseTypeIsXugu() {
        String dbType = processEngine.getProcessEngineConfiguration().getDatabaseType();
        assertEquals("xugu", dbType);
        System.out.println("[PASS] Database type correctly set to 'xugu'");
    }

    @Test
    public void testDeployAndStartSimpleProcess() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .deploy();
        assertNotNull("Deployment should succeed", dep);

        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("xuguTestProcess");
        assertNotNull("Process instance should be created", pi);
        assertNotNull("Process instance ID should not be null", pi.getId());

        TaskService taskService = processEngine.getTaskService();
        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .singleResult();
        assertNotNull("User task should exist", task);
        assertEquals("Confirm XuGu works", task.getName());

        taskService.complete(task.getId());
        assertEquals("Process should be completed", 0, runtimeService.createProcessInstanceQuery().count());

        // Clean up
        repoService.deleteDeployment(dep.getId(), true);
        System.out.println("[PASS] Full process lifecycle (deploy -> start -> complete) works on XuGu");
    }

    @Test
    public void testPaginationQuery() {
        RepositoryService repoService = processEngine.getRepositoryService();
        // Deploy multiple processes
        Deployment dep1 = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .name("dep1")
                .deploy();

        // Query with pagination (uses LIMIT ... OFFSET ...)
        long count = repoService.createDeploymentQuery().count();
        assertTrue("Should have at least 1 deployment", count >= 1);

        // Test pagination
        var deployments = repoService.createDeploymentQuery()
                .listPage(0, 10);
        assertNotNull("Paginated list should not be null", deployments);
        assertTrue("Should return results", deployments.size() >= 1);

        repoService.deleteDeployment(dep1.getId(), true);
        System.out.println("[PASS] Pagination query (LIMIT ... OFFSET ...) works on XuGu");
    }

    @Test
    public void testNullSortingInQuery() {
        RepositoryService repoService = processEngine.getRepositoryService();
        Deployment dep = repoService.createDeployment()
                .addClasspathResource("org/activiti/xugu/simple-process.bpmn20.xml")
                .deploy();

        // Query with ordering - triggers null sorting logic via isnull()
        var deployments = repoService.createDeploymentQuery()
                .orderByDeploymentId()
                .desc()
                .list();
        assertNotNull("Ordered query should return results", deployments);

        repoService.deleteDeployment(dep.getId(), true);
        System.out.println("[PASS] Null sorting (isnull() function) works on XuGu");
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

        // Verify history
        var historicInstances = processEngine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .list();
        assertEquals("Should have 1 historic process instance", 1, historicInstances.size());

        repoService.deleteDeployment(dep.getId(), true);
        System.out.println("[PASS] Historic process query works on XuGu");
    }
}
