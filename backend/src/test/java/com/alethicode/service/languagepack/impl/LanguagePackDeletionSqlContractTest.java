package com.alethicode.service.languagepack.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagePackDeletionSqlContractTest {

    @Test
    void deleteLanguagePackShouldUseExistingInitBatchRunTable() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/alethicode/service/languagepack/impl/LanguagePackExportImportServiceImpl.java"
        ));

        assertThat(source).contains("DELETE FROM language_pack_init_batch_run WHERE task_id = ?");
        assertThat(source).doesNotContain("language_pack_batch_run");
    }
}
