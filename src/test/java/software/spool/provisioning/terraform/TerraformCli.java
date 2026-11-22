package software.spool.provisioning.terraform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Runs the Terraform installed on the machine: the one in {@code TERRAFORM_BINARY}, or {@code terraform} on the path. */
final class TerraformCli {
    private static final String BINARY_VARIABLE = "TERRAFORM_BINARY";
    private static final long TIMEOUT_MINUTES = 10;

    private final String binary;

    private TerraformCli(String binary) {
        this.binary = binary;
    }

    /** The Terraform of the machine, or nothing when there is none, so that its tests are skipped. */
    static Optional<TerraformCli> installed() {
        String binary = Optional.ofNullable(System.getenv(BINARY_VARIABLE)).orElse("terraform");
        try {
            Process version = new ProcessBuilder(binary, "version").redirectErrorStream(true).start();
            version.getInputStream().readAllBytes();
            return version.waitFor() == 0 ? Optional.of(new TerraformCli(binary)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Runs a Terraform command in the folder and fails with its output when it does not end well. */
    String run(Path folder, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(binary, "-chdir=" + folder));
        command.addAll(List.of(arguments));
        command.add("-no-color");
        Path output = Files.createTempFile("terraform", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        String log = Files.readString(output, StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("terraform " + String.join(" ", arguments) + " did not finish in "
                    + TIMEOUT_MINUTES + " minutes:\n" + log);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("terraform " + String.join(" ", arguments) + " failed:\n" + log);
        }
        return log;
    }
}
