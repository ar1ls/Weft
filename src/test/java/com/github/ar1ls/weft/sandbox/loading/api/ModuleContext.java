package com.github.ar1ls.weft.sandbox.loading.api;

import java.nio.file.Path;

/** Host-owned settings shared with a module through its source ID. */
public record ModuleContext(String moduleId, Path dataDirectory) {
}
