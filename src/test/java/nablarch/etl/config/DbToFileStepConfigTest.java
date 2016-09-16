package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;

import nablarch.etl.config.app.TestDto;

import org.junit.Before;
import org.junit.Test;

/**
 * {@link DbToFileStepConfig}のテスト。
 */
public class DbToFileStepConfigTest {

    // 正常な設定値
    private JobConfig jobConfig;
    private final String stepId = "step1";
    private final Class<?> bean = TestDto.class;
    private final String sqlId = "SELECT_TEST";
    private final String fileName = "output.csv";

    @Before
    public void setUp() {
        jobConfig = new JobConfig() {{
            setJobId("job1");
            setInputFileBasePath(new File("/job/input"));
            setOutputFileBasePath(new File("/job/output"));
            setSqlLoaderControlFileBasePath(new File("/job/control"));
            setSqlLoaderOutputFileBasePath(new File("/job/log"));
        }};
        jobConfig.initialize(
                new RootConfig() {{
                    setInputFileBasePath(new File("/base/input"));
                    setOutputFileBasePath(new File("/base/output"));
                    setSqlLoaderControlFileBasePath(new File("/base/control"));
                    setSqlLoaderOutputFileBasePath(new File("/base/log"));
                }});
    }

    /**
     * 正常に設定された場合、値が取得できること。
     */
    @Test
    public void testNormalSetting() {

        DbToFileStepConfig sut = new DbToFileStepConfig() {{
            setStepId(stepId);
            setBean(bean);
            setSqlId(sqlId);
            setFileName(fileName);
        }};
        sut.initialize(jobConfig);

        assertThat(sut.getStepId(), is("step1"));
        assertThat(sut.getBean().getName(), is(TestDto.class.getName()));
        assertThat(sut.getSqlId(), is("SELECT_TEST"));
        assertThat(sut.getFile(), is(notNullValue()));
        assertThat(sut.getFile(), is(new File("/job/output/output.csv")));
    }
}
