package software.spool.provisioning;

/** The Terraform of a descriptor cannot be generated. The message starts with the path of the piece or module. */
public class ProvisioningException extends RuntimeException {
    public ProvisioningException(String path, String problem) {
        super(path + ": " + problem);
    }
}
