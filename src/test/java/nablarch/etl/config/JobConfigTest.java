package nablarch.etl.config;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import mockit.Expectations;
import mockit.Mocked;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * {@link JobConfig}のテスト。
 */
public class JobConfigTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
     * {@link StepConfig}が設定されていること。
     */
    @Test
    public void testSteps() throws Exception {
        Map<String, StepConfig> steps = new HashMap<String, StepConfig>();
        steps.put("hoge", stepConfig);
        sut.setSteps(steps);

        assertThat(sut.getSteps(), Matchers.hasEntry("hoge", stepConfig));
    }

    /**
     * 設定した{@link StepConfig}が取れること。
     */
    @Test
    public void testGetStepConfig() throws Exception {
        Map<String, StepConfig> steps = new HashMap<String, StepConfig>();
        steps.put("hoge", stepConfig);
        sut.setSteps(steps);

        assertThat(sut.getStepConfig("sample", "hoge"), is(stepConfig));
    }

    /**
     * 設定した{@link StepConfig}が{@code null}だった場合、エラーを送出すること。
     */
    @Test
    public void testGetStepConfigNullError() throws Exception {
        Map<String, StepConfig> steps = new HashMap<String, StepConfig>();
        steps.put("hoge", null);
        sut.setSteps(steps);

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("step configuration was not found. jobId = [sample], stepId = [hoge]");
        sut.getStepConfig("sample", "hoge");
    }

    /**
     * 設定していない{@link StepConfig}を取得しようとした場合、エラーを送出すること。
     */
    @Test
    public void testGetStepConfigNotSettingError() throws Exception {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("step configuration was not found. jobId = [sample], stepId = [hoge]");

        sut.getStepConfig("sample", "hoge");
    }
}
