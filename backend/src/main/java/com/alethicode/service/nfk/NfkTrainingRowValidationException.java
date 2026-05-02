package com.alethicode.service.nfk;

/**
 * NFK 训练 CSV 行级 schema 校验失败时抛出。
 *
 * <p>消息固定写出 1-based 行号 + 第一条 schema 违规信息，便于在导出大量行时快速定位
 * 第一条非法数据；同时让 Python 训练侧的 {@code jsonschema} 校验报错语义在两侧保持一致。
 */
public class NfkTrainingRowValidationException extends RuntimeException {

    private final long rowNumber;

    public NfkTrainingRowValidationException(long rowNumber, String message) {
        super("NFK training row " + rowNumber + " violates contract: " + message);
        this.rowNumber = rowNumber;
    }

    public long getRowNumber() {
        return rowNumber;
    }
}
