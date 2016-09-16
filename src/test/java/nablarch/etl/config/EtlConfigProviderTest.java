package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;

import mockit.Deencapsulation;
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
     */
    @Test
    public void testNormal() {

        RootConfig config = EtlConfigProvider.getConfig();

        // ジョブ設定でファイルパスをオーバーライドしてない場合

        FileToDbStepConfig job1Step1 = config.getStepConfig("job1", "step1");

        assertThat(job1Step1, is(notNullValue()));
        assertThat(job1Step1.getStepId(), is("step1"));
        assertThat(job1Step1.getBean().getName(), is(TestDto.class.getName()));
        assertThat(job1Step1.getFile(), is(new File("base/input/test-input.csv")));
        assertThat(job1Step1.getSqlLoaderControlFileBasePath(), is(new File("base/control")));

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
        assertThat(job1Step3.getFile(), is(new File("base/output/test-output.csv")));

        // ジョブ設定でファイルパスをオーバーライドした場合

        FileToDbStepConfig overrideStep1 = config.getStepConfig("override-base-path", "step1");

        assertThat(overrideStep1, is(notNullValue()));
        assertThat(overrideStep1.getFile(), is(new File("override/input/test-input.csv")));
        assertThat(overrideStep1.getSqlLoaderControlFileBasePath(), is(new File("override/control")));

        DbToFileStepConfig overrideStep3 = config.getStepConfig("override-base-path", "step3");

        assertThat(overrideStep3, is(notNullValue()));
        assertThat(overrideStep3.getFile(), is(new File("override/output/test-output.csv")));
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
