package com.github.ar1ls.weft.sandbox.loading.billing;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.sandbox.loading.api.ModuleAction;
import com.github.ar1ls.weft.sandbox.loading.api.ModuleContext;

@Bean
public class BillingAction implements ModuleAction {
    private final ModuleContext context;

    public BillingAction(ModuleContext context) {
        this.context = context;
    }

    @Override
    public String description() {
        return context.moduleId() + " invoices: " + context.dataDirectory().resolve("invoices");
    }
}
