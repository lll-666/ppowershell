package com.fk.ppowershell;

class PSResponse {
    boolean error;
    boolean timeout;
    String outPut;

    PSResponse(boolean error, boolean timeout, String outPut) {
        this.error = error;
        this.timeout = timeout;
        this.outPut = outPut;
    }

    PSResponse(boolean timeout) {
        this.timeout = timeout;
    }

    PSResponse(boolean error, String outPut) {
        this.outPut = outPut;
        this.error = error;
    }

    PSResponse(String outPut) {
        this.outPut = outPut;
    }
    PSResponse() {
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