package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import mockit.Expectations;
import mockit.Mocked;
import org.hamcrest.Matchers;
import org.junit.Test;

/**
 * {@link JobConfig}のテスト。
 */
public class JobConfigTest {

    @Mocked
    private StepConfig stepConfig;

    private final JobConfig sut = new JobConfig();

    /**
     * 初期化時に{@link StepConfig#initialize()}が呼ばれていること。
     */
    @Test
    public void testInitialize() throws Exception {
        Map<String, StepConfig> steps = new HashMap<String, StepConfig>();
        steps.put("hoge", stepConfig);
        sut.setSteps(steps);

        new Expectations(){{
            stepConfig.initialize();
        }};
        sut.initialize();
    }

    /**
     * ジョブIDが設定されていること。
     */
    @Test
    public void testJobId() throws Exception {
        sut.setJobId("hoge");

        assertThat(sut.getJobId(), is("hoge"));
    }

    /**
     * {@link StepConfig}が設定されていること。
     */
    @Test
    public void testSteps() throws Exception {
        Map<String, StepConfig> steps = new HashMap<String, StepConfig>();
        steps.put("hoge", stepConfig);
        sut.setSteps(steps);

        assertThat(sut.getSteps(), Matchers.hasEntry("hoge", stepConfig));
    }
}
