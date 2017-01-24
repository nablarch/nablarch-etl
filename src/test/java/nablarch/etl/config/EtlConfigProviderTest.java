package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import mockit.Deencapsulation;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.etl.config.app.TestDto;
import nablarch.etl.config.app.TestDto2;
import nablarch.etl.config.app.TestDto3;
import nablarch.test.support.SystemRepositoryResource;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * {@link EtlConfigProvider}のテスト
 */
public class EtlConfigProviderTest {

    @Rule
    public SystemRepositoryResource repositoryResource
        = new SystemRepositoryResource("nablarch/etl/config/sql-loader.xml");

    @Before
    @After
    public void eraseConfig() {
        EtlConfigProvider.isInitialized = false;
    }

    /**
     * 設定ファイルを読み込み、設定内容を取得できること。
     * ジョブ設定が複数ある場合でも読み込めること。
     */
    @Test
    public void testNormal() {

        RootConfig config = EtlConfigProvider.getConfig();

        // ジョブ設定１

        FileToDbStepConfig job1Step1 = config.getStepConfig("job1", "step1");

        assertThat(job1Step1, is(notNullValue()));
        assertThat(job1Step1.getStepId(), is("step1"));
        assertThat(job1Step1.getBean().getName(), is(TestDto.class.getName()));
        assertThat(job1Step1.getFileName(), is("test-input.csv"));

        DbToDbStepConfig job1Step2 = config.getStepConfig("job1", "step2");

        assertThat(job1Step2, is(notNullValue()));
        assertThat(job1Step2.getStepId(), is("step2"));
        assertThat(job1Step2.getBean().getName(), is(TestDto2.class.getName()));
        assertThat(job1Step2.getSql(), is("SELECT PROJECT_ID2 FROM PROJECT2"));
        assertThat(job1Step2.getMergeOnColumns(), is(notNullValue()));
        assertThat(job1Step2.getMergeOnColumns().size(), is(3));
        assertThat(job1Step2.getMergeOnColumns().get(0), is("test21"));
        assertThat(job1Step2.getMergeOnColumns().get(1), is("test22"));
        assertThat(job1Step2.getMergeOnColumns().get(2), is("test23"));

        DbToFileStepConfig job1Step3 = config.getStepConfig("job1", "step3");

        assertThat(job1Step3, is(notNullValue()));
        assertThat(job1Step3.getStepId(), is("step3"));
        assertThat(job1Step3.getBean().getName(), is(TestDto3.class.getName()));
        assertThat(job1Step3.getFileName(), is("test-output.csv"));

        // ジョブ設定２

        FileToDbStepConfig job2Step1 = config.getStepConfig("job2", "step1");

        assertThat(job2Step1, is(notNullValue()));
        assertThat(job2Step1.getFileName(), is("test-input.csv"));

        DbToFileStepConfig job2Step3 = config.getStepConfig("job2", "step3");

        assertThat(job2Step3, is(notNullValue()));
        assertThat(job2Step3.getFileName(), is("test-output.csv"));

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

        RootConfig actual = EtlConfigProvider.getConfig();

        assertThat(loader.count, is(1));
        assertThat(actual, is(sameInstance(loader.fixedConfig)));
    }

    public static final class CustomConfigLoader implements EtlConfigLoader {

        int count;
        RootConfig fixedConfig = new RootConfig();

        @Override
        public RootConfig load() {
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

        EtlConfigProvider.getConfig();
        EtlConfigProvider.getConfig();

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

        Deencapsulation.invoke(EtlConfigProvider.class, "initialize");
        Deencapsulation.invoke(EtlConfigProvider.class, "initialize");

        assertThat(loader.count, is(1));
    }
}
