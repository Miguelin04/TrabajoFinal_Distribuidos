package com.example.demo;
import org.springframework.web.client.RestTemplate;
public class TestRest {
    public static void main(String[] args) {
        try {
            RestTemplate rest = new RestTemplate();
            rest.postForObject("http://127.0.0.1:8085/api/node/requestAddDonor?name=Juan Perez&bloodType=O+", null, String.class);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
