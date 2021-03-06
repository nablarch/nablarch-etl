package nablarch.etl.config;

import java.util.Collections;
import java.util.Map;

import nablarch.core.util.annotation.Published;
import nablarch.etl.InvalidEtlConfigException;

/**
 * ジョブの設定を保持するクラス。
 *
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class JobConfig {

    /** ステップの設定。キーはステップID */
    private Map<String, StepConfig> steps = Collections.emptyMap();

    /**
     * ステップの設定を取得する。
     * @return ステップの設定
     */
    public Map<String, StepConfig> getSteps() {
        return steps;
    }

    /**
     * ステップの設定を設定する。
     * @param steps ステップの設定
     */
    public void setSteps(Map<String, StepConfig> steps) {
        this.steps = steps;
    }

    /**
     * ステップの設定を順に初期化する。
     */
    public void initialize() {
        for (Map.Entry<String, StepConfig> entry : steps.entrySet()) {
            StepConfig stepConfig = entry.getValue();
            stepConfig.setStepId(entry.getKey());
            stepConfig.initialize();
        }
    }

    /**
     * ステップの設定を取得する。
     * @param jobId ジョブID
     * @param stepId ステップID
     * @return ステップの設定
     */
    public StepConfig getStepConfig(final String jobId, final String stepId) {

        final StepConfig stepConfig = getSteps().get(stepId);
        if (stepConfig == null) {
            throw new InvalidEtlConfigException(
                    String.format("step configuration was not found. jobId = [%s], stepId = [%s]", jobId, stepId));
        }
        return stepConfig;
    }

}
