package software.spool.provisioning;

import software.spool.dsl.DescriptorLoader;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * The environment the descriptor is loaded with: every {@code ${env:NAME}} becomes the Terraform variable
 * {@code ${var.NAME}}, so no value of the real environment reaches the generated files. The profile is never read
 * from it: it is given when generating.
 */
final class EnvironmentAsVariables implements UnaryOperator<String> {
    private final Set<String> names = new TreeSet<>();

    @Override
    public String apply(String name) {
        if (name.equals(DescriptorLoader.PROFILE_VARIABLE)) return null;
        names.add(name);
        return "${var." + name + "}";
    }

    Set<String> names() {
        return Collections.unmodifiableSet(names);
    }
}
