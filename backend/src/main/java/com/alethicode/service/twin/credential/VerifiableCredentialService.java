package com.alethicode.service.twin.credential;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerifiableCredentialService {

    private final JdbcTemplate jdbcTemplate;

    public VerifiableCredentialService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> issueCredential(Long userId, String credentialType, Map<String, Object> params) {
        String credentialId = "urn:uuid:" + UUID.randomUUID();
        String issuerDid = "did:web:alethicode.com";
        String subjectDid = "did:web:alethicode.com:users:" + userId;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@context", List.of("https://www.w3.org/ns/credentials/v2"));
        payload.put("type", List.of("VerifiableCredential", credentialType));
        payload.put("issuer", issuerDid);
        payload.put("credentialSubject", Map.of("id", subjectDid, "achievement", params));

        String proofJws = "placeholder-jws-" + UUID.randomUUID().toString().substring(0, 8);

        Long vcId = jdbcTemplate.queryForObject("""
            INSERT INTO verifiable_credential (user_id, credential_id, credential_type, issuer_did, subject_did, payload_jsonld, proof_jws)
            VALUES (?, ?, ?, ?, ?, ?::JSONB, ?)
            RETURNING id
            """, Long.class, userId, credentialId, credentialType, issuerDid, subjectDid,
                "{}", proofJws);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vc_id", vcId);
        result.put("credential_id", credentialId);
        result.put("credential_type", credentialType);
        result.put("issuer_did", issuerDid);
        result.put("subject_did", subjectDid);
        return result;
    }

    public List<Map<String, Object>> listCredentials(Long userId) {
        return jdbcTemplate.query("""
            SELECT id, credential_id, credential_type, issuer_did, subject_did, issued_at, expires_at
            FROM verifiable_credential
            WHERE user_id = ? AND revoked_at IS NULL
            ORDER BY issued_at DESC
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vc_id", rs.getLong("id"));
            row.put("credential_id", rs.getString("credential_id"));
            row.put("credential_type", rs.getString("credential_type"));
            row.put("issued_at", rs.getTimestamp("issued_at").toInstant().toString());
            return row;
        }, userId);
    }
}
