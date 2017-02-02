package nablarch.etl.config;

import nablarch.core.repository.SystemRepository;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.inject.Produces;

/**
 * ETLの設定を提供するクラス。
 * <p>
 * デフォルトでは、{@link JsonConfigLoader}を使用してETLの設定をロードする。
 * デフォルトのロード処理を変更したい場合は、{@link EtlConfigLoader}の実装クラスを
 * "etlConfigLoader"という名前でコンポーネント定義に設定して行う。
 *
 * 連続して同一のジョブの設定を取得する場合は1回目にロードしたETLの設定を提供する。
 *
 * @author Kiyohito Itoh
 */
public final class EtlConfigProvider {

    /** 前回初期化した{@link JobContext} */
    private static JobContext cacheJobContext;

    /** ETLの設定 */
    private static JobConfig config;

    /**
     * ETLの設定をロードし、初期化を行う。
     *
     * @param jobContext ジョブコンテキスト
     */
    private synchronized void initialize(JobContext jobContext) {
        if (cacheJobContext == jobContext) {
            return;
        }
        config = getLoader().load(jobContext);
        config.initialize();
        cacheJobContext = jobContext;
    }

    /**
     * {@link EtlConfigLoader}を取得する。
     * <p>
     * "etlConfigLoader"という名前でリポジトリから取得する。
     * リポジトリに存在しない場合は{@link JsonConfigLoader}を返す。
     * @return {@link EtlConfigLoader}
     */
    private EtlConfigLoader getLoader() {
        EtlConfigLoader loader = SystemRepository.get("etlConfigLoader");

        return loader != null ? loader : new JsonConfigLoader();
    }

    /**
     * ETLの設定を取得する。
     *
     * @param jobContext ジョブコンテキスト
     * @param stepContext ステップコンテキスト
     * @return ETLの設定
     */
    @EtlConfig
    @Produces
    public StepConfig getConfig(JobContext jobContext, StepContext stepContext) {
        if (cacheJobContext != jobContext) {
            initialize(jobContext);
        }

        return config.getStepConfig(stepContext.getStepName());
    }
}
