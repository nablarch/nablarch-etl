package nablarch.etl.config;

import nablarch.core.util.annotation.Published;

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
 * {@code @PathConfig("inputFileBasePath")}
 * {@code @Inject}
 * private File inputFileBasePath
 * </pre>
 * </p>
 * ベースパスの設定は{@link BasePathProvider}を参照する。
 *
 * @author TIS
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@Published(tag = "architect")
public @interface PathConfig {
    @Nonbinding String value() default "";
}
