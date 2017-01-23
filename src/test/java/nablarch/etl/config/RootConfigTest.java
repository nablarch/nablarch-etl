package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link RootConfig}のテスト。
 */
public class RootConfigTest {

    @Before
    public void setUp() {
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader("nablarch/etl/config/config-initialize.xml");
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);
    }

    @After
    public void tearDown() {
        SystemRepository.clear();
    }

    /**
     * 初期化呼び出しで、ステップの設定が初期化されること。
     * さらに、ジョブID/ステップIDを指定してステップの設定が取得でき、
     * 存在しない場合は例外が送出されること。
     */
    @Test
    @SuppressWarnings("serial")
    public void testInitializationAndStepConfigGetting() {

        final NopFileToDbStepConfig fileToDb1 = new NopFileToDbStepConfig() {{ setStepId("step1"); }};
        final NopFileToDbStepConfig fileToDb2 = new NopFileToDbStepConfig() {{ setStepId("step2"); }};
        final NopFileToDbStepConfig fileToDb3 = new NopFileToDbStepConfig() {{ setStepId("step3"); }};
        final NopDbToDbStepConfig dbToDb4 = new NopDbToDbStepConfig() {{ setStepId("step4"); }};
        final NopDbToDbStepConfig dbToDb5 = new NopDbToDbStepConfig() {{ setStepId("step5"); }};
        final NopDbToFileStepConfig dbToFile6 = new NopDbToFileStepConfig() {{ setStepId("step6"); }};
        final Map<String, StepConfig> steps = new HashMap<String, StepConfig>() {{
            for (StepConfig stepConfig
                    : Arrays.asList(fileToDb1, fileToDb2, fileToDb3, dbToDb4, dbToDb5, dbToFile6)) {
                put(stepConfig.getStepId(), stepConfig);
            }
        }};
        final Map<String, JobConfig> jobs = new HashMap<String, JobConfig>() {{
            JobConfig jobConfig = new JobConfig() {{
                setJobId("job1");
                setSteps(steps);
            }};
            put(jobConfig.getJobId(), jobConfig);
        }};

        RootConfig sut = new RootConfig() {{
            setJobs(jobs);
        }};
        sut.initialize();

        assertThat(fileToDb1.isInitialized, is(true));
        assertThat(fileToDb2.isInitialized, is(true));
        assertThat(fileToDb3.isInitialized, is(true));
        assertThat(dbToDb4.isInitialized, is(true));
        assertThat(dbToDb5.isInitialized, is(true));
        assertThat(dbToFile6.isInitialized, is(true));

        assertThat(sut.getStepConfig("job1", "step1"), is(sameInstance((StepConfig) fileToDb1)));
        assertThat(sut.getStepConfig("job1", "step2"), is(sameInstance((StepConfig) fileToDb2)));
        assertThat(sut.getStepConfig("job1", "step3"), is(sameInstance((StepConfig) fileToDb3)));
        assertThat(sut.getStepConfig("job1", "step4"), is(sameInstance((StepConfig) dbToDb4)));
        assertThat(sut.getStepConfig("job1", "step5"), is(sameInstance((StepConfig) dbToDb5)));
        assertThat(sut.getStepConfig("job1", "step6"), is(sameInstance((StepConfig) dbToFile6)));

        try {
            sut.getStepConfig("job2", "step1"); // 存在しないジョブID
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("job configuration was not found. jobId = [job2]"));
        }
        try {
            sut.getStepConfig("job1", "step7"); // 存在しないステップID
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("step configuration was not found. jobId = [job1], stepId = [step7]"));
        }
    }

    /**
     * 初期化呼び出しで、ステップの設定が初期化されること。
     */
    @Test
    @SuppressWarnings("serial")
    public void testGetStepConfig() {

        final NopFileToDbStepConfig fileToDb1 = new NopFileToDbStepConfig() {{ setStepId("step1"); }};
        final NopFileToDbStepConfig fileToDb2 = new NopFileToDbStepConfig() {{ setStepId("step2"); }};
        final NopFileToDbStepConfig fileToDb3 = new NopFileToDbStepConfig() {{ setStepId("step3"); }};
        final NopDbToDbStepConfig dbToDb4 = new NopDbToDbStepConfig() {{ setStepId("step4"); }};
        final NopDbToDbStepConfig dbToDb5 = new NopDbToDbStepConfig() {{ setStepId("step5"); }};
        final NopDbToFileStepConfig dbToFile6 = new NopDbToFileStepConfig() {{ setStepId("step6"); }};
        final Map<String, StepConfig> steps = new HashMap<String, StepConfig>() {{
            for (StepConfig stepConfig
                    : Arrays.asList(fileToDb1, fileToDb2, fileToDb3, dbToDb4, dbToDb5, dbToFile6)) {
                put(stepConfig.getStepId(), stepConfig);
            }
        }};
        final Map<String, JobConfig> jobs = new HashMap<String, JobConfig>() {{
            JobConfig jobConfig = new JobConfig() {{
                setJobId("job1");
                setSteps(steps);
            }};
            put(jobConfig.getJobId(), jobConfig);
        }};

        RootConfig sut = new RootConfig() {{
            setJobs(jobs);
        }};
        sut.initialize();

        assertThat(sut.getStepConfig("job1", "step1"), is(sameInstance((StepConfig) fileToDb1)));
        assertThat(sut.getStepConfig("job1", "step2"), is(sameInstance((StepConfig) fileToDb2)));
        assertThat(sut.getStepConfig("job1", "step3"), is(sameInstance((StepConfig) fileToDb3)));
        assertThat(sut.getStepConfig("job1", "step4"), is(sameInstance((StepConfig) dbToDb4)));
        assertThat(sut.getStepConfig("job1", "step5"), is(sameInstance((StepConfig) dbToDb5)));
        assertThat(sut.getStepConfig("job1", "step6"), is(sameInstance((StepConfig) dbToFile6)));
    }

    private static class NopFileToDbStepConfig extends FileToDbStepConfig {
        private boolean isInitialized;
        @Override
        protected void onInitialize() {
            isInitialized = true;
        }
    }

    private static class NopDbToDbStepConfig extends DbToDbStepConfig {
        private boolean isInitialized;
        @Override
        protected void onInitialize() {
            isInitialized = true;
        }
    }

    private static class NopDbToFileStepConfig extends DbToFileStepConfig {
        private boolean isInitialized;
        @Override
        protected void onInitialize() {
            isInitialized = true;
        }
    }
}
