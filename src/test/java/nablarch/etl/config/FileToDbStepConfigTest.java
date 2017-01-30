package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import nablarch.etl.config.app.TestDto;

import org.junit.Test;

/**
 * {@link FileToDbStepConfig}のテスト。
 */
public class FileToDbStepConfigTest {

    // 正常な設定値
    private final String stepId = "step1";
    private final Class<?> bean = TestDto.class;
    private final String fileName = "input.csv";

    /**
     * 正常に設定された場合、値が取得できること。
     */
    @Test
    public void testNormalSetting() {

        FileToDbStepConfig sut = new FileToDbStepConfig() {{
            setStepId(stepId);
            setBean(bean);
            setFileName(fileName);
        }};

        assertThat(sut.getStepId(), is("step1"));
        assertThat(sut.getBean().getName(), is(TestDto.class.getName()));
        assertThat(sut.getFileName(), is(fileName));
    }
}
