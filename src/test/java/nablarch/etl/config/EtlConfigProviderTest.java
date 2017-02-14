package nablarch.etl.config;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.repository.SystemRepository;
import nablarch.etl.config.app.TestDto;
import nablarch.etl.config.app.TestDto2;
import nablarch.etl.config.app.TestDto3;
import nablarch.test.support.SystemRepositoryResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * {@link EtlConfigProvider}のテスト
 */
public class EtlConfigProviderTest {

    @Mocked
    JobContext mockJobContext;

    @Mocked
    StepContext mockStepContext;

    @Rule
    public SystemRepositoryResource repositoryResource
            = new SystemRepositoryResource("nablarch/etl/config/sql-loader.xml");

    /**
     * テスト毎にキャッシュをクリアする。
     */
    @Before
    public void setUp() throws Exception {
        Deencapsulation.setField(EtlConfigProvider.class, "LOADED_ETL_CONFIG", new ConcurrentHashMap<String, JobConfig>());
    }

    /**
     * 設定ファイルを読み込み、設定内容を取得できること。
     * JobContextのjobIDに紐付いたjsonファイルを読み込めること。
     */
    @Test
    public void testNormal() {

        new Expectations() {{
            mockJobContext.getJobName();
            result = "root-config-test-job1";
            mockStepContext.getStepName();
            result = "step1";
            result = "step2";
            result = "step3";
        }};

        // ジョブ設定１

        FileToDbStepConfig job1Step1 = (FileToDbStepConfig) EtlConfigProvider.getConfig(mockJobContext, mockStepContext);

        assertThat(job1Step1, is(notNullValue()));
        assertThat(job1Step1.getStepId(), is("step1"));
        assertThat(job1Step1.getBean().getName(), is(TestDto.class.getName()));
        assertThat(job1Step1.getFileName(), is("test-input.csv"));

        DbToDbStepConfig job1Step2 = (DbToDbStepConfig) EtlConfigProvider.getConfig(mockJobContext, mockStepContext);

        assertThat(job1Step2, is(notNullValue()));
        assertThat(job1Step2.getStepId(), is("step2"));
        assertThat(job1Step2.getBean().getName(), is(TestDto2.class.getName()));
        assertThat(job1Step2.getSql(), is("SELECT PROJECT_ID2 FROM PROJECT2"));
        assertThat(job1Step2.getMergeOnColumns(), is(notNullValue()));
        assertThat(job1Step2.getMergeOnColumns().size(), is(3));
        assertThat(job1Step2.getMergeOnColumns().get(0), is("test21"));
        assertThat(job1Step2.getMergeOnColumns().get(1), is("test22"));
        assertThat(job1Step2.getMergeOnColumns().get(2), is("test23"));

        DbToFileStepConfig job1Step3 = (DbToFileStepConfig) EtlConfigProvider.getConfig(mockJobContext, mockStepContext);
        ;

        assertThat(job1Step3, is(notNullValue()));
        assertThat(job1Step3.getStepId(), is("step3"));
        assertThat(job1Step3.getBean().getName(), is(TestDto3.class.getName()));
        assertThat(job1Step3.getFileName(), is("test-output.csv"));

        SystemRepository.clear();
    }

    /**
     * {@link EtlConfigLoader}をリポジトリに登録することで、
     * {@link EtlConfigLoader}を差し替えられること。
     */
    @Test
    public void testCustomConfigLoader() {

        CustomConfigLoader loader = new CustomConfigLoader();
        repositoryResource.addComponent("etlConfigLoader", loader);

        new Expectations() {{
            mockJobContext.getJobName();
            result = "jobName";
            mockStepContext.getStepName();
            result = "step";
        }};

        StepConfig actual = EtlConfigProvider.getConfig(mockJobContext, mockStepContext);

        assertThat(loader.count, is(1));
    }

    public static final class CustomConfigLoader implements EtlConfigLoader {

        int count;

        @Override
        public JobConfig load(JobContext context) {
            count++;
            JobConfig jobConfig = new JobConfig();
            jobConfig.setSteps(new HashMap<String, StepConfig>() {{
                put("step", new StepConfig() {
                    @Override
                    protected void onInitialize() {
                        // nop
                    }
                });
            }});
            return jobConfig;
        }
    }

    /**
     * {@link EtlConfigProvider#getConfig(JobContext jobContext, StepContext stepContext)}が複数回呼ばれても、
     * 設定のロードが1度だけであること。
     */
    @Test
    public void testLoadTimes() {

        CustomConfigLoader loader = new CustomConfigLoader();
        repositoryResource.addComponent("etlConfigLoader", loader);

        new Expectations() {{
            mockJobContext.getJobName();
            result = "jobName";
            mockStepContext.getStepName();
            result = "step";
        }};

        EtlConfigProvider.getConfig(mockJobContext, mockStepContext);
        EtlConfigProvider.getConfig(mockJobContext, mockStepContext);

        assertThat(loader.count, is(1));
    }

    /**
     * 複数ジョブを複数回ロードした場合でも、ジョブ毎に１回のロードだけ行われること。
     */
    @Test
    public void testLoadMultiJobs() throws Exception {
        CustomConfigLoader loader = new CustomConfigLoader();
        repositoryResource.addComponent("etlConfigLoader", loader);

        new Expectations() {{
            mockJobContext.getJobName();
            result = "root-config-test-job1";
            result = "job1";
            result = "root-config-test-job1";
            result = "job1";
            mockStepContext.getStepName();
            result = "step";
        }};

        EtlConfigProvider.getConfig(mockJobContext, mockStepContext);
        EtlConfigProvider.getConfig(mockJobContext, mockStepContext);
        EtlConfigProvider.getConfig(mockJobContext, mockStepContext);
        EtlConfigProvider.getConfig(mockJobContext, mockStepContext);

        assertThat(loader.count, is(2));
    }

    /**
     * {@link EtlConfigProvider#initialize(JobContext jobContext)}が複数回呼ばれても、
     * 設定のロードが1度だけであること。
     */
    @Test
    public void testInitializeTimes() {

        CustomConfigLoader loader = new CustomConfigLoader();
        repositoryResource.addComponent("etlConfigLoader", loader);

        new Expectations() {{
            mockJobContext.getJobName();
            result = "jobName";
        }};

        Deencapsulation.invoke(EtlConfigProvider.class, "initialize", mockJobContext);
        Deencapsulation.invoke(EtlConfigProvider.class, "initialize", mockJobContext);

        assertThat(loader.count, is(1));
    }
}