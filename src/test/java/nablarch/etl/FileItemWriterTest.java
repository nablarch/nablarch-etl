package nablarch.etl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.batch.operations.BatchRuntimeException;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;

import nablarch.common.databind.csv.Csv;
import nablarch.core.repository.SystemRepository;
import nablarch.etl.config.DbToFileStepConfig;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.Expectations;

/**
 * {@link FileItemWriter}のテストクラス。
 */
public class FileItemWriterTest {

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
        OnMemoryLogWriter.clear();

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            minTimes = 0;
            mockJobContext.getJobName();
            result = "test-job";
            minTimes = 0;
        }};
    }

    @After
    public void tearDown() throws Exception {
        SystemRepository.clear();
    }

    @Test
    public void beanIsNull_shouldThrowException() throws Exception {

        final DbToFileStepConfig stepConfig = new DbToFileStepConfig();
        stepConfig.setBean(null);
        final FileItemWriter sut = new FileItemWriter(mockJobContext, mockStepContext, stepConfig,
                temporaryFolder.getRoot());

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("bean is required. jobId = [test-job], stepId = [test-step]");
        sut.open(null);

    }

    @Test
    public void fileNameIsNull_shouldThrowException() throws Exception {
        final DbToFileStepConfig stepConfig = new DbToFileStepConfig();
        stepConfig.setBean(EtlFileItemWriterBean.class);
        stepConfig.setFileName(null);
        final FileItemWriter sut = new FileItemWriter(mockJobContext, mockStepContext, stepConfig,
                temporaryFolder.getRoot());

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("fileName is required. jobId = [test-job], stepId = [test-step]");
        sut.open(null);
    }

    /**
     * 対象レコードが1件の場合に出力できること
     */
    @Test
    public void testWriteSingleRecord() throws Exception {

        final File outputFileBasePath = temporaryFolder.newFolder();
        final File output = new File(outputFileBasePath, "dummy");

        // -------------------------------------------------- setup objects that is injected
        final DbToFileStepConfig stepConfig = new DbToFileStepConfig();
        stepConfig.setBean(EtlFileItemWriterBean.class);
        stepConfig.setFileName("dummy");

        final FileItemWriter sut = new FileItemWriter(
                mockJobContext,
                mockStepContext,
                stepConfig,
                outputFileBasePath
        );

        List<Object> dbData = Collections.<Object>singletonList(EtlFileItemWriterBean.create("10001", 10000));

        sut.open(null);
        sut.writeItems(dbData);
        sut.close();

        assertThat("データ（1件）がファイル出力されること",
                readFile(output), is("FIELD-NAME1,FIELD-NAME2\r\n10001,10000\r\n"));
    }

    /**
     * 対象レコードが複数件の場合に書き込めること
     */
    @Test
    public void testWriteMultiRecords() throws Exception {

        final File outputFileBasePath = temporaryFolder.newFolder();
        final File output = new File(outputFileBasePath, "dummy");

        // -------------------------------------------------- setup objects that is injected
        final DbToFileStepConfig stepConfig = new DbToFileStepConfig();
        stepConfig.setBean(EtlFileItemWriterBean.class);
        stepConfig.setFileName("dummy");

        final FileItemWriter sut = new FileItemWriter(
                mockJobContext,
                mockStepContext,
                stepConfig,
                outputFileBasePath
        );


        List<Object> dbData = Arrays.<Object>asList(
                EtlFileItemWriterBean.create("10001", 10000),
                EtlFileItemWriterBean.create("10002", 20000),
                EtlFileItemWriterBean.create("10003", 30000)
        );

        sut.open(null);
        sut.writeItems(dbData);
        sut.close();

        assertThat("データ（複数件）がファイル出力されること",
                readFile(output),
                is("FIELD-NAME1,FIELD-NAME2\r\n"
                        + "10001,10000\r\n"
                        + "10002,20000\r\n"
                        + "10003,30000\r\n"));
    }

    /**
     * クローズを呼び出すことでファイルが閉じられること
     * <p/>
     * close -> writeの順に呼び出し、ファイルが閉じられている例外が発生することで確認する。
     */
    @Test
    public void testClose() throws Exception {

        final File outputFileBasePath = temporaryFolder.newFolder();

        // -------------------------------------------------- setup objects that is injected
        final DbToFileStepConfig stepConfig = new DbToFileStepConfig();
        stepConfig.setBean(EtlFileItemWriterBean.class);
        stepConfig.setFileName("dummy");

        final FileItemWriter sut = new FileItemWriter(
                mockJobContext,
                mockStepContext,
                stepConfig,
                outputFileBasePath
        );
        Deencapsulation.setField(sut, "outputFileBasePath", outputFileBasePath);

        sut.open(null);
        sut.close();

        expectedException.expect(RuntimeException.class);
        sut.writeItems(Collections.<Object>singletonList(EtlFileItemWriterBean.create("10001", 10000)));
    }

    /**
     * クローズするものがない状態でクローズ処理を呼んだ場合でも例外が出力されないこと
     */
    @Test
    public void testNotNeedToClose() throws Exception {
        final FileItemWriter sut = new FileItemWriter(
                mockJobContext, mockStepContext, new DbToFileStepConfig(), temporaryFolder.getRoot());
        // ここで例外は発生しないはずなのでアサートは特にしない。
        sut.close();
    }

    /**
     * ファイルに書き込み権限がない場合はオペレータ通知ログが出力されること
     */
    @Test
    public void testOutputFileCanNotWrite() throws Exception {
        final File outputFileBasePath = temporaryFolder.newFolder();
        final File output = new File(outputFileBasePath, "dummy");
        output.createNewFile();

        // -------------------------------------------------- setup objects that is injected
        final DbToFileStepConfig stepConfig = new DbToFileStepConfig();
        stepConfig.setBean(EtlFileItemWriterBean.class);
        stepConfig.setFileName("dummy");
        final FileItemWriter sut = new FileItemWriter(
                mockJobContext, mockStepContext, stepConfig, outputFileBasePath);

        // ファイルを読み取り専用にする
        output.setReadOnly();

        // ここで例外が発生する
        try {
            sut.open(null);
            fail();
        } catch (BatchRuntimeException e) {
            final String message = "出力ファイルパスが正しくありません。ディレクトリが存在しているか、権限が正しいかを確認してください。出力ファイルパス=[" + output.getAbsolutePath() + ']';
            assertThat(OnMemoryLogWriter.getMessages("writer.operator")
                                        .get(0), containsString("-ERROR- " + message));
            assertThat(e.getMessage(), is(message));
        }
    }

    /**
     * テストで出力されたファイルを読み込む。
     *
     * @param file file
     * @return 読み込んだ結果
     */
    private String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        int read;
        final BufferedReader reader = new BufferedReader(new FileReader(file));
        while ((read = reader.read()) != -1) {
            sb.append((char) read);
        }
        reader.close();
        return sb.toString();
    }

    /** テスト用のbean */
    @Csv(type = Csv.CsvType.DEFAULT, headers = {"FIELD-NAME1", "FIELD-NAME2"}, properties = {"field1", "field2"})
    public static class EtlFileItemWriterBean {
        public String field1;

        public Integer field2;

        public String getField1() {
            return field1;
        }

        public void setField1(String field1) {
            this.field1 = field1;
        }

        public Integer getField2() {
            return field2;
        }

        public void setField2(Integer field2) {
            this.field2 = field2;
        }

        private static EtlFileItemWriterBean create(String field1, Integer field2) {
            EtlFileItemWriterBean etlFileItemWriterBean = new EtlFileItemWriterBean();
            etlFileItemWriterBean.field1 = field1;
            etlFileItemWriterBean.field2 = field2;
            return etlFileItemWriterBean;
        }
    }
}