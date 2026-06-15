package com.careermate.agent.tool;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentToolParameter {

    String name;
    AgentToolParameterType type;
    @Builder.Default
    boolean required = false;
    String description;
    String defaultValue;
    @Singular("enumValue")
    List<String> enumValues;
}
