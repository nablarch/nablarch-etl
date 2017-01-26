package nablarch.etl.config;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.etl.BasePath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.enterprise.inject.spi.InjectionPoint;
import java.io.File;
import java.lang.annotation.Annotation;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * {@link BasePathProducer}のテスト。
 */
public class BasePathProducerTest {

    BasePathProducer sut = new BasePathProducer();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mocked
    private InjectionPoint mockInjectionPoint;

    @Before
    public void setUp() throws Exception {
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader("nablarch/etl/config/config-initialize.xml");
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);
    }

    @After
    public void tearDown() throws Exception {
        // BasePath.INPUTの値を書き換えるテストがあるため、ここで初期化する。
        Deencapsulation.setField(BasePath.INPUT, "inputFileBasePath");
        SystemRepository.clear();
    }

    /**
     * configファイルに設定された値が取れること。
     */
    @Test
    public void testGetPathConfig() throws Exception {
        new Expectations() {
            {
                mockInjectionPoint.getAnnotated().getAnnotation(PathConfig.class);
                result = createPathConfig(BasePath.INPUT);
                result = createPathConfig(BasePath.OUTPUT);
                result = createPathConfig(BasePath.SQLLOADER_CONTROL);
                result = createPathConfig(BasePath.SQLLOADER_OUTPUT);
            }
        };
        File actualInput = sut.getPathConfig(mockInjectionPoint);
        File actualOutput = sut.getPathConfig(mockInjectionPoint);
        File actualSqlControl = sut.getPathConfig(mockInjectionPoint);
        File actualSqlOutput = sut.getPathConfig(mockInjectionPoint);

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
        new Expectations() {
            {
                mockInjectionPoint.getAnnotated().getAnnotation(PathConfig.class);
                result = createPathConfig(BasePath.INPUT);
            }
        };
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("notFoundBasePath is not found. Check the config file.");
        sut.getPathConfig(mockInjectionPoint);
    }

    /**
     * configファイルが読み込まれてない場合に例外を送出すること。
     */
    @Test
    public void testGetPathConfigNotSetting() throws Exception {
        SystemRepository.clear();
        new Expectations() {
            {
                mockInjectionPoint.getAnnotated().getAnnotation(PathConfig.class);
                result = createPathConfig(BasePath.INPUT);
            }
        };
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("inputFileBasePath is not found. Check the config file.");
        sut.getPathConfig(mockInjectionPoint);
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
}
