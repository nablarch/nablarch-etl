package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Mocked;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.config.TruncateStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * {@link TableCleaningBatchlet}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class TableCleaningBatchletTest {

    @ClassRule
    public static SystemRepositoryResource resource = new SystemRepositoryResource("db-default.xml");

    @Rule
    public TestName testName = new TestName();

    /** テスト対象 */
    TableCleaningBatchlet sut = new TableCleaningBatchlet();

    @Mocked
    StepContext mockStepContext;

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(TableCleaningBatchletEntity.class);
        VariousDbTestHelper.createTable(TableCleaningBatchletEntity2.class);
    }

    @Before
    public void setUp() throws Exception {
        final ConnectionFactory connectionFactory = resource.getComponentByType(ConnectionFactory.class);
        final TransactionManagerConnection connection = connectionFactory.getConnection(
                TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);

        Deencapsulation.setField(sut, mockStepContext);

        OnMemoryLogWriter.clear();
    }

    @After
    public void tearDown() throws Exception {
        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();
        DbConnectionContext.removeConnection();
        connection.terminate();
    }

    /**
     * 1テーブルのクリーニングが正常に終わること。
     *
     * ※指定されていないテーブルのデータはクリーニングされないこと
     */
    @Test
    public void singleTable() throws Exception {
        // -------------------------------------------------- setup data
        VariousDbTestHelper.setUpTable(
                new TableCleaningBatchletEntity(1L, "name1"),
                new TableCleaningBatchletEntity(999L, "name999")
        );

        VariousDbTestHelper.setUpTable(
                new TableCleaningBatchletEntity2(1L, "name2")
        );

        // -------------------------------------------------- setup root config
        final TruncateStepConfig truncateStepConfig = new TruncateStepConfig();
        truncateStepConfig.setEntities(Collections.<Class<?>>singletonList(TableCleaningBatchletEntity.class));
        Deencapsulation.setField(sut, "stepConfig", truncateStepConfig);

        // -------------------------------------------------- execute
        sut.process();

        // -------------------------------------------------- commit
        DbConnectionContext.getTransactionManagerConnection()
                .commit();

        // -------------------------------------------------- assert
        final List<TableCleaningBatchletEntity> result1 = VariousDbTestHelper.findAll(TableCleaningBatchletEntity.class);
        assertThat("テーブルが空になっていること", result1.size(), is(0));

        final List<TableCleaningBatchletEntity2> result2 = VariousDbTestHelper.findAll(TableCleaningBatchletEntity2.class);
        assertThat("削除対象ではないのでデータは消されない", result2.size(), is(1));
    }

    /**
     * 複数テーブルのクリーニングが正常に終わること
     */
    @Test
    public void multipleTable() throws Exception {
        // -------------------------------------------------- setup data
        VariousDbTestHelper.setUpTable(
                new TableCleaningBatchletEntity(1L, "name1"),
                new TableCleaningBatchletEntity(999L, "name999")
        );
        VariousDbTestHelper.setUpTable(
                new TableCleaningBatchletEntity2(1L, "name1"),
                new TableCleaningBatchletEntity2(10L, "name10"),
                new TableCleaningBatchletEntity2(50L, "name50")
        );

        // -------------------------------------------------- setup root config
        final TruncateStepConfig truncateStepConfig = new TruncateStepConfig();
        truncateStepConfig.setEntities(Arrays.asList(TableCleaningBatchletEntity.class, TableCleaningBatchletEntity2.class));
        Deencapsulation.setField(sut, "stepConfig", truncateStepConfig);

        // -------------------------------------------------- execute
        sut.process();

        // -------------------------------------------------- commit
        DbConnectionContext.getTransactionManagerConnection()
                .commit();

        // -------------------------------------------------- assert
        final List<TableCleaningBatchletEntity> result1 = VariousDbTestHelper.findAll(TableCleaningBatchletEntity.class);
        assertThat("テーブルが空になっていること", result1.size(), is(0));

        final List<TableCleaningBatchletEntity2> result2 = VariousDbTestHelper.findAll(TableCleaningBatchletEntity2.class);
        assertThat("テーブルが空になっていること", result2.size(), is(0));

        // -------------------------------------------------- assert sql log
        final List<String> sqlLogs = OnMemoryLogWriter.getMessages("writers.sql");
        for (String sqlLog : sqlLogs) {
            if (sqlLog.matches("truncate.+table_cleaning_batchlet")) {
                assertThat("スキーマ指定なし", sqlLog, containsString(" table_cleaning_batchlet"));
            }
            if (sqlLog.matches("truncate.+table_cleaning_batchlet2")) {
                assertThat("スキーマ指定有り", sqlLog, containsString("ssd.table_cleaning_batchlet2"));
            }
        }
    }

    @Table(name = "table_cleaning_batchlet")
    @Entity
    public static class TableCleaningBatchletEntity {

        @Id
        @Column(name = "batchlet_id", length = 18)
        public Long id;

        @Column(name = "name", length = 100)
        public String name;

        public TableCleaningBatchletEntity() {
        }

        public TableCleaningBatchletEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Table(name = "table_cleaning_batchlet2", schema = "ssd")
    @Entity
    public static class TableCleaningBatchletEntity2 {

        @Id
        @Column(name = "batchlet_id", length = 18)
        public Long id;

        @Column(name = "name", length = 100)
        public String name;

        public TableCleaningBatchletEntity2() {
        }

        public TableCleaningBatchletEntity2(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
