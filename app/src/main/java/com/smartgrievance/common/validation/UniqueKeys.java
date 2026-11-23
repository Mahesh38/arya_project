package com.smartgrievance.common.validation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

public final class UniqueKeys {
    private static final Logger LOG = LoggerFactory.getLogger(UniqueKeys.class);
    private static final Map<String, String> FIELDS = Map.of(
            "uq_app_user_email", "email", "uq_app_user_mobile", "mobile",
            "uq_department_name", "name", "uq_category_name", "name", "uq_area_name", "name");
    private static final Pattern CONSTRAINT = Pattern.compile(
            "(?:^|[^a-z0-9_])(" + FIELDS.keySet().stream().sorted().collect(Collectors.joining("|"))
                    + ")(?=_index_|[^a-z0-9_]|$)");

    private UniqueKeys() {}

    public static RuntimeException translate(DuplicateKeyException exception) {
        String driverMessage = exception.getMostSpecificCause().getMessage();
        if (driverMessage == null) return exception;

        // Inspect only the constraint portion before the reported indexed value.
        String diagnostic = driverMessage.toLowerCase(Locale.ROOT);
        int mysqlKey = diagnostic.indexOf(" for key ");
        if (mysqlKey >= 0) diagnostic = diagnostic.substring(mysqlKey + " for key ".length());
        else {
            int h2Value = diagnostic.indexOf(" values ");
            if (h2Value >= 0) diagnostic = diagnostic.substring(0, h2Value);
        }

        var matcher = CONSTRAINT.matcher(diagnostic);
        if (!matcher.find()) return exception;
        String constraint = matcher.group(1);
        String field = FIELDS.get(constraint);
        LOG.info("UNIQUE_RACE constraint={}", constraint);
        return new FormRejectedException(List.of(new FormError(field, "validation." + field + ".taken")));
    }
}
