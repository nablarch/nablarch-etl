package nablarch.etl.config.app;

import nablarch.etl.BasePath;
import nablarch.etl.config.DbToFileStepConfig;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.etl.config.PathConfig;
import nablarch.etl.config.StepConfig;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;


/**
 * {@link nablarch.etl.config.ConfigIntegrationTest}で使用する{@link javax.batch.api.Batchlet}。
 */
@Named
@Dependent
public class TestBatchlet extends AbstractBatchlet {

    @Inject
    private JobContext jobContext;

    @Inject
    private StepContext stepContext;

    @EtlConfig
    @Inject
    private StepConfig stepConfig;

    @PathConfig(BasePath.INPUT)
    @Inject
    private File inputFileBasePath;

    @PathConfig(BasePath.OUTPUT)
    @Inject
    private File outputFileBasePath;

    @PathConfig(BasePath.SQLLOADER_CONTROL)
    @Inject
    private File sqlLoaderControlFileBasePath;

    @PathConfig(BasePath.SQLLOADER_OUTPUT)
    @Inject
    private File sqlLoaderOutputFileBasePath;

    @Override
    public String process() {

        String jobId = jobContext.getJobName();
        String stepId = stepContext.getStepName();

        if ("step1".equals(stepId)) {

            FileToDbStepConfig config = (FileToDbStepConfig) stepConfig;
            assertThat(config, is(notNullValue()));
            assertThat(config.getBean().getName(), is(TestDto.class.getName()));
            assertThat(inputFileBasePath, is(new File("base/input")));
            assertThat(config.getFileName(), is("test-input.csv"));
            assertThat(sqlLoaderControlFileBasePath, is(new File("base/control")));
            assertThat(sqlLoaderOutputFileBasePath, is(new File("base/log")));

        } else if ("step3".equals(stepId)) {

            DbToFileStepConfig config = (DbToFileStepConfig) stepConfig;
            assertThat(config, is(notNullValue()));
            assertThat(config.getBean().getName(), is(TestDto3.class.getName()));
            assertThat(config.getSqlId(), is("SELECT_TEST3"));
            assertThat(outputFileBasePath, is(new File("base/output")));
            assertThat(config.getFileName(), is("test-output.csv"));

        } else {
            fail("not happen");
        }

        return "SUCCESS";
    }
}
