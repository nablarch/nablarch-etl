package nablarch.etl.config;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

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

    /**
     * 指定されたパスに配置された設定ファイルがロードできること。
     */
    @Test
    public void testSpecifiedPath() {

        sut.setConfigPath("nablarch/etl/config/etl-normal.json");

        JobConfig config = sut.load(mockJobContext);

        assertThat(config.getSteps().get("step1"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step2"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step3"), is(instanceOf(StepConfig.class)));
    }

    /**
     * ロードに失敗した場合、例外が送出されること。
     */
    @Test
    public void testLoadingFailed() {

        // 存在しないパス
        sut.setConfigPath("/hoge/unknown.json");

        try {
            sut.load(mockJobContext);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("failed to load etl config file. file = [/hoge/unknown.json]"));
        }

        // フォーマットエラー
        sut = new JsonConfigLoader();
        sut.setConfigPath("nablarch/etl/config/etl-error.json");

        try {
            sut.load(mockJobContext);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("failed to load etl config file. file = [nablarch/etl/config/etl-error.json]"));
        }
    }

    /**
     * 共通項目の設定がjsonファイルにあると例外を送出すること。
     */
    @Test
    public void testLoadingCommonSetting() throws Exception {
        sut.setConfigPath("nablarch/etl/config/etl-error-common-setting.json");

        try {
            sut.load(mockJobContext);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("failed to load etl config file. file = [nablarch/etl/config/etl-error-common-setting.json]"));
        }
    }

    /**
     * パスを指定しない場合、
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
}
