package nablarch.etl.config;

import nablarch.core.repository.SystemRepository;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import java.io.File;

/**
 * 共通項目設定をシステムリポジトリから取得するプロバイダー。
 *
 * @author TIS
 */
public final class BasePathProvider {

    /**
     * 隠蔽コンストラクタ
     */
    private BasePathProvider() {
    }

    /**
     * システムリポジトリからベースパスを検証し、取得する。
     * @param key ベースパスのキー
     * @return ベースパスのファイル
     */
    private static File verifyAndGetBasePath(String key) {
        String path = SystemRepository.getString(key);
        if (path == null) {
            throw new IllegalStateException(key + " is not found. Check the config file.");
        }

        return new File(path);
    }

    /**
     * システムリポジトリからベースパスを取得する。
     * <p/>
     * 取得時に、ベースパスが存在しているかどうかを検証する。
     *
     * @param injectionPoint インジェクションポイント
     * @return ベースパスのファイル
     */
    @PathConfig
    @Produces
    public static File getPathConfig(InjectionPoint injectionPoint) {
        PathConfig config = injectionPoint.getAnnotated().getAnnotation(PathConfig.class);
        String key = config.value();

        return verifyAndGetBasePath(key);
    }
}
