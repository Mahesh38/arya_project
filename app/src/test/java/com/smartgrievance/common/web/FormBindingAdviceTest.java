package com.smartgrievance.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyEditor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.WebDataBinder;

@Tag("unit")
class FormBindingAdviceTest {
    @ParameterizedTest
    @CsvSource({
            "name,'  Mahesh   Joshi  ',Mahesh Joshi",
            "email,' Mahesh.J@Example.COM ',mahesh.j@example.com",
            "mobile,'+91 98765-43210',9876543210",
            "mobile,'09876543210',9876543210",
            "mobile,'919876543210',9876543210",
            "original,grv-2026-000001,GRV-2026-000001",
            "originalNo,grv-2026-000001,GRV-2026-000001",
            "code,'482 913',482913",
            "password,'S3cretPass ','S3cretPass '",
            "confirmPassword,'S3cretPass ','S3cretPass '"
    })
    @DisplayName("TC-VE-12 normalize fields before validation while preserving passwords")
    void normalize(String property, String input, String expected) {
        assertThat(bind(property, input)).isEqualTo(expected);
        assertThat(bind("description", " Test  two\r\nLines\t. ")).isEqualTo("Test  two\nLines\t.");
        assertThat(bind("name", " \t\r\n ")).isNull();
        assertThat(bind("q", "x".repeat(105))).isEqualTo("x".repeat(100));
        assertThat(bind("password", "")).isEqualTo("");
    }

    private Object bind(String property, String input) {
        var binder = new WebDataBinder(new Object());
        new FormBindingAdvice().bind(binder, new MockHttpServletRequest());
        PropertyEditor editor = binder.findCustomEditor(String.class, property);
        editor.setAsText(input);
        return editor.getValue();
    }
}
