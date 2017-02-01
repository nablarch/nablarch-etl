package nablarch.etl.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.junit.matchers.JUnitMatchers.containsString;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;

import nablarch.etl.integration.app.InputFile1Dto;
import nablarch.etl.integration.app.InputFile2Dto;
import nablarch.etl.integration.app.OutputTable1Entity;
import nablarch.etl.integration.app.OutputTable2Entity;
import nablarch.etl.integration.app.OutputTable3Entity;
import nablarch.fw.batch.ee.initializer.RepositoryInitializer;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Deencapsulation;

/**
 * ETLの結合テスト。
 */
@RunWith(DatabaseTestRunner.class)
public class EtlIntegrationTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("db-default.xml");

    @Rule
    public FileResource fileResource = new FileResource();

    @BeforeClass
    public static void setUpClass() throws Exception {
        // setup table
        VariousDbTestHelper.createTable(InputFile1WorkTable.class);
        VariousDbTestHelper.createTable(InputFile1ErrorEntity.class);
        VariousDbTestHelper.createTable(InputFile2WorkTable.class);
        VariousDbTestHelper.createTable(InputFile2ErrorEntity.class);
        VariousDbTestHelper.createTable(InputFile3WorkTable.class);
        VariousDbTestHelper.createTable(InputFile3ErrorEntity.class);
        VariousDbTestHelper.createTable(OutputTable1Entity.class);
        VariousDbTestHelper.createTable(OutputTable2Entity.class);
        VariousDbTestHelper.createTable(OutputTable3Entity.class);
    }

    @Before
    public void setUp() throws Exception {

        // init repository
        Deencapsulation.setField(RepositoryInitializer.class, "isInitialized", false);

        VariousDbTestHelper.delete(InputFile1WorkTable.class);
        VariousDbTestHelper.delete(InputFile2WorkTable.class);
        VariousDbTestHelper.delete(InputFile3WorkTable.class);
        VariousDbTestHelper.delete(OutputTable1Entity.class);
        VariousDbTestHelper.delete(OutputTable2Entity.class);
        VariousDbTestHelper.delete(OutputTable3Entity.class);
    }

    /**
     * 1ファイル→1テーブルの処理ができること。
     */
    @Test
    public void executeSingleFileToSingleTable() throws IOException {

        // setup table(truncateステップで削除されるデータ)
        VariousDbTestHelper.setUpTable(new InputFile1WorkTable(2L, "1", "name"));

        // setup input file
        fileResource.createInputFile("inputfile1.csv",
                                     "ユーザID,名前\r\n",
                                     "1,なまえ1\r\n",
                                     "2,なまえ2\r\n",
                                     "a,なまえ3\r\n");

        // execute job
        final JobExecution execution = EtlIntegrationTest.startJob("etl-integration-test-singlefile2singletable");
        assertThat(execution.getBatchStatus(), is(BatchStatus.COMPLETED));

        // assert output table
        // バリデーションエラーとなる3行目以外が取り込まれる
        assertOutputTable1(Arrays.asList(
                new HashMap<String, String>() {{
                    put("userId", "1");
                    put("name", "なまえ1");
                }},
                new HashMap<String, String>() {{
                    put("userId", "2");
                    put("name", "なまえ2");
                }}
        ));

        final List<InputFile1ErrorEntity> errors = VariousDbTestHelper.findAll(InputFile1ErrorEntity.class);
        assertThat("エラーの1レコード登録される", errors.size(), is(1));
        assertThat("ユーザIDはa", errors.get(0).getUserId(), is("a"));

        // assert log
        OnMemoryLogWriter.assertLogContains("writer.memory", "-INFO- load progress. table name=[output_table1], write count=[2]");
    }

    /**
     * 複数ファイル→1テーブルの処理ができること。
     */
    @Test
    public void executeMultiFilesToSingleTable() throws IOException {

        // setup table(削除されるデータ)
        VariousDbTestHelper.setUpTable(new InputFile1WorkTable(3L, "1", "name"));
        VariousDbTestHelper.setUpTable(new InputFile2WorkTable(2L, "1", "2", "3"));

        // setup input file
        fileResource.createInputFile("inputfile1.csv",
                                     "ユーザID,名前\r\n",
                                     "1,なまえ1\r\n",
                                     "a,なまえ2\r\n",
                                     "3,なまえ3\r\n");
        fileResource.createInputFile("inputfile2.csv",
                "列１,列２,列３\r\n",
                "1,abcdefghij,10000\r\n",
                "2,cdefghijkl,20000\r\n",
                "b,efghijklmn,30000\r\n");

        // execute job
        final JobExecution execution = EtlIntegrationTest.startJob("etl-integration-test-multifiles2singletable");
        assertThat(execution.getBatchStatus(), is(BatchStatus.COMPLETED));

        // assert output table
        final List<InputFile1ErrorEntity> errors1 = VariousDbTestHelper.findAll(InputFile1ErrorEntity.class);
        assertThat("エラーレコード数は1", errors1.size(), is(1));

        final List<InputFile2ErrorEntity> errors2 = VariousDbTestHelper.findAll(InputFile2ErrorEntity.class);
        assertThat("エラーレコード数は1", errors2.size(), is(1));

        final List<OutputTable3Entity> output3 = VariousDbTestHelper.findAll(OutputTable3Entity.class);
        assertThat("エラーレコード以外を結合した1レコードが格納される", output3.size(), is(1));
        assertThat("IDは1", output3.get(0).getUserId(), is("1"));

        // assert log
        String[] expected = { "-INFO- clean table. table name=[output_table3], delete count=[0]",
                "-INFO- load progress. table name=[output_table3], write count=[1]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 1ファイル→複数テーブルの処理ができること。
     */
    @Test
    public void executeSingleFileToMultiTables() throws IOException {

        // setup table
        VariousDbTestHelper.setUpTable(new InputFile2WorkTable(3L, "1", "2", "3"));

        // setup input file
        fileResource.createInputFile("inputfile3.csv",
                "ユーザID,名前,列１,列２,列３\r\n",
                "11,なまえ1,abcdefghij,10000\r\n",
                "22,なまえ2,cdefghijkl,20000\r\n",
                "a3,なまえ3,efghijklmn,30000\r\n");

        // execute job
        final JobExecution execution = EtlIntegrationTest.startJob("etl-integration-test-singlefile2multitables");
        assertThat(execution.getBatchStatus(), is(BatchStatus.COMPLETED));

        final List<InputFile3ErrorEntity> errors = VariousDbTestHelper.findAll(InputFile3ErrorEntity.class);
        assertThat("エラーレコード数は1", errors.size(), is(1));

        // assert output table
        assertOutputTable1(Arrays.asList(
                new HashMap<String, String>() {{
                    put("userId", "11");
                    put("name", "なまえ1");
                }},
                new HashMap<String, String>() {{
                    put("userId", "22");
                    put("name", "なまえ2");
                }}
        ));
        assertOutputTable2(Arrays.asList(
                new HashMap<String, String>() {{
                    put("col1", "11");
                    put("col2", "abcdefghij");
                    put("col3", "10000");
                }},
                new HashMap<String, String>() {{
                    put("col1", "22");
                    put("col2", "cdefghijkl");
                    put("col3", "20000");
                }}
        ));

        // assert log
        String[] expected = { "-INFO- clean table. table name=[output_table1], delete count=[0]",
                "-INFO- load progress. table name=[output_table1], write count=[2]",
                "-INFO- clean table. table name=[output_table2], delete count=[0]",
                "-INFO- load progress. table name=[output_table2], write count=[2]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 複数ファイル→複数テーブル(chunk)の処理ができること。
     */
    @Test
    public void executeMultiFilesToMultiTablesUsingChunk() throws IOException {

        // setup input file
        fileResource.createInputFile("inputfile1.csv",
                                     "ユーザID,名前\r\n",
                                     "1,なまえ1\r\n",
                                     "2,なまえ2\r\n",
                                     "3,なまえ3\r\n");
        fileResource.createInputFile("inputfile2.csv",
                "列１,列２,列３\r\n",
                "10001,abcdefghij,10000\r\n",
                "1000q,cdefghijkl,20000\r\n",
                "10003,efghijklmn,30000\r\n");

        // execute job
        final JobExecution execution = EtlIntegrationTest.startJob("etl-integration-test-multifiles2multitables-chunk");
        assertThat(execution.getBatchStatus(), is(BatchStatus.COMPLETED));

        final List<InputFile1ErrorEntity> errors1 = VariousDbTestHelper.findAll(InputFile1ErrorEntity.class);
        assertThat("エラーレコードは存在しない", errors1.size(), is(0));

        final List<InputFile2ErrorEntity> errors2 = VariousDbTestHelper.findAll(InputFile2ErrorEntity.class);
        assertThat("エラーレコード数は1", errors2.size(), is(1));

        // assert output table
        assertOutputTable1(Arrays.asList(
                new HashMap<String, String>() {{
                    put("userId", "1");
                    put("name", "なまえ1");
                }},
                new HashMap<String, String>() {{
                    put("userId", "2");
                    put("name", "なまえ2");
                }},
                new HashMap<String, String>() {{
                    put("userId", "3");
                    put("name", "なまえ3");
                }}
        ));
        assertOutputTable2(Arrays.asList(
                new HashMap<String, String>() {{
                    put("col1", "10001");
                    put("col2", "abcdefghij");
                    put("col3", "10000");
                }},
                new HashMap<String, String>() {{
                    put("col1", "10003");
                    put("col2", "efghijklmn");
                    put("col3", "30000");
                }}
        ));

        // assert log
        String[] expected = { "-INFO- chunk start. table name=[output_table1]",
                "-INFO- chunk progress. write count=[3]",
                "-INFO- chunk start. table name=[output_table2]",
                "-INFO- chunk progress. write count=[2]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    /**
     * 複数ファイル→複数テーブル(batchlet)の処理ができること。
     */
    @Test
    public void executeMultiFilesToMultiTablesUsingBatchlet() throws IOException {
        assumeThat(System.getProperty("os.name").toLowerCase(), containsString("windows"));

        // setup input file
        fileResource.createInputFile("inputfile1.csv",
                                     "ユーザID,名前\r\n",
                                     "1,なまえ1\r\n",
                                     "2,なまえ2\r\n",
                                     "3,なまえ3\r\n");
        fileResource.createInputFile("inputfile2.csv",
                                     "列１,列２,列３\r\n",
                                     "10001,abcdefghij,10000\r\n",
                                     "1000a,cdefghijkl,20000\r\n",
                                     "10003,efghijklmn,30000\r\n");

        // setup control file
        fileResource.createControlFile(InputFile1Dto.class.getSimpleName(), "input_file1_table", "user_id, name");
        fileResource.createControlFile(InputFile2Dto.class.getSimpleName(), "input_file2_table", "col1, col2, col3");

        // execute job
        final JobExecution execution = EtlIntegrationTest.startJob("etl-integration-test-multifiles2multitables-batchlet");
        assertThat(execution.getBatchStatus(), is(BatchStatus.COMPLETED));

        final List<InputFile1ErrorEntity> errors1 = VariousDbTestHelper.findAll(InputFile1ErrorEntity.class);
        assertThat("エラーレコードは存在しない", errors1.size(), is(0));

        final List<InputFile2ErrorEntity> errors2 = VariousDbTestHelper.findAll(InputFile2ErrorEntity.class);
        assertThat("エラーレコード数は1", errors2.size(), is(1));

        // assert output table
        assertOutputTable1(Arrays.asList(
                new HashMap<String, String>() {{
                    put("userId", "1");
                    put("name", "なまえ1");
                }},
                new HashMap<String, String>() {{
                    put("userId", "2");
                    put("name", "なまえ2");
                }},
                new HashMap<String, String>() {{
                    put("userId", "3");
                    put("name", "なまえ3");
                }}
        ));
        assertOutputTable2(Arrays.asList(
                new HashMap<String, String>() {{
                    put("col1", "10001");
                    put("col2", "abcdefghij");
                    put("col3", "10000");
                }},
                new HashMap<String, String>() {{
                    put("col1", "10003");
                    put("col2", "efghijklmn");
                    put("col3", "30000");
                }}
        ));

        // assert log
        String[] expected = { "-INFO- clean table. table name=[output_table1], delete count=[0]",
                "-INFO- load progress. table name=[output_table1], write count=[3]",
                "-INFO- clean table. table name=[output_table2], delete count=[0]",
                "-INFO- load progress. table name=[output_table2], write count=[2]" };
        OnMemoryLogWriter.assertLogContains("writer.memory", expected);
    }

    private void assertOutputTable1(final List<HashMap<String, String>> expectedRowsOrderByUserId) {

        final List<OutputTable1Entity> result = VariousDbTestHelper.findAll(OutputTable1Entity.class, "userId");
        assertThat(result.size(), is(expectedRowsOrderByUserId.size()));

        for (int i = 0; i < expectedRowsOrderByUserId.size(); i++) {
            final OutputTable1Entity row = result.get(i);
            assertThat(row.getUserId(), is(expectedRowsOrderByUserId.get(i).get("userId")));
            assertThat(row.getName(), is(expectedRowsOrderByUserId.get(i).get("name")));
        }
    }

    private void assertOutputTable2(final List<HashMap<String, String>> expectedRowsOrderByCol1) {

        final List<OutputTable2Entity> result = VariousDbTestHelper.findAll(OutputTable2Entity.class, "col1");
        assertThat(result.size(), is(expectedRowsOrderByCol1.size()));

        for (int i = 0; i < expectedRowsOrderByCol1.size(); i++) {
            final OutputTable2Entity row = result.get(i);
            assertThat(row.getCol1(), is(expectedRowsOrderByCol1.get(i).get("col1")));
            assertThat(row.getCol2(), is(expectedRowsOrderByCol1.get(i).get("col2")));
            assertThat(row.getCol3(), is(Integer.valueOf(expectedRowsOrderByCol1.get(i).get("col3"))));
        }
    }

    public static JobExecution startJob(final String jobName) {
        final JobOperator operator = BatchRuntime.getJobOperator();
        final long executionId = operator.start(jobName, null);
        final JobExecution execution = operator.getJobExecution(executionId);
        while (true) {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (execution.getEndTime() != null) {
                break;
            }
        }
        return execution;
    }
}
