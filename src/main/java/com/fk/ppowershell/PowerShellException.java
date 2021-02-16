package com.fk.ppowershell;
class PowerShellException extends RuntimeException {

    PowerShellException(String message) {
        super(message);
    }

    PowerShellException(String message, Throwable cause) {
        super(message, cause);
    }
}