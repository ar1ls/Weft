package com.github.ar1ls.weft.api.lifecycle;

@FunctionalInterface
public interface StartupHook {

    void onStartup() throws Exception;

}
