package com.github.ar1ls.weft.runtime.fixtures.selection;

import com.github.ar1ls.weft.api.annotations.Named;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class WrapperMetadataFixtures {

    public void valid(Optional<Formatter> optional, List<Formatter> list, Set<Formatter> set) {
    }

    public void rawOptional(Optional optional) {
    }

    public void rawList(List list) {
    }

    public void rawSet(Set set) {
    }

    public void wildcardList(List<? extends Formatter> list) {
    }

    public void nestedList(List<Optional<Formatter>> list) {
    }

    public <T> void variableOptional(Optional<T> optional) {
    }

    public void unsupported(Map<String, Formatter> map) {
    }

    public void blank(@Named(" ") Formatter formatter) {
    }

}
