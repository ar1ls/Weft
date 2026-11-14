package com.github.ar1ls.weft.sandbox.loading.reports;

import com.github.ar1ls.weft.api.annotations.Bean;
import com.github.ar1ls.weft.sandbox.loading.api.ModuleAction;
import com.github.ar1ls.weft.sandbox.loading.api.ModuleContext;

@Bean
public class ReportAction implements ModuleAction {
    private final ModuleContext context;

    public ReportAction(ModuleContext context) {
        this.context = context;
    }

    @Override
    public String description() {
        return context.moduleId() + " exports: " + context.dataDirectory().resolve("exports");
    }
}
