package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.statement.exception.DuplicateStatementException;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionFactory;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.After;
import org.junit.AfterClass;
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
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * {@link MergeBatchlet}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class MergeBatchletTest {

    @ClassRule
    public static SystemRepositoryResource resource = new SystemRepositoryResource("db-default.xml");

    TransactionManagerConnection connection;

    /** テスト対象 */
    MergeBatchlet sut = new MergeBatchlet();

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Mocked
    private DbToDbStepConfig mockDbToDbStepConfig;

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(EtlMergeEntity.class);
        VariousDbTestHelper.createTable(EtlMergeInputWorkEntity.class);

        TransactionFactory transactionFactory = resource.getComponent("jdbcTransactionFactory");
        TransactionContext.setTransaction(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY,
                transactionFactory.getTransaction(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        TransactionContext.removeTransaction();
    }

    @Before
    public void setUp() throws Exception {
        ConnectionFactory connectionFactory = resource.getComponent("connectionFactory");
        connection = connectionFactory.getConnection(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);

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

        // clear resource
        OnMemoryLogWriter.clear();
    }

    @After
    public void tearDown() throws Exception {
        connection.rollback();
        connection.terminate();
        DbConnectionContext.removeConnection();

        OnMemoryLogWriter.clear();
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

        // sqlId

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = null;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("sqlId is required. jobId = [test-job], stepId = [test-step]"));
        }

        // mergeOnColumns

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "test";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = null;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("mergeOnColumns is required. jobId = [test-job], stepId = [test-step]"));
        }

        // updateSize

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "test";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = "hoge";
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            result = updateSize;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("updateSize > size is required. jobId = [test-job], stepId = [test-step]"));
        }

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "test";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = "hoge";
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2000);
            result = updateSize;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("updateSize > bean is required. jobId = [test-job], stepId = [test-step]"));
        }
    }

    /**
     * 更新サイズが不正な場合、例外が送出されること。
     */
    @Test
    public void testInvalidUpdateSize() throws Exception {

        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "test";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = "hoge";
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
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
     * マージ処理が正常に終了すること。
     */
    @Test
    public void mergeSuccess() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "3", "3"),
                new EtlMergeEntity(6L, "name6", "address6")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                        + " from etl_merge_input_work_entity";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id");
            mockDbToDbStepConfig.getUpdateSize();
            result = null;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("変更なし1、更新1、追加4で6レコード存在する", result.size(), is(6));

        for (int i = 0; i < 6; i++) {
            final EtlMergeEntity entity = result.get(i);
            int index = i + 1;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("name" + index));
            assertThat(entity.address, is("address" + index));
        }

        OnMemoryLogWriter.assertLogContains("writer.sql", "update count = [5]");
        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- load progress. table name=[etl_merge_entity], merge count=[5]");

        assertSqlExecutionAndCommitTimes("update count = [5]");
    }

    private static final String COMMIT_MSG = "transaction commit.";

    /**
     * SQL実行とコミットの回数を検証する。
     * @param expectedMessages 「update count = [n]」と「transaction commit.」メッセージ
     */
    private void assertSqlExecutionAndCommitTimes(final String... expectedMessages) {

        // 実際に出力された順番で、SQL実行とコミットのメッセージを抜き出します。
        final List<String> actualMessages = new ArrayList<String>();
        for (String message : OnMemoryLogWriter.getMessages("writer.sql")) {
            if (message.contains("update count = ")
                    || message.contains(COMMIT_MSG)) {
                actualMessages.add(message);
            }
        }

        // 実際に出力された順番のメッセージと、期待するメッセージをアサートします。
        assertThat(actualMessages.size(), is(expectedMessages.length));
        for (int i = 0; i < expectedMessages.length; i++) {
            assertThat(actualMessages.get(i), is(containsString(expectedMessages[i])));
        }
    }

    /**
     * 1回のSQL実行で処理するサイズが指定された場合に、マージ処理が正常に終了すること。
     */
    @Test
    public void mergeSuccessUsingSplit() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "3", "3"),
                new EtlMergeEntity(6L, "name6", "address6")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                    + " from etl_merge_input_work_entity where line_number between ? and ?";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id");
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2);
            updateSize.setBean(EtlMergeInputWorkEntity.class);
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("変更なし1、更新1、追加4で6レコード存在する", result.size(), is(6));

        for (int i = 0; i < 6; i++) {
            final EtlMergeEntity entity = result.get(i);
            int index = i + 1;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("name" + index));
            assertThat(entity.address, is("address" + index));
        }

        assertSqlExecutionAndCommitTimes(
                "update count = [2]", COMMIT_MSG,
                "update count = [2]", COMMIT_MSG,
                "update count = [1]", COMMIT_MSG);
        String[] expected = { "-INFO- load progress. table name=[etl_merge_entity], merge count=[2]",
            "-INFO- load progress. table name=[etl_merge_entity], merge count=[4]",
            "-INFO- load progress. table name=[etl_merge_entity], merge count=[5]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 1回のSQL実行で処理するサイズが指定され、
     * 途中のSQL実行で処理件数が0の場合でも、
     * 最後までマージ処理が正常に終了すること。
     */
    @Test
    public void mergeSuccessUsingSplitWhenIncludeNoData() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "3", "3"),
                new EtlMergeEntity(6L, "name6", "address6")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                    + " from etl_merge_input_work_entity where line_number between ? and ?"
                    + " and work_user_id not in(3, 4)";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id");
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2);
            updateSize.setBean(EtlMergeInputWorkEntity.class);
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("変更なし2、更新0、追加3で5レコード存在する", result.size(), is(5));

        assertThat(result.get(0).userId, is(1L));
        assertThat(result.get(0).name, is("name1"));
        assertThat(result.get(0).address, is("address1"));
        assertThat(result.get(1).userId, is(2L));
        assertThat(result.get(1).name, is("name2"));
        assertThat(result.get(1).address, is("address2"));
        assertThat(result.get(2).userId, is(3L));
        assertThat(result.get(2).name, is("3"));
        assertThat(result.get(2).address, is("3"));
        assertThat(result.get(3).userId, is(5L));
        assertThat(result.get(3).name, is("name5"));
        assertThat(result.get(3).address, is("address5"));
        assertThat(result.get(4).userId, is(6L));
        assertThat(result.get(4).name, is("name6"));
        assertThat(result.get(4).address, is("address6"));

        assertSqlExecutionAndCommitTimes(
                "update count = [2]", COMMIT_MSG,
                "update count = [0]", COMMIT_MSG,
                "update count = [1]", COMMIT_MSG);
        String[] expected = { "-INFO- load progress. table name=[etl_merge_entity], merge count=[2]",
            "-INFO- load progress. table name=[etl_merge_entity], merge count=[2]",
            "-INFO- load progress. table name=[etl_merge_entity], merge count=[3]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 1回のSQL実行で処理するサイズが指定され、ワークテーブルのデータがない場合に、マージ処理が正常に終了すること。
     */
    @Test
    public void mergeSuccessUsingSplitForNoWorkData() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.delete(EtlMergeInputWorkEntity.class);

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "3", "3"),
                new EtlMergeEntity(6L, "name6", "address6")
        );

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                    + " from etl_merge_input_work_entity where line_number between ? and ?";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id");
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2);
            updateSize.setBean(EtlMergeInputWorkEntity.class);
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("変更なし2で2レコード存在する", result.size(), is(2));

        assertThat(result.get(0).userId, is(3L));
        assertThat(result.get(0).name, is("3"));
        assertThat(result.get(0).address, is("3"));
        assertThat(result.get(1).userId, is(6L));
        assertThat(result.get(1).name, is("name6"));
        assertThat(result.get(1).address, is("address6"));
    }

    /**
     * 複数の結合カラムを持つテーブルの場合でもマージ処理が正常に完了すること
     */
    @Test
    public void merge_multiJoinColumn() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "name3", "3"),
                new EtlMergeEntity(5L, "name5", "更新される住所"));

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                        + " from etl_merge_input_work_entity";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id", "name");
            mockDbToDbStepConfig.getUpdateSize();
            result = null;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("更新1、追加4で5レコード存在する", result.size(), is(5));
        for (int i = 0; i < 5; i++) {
            final EtlMergeEntity entity = result.get(i);
            int index = i + 1;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("name" + index));
            assertThat(entity.address, is("address" + index));
        }

        assertSqlExecutionAndCommitTimes("update count = [5]");
        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- load progress. table name=[etl_merge_entity], merge count=[5]");
    }

    /**
     * 1回のSQL実行で処理するサイズが指定され、複数の結合カラムを持つテーブルの場合でもマージ処理が正常に完了すること
     */
    @Test
    public void merge_multiJoinColumnUsingSplit() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "name3", "3"),
                new EtlMergeEntity(5L, "name5", "更新される住所"));

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                    + " from etl_merge_input_work_entity where line_number between ? and ?";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id", "name");
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2);
            updateSize.setBean(EtlMergeInputWorkEntity.class);
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        sut.process();
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("更新1、追加4で5レコード存在する", result.size(), is(5));
        for (int i = 0; i < 5; i++) {
            final EtlMergeEntity entity = result.get(i);
            int index = i + 1;
            assertThat(entity.userId, is((long) index));
            assertThat(entity.name, is("name" + index));
            assertThat(entity.address, is("address" + index));
        }

        assertSqlExecutionAndCommitTimes(
                "update count = [2]", COMMIT_MSG,
                "update count = [2]", COMMIT_MSG,
                "update count = [1]", COMMIT_MSG);
        String[] expected = { "-INFO- load progress. table name=[etl_merge_entity], merge count=[2]",
            "-INFO- load progress. table name=[etl_merge_entity], merge count=[4]",
            "-INFO- load progress. table name=[etl_merge_entity], merge count=[5]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * merge処理に失敗するケース
     */
    @Test
    public void mergeFailed() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "3", "3"));

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                        + " from etl_merge_input_work_entity";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id", "name");
            mockDbToDbStepConfig.getUpdateSize();
            result = null;
        }};

        // -------------------------------------------------- execute
        try {
            sut.process();
            fail("一意制約違反がでるのでここまでこない");
        } catch (Exception e) {
            assertThat(e, instanceOf(DuplicateStatementException.class));
        }
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("SQL実行に失敗するので元の1レコードのみ", result.size(), is(1));
        assertThat(result.get(0).userId, is(3L));
        assertThat(result.get(0).name, is("3"));
        assertThat(result.get(0).address, is("3"));
    }

    /**
     * 1回のSQL実行で処理するサイズが指定され、かつmerge処理に失敗するケース
     */
    @Test
    public void mergeFailedUsingSplit() throws Exception {
        // -------------------------------------------------- setup table data
        VariousDbTestHelper.setUpTable(
                new EtlMergeInputWorkEntity(1L, 1L, "name1", "address1"),
                new EtlMergeInputWorkEntity(2L, 2L, "name2", "address2"),
                new EtlMergeInputWorkEntity(3L, 3L, "name3", "address3"),
                new EtlMergeInputWorkEntity(4L, 4L, "name4", "address4"),
                new EtlMergeInputWorkEntity(5L, 5L, "name5", "address5")
        );

        VariousDbTestHelper.setUpTable(
                new EtlMergeEntity(3L, "3", "3"));

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                    + " from etl_merge_input_work_entity where line_number between ? and ?";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id", "name");
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2);
            updateSize.setBean(EtlMergeInputWorkEntity.class);
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        try {
            sut.process();
            fail("一意制約違反がでるのでここまでこない");
        } catch (Exception e) {
            assertThat(e, instanceOf(DuplicateStatementException.class));
        }
        connection.commit();

        // -------------------------------------------------- assert
        final List<EtlMergeEntity> result = VariousDbTestHelper.findAll(EtlMergeEntity.class, "userId");
        assertThat("1回目のSQL実行は成功し、2回目のSQL実行に失敗するので元の1レコード＋成功した2レコードの3レコードのみ",
                result.size(), is(3));
        assertThat(result.get(0).userId, is(1L));
        assertThat(result.get(0).name, is("name1"));
        assertThat(result.get(0).address, is("address1"));
        assertThat(result.get(1).userId, is(2L));
        assertThat(result.get(1).name, is("name2"));
        assertThat(result.get(1).address, is("address2"));
        assertThat(result.get(2).userId, is(3L));
        assertThat(result.get(2).name, is("3"));
        assertThat(result.get(2).address, is("3"));

        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- load progress. table name=[etl_merge_entity], merge count=[2]");
    }

    /**
     * 1回のSQL実行で処理するサイズが指定されたがSQL文にINパラメータがない場合に、例外が送出されること。
     */
    @Test
    public void invalidSqlUsingSplit() throws Exception {

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToDbStepConfig.getBean();
            result = EtlMergeEntity.class;
            mockDbToDbStepConfig.getSqlId();
            result = "dummy";
            mockDbToDbStepConfig.getSql();
            result = "select work_user_id user_id, work_name name, work_address address"
                    + " from etl_merge_input_work_entity";
            mockDbToDbStepConfig.getMergeOnColumns();
            result = Arrays.asList("user_id", "name");
            mockDbToDbStepConfig.getUpdateSize();
            DbToDbStepConfig.UpdateSize updateSize = new DbToDbStepConfig.UpdateSize();
            updateSize.setSize(2);
            updateSize.setBean(EtlMergeInputWorkEntity.class);
            result = updateSize;
        }};

        // -------------------------------------------------- execute
        try {
            sut.process();
            fail("SQL文にINパラメータがないのでここまでこない");
        } catch (Exception e) {
            assertThat(e, instanceOf(IllegalArgumentException.class));
            assertThat(e.getMessage(), is(
                              "sql is invalid. "
                            + "please include a range of data on the condition of the sql statement using the two input parameters. "
                            + "ex) \"where line_number between ? and ?\" "
                            + "sqlId = [dummy]"));
        }
    }

    @Entity
    @Table(name = "etl_merge_input_work_entity")
    public static class EtlMergeInputWorkEntity {

        @Id
        @Column(name = "work_user_id", length = 15)
        public Long userId;

        @Column(name = "work_name")
        public String name;

        @Column(name = "work_address")
        public String address;

        @Column(name = "LINE_NUMBER", length = 18, nullable = false, unique = true)
        public Long lineNumber;

        public EtlMergeInputWorkEntity() {
        }

        public EtlMergeInputWorkEntity(Long lineNumber, Long userId, String name, String address) {
            this.userId = userId;
            this.name = name;
            this.address = address;
            this.lineNumber = lineNumber;
        }
    }

    @Entity
    @Table(name = "etl_merge_entity")
    public static class EtlMergeEntity {

        @Id
        @Column(name = "user_id", length = 15)
        public Long userId;

        @Column(name = "name")
        public String name;

        @Column(name = "address")
        public String address;

        public EtlMergeEntity() {
        }

        public EtlMergeEntity(Long userId, String name, String address) {
            this.userId = userId;
            this.name = name;
            this.address = address;
        }

        @Id
        public Long getUserId() {
            return userId;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }
    }
}

