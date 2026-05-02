package com.alethicode.service.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NatsStreamSupport {

    private NatsStreamSupport() {
    }

    public static void ensureStream(
            JetStreamManagement management,
            String streamName,
            List<String> requiredSubjects
    ) throws IOException, JetStreamApiException {
        requireNonBlank(streamName, "streamName is required");
        List<String> subjects = normalizeSubjects(requiredSubjects);
        StreamConfiguration targetConfiguration = StreamConfiguration.builder()
                .name(streamName.strip())
                .storageType(StorageType.File)
                .subjects(subjects)
                .build();

        try {
            reconcileExistingStream(management, streamName.strip(), subjects, targetConfiguration);
        } catch (JetStreamApiException exception) {
            if (!isStreamNotFound(exception)) {
                throw exception;
            }
            addMissingStream(management, streamName.strip(), subjects, targetConfiguration);
        }
    }

    public static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.strip();
    }

    private static void addMissingStream(
            JetStreamManagement management,
            String streamName,
            List<String> requiredSubjects,
            StreamConfiguration targetConfiguration
    ) throws IOException, JetStreamApiException {
        try {
            management.addStream(targetConfiguration);
        } catch (JetStreamApiException exception) {
            if (!isStreamAlreadyExists(exception)) {
                throw exception;
            }
            reconcileExistingStream(management, streamName, requiredSubjects, targetConfiguration);
        }
    }

    private static void reconcileExistingStream(
            JetStreamManagement management,
            String streamName,
            List<String> requiredSubjects,
            StreamConfiguration targetConfiguration
    ) throws IOException, JetStreamApiException {
        StreamConfiguration existingConfiguration = management.getStreamInfo(streamName).getConfiguration();
        if (existingConfiguration == null) {
            management.updateStream(targetConfiguration);
            return;
        }
        Set<String> existingSubjects = normalizedSubjectSet(existingConfiguration.getSubjects());
        if (existingSubjects.containsAll(requiredSubjects)) {
            return;
        }
        List<String> mergedSubjects = new ArrayList<>(existingSubjects);
        for (String requiredSubject : requiredSubjects) {
            if (!mergedSubjects.contains(requiredSubject)) {
                mergedSubjects.add(requiredSubject);
            }
        }
        management.updateStream(StreamConfiguration.builder(existingConfiguration)
                .subjects(mergedSubjects)
                .build());
    }

    private static List<String> normalizeSubjects(List<String> requiredSubjects) {
        if (requiredSubjects == null || requiredSubjects.isEmpty()) {
            throw new IllegalStateException("NATS subject is required");
        }
        List<String> normalized = new ArrayList<>();
        for (String subject : requiredSubjects) {
            requireNonBlank(subject, "NATS subject is required");
            String stripped = subject.strip();
            if (!normalized.contains(stripped)) {
                normalized.add(stripped);
            }
        }
        return normalized;
    }

    private static Set<String> normalizedSubjectSet(List<String> subjects) {
        Set<String> normalized = new LinkedHashSet<>();
        if (subjects == null) {
            return normalized;
        }
        for (String subject : subjects) {
            if (subject != null && !subject.isBlank()) {
                normalized.add(subject.strip());
            }
        }
        return normalized;
    }

    private static boolean isStreamNotFound(JetStreamApiException exception) {
        return exception.getApiErrorCode() == 10059
                || containsIgnoreCase(exception.getErrorDescription(), "stream not found");
    }

    private static boolean isStreamAlreadyExists(JetStreamApiException exception) {
        String description = exception.getErrorDescription();
        return exception.getApiErrorCode() == 10058
                || containsIgnoreCase(description, "already exists")
                || containsIgnoreCase(description, "already in use");
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }
}
