package com.querykeeper.collector;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.regex.Pattern;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DetachedAccessExceptionCatcher {

    private static final Logger log = LoggerFactory.getLogger(DetachedAccessExceptionCatcher.class);

    @AfterThrowing(pointcut = "(" +
            "execution(* com..*(..)) || " +
            "execution(* kr..*(..)) || " +
            "execution(* io..*(..)) || " +
            "execution(* net..*(..)) || " +
            "execution(* me..*(..)) || " +
            "execution(* dev..*(..)) || " +
            "execution(* edu..*(..)) || " +
            "execution(* example..*(..)) || " +
            "execution(* my..*(..)) || " +
            "execution(* test..*(..)) || " +
            "execution(* core..*(..)) || " +
            "execution(* xyz..*(..))" +
            ") && !execution(* org.springframework..*(..))", throwing = "ex")
    public void afterThrowingDetachedAccess(Exception ex) {
        if (!(ex instanceof LazyInitializationException))
            return;

        String message = ex.getMessage();
        Pattern p = Pattern.compile(".*: ([\\w\\.]+)\\.([\\w]+).*");
        java.util.regex.Matcher m = p.matcher(message);

        if (!m.matches()) {
            log.debug("[QueryKeeper] ▶ ExpectDetachedAccess X Unmatched format: {}", message);
            return;
        }

        String className = m.group(1);
        String fieldName = m.group(2);

        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            Class<?> targetType = resolveCollectionGenericType(field);

            if (targetType != null) {
                String entityName = targetType.getSimpleName();

                String rootEntity = guessRootEntity(className);
                String fullPath = rootEntity + "." + fieldName;

                QueryKeeperContext.getCurrent().markDetachedAccess(
                        entityName,
                        fieldName,
                        rootEntity,
                        fullPath);

            }
        } catch (Exception e) {
            log.debug("[QueryKeeper] ▶ ExpectDetachedAccess X Failed to extract info from: {}", message);
        }
    }

    private Class<?> resolveCollectionGenericType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?>) {
                return (Class<?>) args[0];
            }
        }
        return null;
    }

    private String guessRootEntity(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}
