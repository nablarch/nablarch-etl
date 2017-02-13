package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.statement.exception.SqlStatementException;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.config.DbInputStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link nablarch.etl.DatabaseItemReader}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class DatabaseItemReaderTest {

    /** テスト対象 */
    DatabaseItemReader sut;

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("db-default.xml");

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Mocked
    private DbInputStepConfig mockDbInputStepConfig;

    @BeforeClass
    public static void setUpClass() {
        VariousDbTestHelper.createTable(TestEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        sut = new DatabaseItemReader();

        final ConnectionFactory connectionFactory = repositoryResource.getComponentByType(ConnectionFactory.class);
        final TransactionManagerConnection connection = connectionFactory.getConnection(
                TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);

        VariousDbTestHelper.delete(TestEntity.class);

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            mockJobContext.getJobName();
            result = "test-job";
        }};
        Deencapsulation.setField(sut, "jobContext", mockJobContext);
        Deencapsulation.setField(sut, "stepContext", mockStepContext);
        Deencapsulation.setField(sut, "stepConfig", mockDbInputStepConfig);
    }

    @After
    public void tearDown() throws Exception {
        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();
        connection.terminate();
        DbConnectionContext.removeConnection();
    }

    /**
     * 必須項目が指定されなかった場合、例外が送出されること。
     */
    @Test
    public void testRequired() throws Exception {

        // bean

        new Expectations() {{
            mockDbInputStepConfig.getBean();
            result = null;
        }};

        try {
            sut.open(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("bean is required. jobId = [test-job], stepId = [test-step]"));
        }

        // sqlId

        new Expectations() {{
            mockDbInputStepConfig.getBean();
            result = TestEntity.class;
            mockDbInputStepConfig.getSqlId();
            result = null;
        }};

        try {
            sut.open(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("sqlId is required. jobId = [test-job], stepId = [test-step]"));
        }
    }

    /**
     * 対象レコードが1件の場合に読み込めること
     */
    @Test
    public void testReadSingleRecord() throws Exception {

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbInputStepConfig.getBean();
            result = TestEntity.class;
            mockDbInputStepConfig.getSqlId();
            result = "SELECT_SINGLE_RECORD";
        }};

        VariousDbTestHelper.setUpTable(
                  TestEntity.create("10001", "abcdefghij", 10000)
                , TestEntity.create("10002", "cdefghijkl", 20000)
                , TestEntity.create("10003", "efghijklmn", 30000)
        );

        sut.open(null);

        TestEntity resultEntity = (TestEntity) sut.readItem();
        assertThat(resultEntity.getCol1(), is("10001"));
        assertThat(resultEntity.getCol3(), is(10000));

        assertThat(sut.readItem(), is(nullValue()));
    }

    /**
     * 対象レコードが複数件の場合に読み込めること
     */
    @Test
    public void testReadMultiRecords() throws Exception {

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbInputStepConfig.getBean();
            result = TestEntity.class;
            mockDbInputStepConfig.getSqlId();
            result = "SELECT_MULTI_RECORDS";
        }};

        VariousDbTestHelper.setUpTable(
                  TestEntity.create("10001", "abcdefghij", 10000)
                , TestEntity.create("10002", "cdefghijkl", 20000)
                , TestEntity.create("10003", "efghijklmn", 30000)
                , TestEntity.create("10004", "ghijklmnop", 40000)
                , TestEntity.create("10005", "ijklmnopqr", 50000)
        );

        sut.open(null);

        TestEntity resultEntity3 = (TestEntity) sut.readItem();
        assertThat(resultEntity3.getCol1(), is("10005"));
        assertThat(resultEntity3.getCol3(), is(50000));

        TestEntity resultEntity2 = (TestEntity) sut.readItem();
        assertThat(resultEntity2.getCol1(), is("10004"));
        assertThat(resultEntity2.getCol3(), is(40000));

        TestEntity resultEntity1 = (TestEntity) sut.readItem();
        assertThat(resultEntity1.getCol1(), is("10003"));
        assertThat(resultEntity1.getCol3(), is(30000));

        assertThat(sut.readItem(), is(nullValue()));
    }

    /**
     * 対象レコードが0件の場合に読み込み結果がnullになること
     */
    @Test
    public void testReadNothing() throws Exception {

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbInputStepConfig.getBean();
            result = TestEntity.class;
            mockDbInputStepConfig.getSqlId();
            result = "SELECT_NOTHING";
        }};

        VariousDbTestHelper.setUpTable(
                  TestEntity.create("10001", "abcdefghij", 10000)
                , TestEntity.create("10002", "cdefghijkl", 20000)
                , TestEntity.create("10003", "efghijklmn", 30000)
                , TestEntity.create("10004", "ghijklmnop", 40000)
                , TestEntity.create("10005", "ijklmnopqr", 50000)
        );

        sut.open(null);

        assertThat(sut.readItem(), is(nullValue()));
    }

    /**
     * 指定されたSELECT文に誤りがある場合に例外が発生すること
     */
    @Test
    public void testSelectFailed() throws Exception {

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbInputStepConfig.getBean();
            result = TestEntity.class;
            mockDbInputStepConfig.getSqlId();
            result = "SELECT_FAILED";
        }};

        try {
            sut.open(null);
            fail("存在しないカラムを使ってSELECTしようとしているのでここにはこない");
        } catch (Exception e) {
            assertThat(e, instanceOf(SqlStatementException.class));
        }
    }

    /** テスト用のエンティティ */
    @Entity
    @Table(name="TEST_TABLE")
    public static class TestEntity {

        @Id
        @Column(name="col1", length=5, nullable=false)
        public String col1;

        @Column(name="col2", length=10)
        public String col2;

        @Column(name="col3", length=5)
        public Integer col3;

        public String getCol1() {
            return col1;
        }

        public void setCol1(String col1) {
            this.col1 = col1;
        }

        public String getCol2() {
            return col2;
        }

        public void setCol2(String col2) {
            this.col2 = col2;
        }

        public Integer getCol3() {
            return col3;
        }

        public void setCol3(Integer col3) {
            this.col3 = col3;
        }

        private static TestEntity create(String col1, String col2, Integer col3) {
            TestEntity entity = new TestEntity();
            entity.col1 = col1;
            entity.col2 = col2;
            entity.col3 = col3;
            return entity;
        }
    }
}