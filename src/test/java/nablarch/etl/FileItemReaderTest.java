package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.common.databind.csv.Csv;
import nablarch.core.repository.SystemRepository;
import nablarch.etl.config.FileToDbStepConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link FileItemReader}のテストクラス。
 */
public class FileItemReaderTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    /** テストクラス */
    FileItemReader sut = new FileItemReader();

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Mocked
    private FileToDbStepConfig mockFileToDbStepConfig;

    @Before
    public void setUp() {

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

    @After
    public void tearDown() throws Exception {
        SystemRepository.clear();
    }

    /**
     * 必須項目が指定されなかった場合、例外が送出されること。
     */
    @Test
    public void testRequired() throws Exception {

        final File file = tempDir.newFile();

        // bean

        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = null;
        }};

        try {
            sut.open(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("bean is required. jobId = [test-job], stepId = [test-step]"));
        }

        // fileName

        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = CsvFile.class;
            mockFileToDbStepConfig.getFileName();
            result = null;
        }};

        try {
            sut.open(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("fileName is required. jobId = [test-job], stepId = [test-step]"));
        }
    }

    /**
     * ファイルが読み込めることを検証する。
     */
    @SuppressWarnings("unused")
    @Test
    public void readFile() throws Exception {

        // -------------------------------------------------- setup file
        final File inputFileBasePath = tempDir.newFolder();
        final File file = new File(inputFileBasePath, "dummy");
        Deencapsulation.setField(sut, "inputFileBasePath", inputFileBasePath);
        final BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
        br.write("1,なまえ1\r\n");
        br.write("2,なまえ2\r\n");
        br.write("3,なまえ3\r\n");
        br.flush();
        br.close();

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = CsvFile.class;
            mockFileToDbStepConfig.getFileName();
            result = "dummy";
        }};

        sut.open(null);

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
    @Test(expected = RuntimeException.class)
    public void close() throws Exception {
        // -------------------------------------------------- setup file
        final File inputFileBasePath = tempDir.newFolder();
        final File file = new File(inputFileBasePath, "dummy");
        Deencapsulation.setField(sut, "inputFileBasePath", inputFileBasePath);
        final BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
        br.write("1,なまえ1\r\n");
        br.close();

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = CsvFile.class;
            mockFileToDbStepConfig.getFileName();
            result = "dummy";
        }};

        sut.open(null);
        sut.close();

        sut.readItem();
    }

    /**
     * ファイルが存在しない場合はエラーとなること
     */
    @Test(expected = FileNotFoundException.class)
    public void inputFileNotFound() throws Exception {

        final File inputFileBasePath = new File("notfound");
        Deencapsulation.setField(sut, "inputFileBasePath", inputFileBasePath);
        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockFileToDbStepConfig.getBean();
            result = CsvFile.class;
            mockFileToDbStepConfig.getFileName();
            result = "dummy";
        }};

        // ファイルが存在しないので、ここで例外が発生する。
        sut.open(null);
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