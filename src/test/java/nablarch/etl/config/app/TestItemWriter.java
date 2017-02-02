package nablarch.etl.config.app;

import nablarch.etl.config.ConfigIntegrationTest;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.DbToDbStepConfig.InsertMode;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;

import javax.batch.api.chunk.AbstractItemWriter;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * {@link ConfigIntegrationTest}で使用する{@link javax.batch.api.chunk.ItemWriter}。
 */
@Named
@Dependent
public class TestItemWriter extends AbstractItemWriter {

    @Inject
    private JobContext jobContext;

    @Inject
    private StepContext stepContext;

    @EtlConfig
    @Inject
    private StepConfig stepConfig;

    @Override
    public void writeItems(List<Object> items) throws Exception {

        DbToDbStepConfig config = (DbToDbStepConfig) stepConfig;

        assertThat(config, is(notNullValue()));
        assertThat(config.getBean().getName(), is(TestDto2.class.getName()));
        assertThat(config.getSql(), is("SELECT PROJECT_ID2 FROM PROJECT2"));
        assertThat(config.getMergeOnColumns(), is(Arrays.asList("test21", "test22", "test23")));
        assertThat(config.getUpdateSize().getSize(), is(200000));
        assertThat(config.getUpdateSize().getBean().getName(), is(TestDto3.class.getName()));
        assertThat(config.getInsertMode(), is(InsertMode.ORACLE_DIRECT_PATH));
    }

}
