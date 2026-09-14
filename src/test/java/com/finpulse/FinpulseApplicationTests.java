package com.finpulse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FinpulseApplicationTests {

    @Disabled("Requires a live database; will be covered by the Testcontainers integration-test phase later")
    @Test
    void contextLoads() {
    }

}
