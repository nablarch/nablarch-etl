package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.statement.exception.DuplicateStatementException;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link DatabaseItemWriter}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class DatabaseItemWriterTest {

    /** テスト対象クラス */
    private final DatabaseItemWriter sut = new DatabaseItemWriter();

    @Mocked
    private StepContext mockStepContext;

    @Mocked(cascading = false)
    private DbToDbStepConfig mockDbToDbStepConfig;

    @Mocked
    private FileToDbStepConfig mockFileToDbStepConfig;

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
     *
     * ※例外発生後にコミットを行うと、例外が発生したレコードまでは登録されていることを確認する。
     */
    @Test
    public void insertFailed() throws Exception {

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
            assertThat("一意制約違反の例外が発生する", e, instanceOf(DuplicateStatementException.class));
        }

        // -------------------------------------------------- commit transaction
        DbConnectionContext.getTransactionManagerConnection().commit();

        // -------------------------------------------------- assert table
        final List<EtlDatabaseItemWriterEntity> result =
                VariousDbTestHelper.findAll(EtlDatabaseItemWriterEntity.class, "userId");
        assertThat("元々存在していた1レコードとエラー発生前に挿入した2レコードで3レコードになる",
                result.size(), is(3));

        String[][] expected = {{"001", "name_1"}, {"002", "name_2"}, {"004", "name_4"}};
        for (int i = 0; i < result.size(); i++) {
            EtlDatabaseItemWriterEntity entity = result.get(i);
            assertThat("id", entity.getUserId(), is(expected[i][0]));
            assertThat("name", entity.getName(), is(expected[i][1]));
        }
    }

    /**
     * openメソッドで正しくログが出力されること。
     */
    @Test
    public void testOpenLogDb() throws Exception {
        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlDatabaseItemWriterEntity.class;
        }};
        Deencapsulation.setField(sut, "stepContext", mockStepContext);
        Deencapsulation.setField(sut, "stepConfig", mockDbToDbStepConfig);

        sut.open(null);
        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- chunk start. table name=[etl_database_item_writer]");
    }

    /**
     * openメソッドで正しくログが出力されること。
     */
    @Test
    public void testOpenLogFile() throws Exception {
        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = EtlDatabaseItemWriterEntity.class;
        }};
        Deencapsulation.setField(sut, "stepContext", mockStepContext);
        Deencapsulation.setField(sut, "stepConfig", mockFileToDbStepConfig);

        sut.open(null);
        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- chunk start. table name=[etl_database_item_writer]");
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
}