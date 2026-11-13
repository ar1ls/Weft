package com.github.ar1ls.weft.discovery.fixtures.condition;

import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnEnv;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnMissingBean;
import com.github.ar1ls.weft.api.annotations.condition.ConditionalOnProperty;

public class ConditionSamples {

    @ConditionalOnProperty(prefix = "app", name = "enabled")
    public void enabled() {
    }

    @ConditionalOnProperty(prefix = "app", name = {"mode", "region"}, havingValue = "dev")
    public void multiple() {
    }

    @ConditionalOnProperty(name = "optional", havingValue = "yes", matchIfMissing = true)
    public void optional() {
    }

    @ConditionalOnEnv(name = "APP_MODE", havingValue = "dev")
    public void environment() {
    }

    @ConditionalOnProperty(name = "enabled")
    @ConditionalOnEnv(name = "APP_MODE", havingValue = "dev")
    public void combined() {
    }

    @ConditionalOnProperty(name = {})
    public void emptyNames() {
    }

    @ConditionalOnEnv(name = {"VALID", " "})
    public void blankName() {
    }

    @ConditionalOnMissingBean
    public void fallback() {
    }

    public void unconditional() {
    }

}
