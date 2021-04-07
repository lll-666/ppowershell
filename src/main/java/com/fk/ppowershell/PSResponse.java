package com.fk.ppowershell;

public class PSResponse {
    boolean error;
    boolean timeout;
    String outPut;

    public PSResponse(boolean error, boolean timeout, String outPut) {
        this.error = error;
        this.timeout = timeout;
        this.outPut = outPut;
    }

    public PSResponse(boolean timeout) {
        this.timeout = timeout;
    }

    public PSResponse(boolean error, String outPut) {
        this.outPut = outPut;
        this.error = error;
    }

    public PSResponse(String outPut) {
        this.outPut = outPut;
    }

    public PSResponse() {
    }

    public String getOutPut() {
        return outPut;
    }

    public void setOutPut(String outPut) {
        this.outPut = outPut;
    }

    @Override
    public String toString() {
        return "PSResponse{" +
                "error=" + error +
                ", timeout=" + timeout +
                ", outPut='" + outPut + '\'' +
                '}';
    }
}