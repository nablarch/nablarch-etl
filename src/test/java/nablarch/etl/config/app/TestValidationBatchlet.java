package nablarch.etl.config.app;

import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;
import nablarch.etl.config.ValidationStepConfig;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@Named
@Dependent
public class TestValidationBatchlet extends AbstractBatchlet {

    @Inject
    private JobContext jobContext;

    @Inject
    private StepContext stepContext;

    @EtlConfig
    @Inject
    private StepConfig stepConfig;

    @Override
    public String process() throws Exception {
        ValidationStepConfig config = (ValidationStepConfig) stepConfig;

        assertThat(config, is(notNullValue()));
        assertThat(config.getBean()
                .getName(), is(TestDto.class.getName()));
        assertThat(config.getErrorEntity()
                .getName(), is(TestDtoErrorEntity.class.getName()));
        assertThat(config.getMode(), is(ValidationStepConfig.Mode.ABORT));

        assertThat(config.getErrorLimit(), is(100));

        return "SUCCESS";
    }
}
