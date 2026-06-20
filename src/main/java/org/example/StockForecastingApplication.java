package org.example;

import org.example.parser.wb.WildberriesParserProperties;
import org.example.forecast.NeuralForecastProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({WildberriesParserProperties.class, NeuralForecastProperties.class})
public class StockForecastingApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockForecastingApplication.class, args);
    }
}
