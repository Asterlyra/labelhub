package com.labelhub.modules.workflow;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.constant.RoleConstant;
import com.labelhub.common.service.AgentService;
import com.labelhub.common.util.RedisUtil;
import com.labelhub.modules.annotation.mq.AIReviewProducer;
import com.labelhub.modules.dataset.entity.Dataset;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.dataset.mapper.DatasetMapper;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.mapper.TaskDistributionConfigMapper;
import com.labelhub.modules.task.mapper.TaskItemFlowRecordMapper;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.task.mapper.TaskProgressMapper;
import com.labelhub.modules.task.mapper.TaskReviewerMapper;
import com.labelhub.modules.task.mapper.TaskTagMapper;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.entity.Template;
import com.labelhub.modules.template.mapper.MaterialMapper;
import com.labelhub.modules.template.mapper.TemplateMapper;
import com.labelhub.modules.user.entity.Role;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.entity.UserRole;
import com.labelhub.modules.user.mapper.RoleMapper;
import com.labelhub.modules.user.mapper.UserMapper;
import com.labelhub.modules.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Java 后端核心链路集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendWorkflowIntegrationTest {

    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private DatasetItemMapper datasetItemMapper;

    @Autowired
    private TemplateMapper templateMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskItemMapper taskItemMapper;

    @Autowired
    private TaskProgressMapper taskProgressMapper;

    @Autowired
    private TaskReviewerMapper taskReviewerMapper;

    @Autowired
    private TaskDistributionConfigMapper taskDistributionConfigMapper;

    @Autowired
    private TaskItemFlowRecordMapper taskItemFlowRecordMapper;

    @Autowired
    private TaskTagMapper taskTagMapper;

    @MockBean
    private RedisUtil redisUtil;

    @MockBean
    private AIReviewProducer aiReviewProducer;

    @MockBean
    private AgentService agentService;

    private final Map<String, String> redisStringCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> redisListCache = new ConcurrentHashMap<>();

    private Long adminUserId;
    private Long labelerUserId;
    private Long reviewerUserId;
    private Long datasetId;
    private Long templateId;

    /**
     * 每个用例前重建测试数据。
     */
    @BeforeEach
    void setUp() {
        reset(redisUtil, aiReviewProducer, agentService);
        redisStringCache.clear();
        redisListCache.clear();
        mockRedisBehavior();
        clearTables();
        seedBaseData();
    }

    /**
     * 覆盖任务创建、发布、领取和进度变化。
     */
    @Test
    void shouldPublishTaskAndUpdateProgressAcrossLifecycle() throws Exception {
        String adminToken = login("owner", DEFAULT_PASSWORD);
        Long taskId = createTask(adminToken, "生命周期任务");

        mockMvc.perform(post("/api/tasks/{id}/publish", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/tasks/{id}/progress", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.unassignedCount").value(1))
                .andExpect(jsonPath("$.data.claimedCount").value(0));

        String labelerToken = login("labeler", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/tasks/{id}/claim", taskId)
                        .header("satoken", authHeader(labelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(get("/api/tasks/{id}/progress", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unassignedCount").value(0))
                .andExpect(jsonPath("$.data.claimedCount").value(1))
                .andExpect(jsonPath("$.data.submittedCount").value(0));

        mockMvc.perform(get("/api/tasks/{id}/items", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("CLAIMED"))
                .andExpect(jsonPath("$.data.records[0].labelerName").value("标注员"));
    }

    /**
     * 覆盖标注工作台到审核通过的主流程。
     */
    @Test
    void shouldCompleteAnnotationAndReviewWorkflowWithoutTouchingProductionLogic() throws Exception {
        String adminToken = login("owner", DEFAULT_PASSWORD);
        Long taskId = createTask(adminToken, "全流程任务");

        mockMvc.perform(post("/api/tasks/{id}/publish", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String labelerToken = login("labeler", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/tasks/{id}/claim", taskId)
                        .header("satoken", authHeader(labelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        JsonNode workspace = readDataNode(mockMvc.perform(get("/api/annotation/workspace/{taskId}", taskId)
                        .header("satoken", authHeader(labelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.navigation[0].status").value("CLAIMED"))
                .andReturn());

        Long taskItemId = workspace.path("navigation").get(0).path("taskItemId").asLong();
        assertTrue(taskItemId > 0);

        mockMvc.perform(get("/api/annotation/item/{taskItemId}", taskItemId)
                        .header("satoken", authHeader(labelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLAIMED"))
                .andExpect(jsonPath("$.data.rawData.text").value("样本一"));

        mockMvc.perform(post("/api/annotation/item/{taskItemId}/draft", taskItemId)
                        .header("satoken", authHeader(labelerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(annotationBody("初稿标签"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/annotation/item/{taskItemId}/submit", taskItemId)
                        .header("satoken", authHeader(labelerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(annotationBody("最终标签"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/tasks/{id}/progress", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath("$.data.claimedCount").value(0));

        String reviewerToken = login("reviewer", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/reviews/tasks/{taskId}/workspace", taskId)
                        .header("satoken", authHeader(reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.navigation[0].taskItemId").value(taskItemId))
                .andExpect(jsonPath("$.data.progress.pendingCount").value(1));

        mockMvc.perform(get("/api/reviews/items/{taskItemId}", taskItemId)
                        .header("satoken", authHeader(reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.annotationData.label").value("最终标签"));

        mockMvc.perform(post("/api/reviews/items/{taskItemId}/approve", taskItemId)
                        .header("satoken", authHeader(reviewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/tasks/{id}", taskId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.progress.reviewedCount").value(1));

        mockMvc.perform(get("/api/tasks/items/{taskItemId}/timeline", taskItemId)
                        .header("satoken", authHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].operatorAction").value("SUBMIT"))
                .andExpect(jsonPath("$.data[1].operatorAction").value("APPROVE"));

        TaskItem taskItem = taskItemMapper.selectById(taskItemId);
        assertNotNull(taskItem);
        assertEquals("REVIEWED", taskItem.getStatus());
        assertEquals("最终标签", parseAnnotationData(taskItem).get("label"));

        Task task = taskMapper.selectById(taskId);
        assertNotNull(task);
        assertEquals("ENDED", task.getStatus());
    }

    /**
     * 配置测试态内存 Redis 行为。
     */
    private void mockRedisBehavior() {
        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            String value = invocation.getArgument(1, String.class);
            redisStringCache.put(key, value);
            return null;
        }).when(redisUtil).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        doAnswer(invocation -> redisStringCache.get(invocation.getArgument(0, String.class)))
                .when(redisUtil).get(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            boolean removed = redisStringCache.remove(key) != null;
            removed = redisListCache.remove(key) != null || removed;
            return removed;
        }).when(redisUtil).delete(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            List<String> values = new ArrayList<>(invocation.getArgument(1, List.class));
            redisListCache.put(key, values);
            return null;
        }).when(redisUtil).setList(anyString(), any(), anyLong(), any(TimeUnit.class));

        doAnswer(invocation -> {
            List<String> values = redisListCache.get(invocation.getArgument(0, String.class));
            return values == null ? null : new ArrayList<>(values);
        }).when(redisUtil).getList(anyString());
    }

    /**
     * 清空测试表，避免用例互相污染。
     */
    private void clearTables() {
        List<String> tables = List.of(
                "task_item_flow_record",
                "task_progress",
                "task_item",
                "task_AIreview_config",
                "task_distribution_config",
                "task_reviewer",
                "task_tag",
                "task",
                "material",
                "template",
                "dataset_item",
                "dataset",
                "user_role",
                "role_permission",
                "permission",
                "role",
                "user"
        );
        for (String table : tables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    /**
     * 初始化用户、角色、数据集和模板。
     */
    private void seedBaseData() {
        Long adminRoleId = insertRole("项目管理员", RoleConstant.PROJECT_ADMIN);
        Long labelerRoleId = insertRole("标注员", RoleConstant.LABELER);
        Long reviewerRoleId = insertRole("审核员", RoleConstant.REVIEWER);

        adminUserId = insertUser("owner", "项目管理员");
        labelerUserId = insertUser("labeler", "标注员");
        reviewerUserId = insertUser("reviewer", "审核员");

        bindRole(adminUserId, adminRoleId);
        bindRole(labelerUserId, labelerRoleId);
        bindRole(reviewerUserId, reviewerRoleId);

        datasetId = insertDataset();
        templateId = insertTemplate();
        insertMaterial();
        insertDatasetItems();
    }

    /**
     * 创建角色。
     */
    private Long insertRole(String roleName, String roleKey) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setRoleKey(roleKey);
        role.setDescription(roleName + "测试角色");
        roleMapper.insert(role);
        return role.getId();
    }

    /**
     * 创建测试用户。
     */
    private Long insertUser(String username, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setNickname(nickname);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 绑定用户角色。
     */
    private void bindRole(Long userId, Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);
    }

    /**
     * 创建已解析的数据集。
     */
    private Long insertDataset() {
        Dataset dataset = new Dataset();
        dataset.setName("测试数据集");
        dataset.setDescription("用于集成测试的数据集");
        dataset.setSourceFormat("JSON");
        dataset.setStatus("PARSED");
        dataset.setTotalCount(2);
        dataset.setSuccessCount(2);
        dataset.setFailedCount(0);
        dataset.setSchemaJson("{\"text\":\"string\"}");
        dataset.setCreatedBy(adminUserId);
        dataset.setDeleted(0);
        datasetMapper.insert(dataset);
        return dataset.getId();
    }

    /**
     * 创建启用中的模板。
     */
    private Long insertTemplate() {
        Template template = new Template();
        template.setName("测试模板");
        template.setDescription("用于集成测试的模板");
        template.setStatus(1);
        template.setCreatorId(adminUserId);
        template.setDeleted(0);
        templateMapper.insert(template);
        return template.getId();
    }

    /**
     * 创建最小可提交流程物料。
     */
    private void insertMaterial() {
        Material material = new Material();
        material.setTemplateId(templateId);
        material.setType("input");
        material.setField("label");
        material.setLabel("标签");
        material.setRequired(1);
        material.setSortOrder(1);
        material.setConfig("{}");
        materialMapper.insert(material);
    }

    /**
     * 创建可分发的数据项。
     */
    private void insertDatasetItems() {
        datasetItemMapper.insert(createDatasetItem("item-1", 1, "样本一"));
        datasetItemMapper.insert(createDatasetItem("item-2", 2, "样本二"));
    }

    /**
     * 构建单条数据项。
     */
    private DatasetItem createDatasetItem(String itemKey, int rowIndex, String text) {
        DatasetItem item = new DatasetItem();
        item.setDatasetId(datasetId);
        item.setItemKey(itemKey);
        item.setRowIndex(rowIndex);
        item.setDataType("TEXT");
        item.setContentJson("{\"text\":\"" + text + "\"}");
        item.setParseStatus("SUCCESS");
        item.setDeleted(0);
        return item;
    }

    /**
     * 执行登录并返回 token。
     */
    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readDataNode(result).path("satoken").asText();
    }

    /**
     * 创建任务草稿。
     */
    private Long createTask(String adminToken, String title) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("title", title);
        request.put("description", "测试任务描述");
        request.put("datasetId", datasetId);
        request.put("templateId", templateId);
        request.put("quota", 1);
        request.put("rewardRule", "完成即得分");
        request.put("deadline", LocalDateTime.of(2030, 1, 1, 0, 0));
        request.put("tags", List.of("测试", "流程"));
        request.put("reviewerIds", List.of(reviewerUserId));
        request.put("aiReviewEnabled", false);
        request.put("distributionConfig", Map.of(
                "strategyType", "FIRST_COME_FIRST_SERVED",
                "maxClaimPerUser", 1,
                "claimCount", 1
        ));

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("satoken", authHeader(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return readDataNode(result).path("id").asLong();
    }

    /**
     * 构建统一认证头。
     */
    private String authHeader(String token) {
        return "LabelHub " + token;
    }

    /**
     * 构建标注请求体。
     */
    private Map<String, Object> annotationBody(String labelValue) {
        return Map.of("annotationData", Map.of("label", labelValue));
    }

    /**
     * 解析统一返回体中的 data 节点。
     */
    private JsonNode readDataNode(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(200, root.path("code").asInt());
        return root.path("data");
    }

    /**
     * 解析任务项中的标注 JSON。
     */
    private Map<String, Object> parseAnnotationData(TaskItem taskItem) throws Exception {
        return objectMapper.readValue(taskItem.getAnnotationData(), Map.class);
    }

    /**
     * 为测试显式切回内存 Token 存储，避免依赖 Redis。
     */
    @TestConfiguration
    static class TestSaTokenConfig {

        /**
         * 使用内存 Token Dao 支撑测试登录链路。
         */
        @Bean
        @Primary
        public SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }
}
