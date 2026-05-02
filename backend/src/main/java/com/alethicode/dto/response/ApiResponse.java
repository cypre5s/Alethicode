package com.alethicode.dto.response;

public record ApiResponse<T>(String error, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(null, data);
    }

    public static <T> ApiResponse<T> error(String error, T data) {
        return new ApiResponse<>(error, data);
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(error, null);
    }
}
