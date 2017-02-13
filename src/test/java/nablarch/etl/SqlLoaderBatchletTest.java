package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.eclipse.persistence.tools.file.FileUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.File;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link SqlLoaderBatchlet}のテストクラス。
 */
@RunWith(DatabaseTestRunner.class)
public class SqlLoaderBatchletTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("db-default.xml");

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private SqlLoaderBatchlet sut = new SqlLoaderBatchlet();

    private static String tmpPath;

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Mocked
    private FileToDbStepConfig mockFileToDbStepConfig;

    @BeforeClass
    public static void setUpClass() throws Exception {
        tmpPath = temporaryFolder.getRoot().getPath();
    }

    @Before
    public void setUp() throws Exception {
        OnMemoryLogWriter.clear();

        FileUtil.delete(new File(tmpPath, "Person.log"));
        FileUtil.delete(new File(tmpPath, "Person.bad"));

        repositoryResource.addComponent("db.user", "ssd");
        repositoryResource.addComponent("db.password", "ssd");
        repositoryResource.addComponent("db.databaseName", "xe");

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            mockJobContext.getJobName();
            result = "test-job";
        }};
        Deencapsulation.setField(sut, "jobContext", mockJobContext);
        Deencapsulation.setField(sut, "stepContext", mockStepContext);
        Deencapsulation.setField(sut, "stepConfig", mockFileToDbStepConfig);
    }

    /**
     * 必須項目が指定されなかった場合、例外が送出されること。
     */
    @Test
    public void testRequired() throws Exception {

        // fileName

        new Expectations() {{
            mockFileToDbStepConfig.getFileName();
            result = null;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("fileName is required. jobId = [test-job], stepId = [test-step]"));
        }

        // bean

        new Expectations() {{
            mockFileToDbStepConfig.getFileName();
            result = "test";
            mockFileToDbStepConfig.getBean();
            result = null;
        }};

        try {
            sut.process();
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("bean is required. jobId = [test-job], stepId = [test-step]"));
        }
    }

    /**
     * 外部プロセスの呼び出しが正常に行われ、ログが出力されていること。
     *
     * @throws Exception
     */
    @Test
    public void testProcess_success(@Mocked final Process process, @Mocked final InputStream inputStream) throws Exception {

        // -------------------------------------------------- setup objects that is injected
        Deencapsulation.setField(sut, "inputFileBasePath", new File("src/test/resources/nablarch/etl/data"));
        Deencapsulation.setField(sut, "sqlLoaderControlFileBasePath", new File("src/test/resources/nablarch/etl/ctl"));
        Deencapsulation.setField(sut, "sqlLoaderOutputFileBasePath", new File(tmpPath));
        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = Person.class;
            mockFileToDbStepConfig.getFileName();
            result = "Person.csv";
        }};

        new MockUp<ProcessBuilder>() {
            @Mock
             public void $init(String... command) {
                // 指定した引数でコンストラクタが実行されていること
                assertThat(command[0], is("sqlldr"));
                assertThat(command[1], is("USERID=ssd/ssd@xe"));
                assertThat(command[2], is("CONTROL=" + new File("src/test/resources/nablarch/etl/ctl/Person.ctl").getPath()));
                assertThat(command[3], is("DATA=" + new File("src/test/resources/nablarch/etl/data/Person.csv").getPath()));
                assertThat(command[4], is("BAD=" + new File(tmpPath, "Person.bad").getPath()));
                assertThat(command[5], is("LOG=" + new File(tmpPath, "Person.log").getPath()));
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
        Deencapsulation.setField(sut, "inputFileBasePath", new File("src/test/resources/nablarch/etl/data"));
        Deencapsulation.setField(sut, "sqlLoaderControlFileBasePath", new File("src/test/resources/nablarch/etl/ctl"));
        Deencapsulation.setField(sut, "sqlLoaderOutputFileBasePath", new File(tmpPath));
        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = Person.class;
            mockFileToDbStepConfig.getFileName();
            result = "Person.csv";
        }};

        new MockUp<ProcessBuilder>() {
            @Mock
            public void $init(String... command) {
                // 指定した引数でコンストラクタが実行されていること
                assertThat(command[0], is("sqlldr"));
                assertThat(command[1], is("USERID=ssd/ssd@xe"));
                assertThat(command[2], is("CONTROL=" + new File("src/test/resources/nablarch/etl/ctl/Person.ctl").getPath()));
                assertThat(command[3], is("DATA=" + new File("src/test/resources/nablarch/etl/data/Person.csv").getPath()));
                assertThat(command[4], is("BAD=" + new File(tmpPath, "Person.bad").getPath()));
                assertThat(command[5], is("LOG=" + new File(tmpPath, "Person.log").getPath()));
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
            assertThat(e.getMessage(), is("failed to execute SQL*Loader. controlFile = [" + new File("src/test/resources/nablarch/etl/ctl/Person.ctl").getPath() + "]"));
        }
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