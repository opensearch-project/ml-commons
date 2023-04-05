package org.opensearch.ml.engine;

public class ExecuteException extends Exception{
    public ExecuteException() {}
    public ExecuteException(String msg) { super(msg); }
    public ExecuteException(Throwable cause) { super(cause); }
    public ExecuteException(String msg, Throwable cause) { super(msg, cause); }
}
