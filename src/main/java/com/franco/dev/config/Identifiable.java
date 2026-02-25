package com.franco.dev.config;

import java.io.Serializable;

public interface Identifiable<T extends Serializable> {
    T getId();
}
