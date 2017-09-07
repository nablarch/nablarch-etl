package nablarch.etl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import nablarch.etl.config.FileToDbStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.TargetDb;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.NonStrictExpectations;

/**
 * {@link SqlLoaderBatchlet}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
@TargetDb(include = TargetDb.Db.ORACLE)
public class SqlLoaderBatchletTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/etl/SqlLoaderBatchletTest.xml");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Before
    public void setUp() throws Exception {
        OnMemoryLogWriter.clear();

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            mockJobContext.getJobName();
            result = "test-job";
        }};
    }

    /**
     * 必須項目が指定されなかった場合、例外が送出されること。
     */
    @Test
    public void inputFileIsNull_shouldThrowException() throws Exception {
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(Person.class);
        stepConfig.setFileName(null);
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext, mockStepContext, stepConfig, null, null, null
        );

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("fileName is required. jobId = [test-job], stepId = [test-step]");
        sut.process();
    }

    @Test
    public void beanIsNull_shouldThrowException() throws Exception {
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(null);
        stepConfig.setFileName("test");
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext, mockStepContext, stepConfig, null, null, null
        );

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("bean is required. jobId = [test-job], stepId = [test-step]");
        sut.process();
    }

    /**
     * 外部プロセスの呼び出しが正常に行われ、ログが出力されていること。
     *
     * @throws Exception
     */
    @Test
    public void testProcess_success(@Mocked final Process process, @Mocked final InputStream inputStream) throws Exception {

        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(Person.class);
        stepConfig.setFileName("Person.csv");

        final File sqlLoaderOutputFileBasePath = temporaryFolder.newFolder();
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext,
                mockStepContext,
                stepConfig,
                new File("src/test/resources/nablarch/etl/data"),
                new File("src/test/resources/nablarch/etl/ctl"),
                sqlLoaderOutputFileBasePath);

        new MockUp<ProcessBuilder>() {
            @Mock
             public void $init(String... command) {
                // 指定した引数でコンストラクタが実行されていること
                assertThat(command[0], is("sqlldr"));
                assertThat(command[1], is("USERID=user/pass@db_name"));
                assertThat(command[2], is("CONTROL=" + new File("src/test/resources/nablarch/etl/ctl/Person.ctl").getPath()));
                assertThat(command[3], is("DATA=" + new File("src/test/resources/nablarch/etl/data/Person.csv").getPath()));
                assertThat(command[4], is("BAD=" + new File(sqlLoaderOutputFileBasePath, "Person.bad").getPath()));
                assertThat(command[5], is("LOG=" + new File(sqlLoaderOutputFileBasePath, "Person.log").getPath()));
            }

            @Mock
            public Process start() {
                return process;
            }
        };

        new NonStrictExpectations() {{
            inputStream.read();
            result = (int) 'テ';
            result = (int) 'ス';
            result = (int) 'ト';
            result = -1;
            process.exitValue();
            result = 0;
        }};

        assertThat(sut.process(), is("SUCCESS"));
    }

    /**
     * 外部プロセスの呼び出しに失敗した場合に例外が送出されること。
     */
    @Test
    public void testProcess_failed(@Mocked final Process process, @Mocked final InputStream inputStream) throws Exception {

        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(Person.class);
        stepConfig.setFileName("Person.csv");

        final File sqlLoaderOutputFileBasePath = temporaryFolder.newFolder();
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext,
                mockStepContext,
                stepConfig,
                new File("src/test/resources/nablarch/etl/data"),
                new File("src/test/resources/nablarch/etl/ctl"),
                sqlLoaderOutputFileBasePath);

        new MockUp<ProcessBuilder>() {
            @Mock
            public void $init(String... command) {
                // 指定した引数でコンストラクタが実行されていること
                assertThat(command[0], is("sqlldr"));
                assertThat(command[1], is("USERID=user/pass@db_name"));
                assertThat(command[2], is("CONTROL=" + new File("src/test/resources/nablarch/etl/ctl/Person.ctl").getPath()));
                assertThat(command[3], is("DATA=" + new File("src/test/resources/nablarch/etl/data/Person.csv").getPath()));
                assertThat(command[4], is("BAD=" + new File(sqlLoaderOutputFileBasePath, "Person.bad").getPath()));
                assertThat(command[5], is("LOG=" + new File(sqlLoaderOutputFileBasePath, "Person.log").getPath()));
            }

            @Mock
            public Process start() {
                return process;
            }
        };

        new NonStrictExpectations() {{
            inputStream.read();
            result = (int) 'テ';
            result = (int) 'ス';
            result = (int) 'ト';
            result = -1;
            process.exitValue();
            result = 1;
        }};

        try {
            sut.process();
            fail("プロセスが異常終了したため、例外が発生");
        } catch (Exception e) {
            assertThat(e, instanceOf(SqlLoaderFailedException.class));
            assertThat(e.getMessage(), is("failed to execute SQL*Loader. controlFile = [" + new File("src/test/resources/nablarch/etl/ctl/Person.ctl").getPath() + ']'));
        }
    }

    /**
     * 入力ファイルが存在しない場合はオペレータ向けログが出力されること。
     */
    @Test
    public void inputFileNotFound_shouldWriteOperatorLog() throws Exception {
        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(Person.class);
        stepConfig.setFileName("not_found.csv");

        final File sqlLoaderOutputFileBasePath = temporaryFolder.newFolder();
        final File inputFilePath = new File("src/test/resources/nablarch/etl/data");
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext,
                mockStepContext,
                stepConfig,
                inputFilePath,
                new File("src/test/resources/nablarch/etl/ctl"),
                sqlLoaderOutputFileBasePath);
        

        final String exitStatus = sut.process();
        assertThat(exitStatus, is("FAILED"));

        assertThat(OnMemoryLogWriter.getMessages("writer.operator").get(0),
                containsString("-ERROR- 入力ファイルが存在しません。外部からファイルを受信できているか、"
                        + "ディレクトリやファイルの権限は正しいかを確認してください。入力ファイル=["
                        + new File(inputFilePath, "not_found.csv").getAbsolutePath() + ']'));
    }

    /**
     * 入力ファイルが示すパスがディレクトリの場合オペレータ向けのログが出力されること。
     */
    @Test
    public void inputFileIsDirectory_shouldWriteOperatorLog() throws Exception {
        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(Person.class);
        stepConfig.setFileName("data");

        final File sqlLoaderOutputFileBasePath = temporaryFolder.newFolder();
        final File inputFilePath = new File("src/test/resources/nablarch/etl");
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext,
                mockStepContext,
                stepConfig,
                inputFilePath,
                new File("src/test/resources/nablarch/etl/ctl"),
                sqlLoaderOutputFileBasePath);
        

        final String exitStatus = sut.process();
        assertThat(exitStatus, is("FAILED"));

        assertThat(OnMemoryLogWriter.getMessages("writer.operator").get(0),
                containsString("-ERROR- 入力ファイルが存在しません。外部からファイルを受信できているか、"
                        + "ディレクトリやファイルの権限は正しいかを確認してください。入力ファイル=["
                        + new File(inputFilePath, "data").getAbsolutePath() + ']'));
    }

    /**
     * {@link SqlLoaderConfig}が存在しない場合、例外が送出されること。
     */
    @Test
    public void sqlLoaderConfigNotFound_shouldThrowException() throws Exception {
        repositoryResource.addComponent("sqlLoaderConfig", null);

        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(Person.class);
        stepConfig.setFileName("Person.csv");

        final File sqlLoaderOutputFileBasePath = temporaryFolder.newFolder();
        final SqlLoaderBatchlet sut = new SqlLoaderBatchlet(
                mockJobContext,
                mockStepContext,
                stepConfig,
                new File("src/test/resources/nablarch/etl/data"),
                new File("src/test/resources/nablarch/etl/ctl"),
                sqlLoaderOutputFileBasePath);

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("SqlLoaderConfig must be registered in SystemRepository. key=[sqlLoaderConfig]");

        sut.process();
    }

    @Entity
    @Table(name = "PERSON")
    public static class Person {
        @Id
        @Column(name = "PERSON_ID", length = 9, nullable = false, unique = true)
        public Long personId;

        @Column(name = "AGE", length = 3, nullable = true, unique = false)
        public Long age;

        @Column(name = "NAME", length = 256, nullable = true, unique = false)
        public String name;
    }
}