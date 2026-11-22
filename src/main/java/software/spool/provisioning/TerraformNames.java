package software.spool.provisioning;

/** A name Terraform accepts for a block: letters, digits, underscores and hyphens, not starting with a digit. */
final class TerraformNames {
    private TerraformNames() {}

    static String of(String name) {
        String valid = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return valid.isEmpty() || Character.isDigit(valid.charAt(0)) ? "_" + valid : valid;
    }
}
