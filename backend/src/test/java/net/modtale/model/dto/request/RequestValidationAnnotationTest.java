package net.modtale.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationAnnotationTest {

    @Test
    void notBlankIsOnlyAppliedToTextValues() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);

        for (var candidate : scanner.findCandidateComponents("net.modtale.model.dto.request")) {
            Class<?> requestType = Class.forName(candidate.getBeanClassName());
            for (Field field : requestType.getDeclaredFields()) {
                assertValidNotBlankField(requestType, field);
                assertValidNotBlankTypeArguments(requestType, field);
            }
        }
    }

    private static void assertValidNotBlankField(Class<?> requestType, Field field) {
        if (!field.isAnnotationPresent(NotBlank.class)) {
            return;
        }

        assertTrue(
                CharSequence.class.isAssignableFrom(field.getType()),
                () -> requestType.getSimpleName() + "." + field.getName()
                        + " uses @NotBlank on " + field.getType().getSimpleName()
                        + "; use @NotNull for enum/object fields."
        );
    }

    private static void assertValidNotBlankTypeArguments(Class<?> requestType, Field field) {
        if (!(field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterizedType)) {
            return;
        }

        for (AnnotatedType argument : parameterizedType.getAnnotatedActualTypeArguments()) {
            if (!argument.isAnnotationPresent(NotBlank.class)) {
                continue;
            }

            Type argumentType = argument.getType();
            assertTrue(
                    Collection.class.isAssignableFrom(field.getType())
                            && argumentType instanceof Class<?> argumentClass
                            && CharSequence.class.isAssignableFrom(argumentClass),
                    () -> requestType.getSimpleName() + "." + field.getName()
                            + " uses @NotBlank on a non-text type argument."
            );
        }
    }
}
