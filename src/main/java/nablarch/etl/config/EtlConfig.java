package nablarch.etl.config;

import nablarch.core.util.annotation.Published;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ETLの設定をインジェクトする際に指定する{@link Qualifier}。
 * <p/>
 * <b>使用例</b>
 * <pre>
 * //ETLの設定
 * {@code @EtlConfig}
 * {@code @Inject}
 * private JobConfig jobConfig;
 * </pre>
 * </p>
 * ETLの設定は{@link JobConfig}を参照。
 *
 * @see JobConfig
 * @author Kiyohito Itoh
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@Published(tag = "architect")
public @interface EtlConfig {
    // nop
}
