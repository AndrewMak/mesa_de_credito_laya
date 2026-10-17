package com.jev.creditengine.web;

import com.jev.creditengine.dto.TextPayload;
import com.jev.creditengine.llm.Layer3Result;
import com.jev.creditengine.llm.OpenRouterCreditAnalyzer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit")
public class CreditLayer3Controller {

    private final OpenRouterCreditAnalyzer analyzer;

    public CreditLayer3Controller(OpenRouterCreditAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @PostMapping("/layer3")
    public Layer3Result analyze(@RequestBody TextPayload payload) {
        String text = payload == null ? "" : payload.text();
        return analyzer.analyze(text);
    }
}
