package com.fk.ppowershell;

public class PowerShellException extends RuntimeException {

    public PowerShellException(String message) {
        super(message);
    }

    public PowerShellException(String message, Throwable cause) {
        super(message, cause);
    }
}