package nablarch.etl.config;

import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * {@link CommonConfig}のテスト。
 */
public class CommonConfigTest {

    private CommonConfig sut = new CommonConfig();

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
     * {@link CommonConfig}のアクセサのテスト。
     */
    @Test
    public void testCommonConfig() {
        sut.setInputFileBasePath(new File("input"));
        sut.setOutputFileBasePath(new File("output"));
        sut.setSqlLoaderControlFileBasePath(new File("sql_input"));
        sut.setSqlLoaderOutputFileBasePath(new File("sql_output"));

        assertThat(sut.getInputFileBasePath(), is(new File("input")));
        assertThat(sut.getOutputFileBasePath(), is(new File("output")));
        assertThat(sut.getSqlLoaderControlFileBasePath(), is(new File("sql_input")));
        assertThat(sut.getSqlLoaderOutputFileBasePath(), is(new File("sql_output")));
    }

    /**
     * 初期化時に設定ファイルから値を取得できていること。
     */
    @Test
    public void testInitialize(){
        sut.initialize();

        assertThat(sut.getInputFileBasePath(), is(new File("base/input")));
        assertThat(sut.getOutputFileBasePath(), is(new File("base/output")));
        assertThat(sut.getSqlLoaderControlFileBasePath(), is(new File("base/control")));
        assertThat(sut.getSqlLoaderOutputFileBasePath(), is(new File("base/log")));
    }

    /**
     * 設定ファイルを読み込んでいない場合、例外が送出されること。
     */
    @Test
    public void testInitializeDoNotRead() throws Exception {
        SystemRepository.clear();
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("inputFileBasePath is required. check the config file.");

        sut.initialize();
    }

    /**
     * 設定ファイルに入力漏れがある場合、例外が送出されること。
     */
    @Test
    public void testInitializeErrorUndefinedPath() throws Exception {
        SystemRepository.clear();
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader("nablarch/etl/config/config-initialize-error-undefined-path.xml");
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("outputFileBasePath is required. check the config file.");

        sut.initialize();
    }

    /**
     * 設定ファイルのパスが不正な場合、例外が送出されること。
     */
    @Test
    public void testInitializeErrorInvalidPath() throws Exception {
        SystemRepository.clear();
        XmlComponentDefinitionLoader loader = new XmlComponentDefinitionLoader("nablarch/etl/config/config-initialize-error-invalid-path.xml");
        DiContainer container = new DiContainer(loader);
        SystemRepository.load(container);

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("outputFileBasePath is required. check the config file.");

        sut.initialize();
    }

    /**
     * 生成メソッド（Producer）で生成した場合、設定ファイルから値を取得できていること。
     */
    @Test
    public void testCreateConfig() throws Exception {
        CommonConfig config = CommonConfig.createConfig();

        assertThat(config.getInputFileBasePath(), is(new File("base/input")));
        assertThat(config.getOutputFileBasePath(), is(new File("base/output")));
        assertThat(config.getSqlLoaderControlFileBasePath(), is(new File("base/control")));
        assertThat(config.getSqlLoaderOutputFileBasePath(), is(new File("base/log")));
    }
}
