package nablarch.etl;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hamcrest.Matchers;

import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.statement.exception.SqlStatementException;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.etl.config.StepConfig;
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
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import mockit.Mocked;
import mockit.Expectations;

/**
 * {@link DatabaseItemWriter}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class DatabaseItemWriterTest {
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @ClassRule
    public static SystemRepositoryResource resource = new SystemRepositoryResource("db-default.xml");

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(EtlDatabaseItemWriterEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        final ConnectionFactory connectionFactory = resource.getComponentByType(ConnectionFactory.class);
        final TransactionManagerConnection connection = connectionFactory.getConnection(
                TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);

        VariousDbTestHelper.delete(EtlDatabaseItemWriterEntity.class);
        OnMemoryLogWriter.clear();
        
        new Expectations() {{
            mockJobContext.getJobName();
            result = "test-job";
            minTimes = 0;
            mockStepContext.getStepName();
            result = "test-step";
            minTimes = 0;
        }};
        
    }

    @After
    public void tearDown() throws Exception {
        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();
        connection.terminate();
        DbConnectionContext.removeConnection();
    }

    /**
     * 引数で指定されたEntityの内容がデータベースに登録されること。
     */
    @Test
    public void insertSuccess() throws Exception {

        final DbToDbStepConfig stepConfig = new DbToDbStepConfig();
        stepConfig.setBean(EtlDatabaseItemWriterEntity.class);
        final DatabaseItemWriter sut = new DatabaseItemWriter(
                mockJobContext, mockStepContext, stepConfig);

        // -------------------------------------------------- execute
        sut.writeItems(Arrays.<Object>asList(
                        new EtlDatabaseItemWriterEntity("001", "name_1"),
                        new EtlDatabaseItemWriterEntity("002", "name_2"),
                        new EtlDatabaseItemWriterEntity("999", "name_999"),
                        new EtlDatabaseItemWriterEntity("003", "name_3")
                )
        );

        // -------------------------------------------------- commit transaction
        DbConnectionContext.getTransactionManagerConnection().commit();

        // -------------------------------------------------- assert table
        final List<EtlDatabaseItemWriterEntity> result =
                VariousDbTestHelper.findAll(EtlDatabaseItemWriterEntity.class, "userId");
        assertThat("4レコード登録されていること", result.size(), is(4));

        String[][] expected = {{"001", "name_1"}, {"002", "name_2"}, {"003", "name_3"}, {"999", "name_999"}};
        for (int i = 0; i < result.size(); i++) {
            EtlDatabaseItemWriterEntity entity = result.get(i);
            assertThat("id", entity.getUserId(), is(expected[i][0]));
            assertThat("name", entity.getName(), is(expected[i][1]));
        }
    }
    
    /**
     * INSERTに失敗した場合、例外が送出されること。
     */
    @Test
    public void insertFailed() throws Exception {

        final DbToDbStepConfig stepConfig = new DbToDbStepConfig();
        final DatabaseItemWriter sut = new DatabaseItemWriter(mockJobContext, mockStepContext, stepConfig);

        // -------------------------------------------------- setup database
        VariousDbTestHelper.setUpTable(new EtlDatabaseItemWriterEntity("004", "name_4"));

        // -------------------------------------------------- execute(004のレコードで一意制約違反が発生する)
        try {
            sut.writeItems(Arrays.<Object>asList(
                            new EtlDatabaseItemWriterEntity("001", "name_1"),
                            new EtlDatabaseItemWriterEntity("002", "name_2"),
                            new EtlDatabaseItemWriterEntity("004", "name_4"),
                            new EtlDatabaseItemWriterEntity("003", "name_3")
                    )
            );
            fail("一意制約違反が発生するのでここは通過しない");
        } catch (Exception e) {
            assertThat("一意制約違反の例外が発生する", e, instanceOf(SqlStatementException.class));
        }

        // -------------------------------------------------- rollback transaction
        DbConnectionContext.getTransactionManagerConnection().rollback();

        // -------------------------------------------------- assert table
        final List<EtlDatabaseItemWriterEntity> result =
                VariousDbTestHelper.findAll(EtlDatabaseItemWriterEntity.class, "userId");

        assertThat("元々の1レコードだけ存在していること", result, Matchers.contains(allOf(
                hasProperty("userId", is("004")),
                hasProperty("name", is("name_4"))
        )));
    }

    /**
     * openメソッドで正しくログが出力されること。
     */
    @Test
    public void testOpenLogDb() throws Exception {
        
        // -------------------------------------------------- setup objects that is injected
        final DbToDbStepConfig stepConfig = new DbToDbStepConfig();
        stepConfig.setBean(EtlDatabaseItemWriterEntity.class);
        final DatabaseItemWriter sut = new DatabaseItemWriter(
                mockJobContext, mockStepContext, stepConfig
        );

        sut.open(null);
        OnMemoryLogWriter.assertLogContains("writer.progress",
                "-INFO- job name: [test-job] step name: [test-step] write table name: [etl_database_item_writer]");
    }

    /**
     * openメソッドで正しくログが出力されること。
     */
    @Test
    public void testOpenLogFile() throws Exception {
        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(EtlDatabaseItemWriterEntity.class);
        final DatabaseItemWriter sut = new DatabaseItemWriter(
                mockJobContext, mockStepContext, stepConfig
        );

        sut.open(null);
        OnMemoryLogWriter.assertLogContains("writer.progress",
                "-INFO- job name: [test-job] step name: [test-step] write table name: [etl_database_item_writer]");
    }

    @Test
    public void unsupportedStepConfigType_shouldThrowException() throws Exception {
        final DatabaseItemWriter sut = new DatabaseItemWriter(
                mockJobContext, mockStepContext, new MyStepConfig());

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("unsupported config type. supported class is DbToDbStepConfig or FileToDbStepConfig."
                + " step config class: " + MyStepConfig.class.getName());
        sut.open(null);
    }

    @Test
    public void stepConfigIsNull_shouldThrowException() throws Exception {
        final DatabaseItemWriter sut = new DatabaseItemWriter(
                mockJobContext, mockStepContext, null);

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("unsupported config type. supported class is DbToDbStepConfig or FileToDbStepConfig."
                + " step config class: null");
        sut.open(null);
    }

    // テスト用のEntityクラス
    @Entity
    @Table(name = "etl_database_item_writer")
    public static class EtlDatabaseItemWriterEntity {

        @Id
        @Column(name = "user_id", length = 3)
        public String userId;

        @Column(name = "name")
        public String name;

        public EtlDatabaseItemWriterEntity() {
        }

        public EtlDatabaseItemWriterEntity(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        @Id
        @Column(name = "user_id")
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        @Column(name = "name")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class MyStepConfig extends StepConfig {

        @Override
        protected void onInitialize() {

        }
    }
}