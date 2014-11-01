package org.kantega.reststop.api;

/**
 *
 */
public enum FilterPhase {
    PRE_UNMARSHAL,
    UNMARSHAL,
    POST_UNMARSHAL,
    PRE_AUTHENTICATION,
    AUTHENTICATION,
    POST_AUTHENTICATION,
    PRE_AUTHORIZATION,
    AUTHORIZATION,
    POST_AUTHORIZATION,
    PRE_USER,
    USER,
    POST_USER
}
