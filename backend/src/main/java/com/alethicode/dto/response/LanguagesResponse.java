package com.alethicode.dto.response;

import java.util.List;

public record LanguagesResponse(
        List<String> languages,
        List<String> spjLanguages
) {
}
