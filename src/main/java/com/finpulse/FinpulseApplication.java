package com.finpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinpulseApplication {

    public static void main(String[] args) {
                int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Cores: " + cores);
        System.out.println("Recommended threads: " + (cores * 2));

        SpringApplication.run(FinpulseApplication.class, args);
    }

}
