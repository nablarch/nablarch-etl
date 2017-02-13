package nablarch.etl;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import nablarch.common.databind.csv.Csv;
import nablarch.core.repository.SystemRepository;
import nablarch.etl.config.DbToFileStepConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link FileItemWriter}のテストクラス。
 */
public class FileItemWriterTest {

    /** テスト対象 */
    FileItemWriter sut;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mocked
    private JobContext mockJobContext;

    @Mocked
    private StepContext mockStepContext;

    @Mocked
    private DbToFileStepConfig mockDbToFileStepConfig;

    @Before
    public void setUp() {

        sut = new FileItemWriter();

        // -------------------------------------------------- setup objects that is injected
        new NonStrictExpectations() {{
            mockStepContext.getStepName();
            result = "test-step";
            mockJobContext.getJobName();
            result = "test-job";
        }};
        Deencapsulation.setField(sut, "jobContext", mockJobContext);
        Deencapsulation.setField(sut, "stepContext", mockStepContext);
        Deencapsulation.setField(sut, "stepConfig", mockDbToFileStepConfig);
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

        // bean

        new Expectations() {{
            mockDbToFileStepConfig.getBean();
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
            mockDbToFileStepConfig.getBean();
            result = EtlFileItemWriterBean.class;
            mockDbToFileStepConfig.getFileName();
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
     * 対象レコードが1件の場合に読み込めること
     */
    @Test
    public void testWriteSingleRecord() throws Exception {

        final File outputFileBasePath = folder.newFolder();
        final File output = new File(outputFileBasePath, "dummy");
        Deencapsulation.setField(sut, "outputFileBasePath", outputFileBasePath);

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToFileStepConfig.getBean();
            result = EtlFileItemWriterBean.class;
            mockDbToFileStepConfig.getFileName();
            result = "dummy";
        }};

        List<Object> dbData = Arrays.<Object>asList(
                  EtlFileItemWriterBean.create("10001", 10000)
        );

        sut.open(null);
        sut.writeItems(dbData);
        sut.close();

        assertThat("データ（1件）がファイル出力されること",
                readFile(new BufferedReader(new FileReader(output))),
                is("FIELD-NAME1,FIELD-NAME2\r\n"
                        + "10001,10000\r\n"));
    }

    /**
     * 対象レコードが複数件の場合に書き込めること
     */
    @Test
    public void testWriteMultiRecords() throws Exception {

        final File outputFileBasePath = folder.newFolder();
        final File output = new File(outputFileBasePath, "dummy");
        Deencapsulation.setField(sut, "outputFileBasePath", outputFileBasePath);

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToFileStepConfig.getBean();
            result = EtlFileItemWriterBean.class;
            mockDbToFileStepConfig.getFileName();
            result = "dummy";
        }};

        List<Object> dbData = Arrays.<Object>asList(
                  EtlFileItemWriterBean.create("10001", 10000)
                , EtlFileItemWriterBean.create("10002", 20000)
                , EtlFileItemWriterBean.create("10003", 30000)
        );

        sut.open(null);
        sut.writeItems(dbData);
        sut.close();

        assertThat("データ（複数件）がファイル出力されること",
                readFile(new BufferedReader(new FileReader(output))),
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
    @Test(expected = RuntimeException.class)
    public void testClose() throws Exception {

        final File outputFileBasePath = folder.newFolder();
        Deencapsulation.setField(sut, "outputFileBasePath", outputFileBasePath);

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToFileStepConfig.getBean();
            result = EtlFileItemWriterBean.class;
            mockDbToFileStepConfig.getFileName();
            result = "dummy";
        }};

        List<Object> dbData = Arrays.<Object>asList(
                EtlFileItemWriterBean.create("10001", 10000)
        );

        sut.open(null);
        sut.close();

        sut.writeItems(dbData);
    }

    /**
     * クローズするものがない状態でクローズ処理を呼んだ場合でも例外が出力されないこと
     */
    @Test
    public void testNotNeedToClose() throws Exception {
        // クローズ対象のmapperをつくるopen処理は呼ばない

        try{
            sut.close();
        }catch(NullPointerException e){
            fail("例外は発生しないのでここにはこない");
        }
    }

    /**
     * ファイルに書き込み権限がない場合はエラーとなること
     */
    @Test(expected = FileNotFoundException.class)
    public void testOutputFileCanNotWrite() throws Exception {
        final File outputFileBasePath = folder.newFolder();
        final File output = new File(outputFileBasePath, "dummy");
        output.createNewFile();
        Deencapsulation.setField(sut, "outputFileBasePath", outputFileBasePath);

        // -------------------------------------------------- setup objects that is injected
        new Expectations() {{
            mockDbToFileStepConfig.getBean();
            result = EtlFileItemWriterBean.class;
            mockDbToFileStepConfig.getFileName();
            result = "dummy";
        }};

        // ファイルを読み取り専用にする
        output.setReadOnly();

        // ここで例外が発生する
        sut.open(null);
    }

    /**
     * テストで出力されたファイルを読み込む。
     *
     * @param reader リソース
     * @return 読み込んだ結果
     */
    private String readFile(Reader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        int read;
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