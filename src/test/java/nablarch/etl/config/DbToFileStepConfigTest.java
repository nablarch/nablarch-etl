package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import nablarch.etl.config.app.TestDto;

import org.junit.Test;

/**
 * {@link DbToFileStepConfig}のテスト。
 */
public class DbToFileStepConfigTest {

    // 正常な設定値
    private final String stepId = "step1";
    private final Class<?> bean = TestDto.class;
    private final String sqlId = "SELECT_TEST";
    private final String fileName = "output.csv";

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

        assertThat(sut.getStepId(), is("step1"));
        assertThat(sut.getBean().getName(), is(TestDto.class.getName()));
        assertThat(sut.getSqlId(), is("SELECT_TEST"));
        assertThat(sut.getFileName(), is(fileName));
    }
}
