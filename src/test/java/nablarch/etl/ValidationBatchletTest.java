package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.common.databind.csv.Csv;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionFactory;
import nablarch.core.validation.ee.Domain;
import nablarch.etl.config.ValidationStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.AssertTrue;
import java.util.List;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * {@link ValidationBatchlet}のテスト。
 */
@RunWith(DatabaseTestRunner.class)
public class ValidationBatchletTest {

    @ClassRule
    public static SystemRepositoryResource resource = new SystemRepositoryResource("nablarch/etl/validation.xml");

    @Rule
    public TestName testName = new TestName();


    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(ValidationBatchletEntity.class);
        VariousDbTestHelper.createTable(ValidationBatchletErrorEntity.class);

        TransactionFactory transactionFactory = resource.getComponent("jdbcTransactionFactory");
        TransactionContext.setTransaction(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY,
                transactionFactory.getTransaction(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        TransactionContext.removeTransaction();
    }

    /** テスト対象 */
    ValidationBatchlet sut = new ValidationBatchlet();

    @Mocked
    JobContext mockJobContext;

    @Mocked
    StepContext mockStepContext;

    ValidationStepConfig stepConfig;

    @Before
    public void setUp() throws Exception {
        // set mock object
        stepConfig = new ValidationStepConfig();
        stepConfig.setBean(ValidationBatchletBean.class);
        stepConfig.setErrorTableEntity(ValidationBatchletErrorBean.class);

        Deencapsulation.setField(sut, "stepConfig", stepConfig);
        Deencapsulation.setField(sut, mockJobContext);
        Deencapsulation.setField(sut, mockStepContext);

        new Expectations() {{
            mockJobContext.getJobName();
            final String methodName = testName.getMethodName();
            result = methodName + "_job";
            mockStepContext.getStepName();
            result = methodName + "_step";
        }};

        // set database connection
        final ConnectionFactory connectionFactory = resource.getComponentByType(ConnectionFactory.class);
        final TransactionManagerConnection connection = connectionFactory.getConnection(
                TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);

        // clear resource
        OnMemoryLogWriter.clear();

        // clear error table
        VariousDbTestHelper.delete(ValidationBatchletErrorEntity.class);
    }

    @After
    public void tearDown() throws Exception {
        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();
        DbConnectionContext.removeConnection();
        connection.terminate();

        OnMemoryLogWriter.clear();
    }

    /**
     * validationでエラーが発生しないケース。
     * <p/>
     * 結果ログには、エラー件数0が出力されること。
     */
    @Test
    public void validationSuccess() throws Exception {
        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "1"),
                new ValidationBatchletEntity(2L, "い", "き", "1")
        );

        // -------------------------------------------------- execute
        assertThat("エラーなしなのでSUCCESS", sut.process(), is("SUCCESS"));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラーはないのでレコードは登録せれない", errors.isEmpty(), is(true));

        final List<ValidationBatchletEntity> inputs = VariousDbTestHelper.findAll(ValidationBatchletEntity.class);
        assertThat("エラーはないのでレコードは減らない", inputs.size(), is(2));

        // -------------------------------------------------- assert log
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("ログは結果ログの1つだけ", logMessages.size(), is(1));
        assertThat("バリデーション結果がログ出力される", trimCrlf(logMessages.get(0)),
                is("-INFO- validation result."
                        + " bean class=[" + ValidationBatchletBean.class.getName() + "],"
                        + " line count=[2],"
                        + " error count=[0]"));

        // -------------------------------------------------- assert sql
        final List<String> sqlLogs = OnMemoryLogWriter.getMessages("writer.sql");
        for (String sqlLog : sqlLogs) {
            if (sqlLog.contains("truncate")) {
                assertThat("スキーマを指定したテーブル名になっていること", sqlLog, containsString("ssd.etl_validation_test_error"));
            }
            if (sqlLog.matches("^select")) {
                assertThat("スキーマを指定したテーブル名になっていること", sqlLog, containsString("ssd.etl_validation_test"));
            }
        }
    }

    /**
     * validationでエラーが1つ発生する場合のケース
     * <p/>
     * 結果ログのエラー件数が正しいこと。<br />
     * エラーの内容がログに出力されること。<br />
     * エラーテーブルの既存の情報はクリーニングされること<br />
     * エラーレコードが移動されること
     * <p/>
     */
    @Test
    public void validation_singleError() throws Exception {
        // -------------------------------------------------- set continue mode
        stepConfig.setMode(ValidationStepConfig.Mode.CONTINUE);

        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "1"),
                new ValidationBatchletEntity(2L, "いいいいいい", "き", "1"),       // error 1
                new ValidationBatchletEntity(3L, "う", "く", "1")
        );

        VariousDbTestHelper.setUpTable(
                new ValidationBatchletErrorEntity(99L, "あ", "い", "10")
        );

        // -------------------------------------------------- execute
        assertThat("エラーありなのでVALIDATION_ERROR", sut.process(), is("VALIDATION_ERROR"));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラーが1レコードあるので1レコード登録される", errors.size(), is(1));
        assertThat(errors.get(0).lineNumber, is(2L));
        assertThat(errors.get(0).firstName, is("いいいいいい"));
        assertThat(errors.get(0).lastName, is("き"));
        assertThat(errors.get(0).age, is("1"));

        final List<ValidationBatchletEntity> inputs = VariousDbTestHelper.findAll(ValidationBatchletEntity.class,
                "lineNumber");
        assertThat("エラーの1レコード削除される", inputs.size(), is(2));
        assertThat(inputs.get(0).lineNumber, is(1L));
        assertThat(inputs.get(1).lineNumber, is(3L));

        // -------------------------------------------------- assert log
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("ログは結果ログの2つ", logMessages.size(), is(2));

        assertThat("エラー情報がログに出力されること", trimCrlf(logMessages.get(0)),
                allOf(
                        containsString("-WARN-"),
                        containsString("validation error has occurred. "),
                        containsString("bean class=[nablarch.etl.ValidationBatchletTest$ValidationBatchletBean],"),
                        containsString("property name=[firstName]"),
                        containsString("error message=[5文字以内で入力してください。]"),
                        containsString("line number=[2]")
                ));
        assertThat("バリデーション結果がログ出力される", trimCrlf(logMessages.get(1)),
                is("-INFO- validation result."
                        + " bean class=[" + ValidationBatchletBean.class.getName() + "],"
                        + " line count=[3],"
                        + " error count=[1]"));

        // -------------------------------------------------- assert sql
        final List<String> sqlLogs = OnMemoryLogWriter.getMessages("writer.sql");
        for (String sqlLog : sqlLogs) {
            if (sqlLog.contains("[truncate table")) {
                assertThat("スキーマを指定したテーブル名になっていること", sqlLog, containsString("ssd.etl_validation_test_error"));
            }
            if (sqlLog.contains("[SELECT ")) {
                assertThat("スキーマを指定したテーブル名になっていること", sqlLog, containsString("ssd.etl_validation_test"));
            }

            if (sqlLog.contains("[delete ")) {
                assertThat("スキーマを指定したテーブル名になっていること", sqlLog,
                        allOf(
                                containsString("ssd.etl_validation_test"),
                                containsString("ssd.etl_validation_test_error")
                        ));
            }
        }
    }

    /**
     * validationでエラーが複数発生する場合のケース
     * <p/>
     * 結果ログのエラー件数が正しいこと。
     * エラーの内容がログに出力されること。
     * エラーレコードが移動されること。
     * <p/>
     */
    @Test
    public void validation_multiError() throws Exception {
        // -------------------------------------------------- set continue mode
        stepConfig.setMode(ValidationStepConfig.Mode.CONTINUE);

        // -------------------------------------------------- set error limit
        stepConfig.setErrorLimit(-1);
        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "1"),
                new ValidationBatchletEntity(2L, "いいいいいい", "き", "1"),     // error 1
                new ValidationBatchletEntity(3L, "う", "ku", "100"),             // error 2
                new ValidationBatchletEntity(4L, "え", "け", "1"),
                new ValidationBatchletEntity(5L, "お", "こ", "2")
        );

        // -------------------------------------------------- execute
        assertThat("エラーありなのでVALIDATION_ERROR", sut.process(), is("VALIDATION_ERROR"));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラー数は3だがエラーが発生するレコードは2つだけなので2レコード登録される",
                errors.size(), is(2));

        final List<ValidationBatchletEntity> inputs = VariousDbTestHelper.findAll(ValidationBatchletEntity.class,
                "lineNumber");
        assertThat("エラーの2レコード削除される", inputs.size(), is(3));
        assertThat(inputs.get(0).lineNumber, is(1L));
        assertThat(inputs.get(1).lineNumber, is(4L));
        assertThat(inputs.get(2).lineNumber, is(5L));

        // -------------------------------------------------- assert log
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("ログは結果ログの4つ", logMessages.size(), is(4));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "bean class=[nablarch.etl.ValidationBatchletTest$ValidationBatchletBean], property name=[firstName]");
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "bean class=[nablarch.etl.ValidationBatchletTest$ValidationBatchletBean], property name=[lastName]");
        OnMemoryLogWriter.assertLogContains("writer.memory",
                "bean class=[nablarch.etl.ValidationBatchletTest$ValidationBatchletBean], property name=[age]");

        assertThat("バリデーション結果がログ出力される", trimCrlf(logMessages.get(3)),
                is("-INFO- validation result."
                        + " bean class=[" + ValidationBatchletBean.class.getName() + "],"
                        + " line count=[5],"
                        + " error count=[3]"));
    }

    /**
     * 項目間バリデーションでエラーがある場合のテスト。
     * <p/>
     * 単項目の場合と動作が同じであること
     */
    @Test
    public void validation_betweenItemsError() throws Exception {
        // -------------------------------------------------- set continue mode
        stepConfig.setMode(ValidationStepConfig.Mode.CONTINUE);

        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "1"),
                new ValidationBatchletEntity(6L, "いいいいい", "ききききき", "1"),        // 項目間バリデーションでエラーとなるレコード
                new ValidationBatchletEntity(7L, "う", "く", "1"),
                new ValidationBatchletEntity(8L, "え", "け", "1"),
                new ValidationBatchletEntity(10L, "お", "こ", "2")
        );

        // -------------------------------------------------- execute
        assertThat("エラーありなのでVALIDATION_ERROR", sut.process(), is("VALIDATION_ERROR"));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラーレコードは1つだけなので1レコード登録される", errors.size(), is(1));
        assertThat(errors.get(0).lineNumber, is(6L));

        final List<ValidationBatchletEntity> inputs = VariousDbTestHelper.findAll(ValidationBatchletEntity.class);
        assertThat("エラーの1レコード削除される", inputs.size(), is(4));

        // -------------------------------------------------- assert log
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("ログは結果ログの2つ", logMessages.size(), is(2));

        OnMemoryLogWriter.assertLogContains("writer.memory",
                "bean class=[nablarch.etl.ValidationBatchletTest$ValidationBatchletBean], property name=[nameLength],");

        assertThat("エラーなしのログが出力される", trimCrlf(logMessages.get(1)),
                is("-INFO- validation result."
                        + " bean class=[" + ValidationBatchletBean.class.getName() + "],"
                        + " line count=[5],"
                        + " error count=[1]"));
    }

    /**
     * バリデーションエラーがあった場合にジョブを中止するモードの場合、処理終了後に例外が送出されること。
     * <p/>
     * ※デフォルトモードを使用する
     */
    @Test
    public void validation_aborted() throws Exception {
        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "1"),
                new ValidationBatchletEntity(2L, "いいいいいい", "き", "1"),     // error 1
                new ValidationBatchletEntity(3L, "う", "ku", "100"),             // error 2
                new ValidationBatchletEntity(4L, "え", "け", "1"),
                new ValidationBatchletEntity(5L, "お", "こ", "2")
        );

        try {
            sut.process();
            fail();
        } catch (EtlJobAbortedException e) {
            assertThat("メッセージの確認", e.getMessage(), is("abort the JOB because there was a validation error."
                    + " bean class=[" + ValidationBatchletBean.class.getName() + "], error count=[3]"));
        }

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラー数は3だがエラーが発生するレコードは2つだけなので2レコード登録される",
                errors.size(), is(2));

        final List<ValidationBatchletEntity> inputs = VariousDbTestHelper.findAll(ValidationBatchletEntity.class);
        assertThat("エラーの2レコード削除される", inputs.size(), is(3));

        // -------------------------------------------------- assert log
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("バリデーション結果がログ出力される", trimCrlf(logMessages.get(3)),
                is("-INFO- validation result."
                        + " bean class=[" + ValidationBatchletBean.class.getName() + "],"
                        + " line count=[5],"
                        + " error count=[3]"));
    }

    /**
     * 許容するエラー数を超えた場合は、JOBが異常終了すること。
     */
    @Test
    public void testErrorLimitOver() throws Exception {

        // -------------------------------------------------- setup error limit
        stepConfig.setErrorLimit(5);

        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "ka", "10"),                  // error 1
                new ValidationBatchletEntity(2L, "い", "き", "1"),
                new ValidationBatchletEntity(3L, "u", "く", "10"),                   // error 2
                new ValidationBatchletEntity(4L, "e", "け", "10"),                   // error 3
                new ValidationBatchletEntity(5L, "お", "こ", "200"),                 // error 4
                new ValidationBatchletEntity(6L, "あああああ", "かかかかか", "20"),  // error 5
                new ValidationBatchletEntity(7L, "あ", "あ", "20"),
                new ValidationBatchletEntity(8L, "お", "こ", "200")                  // error 6
        );

        // -------------------------------------------------- execute
        try {
            sut.process();
            fail();
        } catch (EtlJobAbortedException e) {
            assertThat(e.getMessage(), is("number of validation errors has exceeded the maximum number of errors."
                    + " bean class=[" + ValidationBatchletBean.class.getName() + ']'));
        }


        // -------------------------------------------------- assert log
        // 処理順は保証されないので件数のみアサート
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("バリデーションエラーのログが6件出力されていること", logMessages.size(), is(6));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラーテーブルにはレコードが移動されないこと", errors.size(), is(0));

        final List<ValidationBatchletEntity> work = VariousDbTestHelper.findAll(ValidationBatchletEntity.class);
        assertThat("一時テーブルからデータは削除されないこと", work.size(), is(8));
    }

    /**
     * 許容するエラー数が「0」の場合、1件目のエラーで異常終了すること。
     */
    @Test
    public void testErrorLimitIsZero() throws Exception {

        // -------------------------------------------------- setup error limit
        stepConfig.setErrorLimit(0);

        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "10"),
                new ValidationBatchletEntity(2L, "い", "き", "1"),
                new ValidationBatchletEntity(3L, "u", "く", "10")            // error
        );

        // -------------------------------------------------- execute
        try {
            sut.process();
            fail();
        } catch (EtlJobAbortedException e) {
            assertThat(e.getMessage(), is("number of validation errors has exceeded the maximum number of errors."
                    + " bean class=[" + ValidationBatchletBean.class.getName() + ']'));
        }


        // -------------------------------------------------- assert log
        // 処理順は保証されないので件数のみアサート
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("1件目のエラーで落ちるのでエラー数は1", logMessages.size(), is(1));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラーテーブルにはレコードが移動されないこと", errors.size(), is(0));

        final List<ValidationBatchletEntity> work = VariousDbTestHelper.findAll(ValidationBatchletEntity.class);
        assertThat("一時テーブルからデータは削除されないこと", work.size(), is(3));
    }

    /**
     * 単一のレコードで、許容するエラー数を突破するケース。
     */
    @Test
    public void testErrorLimitOver_OneLine() throws Exception {
        // -------------------------------------------------- setup error limit
        stepConfig.setErrorLimit(2);

        // -------------------------------------------------- setup input data
        VariousDbTestHelper.setUpTable(
                new ValidationBatchletEntity(1L, "あ", "か", "10"),
                new ValidationBatchletEntity(2L, "i", "ki", "999"),
                new ValidationBatchletEntity(3L, "う", "く", "10")
        );

        // -------------------------------------------------- execute
        try {
            sut.process();
            fail();
        } catch (EtlJobAbortedException e) {
            assertThat(e.getMessage(), is("number of validation errors has exceeded the maximum number of errors."
                    + " bean class=[" + ValidationBatchletBean.class.getName() + ']'));
        }

        // -------------------------------------------------- assert log
        final List<String> logMessages = OnMemoryLogWriter.getMessages("writer.memory");
        assertThat("エラーログが3件以上出力されていること", logMessages.size(), is(3));

        // -------------------------------------------------- assert table
        final List<ValidationBatchletErrorEntity> errors = VariousDbTestHelper.findAll(
                ValidationBatchletErrorEntity.class);
        assertThat("エラーテーブルにはレコードが移動されないこと", errors.size(), is(0));

        final List<ValidationBatchletEntity> work = VariousDbTestHelper.findAll(ValidationBatchletEntity.class);
        assertThat("一時テーブルからデータは削除されないこと", work.size(), is(3));
    }

    /**
     * 設定値[bean]が不正な場合エラーが送出されること
     */
    @Test
    public void testInvalidBean() throws Exception {
        stepConfig.setBean(null);
        try {
            sut.process();
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("bean is required."));
        }
    }

    /**
     * 設定値[errorEntity]が不正な場合エラーが送出されること
     */
    @Test
    public void testInvalidErrorTable() throws Exception {
        stepConfig.setErrorTableEntity(null);
        try {
            sut.process();
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("errorEntity is required."));
        }
    }

    /**
     * 設定値[mode]が不正な場合エラーが送出されること
     */
    @Test
    public void testInvalidMode() throws Exception {
        stepConfig.setMode(null);
        try {
            sut.process();
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("mode is required."));
        }
    }

    /**
     * ログメッセージの最後の改行を削除する。
     *
     * @param message メッセージ
     * @return 改行を削除したメッセージ
     */
    private static String trimCrlf(String message) {
        return message.replaceAll("\r\n|\n", "");
    }

    @Entity
    @Csv(type = Csv.CsvType.DEFAULT, properties = {"firstName", "lastName"})
    @Table(name = "etl_validation_test", schema = "ssd")
    public static class ValidationBatchletBean extends WorkItem {

        private String firstName;

        private String lastName;

        private String age;

        @Domain("name")
        @Column(name = "first_name")
        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        @Domain("name")
        @Column(name = "last_name")
        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        @Domain("age")
        @Column(name = "age")
        public String getAge() {
            return age;
        }

        public void setAge(String age) {
            this.age = age;
        }

        @AssertTrue(message = "名前の長さは合計8文字まで")
        @Transient
        public boolean isNameLength() {
            return (firstName + lastName).length() <= 8;
        }
    }

    @Entity
    @Table(name = "etl_validation_test_error", schema = "ssd")
    public static class ValidationBatchletErrorBean extends ValidationBatchletBean {

    }

    @Entity
    @Table(name = "etl_validation_test")
    public static class ValidationBatchletEntity {

        @Column(name = "line_number", length = 15)
        @Id
        public Long lineNumber;

        @Column(name = "first_name")
        public String firstName;

        @Column(name = "last_name")
        public String lastName;

        @Column(name = "age", length = 3)
        public String age;

        public ValidationBatchletEntity() {
        }

        public ValidationBatchletEntity(Long lineNumber, String firstName, String lastName, String age) {
            this.lineNumber = lineNumber;
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }
    }

    @Entity
    @Table(name = "etl_validation_test_error")
    public static class ValidationBatchletErrorEntity {

        @Column(name = "line_number", length = 15)
        @Id
        public Long lineNumber;

        @Column(name = "first_name")
        public String firstName;

        @Column(name = "last_name")
        public String lastName;

        @Column(name = "age", length = 3)
        public String age;

        public ValidationBatchletErrorEntity() {
        }

        public ValidationBatchletErrorEntity(Long lineNumber, String firstName, String lastName, String age) {
            this.lineNumber = lineNumber;
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

    }
}
