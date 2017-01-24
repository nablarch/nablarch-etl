package nablarch.etl.config;

import mockit.Expectations;
import mockit.Mocked;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.enterprise.inject.spi.InjectionPoint;
import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * {@link BasePathProvider}のテスト。
 */
public class BasePathProviderTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mocked
    private InjectionPoint mockInjectionPoint;

    @Mocked
    private PathConfig mockPathConfig;

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
        new Expectations() {
            {
                mockInjectionPoint.getAnnotated().getAnnotation(PathConfig.class);
                result = mockPathConfig;

                mockPathConfig.value();
                result = "inputFileBasePath";
                result = "outputFileBasePath";
                result = "sqlLoaderControlFileBasePath";
                result = "sqlLoaderOutputFileBasePath";
            }
        };
        File actualInput = BasePathProvider.getPathConfig(mockInjectionPoint);
        File actualOutput = BasePathProvider.getPathConfig(mockInjectionPoint);
        File actualSqlControl = BasePathProvider.getPathConfig(mockInjectionPoint);
        File actualSqlOutput = BasePathProvider.getPathConfig(mockInjectionPoint);

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
        new Expectations() {
            {
                mockInjectionPoint.getAnnotated().getAnnotation(PathConfig.class);
                result = mockPathConfig;

                mockPathConfig.value();
                result = "notFoundBasePath";
            }
        };
        exception.expect(IllegalStateException.class);
        exception.expectMessage("notFoundBasePath is not found. Check the config file.");
        File actual = BasePathProvider.getPathConfig(mockInjectionPoint);
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
                result = mockPathConfig;

                mockPathConfig.value();
                result = "inputFileBasePath";
            }
        };
        exception.expect(IllegalStateException.class);
        exception.expectMessage("inputFileBasePath is not found. Check the config file.");
        File actual = BasePathProvider.getPathConfig(mockInjectionPoint);
    }
}
