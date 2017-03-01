package nablarch.etl.config;

import com.fasterxml.jackson.databind.JsonMappingException;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.etl.InvalidEtlConfigException;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.batch.runtime.context.JobContext;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

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
     * META-INF/etl-config/(JOB_ID).jsonの設定ファイルが読み込まれること。
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
     * 設定ファイルの配置ディレクトリ(クラスパス指定)を変更しても読み込めること。
     */
    @Test
    public void testChangeClassPath() throws Exception {
        new Expectations() {{
            mockJobContext.getJobName();
            result = "root-config-test-job1";
        }};

        sut.setConfigBasePath("classpath:META-INF/etl-test-config/");
        JobConfig config = sut.load(mockJobContext);

        assertThat(config.getSteps().get("step1"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step2"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step3"), is(instanceOf(StepConfig.class)));
    }

    /**
     * 設定ファイルの配置ディレクトリ(ファイル指定)を変更しても読み込めること。
     */
    @Test
    public void testChangeFilePath() throws Exception {
        new Expectations() {{
            mockJobContext.getJobName();
            result = "root-config-test-job1";
        }};

        sut.setConfigBasePath("file:src/test/resources/etl-test-config/");
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

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("failed to load etl config file. file = [classpath:META-INF/etl-config/etl-error.json]");

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
        expectedException.expectMessage("failed to load etl config file. file = [classpath:META-INF/etl-config/unknown.json]");

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

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("failed to load etl config file. file = [classpath:META-INF/etl-config/etl-error-common-setting.json]");

        sut.load(mockJobContext);
    }

    /**
     * jsonファイルに不備があった場合に、JsonMappingExceptionを例外チェーンに含めないこと。
     * 元例外のメッセージが詰められていること。
     */
    @Test
    public void testNotIncludeJsonMappingExceptionInExceptionChain() throws Exception {
        new Expectations(){{
            mockJobContext.getJobName();
            result = "json-fail-job";
        }};

        expectedException.expect(InvalidEtlConfigException.class);
        expectedException.expectMessage("failed to load etl config file. file = [classpath:META-INF/etl-config/json-fail-job.json], message = [Can not construct instance of java.lang.Class, problem: notFound");
        expectedException.expectCause(is(not(CoreMatchers.<Throwable> instanceOf(JsonMappingException.class))));

        sut.load(mockJobContext);
    }

    /**
     * コンポーネント設定ファイルから設定ファイルのベースパスを変更できること。
     */
    @Test
    public void testLoadingFromComponentFile() throws Exception {
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader("nablarch/etl/config/config-initialize-change-path.xml");
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);

        new Expectations() {{
            mockJobContext.getJobName();
            result = "root-config-test-job1";
        }};

        JobConfig config = sut.load(mockJobContext);

        assertThat(config.getSteps().get("step1"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step2"), is(instanceOf(StepConfig.class)));
        assertThat(config.getSteps().get("step3"), is(instanceOf(StepConfig.class)));

        SystemRepository.clear();
    }
}
