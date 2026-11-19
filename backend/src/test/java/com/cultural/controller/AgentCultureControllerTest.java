package com.cultural.controller;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class AgentCultureControllerTest {
    AgentCultureController controller;
    JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        for (String table : new String[]{"heritage", "traditional_art"}) {
            jdbc.execute("CREATE TABLE " + table + " (id BIGINT, name VARCHAR(100), description VARCHAR(500), content VARCHAR(20000), city VARCHAR(50), category VARCHAR(50), status INT, deleted INT, view_count INT)");
            jdbc.update("INSERT INTO " + table + " VALUES(1,'南京云锦','传统工艺','<p>文化详情</p>','南京','工艺',1,0,5)");
            jdbc.update("INSERT INTO " + table + " VALUES(2,'云锦草稿','','','南京','工艺',0,0,6)");
            jdbc.update("INSERT INTO " + table + " VALUES(3,'云锦删除','','','南京','工艺',1,1,7)");
            jdbc.execute("ALTER TABLE " + table + " ADD alias VARCHAR(200)");
        }
        controller = new AgentCultureController(jdbc);
    }
    @Test void searchesPublishedRecordsWithoutNonexistentTagsColumn() {
        assertEquals(2, controller.search("云锦", "all", "南京").getData().size());
        assertEquals(1, controller.search("云锦", "heritage", "南京").getData().size());
        assertTrue(controller.search("云锦", "all", "苏州").getData().isEmpty());
        assertTrue(controller.search("%", "all", "").getData().isEmpty());
        assertTrue(controller.search("' OR 1=1 --", "all", "").getData().isEmpty());
    }
    @Test void detailsNeverExposeDraftOrDeletedRecords() {
        assertEquals(404, controller.detail("heritage", 2L).getCode());
        assertEquals(404, controller.detail("art", 3L).getCode());
        assertEquals(404, controller.detail("heritage", 99L).getCode());
        assertFalse(controller.detail("heritage", 1L).getData().get("CONTENT").toString().contains("<p>"));
    }
    @Test void searchesAliasesAndFullTextAndKeepsParagraphs() {
        jdbc.update("UPDATE heritage SET alias=?, content=? WHERE id=1", "妆花织造", "<h3>工艺</h3><p>通经断纬 &amp; 配色</p><p>第二段<br>换行</p>");
        assertEquals(1, controller.search("妆花", "heritage", "").getData().size());
        assertEquals(1, controller.search("通经断纬", "heritage", "").getData().size());
        assertEquals("工艺\n通经断纬 & 配色\n第二段\n换行", controller.detail("heritage", 1L).getData().get("content"));
    }
    @Test void duplicateSeedCopiesDoNotConsumeResultLimit() {
        for (int i=4; i<12; i++) {
            jdbc.update("INSERT INTO heritage(id,name,description,content,city,category,status,deleted,view_count) VALUES(?, '南京云锦','重复样例','','南京','工艺',1,0,100)", i);
        }
        jdbc.update("INSERT INTO heritage(id,name,description,content,city,category,status,deleted,view_count) VALUES(12,'宋锦','另一种丝织锦缎','','苏州','工艺',1,0,0)");
        var results = controller.search("锦", "heritage", "").getData();
        assertEquals(2, results.size());
        assertEquals(1L, ((Number) results.get(0).get("id")).longValue());
    }
}
