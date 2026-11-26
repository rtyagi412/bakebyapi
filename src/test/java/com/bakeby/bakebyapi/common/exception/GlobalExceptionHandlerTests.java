package com.bakeby.bakebyapi.common.exception;

import com.bakeby.bakebyapi.common.config.SecurityConfig;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web slice test (no database) of the global ProblemDetail error mapping via a test-only controller. */
@WebMvcTest(controllers = {GlobalExceptionHandlerTests.ProbeController.class,
        GlobalExceptionHandlerTests.MethodValidationController.class})
@Import({GlobalExceptionHandler.class, SecurityConfig.class, GlobalExceptionHandlerTests.ProbeController.class,
        GlobalExceptionHandlerTests.MethodValidationController.class})
class GlobalExceptionHandlerTests {

    record CreateBody(@NotBlank(message = "must not be blank") String name,
                      @PositiveOrZero(message = "must be zero or positive") Integer price) {
    }

    static class CustomException extends ApiException {
        CustomException(String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable thing", message);
        }
    }

    /** No class-level @Validated, so Spring's built-in method validation raises HandlerMethodValidationException. */
    @RestController
    static class MethodValidationController {
        @GetMapping("/mv/search")
        String search(@RequestParam @Size(min = 2, message = "too short") String q) {
            return q;
        }

        @GetMapping("/mv/items/{id}")
        String item(@PathVariable @Positive(message = "must be positive") Long id) {
            return String.valueOf(id);
        }

        @GetMapping("/mv/both/{id}")
        String both(@PathVariable @Positive(message = "must be positive") Long id,
                    @RequestParam @Size(min = 2, message = "too short") String q) {
            return q + id;
        }

        @PostMapping("/mv/body")
        String body(@RequestBody @Valid CreateBody body, @RequestParam @Size(min = 2, message = "too short") String q) {
            return "ok";
        }
    }

    @RestController
    @Validated
    static class ProbeController {
        @PostMapping("/probe/validate")
        String validate(@RequestBody @Valid CreateBody body) {
            return "ok";
        }

        @GetMapping("/probe/constraint")
        String constraint(@RequestParam @Size(min = 3, message = "too short") String q) {
            return q;
        }

        @GetMapping("/probe/custom")
        String custom() {
            throw new CustomException("cannot process");
        }

        @GetMapping("/probe/bad-request")
        String badRequest() {
            throw new BadRequestException("bad thing");
        }

        @GetMapping("/probe/not-found")
        String notFound() {
            throw new NotFoundException("Product 7 not found");
        }

        @GetMapping("/probe/conflict")
        String conflict() {
            throw new ConflictException("Slug already exists");
        }

        @GetMapping("/probe/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates constraint uq_secret_table");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/probe/int")
        String intParam(@RequestParam int n) {
            return String.valueOf(n);
        }

        @GetMapping("/probe/required")
        String required(@RequestParam String must) {
            return must;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsErrorsKeyedByParamNameWhenRequestParamFailsMethodValidation() throws Exception {
        problem(mockMvc.perform(get("/mv/search").param("q", "a")), 400)
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").value("too short"))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void returnsErrorsKeyedByParamNameWhenPathVariableFailsMethodValidation() throws Exception {
        problem(mockMvc.perform(get("/mv/items/-5")), 400)
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.id").value("must be positive"))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void returnsAllParameterErrorsWhenSeveralParametersFailMethodValidation() throws Exception {
        problem(mockMvc.perform(get("/mv/both/0").param("q", "a")), 400)
                .andExpect(jsonPath("$.errors.id").value("must be positive"))
                .andExpect(jsonPath("$.errors.q").value("too short"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void returnsOkWhenMethodValidatedParametersAreValid() throws Exception {
        mockMvc.perform(get("/mv/search").param("q", "ab")).andExpect(status().isOk());
    }

    @Test
    void returnsOnlyParamErrorWhenValidBodyAccompaniesInvalidRequestParam() throws Exception {
        problem(mockMvc.perform(post("/mv/body").param("q", "a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"price\":1}")), 400)
                .andExpect(jsonPath("$.errors.q").value("too short"))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void returnsFieldKeyedErrorsWhenInvalidBodyAccompaniesMethodValidatedParam() throws Exception {
        problem(mockMvc.perform(post("/mv/body").param("q", "ab").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":1}")), 400)
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));
    }

    private ResultActions problem(ResultActions r, int expected) throws Exception {
        return r.andExpect(status().is(expected))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expected));
    }

    @Test
    void returnsBadRequestWithErrorsMapWhenBodyValidationFails() throws Exception {
        problem(mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":-1}")), 400)
                .andExpect(jsonPath("$.errors.name").value("must not be blank"))
                .andExpect(jsonPath("$.errors.price").value("must be zero or positive"));
    }

    @Test
    void returnsBadRequestWhenRequestParameterViolatesConstraint() throws Exception {
        // Surfaces as ConstraintViolationException or HandlerMethodValidationException depending on the Spring version.
        problem(mockMvc.perform(get("/probe/constraint").param("q", "a")), 400);
    }

    record Nested(@Size(min = 2, message = "too short") String name) {
    }

    static class Target {
        @Size(min = 3, message = "too short")
        String q;
        @Positive(message = "must be positive")
        Long id;
    }

    static class Holder {
        @Valid
        Nested nested = new Nested("a");
    }

    @Test
    void mapsConstraintViolationExceptionToErrorsKeyedByLastPathNode() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        Target t = new Target();
        t.q = "a";
        t.id = -1L;
        Set<ConstraintViolation<Target>> violations = validator.validate(t);
        assertThat(violations).hasSize(2);

        var pd = new GlobalExceptionHandler().handleConstraintViolation(new ConstraintViolationException(violations));
        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getTitle()).isEqualTo("Validation failed");
        assertThat(pd.getDetail()).isEqualTo("Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors).containsOnly(Map.entry("q", "too short"), Map.entry("id", "must be positive"));
    }

    @Test
    void keysNestedConstraintViolationByLeafPropertyName() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(new Holder());
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("nested.name");

        var pd = new GlobalExceptionHandler().handleConstraintViolation(new ConstraintViolationException(violations));
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors).containsOnly(Map.entry("name", "too short"));
    }

    @Test
    void returnsErrorsKeyedByParameterNameWhenClassLevelValidatedParamViolates() throws Exception {
        String body = problem(mockMvc.perform(get("/probe/constraint").param("q", "a")), 400)
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").value("too short"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("constraint.q").doesNotContain("arg0");
    }

    @Test
    void returnsCustomStatusAndTitleWhenApiExceptionSubclassThrown() throws Exception {
        problem(mockMvc.perform(get("/probe/custom")), 422)
                .andExpect(jsonPath("$.title").value("Unprocessable thing"))
                .andExpect(jsonPath("$.detail").value("cannot process"));
    }

    @Test
    void returnsBadRequestWhenBadRequestExceptionThrown() throws Exception {
        problem(mockMvc.perform(get("/probe/bad-request")), 400)
                .andExpect(jsonPath("$.detail").value("bad thing"));
    }

    @Test
    void returnsBadRequestWhenJsonBodyMalformed() throws Exception {
        problem(mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{not json")), 400);
    }

    @Test
    void returnsBadRequestWhenParameterTypeMismatches() throws Exception {
        problem(mockMvc.perform(get("/probe/int").param("n", "abc")), 400);
    }

    @Test
    void returnsBadRequestWhenRequiredParameterMissing() throws Exception {
        problem(mockMvc.perform(get("/probe/required")), 400);
    }

    @Test
    void returnsNotFoundWhenNotFoundExceptionThrown() throws Exception {
        problem(mockMvc.perform(get("/probe/not-found")), 404)
                .andExpect(jsonPath("$.detail").value("Product 7 not found"));
    }

    @Test
    void returnsConflictWhenConflictExceptionThrown() throws Exception {
        problem(mockMvc.perform(get("/probe/conflict")), 409)
                .andExpect(jsonPath("$.detail").value("Slug already exists"));
    }

    @Test
    void returnsConflictWithoutLeakingSqlWhenDataIntegrityViolation() throws Exception {
        problem(mockMvc.perform(get("/probe/integrity")), 409)
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("uq_secret_table"))));
    }

    @Test
    void returnsGenericInternalServerErrorWithoutLeakingUnexpectedExceptionDetails() throws Exception {
        problem(mockMvc.perform(get("/probe/boom")), 500)
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret internal detail"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("at com."))));
    }

    @Test
    void returnsProblemJsonNotFoundForUnknownRoute() throws Exception {
        problem(mockMvc.perform(get("/probe/does-not-exist")), 404);
    }

    @Test
    void returnsProblemJsonMethodNotAllowedForWrongMethod() throws Exception {
        problem(mockMvc.perform(post("/probe/not-found")), 405);
    }
}
