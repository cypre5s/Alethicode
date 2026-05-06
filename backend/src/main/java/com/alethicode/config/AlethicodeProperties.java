package com.alethicode.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "alethicode")
public class AlethicodeProperties {

    private final Website website = new Website();
    private final Language language = new Language();
    private final JudgeServer judgeServer = new JudgeServer();
    private final System system = new System();
    private final LanguagePack languagePack = new LanguagePack();
    private final Nfk nfk = new Nfk();
    private final Rag rag = new Rag();

    @PostConstruct
    void validate() {
        if (judgeServer.getToken() == null || judgeServer.getToken().isBlank()) {
            throw new IllegalStateException(
                    "alethicode.judge-server.token is required — set JUDGE_SERVER_TOKEN in .env");
        }
    }

    public Website getWebsite() {
        return website;
    }

    public Language getLanguage() {
        return language;
    }

    public JudgeServer getJudgeServer() {
        return judgeServer;
    }

    public System getSystem() {
        return system;
    }

    public LanguagePack getLanguagePack() {
        return languagePack;
    }

    public Nfk getNfk() {
        return nfk;
    }

    public Rag getRag() {
        return rag;
    }

    public static class Website {

        private String baseUrl = "http://127.0.0.1";
        private String name = "Alethicode";
        private String nameShortcut = "Alethicode";
        private String footer = "";
        private boolean allowRegister = true;
        private boolean submissionListShowAll = true;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNameShortcut() {
            return nameShortcut;
        }

        public void setNameShortcut(String nameShortcut) {
            this.nameShortcut = nameShortcut;
        }

        public String getFooter() {
            return footer;
        }

        public void setFooter(String footer) {
            this.footer = footer;
        }

        public boolean isAllowRegister() {
            return allowRegister;
        }

        public void setAllowRegister(boolean allowRegister) {
            this.allowRegister = allowRegister;
        }

        public boolean isSubmissionListShowAll() {
            return submissionListShowAll;
        }

        public void setSubmissionListShowAll(boolean submissionListShowAll) {
            this.submissionListShowAll = submissionListShowAll;
        }
    }

    public static class Language {

        private List<String> languages = new ArrayList<>(List.of("Python3", "C", "C++", "Java"));
        private List<String> spjLanguages = new ArrayList<>(List.of("C", "C++"));

        public List<String> getLanguages() {
            return languages;
        }

        public void setLanguages(List<String> languages) {
            this.languages = languages;
        }

        public List<String> getSpjLanguages() {
            return spjLanguages;
        }

        public void setSpjLanguages(List<String> spjLanguages) {
            this.spjLanguages = spjLanguages;
        }
    }

    public static class JudgeServer {

        private String token = "";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class System {

        private boolean forceHttps = false;
        /**
         * MED-1 (2026-05-02 渗透报告): 是否给 csrftoken / SESSION cookie 加 Secure 标记。
         * 默认 false，让 HTTP 部署阶段也能正常登录；上线 HTTPS 后由 env
         * {@code ALETHICODE_SYSTEM_COOKIE_SECURE=true} 显式启用，浏览器仅在
         * HTTPS 连接回传 cookie，对抗校园 WiFi / 公共网络中间人嗅探。
         * 与 {@code force-https} 解耦：force-https 仅供前端 / admin UI 展示。
         */
        private boolean cookieSecure = false;
        private String staticCdnHost = "";
        private String testCaseDir = "";
        private String uploadDir = "";
        private String uploadPrefix = "/public/upload";
        private String classroomLessonDir = "";
        private String submissionDataDir = "";
        private String localVersion = "2026.03-java-m2";
        private String releaseNotesUrl =
                "https://raw.githubusercontent.com/QingdaoU/OnlineJudge/master/docs/data.json";

        public boolean isForceHttps() {
            return forceHttps;
        }

        public void setForceHttps(boolean forceHttps) {
            this.forceHttps = forceHttps;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getStaticCdnHost() {
            return staticCdnHost;
        }

        public void setStaticCdnHost(String staticCdnHost) {
            this.staticCdnHost = staticCdnHost;
        }

        public String getTestCaseDir() {
            return testCaseDir;
        }

        public void setTestCaseDir(String testCaseDir) {
            this.testCaseDir = testCaseDir;
        }

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public String getUploadPrefix() {
            return uploadPrefix;
        }

        public void setUploadPrefix(String uploadPrefix) {
            this.uploadPrefix = uploadPrefix;
        }

        public String getClassroomLessonDir() {
            return classroomLessonDir;
        }

        public void setClassroomLessonDir(String classroomLessonDir) {
            this.classroomLessonDir = classroomLessonDir;
        }

        public String getSubmissionDataDir() {
            return submissionDataDir;
        }

        public void setSubmissionDataDir(String submissionDataDir) {
            this.submissionDataDir = submissionDataDir;
        }

        public String getLocalVersion() {
            return localVersion;
        }

        public void setLocalVersion(String localVersion) {
            this.localVersion = localVersion;
        }

        public String getReleaseNotesUrl() {
            return releaseNotesUrl;
        }

        public void setReleaseNotesUrl(String releaseNotesUrl) {
            this.releaseNotesUrl = releaseNotesUrl;
        }
    }

    public static class LanguagePack {

        private String storageRoot = "";
        private String previewDir = "";
        private double minTextExtractionRatio = 0.05;
        private String libreOfficePath = "libreoffice";
        private String pythonPath = "python3";
        private final Concurrency concurrency = new Concurrency();
        private final Publish publish = new Publish();

        public String getStorageRoot() {
            return storageRoot;
        }

        public void setStorageRoot(String storageRoot) {
            this.storageRoot = storageRoot;
        }

        public String getPreviewDir() {
            return previewDir;
        }

        public void setPreviewDir(String previewDir) {
            this.previewDir = previewDir;
        }

        public double getMinTextExtractionRatio() {
            return minTextExtractionRatio;
        }

        public void setMinTextExtractionRatio(double minTextExtractionRatio) {
            this.minTextExtractionRatio = minTextExtractionRatio;
        }

        public String getLibreOfficePath() {
            return libreOfficePath;
        }

        public void setLibreOfficePath(String libreOfficePath) {
            this.libreOfficePath = libreOfficePath;
        }

        public String getPythonPath() {
            return pythonPath;
        }

        public void setPythonPath(String pythonPath) {
            this.pythonPath = pythonPath;
        }

        public Concurrency getConcurrency() {
            return concurrency;
        }

        public Publish getPublish() {
            return publish;
        }

        public static class Concurrency {
            private int documentNormalize = 4;
            private int documentParse = 4;
            private int kcExtract = 4;
            private int unitExtract = 4;
            private int problemGenerate = 4;

            public int getDocumentNormalize() { return documentNormalize; }
            public void setDocumentNormalize(int documentNormalize) { this.documentNormalize = documentNormalize; }
            public int getDocumentParse() { return documentParse; }
            public void setDocumentParse(int documentParse) { this.documentParse = documentParse; }
            public int getKcExtract() { return kcExtract; }
            public void setKcExtract(int kcExtract) { this.kcExtract = kcExtract; }
            public int getUnitExtract() { return unitExtract; }
            public void setUnitExtract(int unitExtract) { this.unitExtract = unitExtract; }
            public int getProblemGenerate() { return problemGenerate; }
            public void setProblemGenerate(int problemGenerate) { this.problemGenerate = problemGenerate; }
        }

        public static class Publish {
            private boolean skipCoverageGate = false;

            public boolean isSkipCoverageGate() {
                return skipCoverageGate;
            }

            public void setSkipCoverageGate(boolean skipCoverageGate) {
                this.skipCoverageGate = skipCoverageGate;
            }
        }
    }

    public static class Nfk {
        private String modelPath = "";
        private boolean enabled = false;
        private boolean fallbackToBkt = true;
        private long inferenceTimeoutMs = 50;

        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isFallbackToBkt() { return fallbackToBkt; }
        public void setFallbackToBkt(boolean fallbackToBkt) { this.fallbackToBkt = fallbackToBkt; }
        public long getInferenceTimeoutMs() { return inferenceTimeoutMs; }
        public void setInferenceTimeoutMs(long inferenceTimeoutMs) { this.inferenceTimeoutMs = inferenceTimeoutMs; }
    }

    public static class Rag {
        private boolean qaAllowNotReady = false;
        private String baseUrl = "http://alethicode-rag:8200";
        private String internalToken = "dev-internal-key";
        // Default 60s aligns with the asynchronous WebSocket-driven QA model:
        // sendMessageAsync dispatches the RAG call to a background worker and
        // streams TASK_STARTED/TASK_COMPLETED to the client. The client never
        // blocks on the HTTP call directly. RAG cold queries (LightRAG mix
        // mode + remote LLM keyword extraction + remote dashscope embedding)
        // observed at 8-15s P95; 60s gives ~4x headroom for tail latency
        // without colliding with the upstream LLM_API_TIMEOUT_SECONDS=300s.
        private int queryTimeoutSeconds = 60;
        private int connectTimeoutSeconds = 5;
        private int indexTimeoutSeconds = 300;

        public boolean isQaAllowNotReady() { return qaAllowNotReady; }
        public void setQaAllowNotReady(boolean qaAllowNotReady) { this.qaAllowNotReady = qaAllowNotReady; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getInternalToken() { return internalToken; }
        public void setInternalToken(String internalToken) { this.internalToken = internalToken; }

        public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }

        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }

        public int getIndexTimeoutSeconds() { return indexTimeoutSeconds; }
        public void setIndexTimeoutSeconds(int indexTimeoutSeconds) { this.indexTimeoutSeconds = indexTimeoutSeconds; }
    }
}
