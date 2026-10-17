package com.jev.creditengine.rules;

import com.jev.creditengine.dto.ClientProfile;

public interface Layer1Evaluator {
    Layer1Result evaluate(ClientProfile client);
}
