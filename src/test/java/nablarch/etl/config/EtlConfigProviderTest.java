package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import mockit.Deencapsulation;
import nablarch.core.repository.SystemRepository;
import nablarch.etl.config.app.TestDto;
import nablarch.etl.config.app.TestDto2;
import nablarch.etl.config.app.TestDto3;
import nablarch.test.support.SystemRepositoryResource;

import org.junit.Rule;
import org.junit.Test;

import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import java.util.Properties;

/**
 * {@link EtlConfigProvider}のテスト
 */
public class EtlConfigProviderTest {

    EtlConfigProvider sut = new EtlConfigProvider();

    @Rule
    public SystemRepositoryResource repositoryResource
        = new SystemRepositoryResource("nablarch/etl/config/sql-loader.xml");

    /**
     * 設定ファイルを読み込み、設定内容を取得できること。
     * JobContextのjobIDに紐付いたjsonファイルを読み込めること。
     */
    @Test
    public void testNormal() {

        Deencapsulation.setField(sut, "jobContext", createJobContext("root-config-test-job1"));
        JobConfig config = sut.getConfig();

        // ジョブ設定１

        FileToDbStepConfig job1Step1 = config.getStepConfig("step1");

        assertThat(job1Step1, is(notNullValue()));
        assertThat(job1Step1.getStepId(), is("step1"));
        assertThat(job1Step1.getBean().getName(), is(TestDto.class.getName()));
        assertThat(job1Step1.getFileName(), is("test-input.csv"));

        DbToDbStepConfig job1Step2 = config.getStepConfig("step2");

        assertThat(job1Step2, is(notNullValue()));
        assertThat(job1Step2.getStepId(), is("step2"));
        assertThat(job1Step2.getBean().getName(), is(TestDto2.class.getName()));
        assertThat(job1Step2.getSql(), is("SELECT PROJECT_ID2 FROM PROJECT2"));
        assertThat(job1Step2.getMergeOnColumns(), is(notNullValue()));
        assertThat(job1Step2.getMergeOnColumns().size(), is(3));
        assertThat(job1Step2.getMergeOnColumns().get(0), is("test21"));
        assertThat(job1Step2.getMergeOnColumns().get(1), is("test22"));
        assertThat(job1Step2.getMergeOnColumns().get(2), is("test23"));

        DbToFileStepConfig job1Step3 = config.getStepConfig("step3");

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

        JobConfig actual = sut.getConfig();

        assertThat(loader.count, is(1));
        assertThat(actual, is(sameInstance(loader.fixedConfig)));
    }

    public static final class CustomConfigLoader implements EtlConfigLoader {

        int count;
        JobConfig fixedConfig = new JobConfig();

        @Override
        public JobConfig load() {
            count++;
            return fixedConfig;
        }
    }

    /**
     * {@link EtlConfigProvider#getConfig()}が複数回呼ばれても、
     * 設定のロードが1度だけであること。
     */
    @Test
    public void testLoadTimes() {

        CustomConfigLoader loader = new CustomConfigLoader();
        repositoryResource.addComponent("etlConfigLoader", loader);

        sut.getConfig();
        sut.getConfig();

        assertThat(loader.count, is(1));
    }

    /**
     * {@link EtlConfigProvider#initialize()}が複数回呼ばれても、
     * 設定のロードが1度だけであること。
     */
    @Test
    public void testInitializeTimes() {

        CustomConfigLoader loader = new CustomConfigLoader();
        repositoryResource.addComponent("etlConfigLoader", loader);

        Deencapsulation.invoke(sut, "initialize");
        Deencapsulation.invoke(sut, "initialize");

        assertThat(loader.count, is(1));
    }

    private JobContext createJobContext(final String jobName) {
        return new JobContext(){
            @Override
            public String getJobName() {
                return jobName;
            }

            @Override
            public Object getTransientUserData() {
                return null;
            }

            @Override
            public void setTransientUserData(Object data) {
                // NOP
            }

            @Override
            public long getInstanceId() {
                return 0;
            }

            @Override
            public long getExecutionId() {
                return 0;
            }

            @Override
            public Properties getProperties() {
                return null;
            }

            @Override
            public BatchStatus getBatchStatus() {
                return null;
            }

            @Override
            public String getExitStatus() {
                return null;
            }

            @Override
            public void setExitStatus(String status) {
                // NOP
            }
        };
    }
}
