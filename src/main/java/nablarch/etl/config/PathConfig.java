package nablarch.etl.config;

import nablarch.core.util.annotation.Published;
import nablarch.etl.BasePath;

import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ベースパスをインジェクトする際に指定する{@link Qualifier}。
 * <p/>
 * <b>使用例</b>
 * <pre>
 * //ベースパス指定
 * {@code @PathConfig(BasePath.INPUT)}
 * {@code @Inject}
 * private File inputFileBasePath
 * </pre>
 * </p>
 * ベースパスの設定は{@link BasePathProducer}を参照。
 * ベースパスのキーを定義したEnumは{@link BasePath}を参照。
 *
 * @see BasePathProducer
 * @see BasePath
 * @author TIS
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@Published(tag = "architect")
public @interface PathConfig {
    /**
     * ベースパスのキーを定義したEnum。
     *
     * @return Enumオブジェクト
     */
    @Nonbinding BasePath value();
}
