package com.giveindia.backendchallenge;
/*
 * @author Gaurav Kumar
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Controller
public class HomeController {

    @Autowired
    private Environment env;

    @GetMapping("/")
    public String home(Model model) {
        String[] currencies = new String[]{"CAD", "HKD", "ISK", "PHP", "DKK", "HUF", "CZK", "AUD", "RON",
                "SEK", "IDR", "INR", "BRL", "RUB", "HRK", "JPY", "THB", "CHF", "SGD", "PLN", "BGN", "TRY",
                "CNY", "NOK", "NZD", "ZAR", "USD", "MXN", "ILS", "GBP", "KRW", "MYR"};
        model.addAttribute("currencies", currencies);
        return "home";
    }

    @PostMapping("/create-report")
    public String createReport(@RequestParam("csv-file") MultipartFile file, @RequestParam("curr") String cur, Model model) {
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()));
            String[] row = reader.readNext();
            Map<String, String[]> entries = new HashMap<>();
            String filename = System.nanoTime() + "report.csv";
            CSVWriter writer = new CSVWriter(new FileWriter(this.getClass().getClassLoader().getResource(".").getFile() + "static/" + filename));
            String[] values = new String[]{"Nonprofit", "Total amount", "Total Fee", "Number of Donations"};
            writer.writeNext(values);
            RestTemplate template = new RestTemplate();
            URI uri = UriComponentsBuilder.fromUriString("https://api.exchangerate-api.com/v4/latest/" + cur)
                    .build()
                    .toUri();
            ResponseEntity<String> res = template.getForEntity(uri, String.class);
            String discardFile = "discarded_" + filename;
            CSVWriter discardWriter = new CSVWriter(new FileWriter(this.getClass().getClassLoader().getResource(".").getFile() + "static/" + discardFile));
            discardWriter.writeNext(row);
            if (!res.getStatusCode().isError()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rates = mapper.readTree(res.getBody()).get("rates");
                while ((row = reader.readNext()) != null) {
                    Double amount;
                    Double fees;
                    if (rates.get(row[3]) != null) {
                        if (row[4].isEmpty()) {
                            amount = 0.0;
                        } else {
                            amount = Double.valueOf(row[4].replaceAll(",", "")) / (rates.get(row[3]).asDouble());
                        }
                        if (row[5].isEmpty()) {
                            fees = 0.0;
                        } else {
                            fees = Double.valueOf(row[5].replaceAll(",", "")) / (rates.get(row[3]).asDouble());
                        }

                        if (entries.containsKey(row[2])) {
                            values = entries.get(row[2]);
                            // total amount
                            values[1] = String.valueOf(Double.valueOf(values[1]) + amount);
                            // total fee
                            values[2] = String.valueOf(Double.valueOf(values[2]) + fees);
                            // no. of donations
                            values[3] = String.valueOf(Integer.valueOf(values[3]) + 1);
                        } else {
                            values = new String[]{row[2], String.valueOf(amount), String.valueOf(fees), "1"};
                            entries.put(row[2], values);
                        }
                    } else {
                        discardWriter.writeNext(row);
                    }
                }
                for (Map.Entry<String, String[]> entry : entries.entrySet()) {
                    writer.writeNext(entry.getValue());
                }
                writer.close();
                discardWriter.close();
                model.addAttribute("report", filename);
                model.addAttribute("dicard", discardFile);
            } else {
                model.addAttribute("error", "Api Access Error");
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        return "report-response";
    }


}
