package nablarch.etl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import javax.batch.operations.BatchRuntimeException;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;

import nablarch.common.databind.csv.Csv;
import nablarch.core.repository.SystemRepository;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.fw.batch.ee.progress.BasicProgressManager;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import mockit.Mocked;
import mockit.Expectations;

/**
 * {@link FileItemReader}のテストクラス。
 */
public class FileItemReaderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Before
    public void setUp() {

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            mockJobContext.getJobName();
            result = "test-job";
        }};

        OnMemoryLogWriter.clear();
    }

    @After
    public void tearDown() throws Exception {
        SystemRepository.clear();
    }

    /**
     * 必須項目が指定されなかった場合、例外が送出されること。
     */
    @Test
    public void beanSetNull_shouldThrowException() throws Exception {

        final FileItemReader sut = new FileItemReader(
                mockJobContext,
                mockStepContext,
                new FileToDbStepConfig(),
                temporaryFolder.getRoot(),
                new BasicProgressManager(mockJobContext, mockStepContext));

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("bean is required. jobId = [test-job], stepId = [test-step]");
        sut.open(null);
    }

    @Test
    public void fileNameSetNull_shouldThrowException() throws Exception {
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(CsvFile.class);
        final FileItemReader sut = new FileItemReader(
                mockJobContext,
                mockStepContext,
                stepConfig,
                temporaryFolder.getRoot(),
                new BasicProgressManager(mockJobContext, mockStepContext));

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("fileName is required. jobId = [test-job], stepId = [test-step]");
        sut.open(null);
    }


    /**
     * ファイルが読み込めることを検証する。
     */
    @SuppressWarnings("unused")
    @Test
    public void readFile() throws Exception {

        // -------------------------------------------------- setup file
        final File file = new File(temporaryFolder.getRoot(), "dummy");
        final BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
        br.write("1,なまえ1\r\n");
        br.write("2,なまえ2\r\n");
        br.write("3,なまえ3\r\n");
        br.flush();
        br.close();

        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(CsvFile.class);
        stepConfig.setFileName("dummy");

        final FileItemReader sut = new FileItemReader(
                mockJobContext,
                mockStepContext,
                stepConfig,
                temporaryFolder.getRoot(),
                new BasicProgressManager(mockJobContext, mockStepContext));
        sut.open(null);

        final String inputCountLog = OnMemoryLogWriter.getMessages("writer.progress")
                                                      .get(0);
        assertThat(inputCountLog, containsString(
                "-INFO- job name: [test-job] step name: [test-step] input count: [3]"));

        _1行目:
        {
            final CsvFile actual = (CsvFile) sut.readItem();
            assertThat(actual.getUserId(), is("1"));
            assertThat(actual.getName(), is("なまえ1"));
        }

        _2行目:
        {
            final CsvFile actual = (CsvFile) sut.readItem();
            assertThat(actual.getUserId(), is("2"));
            assertThat(actual.getName(), is("なまえ2"));
        }

        _3行目:
        {
            final CsvFile actual = (CsvFile) sut.readItem();
            assertThat(actual.getUserId(), is("3"));
            assertThat(actual.getName(), is("なまえ3"));
        }

        assertThat("3レコードで終わり", sut.readItem(), is(nullValue()));
    }

    /**
     * クローズを呼び出すことでファイルが閉じられること
     * <p/>
     * close -> readの順に呼び出し、ファイルが閉じられている例外が発生することで確認する。
     */
    @Test
    public void close() throws Exception {
        // -------------------------------------------------- setup file
        final File file = new File(temporaryFolder.getRoot(), "dummy");
        final BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
        br.write("1,なまえ1\r\n");
        br.close();

        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(CsvFile.class);
        stepConfig.setFileName("dummy");

        final FileItemReader sut = new FileItemReader(
                mockJobContext,
                mockStepContext,
                stepConfig,
                temporaryFolder.getRoot(),
                new BasicProgressManager(mockJobContext, mockStepContext));

        sut.open(null);

        sut.open(null);
        sut.close();

        expectedException.expect(RuntimeException.class);
        sut.readItem();
    }

    /**
     * ファイルが存在しない場合はオペレータ向けログが出力されること
     */
    @Test
    public void inputFileNotFound() throws Exception {

        // -------------------------------------------------- setup objects that is injected
        final FileToDbStepConfig stepConfig = new FileToDbStepConfig();
        stepConfig.setBean(CsvFile.class);
        stepConfig.setFileName("dummy");

        final File inputFileBasePath = temporaryFolder.newFolder();
        final FileItemReader sut = new FileItemReader(
                mockJobContext,
                mockStepContext,
                stepConfig,
                inputFileBasePath,
                new BasicProgressManager(mockJobContext, mockStepContext));

        try {
            sut.open(null);
            fail();
        } catch (BatchRuntimeException e) {
            final String log = OnMemoryLogWriter.getMessages("writer.operator")
                                                .get(0);

            final String inputFilePath = new File(inputFileBasePath, "dummy").getAbsolutePath();
            final String message = "入力ファイルが存在しません。外部からファイルを受信できているか、"
                    + "ディレクトリやファイルの権限は正しいかを確認してください。入力ファイル=[" + inputFilePath + ']';

            assertThat(log, containsString("-ERROR- " + message));
            assertThat(e.getMessage(), is(message));
        }
    }

    @Csv(
            type = Csv.CsvType.EXCEL,
            properties = {"userId", "name"}
    )
    public static class CsvFile {

        private String userId;

        private String name;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}