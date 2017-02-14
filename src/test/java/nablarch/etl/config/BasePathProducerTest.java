package nablarch.etl.config;

import mockit.Deencapsulation;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.etl.BasePath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * {@link BasePathProducer}のテスト。
 */
public class BasePathProducerTest {

    BasePathProducer sut = new BasePathProducer();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader("nablarch/etl/config/config-initialize.xml");
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);
    }

    @After
    public void tearDown() throws Exception {
        SystemRepository.clear();
    }

    /**
     * configファイルに設定された値が取れること。
     */
    @Test
    public void testGetPathConfig() throws Exception {
        File actualInput = sut.getPathConfig(createInjectionPoint(BasePath.INPUT));
        File actualOutput = sut.getPathConfig(createInjectionPoint(BasePath.OUTPUT));
        File actualSqlControl = sut.getPathConfig(createInjectionPoint(BasePath.SQLLOADER_CONTROL));
        File actualSqlOutput = sut.getPathConfig(createInjectionPoint(BasePath.SQLLOADER_OUTPUT));

        assertThat(actualInput, is(new File("base/input")));
        assertThat(actualOutput, is(new File("base/output")));
        assertThat(actualSqlControl, is(new File("base/control")));
        assertThat(actualSqlOutput, is(new File("base/log")));
    }


    /**
     * configファイルに設定されてないキーが指定された場合に例外を送出すること。
     */
    @Test
    public void testGetPathConfigNotFound() throws Exception {
        Deencapsulation.setField(BasePath.INPUT, "notFoundBasePath");
        InjectionPoint injectionPoint = createInjectionPoint(BasePath.INPUT);
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("notFoundBasePath is not found. Check the config file.");
        try {
            sut.getPathConfig(injectionPoint);
        } finally {
            Deencapsulation.setField(BasePath.INPUT, "nablarch.etl.inputFileBasePath");
        }
    }

    /**
     * configファイルが読み込まれてない場合に例外を送出すること。
     */
    @Test
    public void testGetPathConfigNotSetting() throws Exception {
        SystemRepository.clear();
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("inputFileBasePath is not found. Check the config file.");
        sut.getPathConfig(createInjectionPoint(BasePath.INPUT));
    }

    /**
     * {@link PathConfig}アノテーションのインスタンスを生成する。
     *
     * @param basePath ベースパス
     * @return PathConfigアノテーションのインスタンス
     */
    private PathConfig createPathConfig(final BasePath basePath) {
        final PathConfig pathConfig = new PathConfig() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public BasePath value() {
                return basePath;
            }
        };
        return pathConfig;
    }

    /**
     * {@link InjectionPoint}のインスタンスを生成する。
     * @param basePath ベースパス
     * @return InjectionPointのインスタンス
     */
    private InjectionPoint createInjectionPoint(final BasePath basePath) {
        final InjectionPoint injectionPoint = new InjectionPoint() {
            @Override
            public Type getType() {
                return null;
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return null;
            }

            @Override
            public Bean<?> getBean() {
                return null;
            }

            @Override
            public Member getMember() {
                return null;
            }

            @Override
            public Annotated getAnnotated() {
                return new Annotated() {
                    @Override
                    public Type getBaseType() {
                        return null;
                    }

                    @Override
                    public Set<Type> getTypeClosure() {
                        return null;
                    }

                    @Override
                    public <T extends Annotation> T getAnnotation(Class<T> aClass) {
                        return (T) createPathConfig(basePath);
                    }

                    @Override
                    public Set<Annotation> getAnnotations() {
                        return null;
                    }

                    @Override
                    public boolean isAnnotationPresent(Class<? extends Annotation> aClass) {
                        return false;
                    }
                };
            }

            @Override
            public boolean isDelegate() {
                return false;
            }

            @Override
            public boolean isTransient() {
                return false;
            }
        };
        return injectionPoint;
    }
}
