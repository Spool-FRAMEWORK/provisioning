package software.spool.provisioning.terraform;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.provisioning.TerraformGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Terraform itself accepts what is generated with the plugins of the framework. Needs Terraform and the network. */
@Tag("terraform")
class TerraformValidateTest {
    private static final String LOCAL = """
            infrastructure:
              bus: {kind: EVENT_BUS, type: KAFKA, configuration: {bootstrap.servers: "localhost:9092"}}
              inbox: {kind: INBOX, type: S3, configuration: {bucket: spool-inbox, region: eu-west-1, endpoint: "http://localhost:4566"}}
              lake: {kind: DATA_LAKE, type: S3, configuration: {bucket: "${env:SPOOL_LAKE_BUCKET}", region: eu-west-1, endpoint: "http://localhost:4566"}}
            modules:
              - ingester: {type: REACTIVE, id: orders-ingester}
            """;
    private static final String AWS = """
            infrastructure:
              bus: {kind: EVENT_BUS, type: IN_MEMORY}
              inbox: {kind: INBOX, type: S3, configuration: {bucket: spool-inbox, region: eu-west-1, endpoint: "https://s3.eu-west-1.amazonaws.com"}}
              lake: {kind: DATA_LAKE, type: S3, configuration: {bucket: spool-lake, region: eu-west-1, endpoint: "https://s3.eu-west-1.amazonaws.com"}}
            modules:
              - ingester: {type: REACTIVE, id: orders-ingester}
              - janitor: {id: orders-janitor}
            """;

    @TempDir
    Path folder;

    @ParameterizedTest
    @CsvSource({"LOCAL", "AWS"})
    void validate_acceptsWhatIsGenerated(ProvisioningTarget target) throws Exception {
        TerraformCli terraform = TerraformCli.installed().orElse(null);
        Assumptions.assumeTrue(terraform != null, "Terraform not installed: set TERRAFORM_BINARY or put terraform on the path");
        Path descriptor = Files.writeString(folder.resolve("Spool.yaml"), target == ProvisioningTarget.LOCAL ? LOCAL : AWS);
        Path out = TerraformGenerator.fromFileSystem().generate(descriptor, Optional.empty(), target).writeTo(folder.resolve("tf"));

        terraform.run(out, "init", "-backend=false", "-input=false");

        assertThat(terraform.run(out, "validate")).contains("The configuration is valid");
    }
}
