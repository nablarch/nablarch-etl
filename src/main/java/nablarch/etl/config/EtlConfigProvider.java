package nablarch.etl.config;

import javax.batch.runtime.context.JobContext;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import nablarch.core.repository.SystemRepository;

/**
 * ETLの設定を提供するクラス。
 * <p>
 * デフォルトでは、{@link JsonConfigLoader}を使用してETLの設定をロードする。
 * デフォルトのロード処理を変更したい場合は、{@link EtlConfigLoader}の実装クラスを
 * "etlConfigLoader"という名前でコンポーネント定義に設定して行う。
 * 
 * @author Kiyohito Itoh
 */
@Named
@Dependent
public final class EtlConfigProvider {

    /** デフォルトの{@link EtlConfigLoader} */
    private static final EtlConfigLoader DEFAULT_LOADER = new JsonConfigLoader();

    /** ETLの設定を初期化済みか否か */
    private boolean isInitialized;

    /** ETLの設定 */
    private JobConfig config;

    /** ジョブコンテキスト */
    @Inject
    private JobContext jobContext;

    /**
     * ETLの設定が初期化済みかを判定する。
     * @return 初期化済みの場合は{@code true}
     */
    private boolean isInitialized() {
        return isInitialized;
    }

    /**
     * ETLの設定をロードし、初期化を行う。
     */
    private synchronized void initialize() {
        if (isInitialized()) {
            return;
        }
        config = getLoader().load();
        config.initialize();
        isInitialized = true;
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
        if (loader == null) {
            ((JsonConfigLoader) DEFAULT_LOADER).setConfigPath("META-INF/batch-config/" + jobContext.getJobName() + ".json");
        }

        return loader != null ? loader : DEFAULT_LOADER;
    }

    /**
     * ETLの設定を取得する。
     * @return ETLの設定
     */
    @EtlConfig
    @Produces
    public JobConfig getConfig() {
        if (!isInitialized()) {
            initialize();
        }
        return config;
    }
}
