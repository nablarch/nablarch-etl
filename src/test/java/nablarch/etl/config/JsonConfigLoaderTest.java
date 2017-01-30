package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.hamcrest.Matchers;
import org.junit.Test;

/**
 * {@link JsonConfigLoader}のテスト。
 */
public class JsonConfigLoaderTest {

    /**
     * 指定されたパスに配置された設定ファイルがロードできること。
     */
    @Test
    public void testSpecifiedPath() {

        JsonConfigLoader sut = new JsonConfigLoader();
        sut.setConfigPath("nablarch/etl/config/etl-normal.json");

        RootConfig config = sut.load();

        assertThat(config.getJobs(), Matchers.hasKey("test"));
    }

    /**
     * ロードに失敗した場合、例外が送出されること。
     */
    @Test
    public void testLoadingFailed() {

        // 存在しないパス
        JsonConfigLoader sut = new JsonConfigLoader();
        sut.setConfigPath("/hoge/unknown.json");

        try {
            sut.load();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("failed to load etl config file. file = [/hoge/unknown.json]"));
        }

        // フォーマットエラー
        sut = new JsonConfigLoader();
        sut.setConfigPath("nablarch/etl/config/etl-error.json");

        try {
            sut.load();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("failed to load etl config file. file = [nablarch/etl/config/etl-error.json]"));
        }
    }

    /**
     * 共通項目の設定がjsonファイルにあると例外を送出すること。
     */
    @Test
    public void testLoadingCommonSetting() throws Exception {
        JsonConfigLoader sut = new JsonConfigLoader();
        sut.setConfigPath("nablarch/etl/config/etl-error-common-setting.json");

        try {
            sut.load();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("failed to load etl config file. file = [nablarch/etl/config/etl-error-common-setting.json]"));
        }
    }
}
