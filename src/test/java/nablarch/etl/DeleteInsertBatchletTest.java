package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.statement.exception.SqlStatementException;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionFactory;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.DbToDbStepConfig.InsertMode;
import nablarch.etl.config.DbToDbStepConfig.UpdateSize;
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

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link DeleteInsertBatchlet}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class DeleteInsertBatchletTest {

    @ClassRule
    public static SystemRepositoryResource repositoryResource = new SystemRepositoryResource("db-default.xml");

    TransactionManagerConnection connection;

    /** テスト対象 */
    private DeleteInsertBatchlet sut;

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Mocked(cascading = false)
    private DbToDbStepConfig mockDbToDbStepConfig;

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(WorkTableEntity.class);
        VariousDbTestHelper.createTable(BulkInsertEntity.class);
    }

    @Before
    public void setUp() throws Exception {

        VariousDbTestHelper.delete(WorkTableEntity.class);
        VariousDbTestHelper.delete(BulkInsertEntity.class);
        sut = new DeleteInsertBatchlet();
        ConnectionFactory connectionFactory = repositoryResource.getComponent("connectionFactory");
        connection = connectionFactory.getConnection(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);

        TransactionFactory transactionFactory = repositoryResource.getComponent("jdbcTransactionFactory");
        TransactionContext.setTransaction(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY,
                transactionFactory.getTransaction(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY));

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            mockJobContext.getJobName();
            result = "test-job";
        }};
        Deencapsulation.setField(sut, "jobContext", mockJobContext);
        Deencapsulation.setField(sut, "stepContext", mockStepContext);
        Deencapsulation.setField(sut, "stepConfig", mockDbToDbStepConfig);

        final RangeUpdateHelper rangeUpdateHelper = new RangeUpdateHelper();
        Deencapsulation.setField(rangeUpdateHelper, mockJobContext);
        Deencapsulation.setField(rangeUpdateHelper, mockStepContext);
        Deencapsulation.setField(sut, rangeUpdateHelper);

        OnMemoryLogWriter.clear();
    }

    @After
    public void tearDown() throws Exception {
        TransactionContext.removeTransaction();
        connection.rollback();
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
            mockDbToDbStepConfig.getBean();
            result = null;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("bean is required. jobId = [test-job], stepId = [test-step]"));
        }

        // bean

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = null;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("sqlId is required. jobId = [test-job], stepId = [test-step]"));
        }
    }

    /**
     * {@link InsertMode#ORACLE_DIRECT_PATH}でかつ更新サイズが指定されている場合はエラー
     */
    @Test
    public void testSpecifyOracleDirectPathModeAndUpdateSize() throws Exception {

        new Expectations() {{
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;

            mockDbToDbStepConfig.getInsertMode();
            result = InsertMode.ORACLE_DIRECT_PATH;

            final UpdateSize updateSize = new UpdateSize();
            updateSize.setBean(WorkTableEntity.class);
            updateSize.setSize(10);
            mockDbToDbStepConfig.getUpdateSize();
            result = updateSize;

        }};

        try {
            sut.process();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Oracle Direct Path mode does not support UpdateSize."));
        }
    }

    /**
     * 不正な更新サイズを指定した場合、エラーとなること。
     */
    @Test
    public void testInvalidUpdateSize() throws Exception {

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getUpdateSize();
            UpdateSize updateSize = new UpdateSize();
            updateSize.setBean(WorkTableEntity.class);
            updateSize.setSize(0);
            result = updateSize;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("updateSize > size must be greater than 0. "
                    + "jobId = [test-job], stepId = [test-step], size = [0]"));
        }

    }

    /**
     * インサートが正常にできること。
     */
    @Test
    public void insertSuccess() throws Exception {

        // -------------------------------------------------- setup work table data
        VariousDbTestHelper.insert(
                new WorkTableEntity(1L, 1L, "last1", "first1", "北海道"),
                new WorkTableEntity(2L, 2L, "last2", "first2", "東京"),
                new WorkTableEntity(3L, 3L, "last3", "first3", "長野"),
                new WorkTableEntity(4L, 4L, "last4", "first4", "大阪"),
                new WorkTableEntity(5L, 5L, "last5", "first5", "福岡"),
                new WorkTableEntity(6L, 6L, "last6", "first6", "沖縄")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;
            mockDbToDbStepConfig.getSql();
            result = createTransferQuery();

            mockDbToDbStepConfig.getInsertMode();
            result = InsertMode.NORMAL;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<BulkInsertEntity> result = VariousDbTestHelper.findAll(BulkInsertEntity.class, "userId");
        assertThat("6レコード移送されていること", result.size(), is(6));

        int index = 0;
        String[] address = {"北海道", "東京", "長野", "大阪", "福岡", "沖縄"};
        for (BulkInsertEntity entity : result) {
            index++;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("last" + index + " first" + index));
            assertThat(entity.address, is(address[index - 1]));
        }

        // -------------------------------------------------- assert log
        OnMemoryLogWriter.assertLogContains("writer.sql", "insert into");
        String[] expected = { "-INFO- clean table. table name=[bulk_insert_entity], delete count=[0]",
                "-INFO- load progress. table name=[bulk_insert_entity], write count=[6]"};
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 既存のデータが存在している場合でもクリーニング後に登録処理が行われること
     */
    @Test
    public void cleaningAndInsert() throws Exception {

        // -------------------------------------------------- setup work table data
        VariousDbTestHelper.insert(
                new WorkTableEntity(1L, 1L, "last1", "first1", "北海道"),
                new WorkTableEntity(2L, 2L, "last2", "first2", "東京"),
                new WorkTableEntity(3L, 3L, "last3", "first3", "長野"),
                new WorkTableEntity(4L, 4L, "last4", "first4", "大阪"),
                new WorkTableEntity(5L, 5L, "last5", "first5", "福岡"),
                new WorkTableEntity(6L, 6L, "last6", "first6", "沖縄")
        );

        // 既存のデータ
        VariousDbTestHelper.insert(
                new BulkInsertEntity(3L, "hoge", "fuga")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;
            mockDbToDbStepConfig.getSql();
            result = createTransferQuery();
            mockDbToDbStepConfig.getInsertMode();
            result = InsertMode.ORACLE_DIRECT_PATH;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        final List<BulkInsertEntity> result = VariousDbTestHelper.findAll(BulkInsertEntity.class, "userId");
        assertThat("delete -> insertで6レコード登録されていること", result.size(), is(6));

        int index = 0;
        String[] address = {"北海道", "東京", "長野", "大阪", "福岡", "沖縄"};
        for (BulkInsertEntity entity : result) {
            index++;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("last" + index + " first" + index));
            assertThat(entity.address, is(address[index - 1]));
        }

        // -------------------------------------------------- assert log
        OnMemoryLogWriter.assertLogContains("writer.sql", "insert /*+ APPEND */ into");
        String[] expected = { "-INFO- clean table. table name=[bulk_insert_entity], delete count=[1]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[6]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * Rangeで分割してInsert処理が実行できること
     */
    @Test
    public void rangeSplitInsert() throws Exception {
        // -------------------------------------------------- setup work table data
        VariousDbTestHelper.insert(
                new WorkTableEntity(1L, 1L, "last1", "first1", "北海道"),
                new WorkTableEntity(2L, 2L, "last2", "first2", "東京"),
                new WorkTableEntity(3L, 3L, "last3", "first3", "長野"),
                new WorkTableEntity(4L, 4L, "last4", "first4", "大阪"),
                new WorkTableEntity(5L, 5L, "last5", "first5", "福岡"),
                new WorkTableEntity(6L, 6L, "last6", "first6", "沖縄")
        );

        // 既存のデータ
        VariousDbTestHelper.insert(
                new BulkInsertEntity(4L, "hoge", "fuga")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;
            mockDbToDbStepConfig.getSql();
            result = createTransferQuery() + " where line_number between ? and ?";
            mockDbToDbStepConfig.getInsertMode();
            result = InsertMode.NORMAL;

            final UpdateSize updateSize = new UpdateSize();
            updateSize.setSize(3);
            updateSize.setBean(WorkTableEntity.class);
            mockDbToDbStepConfig.getUpdateSize();
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.rollback();

        // -------------------------------------------------- assert database
        final List<BulkInsertEntity> result = VariousDbTestHelper.findAll(BulkInsertEntity.class, "userId");
        assertThat("delete -> insertで6レコード登録されていること", result.size(), is(6));

        int index = 0;
        String[] address = {"北海道", "東京", "長野", "大阪", "福岡", "沖縄"};
        for (BulkInsertEntity entity : result) {
            index++;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("last" + index + " first" + index));
            assertThat(entity.address, is(address[index - 1]));
        }

        // -------------------------------------------------- assert log
        final List<String> messages = OnMemoryLogWriter.getMessages("writer.sql");

        int commitLogCount = 0;
        for (String message : messages) {
            if (message.contains("transaction commit.")) {
                commitLogCount++;
            }
        }
        assertThat("コミットが2回行われること", commitLogCount, is(2));

        String[] expected = { "-INFO- clean table. table name=[bulk_insert_entity], delete count=[1]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[3]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[6]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * Rangeで分割してInsert処理を行う場合でも、既存データのクリーニング処理が行われること
     */
    @Test
    public void cleaningAndRangeSplitInsert() throws Exception {

        // -------------------------------------------------- setup work table data
        VariousDbTestHelper.insert(
                new WorkTableEntity(1L, 1L, "last1", "first1", "北海道"),
                new WorkTableEntity(2L, 2L, "last2", "first2", "東京"),
                new WorkTableEntity(3L, 3L, "last3", "first3", "長野"),
                new WorkTableEntity(4L, 4L, "last4", "first4", "大阪"),
                new WorkTableEntity(5L, 5L, "last5", "first5", "福岡")
        );

        // 既存のデータ
        VariousDbTestHelper.insert(
                new BulkInsertEntity(3L, "hoge", "fuga"),
                new BulkInsertEntity(99999L, "hoge-hoge", "fuga-fuga")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getBean();
            result = BulkInsertEntity.class;
            mockDbToDbStepConfig.getSql();
            result = createTransferQuery() + " where line_number between ? and ?";
            mockDbToDbStepConfig.getInsertMode();
            result = InsertMode.NORMAL;

            final UpdateSize updateSize = new UpdateSize();
            updateSize.setSize(1);
            updateSize.setBean(WorkTableEntity.class);
            mockDbToDbStepConfig.getUpdateSize();
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        final List<BulkInsertEntity> result = VariousDbTestHelper.findAll(BulkInsertEntity.class, "userId");
        assertThat("delete -> insertで5レコード登録されていること", result.size(), is(5));

        int index = 0;
        String[] address = {"北海道", "東京", "長野", "大阪", "福岡"};
        for (BulkInsertEntity entity : result) {
            index++;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("last" + index + " first" + index));
            assertThat(entity.address, is(address[index - 1]));
        }

        // -------------------------------------------------- assert log
        final List<String> messages = OnMemoryLogWriter.getMessages("writer.sql");

        int commitLogCount = 0;
        for (String message : messages) {
            if (message.contains("transaction commit.")) {
                commitLogCount++;
            }
        }
        assertThat("コミットが5回行われること", commitLogCount, is(5));

        String[] expected = { "-INFO- clean table. table name=[bulk_insert_entity], delete count=[2]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[1]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[2]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[3]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[4]",
            "-INFO- load progress. table name=[bulk_insert_entity], write count=[5]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 登録処理に失敗する場合
     */
    @Test
    public void processFailed() throws Exception {
        // -------------------------------------------------- setup work table data
        VariousDbTestHelper.insert(
                new WorkTableEntity(1L, 1L, "last1", "first1", "北海道"),
                new WorkTableEntity(2L, 2L, "last2", "first2", "東京"),
                new WorkTableEntity(3L, 3L, "last3", "first3", "長野"),
                new WorkTableEntity(4L, 4L, "last4", "first4", "大阪"),
                new WorkTableEntity(5L, 5L, "last5", "first5", "福岡"),
                new WorkTableEntity(6L, 6L, "last6", "first6", "沖縄")
        );

        // 既存のデータ
        VariousDbTestHelper.insert(
                new BulkInsertEntity(3L, "hoge", "fuga")
        );

        // -------------------------------------------------- setup objects that is injected
        new NonStrictExpectations() {{
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getBean();
            result = ArrayList.class;                            // テーブルが存在しないクラス
            mockDbToDbStepConfig.getSql();
            result = createTransferQuery();
        }};

        try {
            sut.process();
            fail("ここは通過しない");
        } catch (SqlStatementException ignored) {
        }
        connection.commit();

        final List<BulkInsertEntity> result = VariousDbTestHelper.findAll(BulkInsertEntity.class);
        assertThat("元の1レコードだけ存在している", result.size(), is(1));
        assertThat("IDは3", result.get(0).userId, is(3L));
    }

    /**
     * ワークテーブルからデータを抽出するSQL文を生成する。
     */
    private String createTransferQuery() {
        final List<String> columns = EtlUtil.getAllColumns("bulk_insert_entity");
        final StringBuilder select = new StringBuilder();
        select.append("select ");
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                select.append(',');
            }
            final String name = columns.get(i);
            if (name.equalsIgnoreCase("name")) {
                select.append("last_name || ' ' || first_name ")
                        .append(name);
            } else {
                select.append(name)
                        .append(' ')
                        .append(name);
            }
        }
        select.append(" from etl_work_table");
        return select.toString();
    }

    /**
     * 元テーブル
     */
    @Entity
    @Table(name = "etl_work_table")
    public static class WorkTableEntity {

        @Id
        @Column(name = "line_number", length = 10)
        public Long lineNumber;

        @Column(name = "user_id", length = 15)
        public Long userId;

        @Column(name = "last_name")
        public String lastName;

        @Column(name = "first_name")
        public String firstName;

        @Column(name = "address")
        public String address;

        public WorkTableEntity() {
        }

        public WorkTableEntity(Long lineNumber, Long userId, String lastName, String firstName, String address) {
            this.lineNumber = lineNumber;
            this.userId = userId;
            this.lastName = lastName;
            this.firstName = firstName;
            this.address = address;
        }
    }


    /**
     * INSERT先テーブル
     */
    @Entity
    @Table(name = "bulk_insert_entity")
    public static class BulkInsertEntity {

        @Id
        @Column(name = "user_id", length = 15)
        public Long userId;

        @Column(name = "name")
        public String name;

        @Column(name = "address")
        public String address;

        public BulkInsertEntity() {
        }

        public BulkInsertEntity(Long userId, String name, String address) {
            this.userId = userId;
            this.name = name;
            this.address = address;
        }
    }
}
