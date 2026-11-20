package br.com.freelas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FreelasApplication {
    public static void main(String[] args) {
        // Datas gravadas e devolvidas em UTC (Padrão da API, item 7.2)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(FreelasApplication.class, args);
    }
}
