package nablarch.etl.config;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.batch.runtime.context.JobContext;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link JsonConfigLoader}のテスト。
 */
public class JsonConfigLoaderTest {

    private JsonConfigLoader sut = new JsonConfigLoader();

    @Mocked
    JobContext mockJobContext;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * META-INF/batch-config/(JOB_ID).jsonの設定ファイルが読み込まれること。
     */
    @Test
    public void testDefaultPath() throws Exception {
        new Expectations() {{
            mockJobContext.getJobName();
            result = "root-config-test-job1";
        }};

        JobConfig config = sut.load(mockJobContext);

        assertThat(config.getSteps().get("step1"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step2"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step3"), is(instanceOf(StepConfig.class)));
    }

    /**
     * ファイルのフォーマットが間違っている場合、例外が送出されること。
     */
    @Test
    public void testLoadingFailedJsonFileFormatError() {
        new Expectations(){{
            mockJobContext.getJobName();
            result = "etl-error";
        }};

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("failed to load etl config file. file = [META-INF/batch-config/etl-error.json]");

        sut.load(mockJobContext);
    }

    /**
     * ファイルが見つからない場合、例外が送出されること。
     */
    @Test
    public void testLoadingFailedNotFoundJsonFile() throws Exception {
        new Expectations(){{
            mockJobContext.getJobName();
            result = "unknown";
        }};

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("failed to load etl config file. file = [META-INF/batch-config/unknown.json]");

        sut.load(mockJobContext);
    }

    /**
     * 共通項目の設定がjsonファイルにあると例外を送出すること。
     */
    @Test
    public void testLoadingCommonSetting() throws Exception {
        new Expectations(){{
            mockJobContext.getJobName();
            result = "etl-error-common-setting";
        }};

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("failed to load etl config file. file = [META-INF/batch-config/etl-error-common-setting.json]");

        sut.load(mockJobContext);
    }
}
