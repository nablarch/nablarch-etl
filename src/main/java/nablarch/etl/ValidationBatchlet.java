package nablarch.etl;

import nablarch.common.dao.DeferredEntityList;
import nablarch.common.dao.EntityUtil;
import nablarch.common.dao.UniversalDao;
import nablarch.core.beans.BeanUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.validation.ee.ValidatorUtil;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;
import nablarch.etl.config.ValidationStepConfig;
import nablarch.etl.config.ValidationStepConfig.Mode;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.text.MessageFormat;
import java.util.Set;

import static nablarch.etl.EtlUtil.verifyRequired;

/**
 * 一時テーブルのデータをバリデーションする{@link javax.batch.api.Batchlet}実装クラス。
 * <p/>
 * 一時テーブルのデータを全レコード取得し、{@link ValidationStepConfig#getBean()}のバリデーションルールに従いバリデーションを実施する。
 * エラーが発生した場合には、そのレコードを退避テーブル(エラーテーブル)({@link ValidationStepConfig#getErrorEntity}に対応するテーブル)に移動する。
 * また、エラーの詳細はワーニングレベルでログ出力を行う。
 * <p/>
 * エラー発生時にジョブを継続するか否かのモード指定によって切り替えることができる。
 * {@link ValidationStepConfig#getMode()}が{@link Mode#CONTINUE}の場合には処理を継続し、
 * {@link Mode#ABORT}の場合には、{@link EtlJobAbortedException}を送出しジョブを異常終了する。
 * <p/>
 * 許容するエラー数が設定でき、その数を超えた場合には即ジョブをアボートする。
 * 許容するエラー数の設定は、{@link ValidationStepConfig#getErrorLimit()}より取得する。
 * この値が設定されていない場合やマイナス値の場合は、この機能は無効化される。
 *
 * @author Hisaaki Shioiri
 */
@Named
@Dependent
public class ValidationBatchlet extends AbstractBatchlet {

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get("etl");

    /** {@link JobContext} */
    @Inject
    private JobContext jobContext;

    /** {@link StepContext} */
    @Inject
    private StepContext stepContext;

    /** ETLの設定 */
    @Inject
    @EtlConfig
    private StepConfig stepConfig;

    @Override
    public String process() throws Exception {

        final ValidationStepConfig config = (ValidationStepConfig) stepConfig;

        verifyConfig(config);

        final Class<?> inputTable = config.getBean();
        final Class<?> errorTable = config.getErrorEntity();

        truncateErrorTable(errorTable);

        final ValidationResult validationResult = new ValidationResult();
        final Validator validator = ValidatorUtil.getValidator();

        // 一時テーブルのデータを全て取得しValidationを行う。
        final DeferredEntityList<?> workItems = (DeferredEntityList<?>) UniversalDao.defer().findAll(inputTable);

        for (Object item : workItems) {
            validationResult.incrementCount();

            final WorkItem workItem = (WorkItem) item;
            final Set<ConstraintViolation<WorkItem>> constraintViolations = validator.validate(workItem);

            if (constraintViolations.isEmpty()) {
                continue;
            }

            validationResult.addErrorCount(constraintViolations.size());
            onError(workItem, constraintViolations, errorTable);
            if (isOverLimit(config, validationResult)) {
                throw new EtlJobAbortedException("number of validation errors has exceeded the maximum number of errors."
                        + " bean class=[" + inputTable.getName() + ']');
            }
        }
        workItems.close();

        deleteErrorRecord(inputTable, errorTable);

        LOGGER.logInfo(MessageFormat.format(
                "validation result. bean class=[{0}], line count=[{1}], error count=[{2}]",
                inputTable.getName(), validationResult.getLineCount(), validationResult.getErrorCount()));

        // トランザクションをコミットし処理を終了する。
        // トランザクションをコミットしない場合、ジョブを異常終了するモードの場合に、
        // エラーテーブルに格納した情報などが破棄されてしまう。
        commit();

        return buildResult(config.getMode(), inputTable, validationResult);
    }

    /**
     * エラーテーブルの内容をクリーニングする。
     *
     * @param errorTable エラーテーブルの内容
     */
    private static void truncateErrorTable(final Class<?> errorTable) {
        final AppDbConnection connection = DbConnectionContext.getConnection();
        final SqlPStatement statement = connection.prepareStatement(
                "truncate table " + EntityUtil.getTableNameWithSchema(errorTable));
        statement.executeUpdate();
        commit();
    }

    /**
     * 一時テーブルからエラーのレコード情報を削除する。
     *
     * @param inputTable 一時テーブル
     * @param errorTable エラーテーブル
     */
    private static void deleteErrorRecord(final Class<?> inputTable, final Class<?> errorTable) {
        final AppDbConnection connection = DbConnectionContext.getConnection();
        final SqlPStatement statement = connection.prepareStatement(buildDeleteSql(inputTable, errorTable));
        statement.executeUpdate();
    }

    /**
     * エラーレコードをクリーニングするためのSQL文を構築する。
     *
     * @param inputTable 一時テーブル
     * @param errorTable エラーテーブル
     * @return SQL文
     */
    private static String buildDeleteSql(final Class<?> inputTable, final Class<?> errorTable) {
        return "delete from " + EntityUtil.getTableNameWithSchema(inputTable)
                + " where line_number in (select line_number from " + EntityUtil.getTableNameWithSchema(errorTable) + ')';
    }

    /**
     * 設定値の検証を行う。
     *
     * @param stepConfig 設定値
     */
    private void verifyConfig(final ValidationStepConfig stepConfig) {
        final String jobName = jobContext.getJobName();
        final String stepName = stepContext.getStepName();

        verifyRequired(jobName, stepName, "bean", stepConfig.getBean());
        verifyRequired(jobName, stepName, "errorEntity", stepConfig.getErrorEntity());
        verifyRequired(jobName, stepName, "mode", stepConfig.getMode());
    }

    /**
     * 結果を構築する。
     *
     * @param mode モード
     * @param inputTableEntity 入力Entityクラス
     * @param validationResult バリデーション結果
     * @return 終了ステータス
     */
    private static String buildResult(
            final Mode mode, final Class<?> inputTableEntity, final ValidationResult validationResult) {

        if (validationResult.hasError()) {
            if (mode == Mode.CONTINUE) {
                return "VALIDATION_ERROR";
            } else {
                throw new EtlJobAbortedException(
                        "abort the JOB because there was a validation error."
                                + " bean class=[" + inputTableEntity.getName() + "],"
                                + " error count=[" + validationResult.getErrorCount() + ']');
            }
        } else {
            return "SUCCESS";
        }
    }


    /**
     * Validationエラー時の処理を行う。
     *
     * @param item Validationエラーが発生したアイテム
     * @param constraintViolations Validationのエラー内容
     * @param errorTable エラーテーブルのエンティティクラス
     */
    private static void onError(
            final WorkItem item,
            final Set<ConstraintViolation<WorkItem>> constraintViolations,
            final Class<?> errorTable) {

        for (ConstraintViolation<WorkItem> violation : constraintViolations) {
            LOGGER.logWarn(MessageFormat.format(
                            "validation error has occurred. bean class=[{0}], property name=[{1}], error message=[{2}], line number=[{3}]",
                            item.getClass()
                                    .getName(),
                            violation.getPropertyPath()
                                    .toString(),
                            violation.getMessage(),
                            item.getLineNumber()
                    )
            );

        }

        UniversalDao.insert(BeanUtil.createAndCopy(errorTable, item));
    }

    /**
     * エラーの許容数を超えたか否か。
     *
     * @param stepConfig 設定値
     * @param validationResult バリデーション結果情報
     * @return 超えている場合は{@code true}
     */
    private static boolean isOverLimit(final ValidationStepConfig stepConfig, final ValidationResult validationResult) {
        final Integer errorLimitCount = stepConfig.getErrorLimit();
        if (errorLimitCount == null || errorLimitCount.compareTo(0) < 0) {
            return false;
        }
        return validationResult.getErrorCount() > errorLimitCount;
    }

    /**
     * コミットを行う。
     */
    private static void commit() {
        TransactionContext.getTransaction().commit();
    }

}

