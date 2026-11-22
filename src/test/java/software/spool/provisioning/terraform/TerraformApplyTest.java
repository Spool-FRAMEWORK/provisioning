package software.spool.provisioning.terraform;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.spool.core.model.spool.SpoolNode;
import software.spool.dsl.SpoolNodeDSL;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.provisioning.TerraformGenerator;

import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What is generated for the local target brings the pieces up for real, and a node starts against them. Needs
 * Terraform and Docker. Its containers have names and ports of their own, so they do not meet the test containers
 * other work uses, and they are destroyed at the end.
 */
@Tag("terraform")
@Tag("docker")
class TerraformApplyTest {
    private static final int LOCALSTACK_PORT = 4576;
    private static final int KAFKA_PORT = 9192;
    private static final String ENDPOINT = "http://localhost:" + LOCALSTACK_PORT;
    private static final String REGION = "eu-west-1";
    private static final String[] OWN_CONTAINERS = {
            "-var=localstack_container_name=spool-tf-localstack", "-var=kafka_container_name=spool-tf-kafka"};

    @TempDir
    Path folder;

    private TerraformCli terraform;
    private Path out;
    private Optional<SpoolNode> node = Optional.empty();

    @Test
    void apply_bringsThePiecesUpAndANodeStartsAgainstThem() throws Exception {
        terraform = TerraformCli.installed().orElse(null);
        Assumptions.assumeTrue(terraform != null, "Terraform not installed: set TERRAFORM_BINARY or put terraform on the path");
        Path descriptor = Files.writeString(folder.resolve("Spool.yaml"), """
                server: {port: %d}
                infrastructure:
                  bus: {kind: EVENT_BUS, type: KAFKA, configuration: {bootstrap.servers: "localhost:%d"}}
                  inbox: {kind: INBOX, type: S3, configuration: {bucket: spool-tf-inbox, region: %s, endpoint: "%s"}}
                  lake: {kind: DATA_LAKE, type: S3, configuration: {bucket: spool-tf-lake, region: %s, endpoint: "%s"}}
                modules:
                  - ingester: {type: REACTIVE, id: orders-ingester}
                """.formatted(freePort(), KAFKA_PORT, REGION, ENDPOINT, REGION, ENDPOINT));
        out = TerraformGenerator.fromFileSystem().generate(descriptor, Optional.empty(), ProvisioningTarget.LOCAL)
                .writeTo(folder.resolve("tf"));

        terraform.run(out, "init", "-input=false");
        terraform.run(out, withOwnContainers("apply", "-auto-approve", "-input=false"));
        node = Optional.of(SpoolNodeDSL.fromDescriptor(descriptor));
        node.get().start();

        try (S3Client s3 = s3()) {
            assertThat(s3.listBuckets().buckets()).extracting(bucket -> bucket.name())
                    .contains("spool-tf-inbox", "spool-tf-lake");
        }
        try (Admin kafka = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + KAFKA_PORT))) {
            assertThat(kafka.describeCluster().nodes().get(1, TimeUnit.MINUTES)).isNotEmpty();
        }
    }

    @AfterEach
    void destroy() throws Exception {
        node.ifPresent(SpoolNode::stop);
        if (out != null) terraform.run(out, withOwnContainers("destroy", "-auto-approve", "-input=false"));
    }

    private static String[] withOwnContainers(String... arguments) {
        String[] all = new String[arguments.length + OWN_CONTAINERS.length];
        System.arraycopy(arguments, 0, all, 0, arguments.length);
        System.arraycopy(OWN_CONTAINERS, 0, all, arguments.length, OWN_CONTAINERS.length);
        return all;
    }

    private static S3Client s3() {
        return S3Client.builder()
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
