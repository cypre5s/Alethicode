package com.alethicode.service.languagepack.quality;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python3 reference solution 静态 lint。
 *
 * <p>对应设计稿附录 C 的 7 条规则，按"硬规则阻塞入库 + 软规则仅记录"两档处理。
 * 全部用文本/正则模式扫描，不引入 Python AST 子进程：本期 lint 关注的都是
 * 表层模式（直接 print set/dict、未限位浮点、未 seed 的 random 等），
 * 引入 AST 反而会增加运行时依赖与脆弱性。</p>
 */
@Service
public class ReferenceSolutionLinter {

    private static final int SOFT_LINE_LIMIT = 60;

    private static final Pattern PRINT_CALL = Pattern.compile("\\bprint\\s*\\(([^)]*)\\)");
    private static final Pattern SET_LITERAL_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\{[^:}\\n]*}\\s*$"
    );
    private static final Pattern DICT_LITERAL_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\{[^}]*:[^}]*}\\s*$"
    );
    private static final Pattern SET_CTOR_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*set\\s*\\("
    );
    private static final Pattern DICT_CTOR_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*dict\\s*\\("
    );
    private static final Pattern SET_COMPREHENSION = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\{[^}]*for[^}]*}\\s*$"
    );
    private static final Pattern DICT_COMPREHENSION = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\{[^:}]*:[^}]*for[^}]*}\\s*$"
    );
    private static final Pattern COUNTER_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*Counter\\s*\\("
    );

    private static final Pattern FLOAT_LITERAL_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[-+]?\\d+\\.\\d+\\b"
    );
    private static final Pattern FLOAT_OP_ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[^#]*\\b(/|\\*\\*)\\b|^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[^#]*\\b(math\\.(?:pi|sqrt|log|exp|sin|cos|tan|pow))\\b"
    );

    private static final Pattern RANDOM_IMPORT = Pattern.compile(
            "^\\s*(?:import\\s+random\\b|from\\s+random\\s+import\\s+\\w)"
    );
    private static final Pattern RANDOM_SEED_CALL = Pattern.compile(
            "\\brandom\\.seed\\s*\\(|\\bseed\\s*\\("
    );

    private static final Pattern INPUT_CALL = Pattern.compile("(?<!_)\\binput\\s*\\(");
    private static final Pattern STDIN_READ_PATTERN = Pattern.compile(
            "\\bsys\\.stdin\\b|\\bstdin\\.read\\b|\\bstdin\\.readline\\b"
    );
    private static final Pattern EOF_GUARD = Pattern.compile("EOFError|StopIteration|except\\s*:");

    private static final Pattern STRING_LITERAL = Pattern.compile(
            "(?<![A-Za-z_])[bru]{0,2}(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
    );

    private static final Pattern MAIN_GUARD = Pattern.compile(
            "if\\s+__name__\\s*==\\s*['\"]__main__['\"]"
    );

    private static final Map<Character, Character> FULL_TO_HALF_PUNCT = Map.of(
            '，', ',',
            '。', '.',
            '：', ':',
            '；', ';',
            '？', '?',
            '！', '!'
    );

    /**
     * 只用 reference 代码做 lint（无题面上下文时调用，REF002/REF007 跳过）。
     */
    public ReferenceLintReport lint(String referenceCode, String language) {
        return lint(referenceCode, language, ReferenceLintContext.empty());
    }

    /**
     * 完整 lint。当前仅支持 Python3，其它语言直接返回空报告（设计稿 N5：本期题库 100% Python3）。
     */
    public ReferenceLintReport lint(String referenceCode, String language, ReferenceLintContext context) {
        if (referenceCode == null || referenceCode.isBlank()) {
            return ReferenceLintReport.empty();
        }
        if (!isPython(language)) {
            return ReferenceLintReport.empty();
        }

        List<LintViolation> hard = new ArrayList<>();
        List<LintViolation> soft = new ArrayList<>();
        List<String> rawLines = referenceCode.split("\n", -1) instanceof String[] arr
                ? List.of(arr) : List.of();
        List<String> codeLines = stripComments(rawLines);

        Map<String, String> assignedTypes = collectAssignedTypes(codeLines);

        checkRef001(codeLines, assignedTypes, hard);
        if (context != null && context.hasOutputDescription()) {
            checkRef002(codeLines, assignedTypes, context, hard);
        }
        checkRef003(codeLines, hard);
        checkRef004(codeLines, hard);
        if (context != null && context.hasDescription()) {
            checkRef007(codeLines, context, hard);
        }

        checkRef005(codeLines, soft);
        checkRef006(codeLines, soft);

        return new ReferenceLintReport(List.copyOf(hard), List.copyOf(soft));
    }

    private void checkRef001(List<String> lines,
                             Map<String, String> assignedTypes,
                             List<LintViolation> hard) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = PRINT_CALL.matcher(line);
            while (m.find()) {
                String arg = m.group(1).strip();
                if (arg.isEmpty()) {
                    continue;
                }
                if (containsSorted(line)) {
                    continue;
                }
                String type = inferPrintArgType(arg, assignedTypes);
                if ("set".equals(type) || "dict".equals(type)) {
                    hard.add(LintViolation.hard(
                            "REF001",
                            "print(" + arg + ") 直接打印 " + type + "，输出顺序非 deterministic；"
                                    + "请改用 sorted/格式化包装（例如 \"{\" + \", \".join(repr(x) for x in sorted(s)) + \"}\"）",
                            i + 1
                    ));
                }
            }
        }
    }

    private boolean containsSorted(String line) {
        return line.contains("sorted(") || line.contains(".sort(");
    }

    private String inferPrintArgType(String arg, Map<String, String> assignedTypes) {
        String trimmed = arg.strip();
        if (trimmed.startsWith("set(")) {
            return "set";
        }
        if (trimmed.startsWith("dict(") || trimmed.startsWith("Counter(")) {
            return "dict";
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String body = trimmed.substring(1, trimmed.length() - 1);
            if (body.isBlank()) {
                return null;
            }
            return body.contains(":") ? "dict" : "set";
        }
        Matcher var = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\b").matcher(trimmed);
        if (var.find()) {
            return assignedTypes.get(var.group(1));
        }
        return null;
    }

    private Map<String, String> collectAssignedTypes(List<String> lines) {
        Map<String, String> types = new HashMap<>();
        for (String line : lines) {
            recordIfMatch(line, SET_LITERAL_ASSIGN, "set", types);
            recordIfMatch(line, DICT_LITERAL_ASSIGN, "dict", types);
            recordIfMatch(line, SET_CTOR_ASSIGN, "set", types);
            recordIfMatch(line, DICT_CTOR_ASSIGN, "dict", types);
            recordIfMatch(line, SET_COMPREHENSION, "set", types);
            recordIfMatch(line, DICT_COMPREHENSION, "dict", types);
            recordIfMatch(line, COUNTER_ASSIGN, "dict", types);
            recordIfMatch(line, FLOAT_LITERAL_ASSIGN, "float", types);
            Matcher op = FLOAT_OP_ASSIGN.matcher(line);
            if (op.find()) {
                String name = op.group(1) != null ? op.group(1) : op.group(3);
                if (name != null) {
                    types.putIfAbsent(name, "float");
                }
            }
        }
        return types;
    }

    private void recordIfMatch(String line, Pattern pattern, String type, Map<String, String> types) {
        Matcher m = pattern.matcher(line);
        if (m.find()) {
            String name = m.group(1);
            if (name != null) {
                types.put(name, type);
            }
        }
    }

    private void checkRef002(List<String> lines,
                             Map<String, String> assignedTypes,
                             ReferenceLintContext context,
                             List<LintViolation> hard) {
        if (!outputDescriptionRequiresPrecision(context.outputDescription())) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = PRINT_CALL.matcher(line);
            while (m.find()) {
                String arg = m.group(1).strip();
                if (arg.isEmpty()) {
                    continue;
                }
                if (printArgUsesPrecision(line, arg)) {
                    continue;
                }
                if (printArgIsFloat(arg, assignedTypes)) {
                    hard.add(LintViolation.hard(
                            "REF002",
                            "题面要求保留小数位，但 print(" + arg + ") 未限位；"
                                    + "请改用 f-string \":.Nf\" 或 round(x, N)",
                            i + 1
                    ));
                }
            }
        }
    }

    private boolean printArgUsesPrecision(String line, String arg) {
        return arg.contains(":.") || arg.contains("round(") || arg.contains("format(")
                || line.contains(":.") || line.contains("round(");
    }

    private boolean printArgIsFloat(String arg, Map<String, String> assignedTypes) {
        String trimmed = arg.strip();
        if (trimmed.contains("/") || trimmed.contains("**")
                || trimmed.contains("math.pi") || trimmed.contains("math.sqrt")) {
            return true;
        }
        Matcher var = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\b").matcher(trimmed);
        if (var.find()) {
            return "float".equals(assignedTypes.get(var.group(1)));
        }
        return false;
    }

    private boolean outputDescriptionRequiresPrecision(String outputDescription) {
        if (outputDescription == null) {
            return false;
        }
        String norm = outputDescription.toLowerCase(Locale.ROOT);
        return norm.contains("小数") || norm.contains("精度") || norm.contains("保留")
                || norm.contains("decimal") || norm.contains("precision") || norm.contains("digits");
    }

    private void checkRef003(List<String> lines, List<LintViolation> hard) {
        boolean importsRandom = false;
        boolean hasSeed = false;
        int importLine = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (RANDOM_IMPORT.matcher(line).find()) {
                importsRandom = true;
                if (importLine == 0) {
                    importLine = i + 1;
                }
            }
            if (RANDOM_SEED_CALL.matcher(line).find()) {
                hasSeed = true;
            }
        }
        if (importsRandom && !hasSeed) {
            hard.add(LintViolation.hard(
                    "REF003",
                    "import random 但未显式 random.seed(...)：每次跑结果不一致，"
                            + "请加 random.seed(int(input()))（推荐题面提供随机种子）",
                    importLine
            ));
        }
    }

    private void checkRef004(List<String> lines, List<LintViolation> hard) {
        int inputCount = 0;
        boolean usesStdin = false;
        boolean hasGuard = false;
        int firstInputLine = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = INPUT_CALL.matcher(line);
            while (m.find()) {
                inputCount++;
                if (firstInputLine == 0) {
                    firstInputLine = i + 1;
                }
            }
            if (STDIN_READ_PATTERN.matcher(line).find()) {
                usesStdin = true;
            }
            if (EOF_GUARD.matcher(line).find()) {
                hasGuard = true;
            }
        }
        if (inputCount >= 3 && !usesStdin && !hasGuard) {
            hard.add(LintViolation.hard(
                    "REF004",
                    "input() 调用 " + inputCount + " 次且无 try/except EOFError 防护，"
                            + "易因 case 输入行数不一致触发 RE；建议改用 sys.stdin.read().split()",
                    firstInputLine
            ));
        }
    }

    private void checkRef007(List<String> lines, ReferenceLintContext context, List<LintViolation> hard) {
        Set<Character> descriptionPunct = collectFullWidthPunct(context.description());
        if (descriptionPunct.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = STRING_LITERAL.matcher(line);
            while (m.find()) {
                String literal = m.group(1);
                for (Map.Entry<Character, Character> entry : FULL_TO_HALF_PUNCT.entrySet()) {
                    char full = entry.getKey();
                    char half = entry.getValue();
                    if (descriptionPunct.contains(full) && literal.indexOf(half) >= 0
                            && literal.indexOf(full) < 0) {
                        hard.add(LintViolation.hard(
                                "REF007",
                                "字符串字面量含半角 '" + half + "'，但题面 description 使用全角 '" + full + "'，"
                                        + "请保持与题面一致（中文标点）",
                                i + 1
                        ));
                        return;
                    }
                }
            }
        }
    }

    private Set<Character> collectFullWidthPunct(String text) {
        Set<Character> result = new HashSet<>();
        if (text == null) {
            return result;
        }
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            if (FULL_TO_HALF_PUNCT.containsKey(c)) {
                result.add(c);
            }
        }
        return result;
    }

    private void checkRef005(List<String> lines, List<LintViolation> soft) {
        for (String line : lines) {
            if (MAIN_GUARD.matcher(line).find()) {
                return;
            }
        }
        soft.add(LintViolation.soft(
                "REF005",
                "缺少 if __name__ == \"__main__\": 包裹；建议补齐以与教学规范一致",
                0
        ));
    }

    private void checkRef006(List<String> lines, List<LintViolation> soft) {
        long nonBlank = lines.stream().filter(l -> !l.isBlank()).count();
        if (nonBlank > SOFT_LINE_LIMIT) {
            soft.add(LintViolation.soft(
                    "REF006",
                    "reference solution 共 " + nonBlank + " 行非空，超过软上限 " + SOFT_LINE_LIMIT
                            + "，建议简化以贴近教学一致性",
                    0
            ));
        }
    }

    private boolean isPython(String language) {
        if (language == null) {
            return true;
        }
        String lower = language.toLowerCase(Locale.ROOT);
        return lower.startsWith("python");
    }

    /**
     * 删除每行 # 之后的注释（保留字符串字面量内的 #）。
     * 保持行号一致：注释行被替换为空字符串而不是被删除。
     */
    private List<String> stripComments(List<String> lines) {
        List<String> stripped = new ArrayList<>(lines.size());
        for (String line : lines) {
            stripped.add(removeInlineComment(line));
        }
        return stripped;
    }

    private String removeInlineComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
