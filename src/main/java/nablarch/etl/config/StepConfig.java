package nablarch.etl.config;

import nablarch.core.util.annotation.Published;

/**
 * ステップの設定をサポートするクラス。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public abstract class StepConfig {

    /** ステップID */
    private String stepId;

    /**
     * ステップIDを取得する。
     * @return ステップID
     */
    public String getStepId() {
        return stepId;
    }

    /**
     * ステップIDを設定する。
     * @param stepId ステップID
     */
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    /**
     * 設定値から初期化を行う。
     */
    public final void initialize() {
        onInitialize();
    }

    /**
     * 設定値から初期化を行う。
     */
    protected abstract void onInitialize();
}
