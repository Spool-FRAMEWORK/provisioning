package software.spool.provisioning.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.provisioning.GeneratedTerraform;
import software.spool.provisioning.TerraformGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TerraformSteps {
    private static final Pattern REACHED_PIECE = Pattern.compile("\\$\\{test_store\\.([^.]+)\\.arn}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path descriptor;
    private Optional<GeneratedTerraform> generated = Optional.empty();
    private Optional<Exception> failure = Optional.empty();

    public TerraformSteps() throws IOException {
        descriptor = Files.createTempDirectory("spool-provisioning").resolve("Spool.yaml");
    }

    @Given("the descriptor:")
    public void theDescriptor(String yaml) throws IOException {
        Files.writeString(descriptor, yaml);
    }

    @Given("the environment variable {string} is {string}")
    public void theEnvironmentVariableIs(String name, String value) {
        assertThat(name).isNotBlank();
        assertThat(value).isNotBlank();
    }

    @When("the Terraform of the descriptor is generated for the target {string}")
    public void theTerraformIsGeneratedForTheTarget(String target) {
        try {
            generated = Optional.of(TerraformGenerator.fromFileSystem().generate(descriptor, Optional.empty(),
                    ProvisioningTarget.valueOf(target.toUpperCase(Locale.ROOT))));
        } catch (Exception e) {
            failure = Optional.of(e);
        }
    }

    @Then("the Terraform has the resource {string} {string}")
    public void theTerraformHasTheResource(String type, String name) {
        assertThat(resources(type)).containsKey(name);
    }

    @Then("the Terraform has the resources {string} and {string} of {string}")
    public void theTerraformHasTheResourcesOf(String first, String second, String type) {
        assertThat(resources(type)).containsKeys(first, second);
    }

    @Then("the Terraform has the resource {string} {string} once")
    public void theTerraformHasTheResourceOnce(String type, String name) {
        assertThat(resources(type)).containsKey(name);
        String main = terraform().files().get(GeneratedTerraform.MAIN_FILE);
        assertThat(main.split("\"" + type + "\"", -1)).hasSize(2);
    }

    @Then("the Terraform has nothing for the piece {string}")
    public void theTerraformHasNothingForThePiece(String piece) {
        assertThat(allResources().values()).noneMatch(byName -> byName.containsKey(piece));
    }

    @Then("the generation fails")
    public void theGenerationFails() {
        assertThat(failure).as("the generation was expected to fail").isPresent();
    }

    @Then("the error names {string}")
    public void theErrorNames(String text) {
        assertThat(failure).hasValueSatisfying(error -> assertThat(error.getMessage()).contains(text));
    }

    @Then("the Terraform declares the variable {string}")
    @SuppressWarnings("unchecked")
    public void theTerraformDeclaresTheVariable(String name) {
        assertThat((Map<String, Object>) terraform().variables().get("variable")).containsKey(name);
    }

    @Then("no generated file contains {string}")
    public void noGeneratedFileContains(String text) {
        assertThat(terraform().files().values()).noneMatch(content -> content.contains(text));
    }

    @Then("the role of {string} reaches the piece {string} and nothing else")
    public void theRoleReachesThePiece(String module, String piece) throws IOException {
        assertThat(piecesReachedBy(module)).containsExactly(piece);
    }

    @Then("the role of {string} reaches the pieces {string} and {string} and nothing else")
    public void theRoleReachesThePieces(String module, String first, String second) throws IOException {
        assertThat(piecesReachedBy(module)).containsExactlyInAnyOrder(first, second);
    }

    private Set<String> piecesReachedBy(String module) throws IOException {
        String policy = (String) resources("aws_iam_role_policy").get(module).get("policy");
        Matcher reached = REACHED_PIECE.matcher(JSON.readTree(policy).findValues("Resource").toString());
        return reached.results().map(match -> match.group(1)).collect(Collectors.toSet());
    }

    private Map<String, Map<String, Object>> resources(String type) {
        Map<String, Map<String, Object>> byName = allResources().get(type);
        assertThat(byName).as("resources of type " + type).isNotNull();
        return byName;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Map<String, Object>>> allResources() {
        return (Map<String, Map<String, Map<String, Object>>>) terraform().main().getOrDefault("resource", Map.of());
    }

    private GeneratedTerraform terraform() {
        assertThat(failure).as(() -> "the generation was expected to work, but: "
                + failure.map(Exception::getMessage).orElse("")).isEmpty();
        return generated.orElseThrow();
    }
}
