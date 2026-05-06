package com.alethicode.service.career.preference;

/**
 * 学生在自己「我的」面板里关闭 Career Bridging Closure 4 个模块中的任一个
 * （plan 9.3 节）。
 *
 * <p>读写都对 {@code user_profile} 行（V87 扩展 4 列），不引入新表；4 个 service
 * 入口（CareerBridging / DomainLens / Studio / Path）在执行前调用
 * {@link #findPreferences} 短路，返回 disabled 即跳过该模块的 LLM / 真判题路径。
 *
 * <p>语义：global enabled (AlethicodeProperties) ∧ ¬user disabled ⇒ 模块启用。
 */
public interface CareerPreferenceService {

    /**
     * 读取学生当前 4 个模块开关。{@code user_profile} 行不存在时返回全 false
     * （等价于全部启用，让首次注册学生默认享有完整功能）。
     */
    CareerPreferences findPreferences(long userId);

    /**
     * 更新学生 4 个模块开关；行不存在时抛 404（学生应先完成注册创建
     * user_profile 行）。
     */
    void updatePreferences(long userId, CareerPreferences preferences);

    /**
     * 4 个 service 调用的便捷方法：返回当前用户对应模块是否被关闭。
     * 模块名取值：{@code career_bridging} / {@code coding_lens} /
     * {@code career_studio} / {@code career_path}。
     */
    boolean isModuleDisabled(long userId, String moduleName);

    record CareerPreferences(
            boolean careerBridgingDisabled,
            boolean codingLensDisabled,
            boolean careerStudioDisabled,
            boolean careerPathDisabled
    ) {
        public static CareerPreferences allEnabled() {
            return new CareerPreferences(false, false, false, false);
        }
    }
}
