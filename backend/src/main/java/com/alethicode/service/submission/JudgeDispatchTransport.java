package com.alethicode.service.submission;

import java.util.Map;

public interface JudgeDispatchTransport {

    String transportName();

    String publish(Map<String, String> fields);
}
