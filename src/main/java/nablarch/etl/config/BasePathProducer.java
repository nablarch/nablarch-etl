package nablarch.etl.config;

import nablarch.core.repository.SystemRepository;
import nablarch.etl.BasePath;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import java.io.File;

/**
 * ベースパスをシステムリポジトリから取得するクラス。
 *
 * @author TIS
 */
public final class BasePathProducer {

    /**
     * システムリポジトリからベースパスを検証し、取得する。
     * @param key ベースパスのキー
     * @return ベースパスのファイル
     */
    private File verifyAndGetBasePath(String key) {
        String path = SystemRepository.getString(key);
        if (path == null) {
            throw new IllegalArgumentException(key + " is not found. Check the config file.");
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
    @PathConfig(BasePath.INPUT)
    @Produces
    public File getPathConfig(InjectionPoint injectionPoint) {
        PathConfig config = injectionPoint.getAnnotated().getAnnotation(PathConfig.class);
        String key = config.value().getKey();

        return verifyAndGetBasePath(key);
    }
}
