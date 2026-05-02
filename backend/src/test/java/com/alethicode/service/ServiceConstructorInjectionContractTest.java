package com.alethicode.service;

import com.alethicode.service.aitutor.review.ErrorReviewPackageService;
import com.alethicode.service.aitutor.impl.AITutorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceConstructorInjectionContractTest {

    @Test
    void servicesWithMultiplePublicConstructorsMustDeclareSingleAutowiredInjectionEntry() {
        assertUnambiguousSpringConstructor(AITutorServiceImpl.class);
        assertUnambiguousSpringConstructor(ErrorReviewPackageService.class);
    }

    private void assertUnambiguousSpringConstructor(Class<?> beanType) {
        Constructor<?>[] publicConstructors = beanType.getConstructors();
        if (publicConstructors.length <= 1) {
            return;
        }

        long autowiredConstructors = Arrays.stream(publicConstructors)
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertThat(autowiredConstructors)
                .as("%s 存在多个 public 构造器时，必须且只能有一个使用 @Autowired 作为 Spring 注入入口", beanType.getName())
                .isEqualTo(1);
    }
}
