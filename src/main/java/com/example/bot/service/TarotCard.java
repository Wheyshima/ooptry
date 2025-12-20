package com.example.bot.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TarotCard(
        String name,
        String upright,
        String reversed
) {
    @JsonCreator
    public TarotCard(
            @JsonProperty("name") String name,
            @JsonProperty("upright") String upright,
            @JsonProperty("reversed") String reversed
    ) {
        this.name = name;
        this.upright = upright;
        this.reversed = reversed;
    }
}