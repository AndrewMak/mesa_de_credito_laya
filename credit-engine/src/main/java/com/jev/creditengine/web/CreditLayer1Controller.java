package com.jev.creditengine.web;

import com.jev.creditengine.dto.ClientProfile;
import com.jev.creditengine.rules.CreditRuleEngine;
import com.jev.creditengine.rules.Layer1Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit")
public class CreditLayer1Controller {

    private final CreditRuleEngine engine;

    public CreditLayer1Controller(CreditRuleEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/layer1")
    public Layer1Result evaluate(@RequestBody ClientProfile profile) {
        return engine.evaluate(profile);
    }
}
