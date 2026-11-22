package software.spool.provisioning;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TerraformNamesTest {
    @ParameterizedTest
    @CsvSource({"orders-crawler,orders-crawler", "orders.crawler,orders_crawler", "2nd-crawler,_2nd-crawler"})
    void of_givesANameTerraformAccepts(String name, String expected) {
        assertThat(TerraformNames.of(name)).isEqualTo(expected);
    }
}
