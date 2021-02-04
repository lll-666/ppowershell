package com.fk.ppowershell;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

public class TestPSAsy {
    static PowerShellAyn powerShellAsy;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.out.println("BeforeClass");
        powerShellAsy = PowerShellAyn.openProcess();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        System.out.println("AfterClass");
        powerShellAsy.close();
    }

    @Test
    public void testListDir() throws IOException {
        HashMap<String, String> head = new HashMap<>();
        head.put("testListDir","testListDir");
        powerShellAsy.executeScript(head,"dir c:/");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testListProcesses() throws IOException {
        HashMap<String, String> head = new HashMap<>();
        head.put("testListProcesses","testListProcesses");
        powerShellAsy.executeScript(head,"Get-Process");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCheckBIOSByWMI() throws IOException {
        powerShellAsy.executeScript("Get-WmiObject Win32_BIOS");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCheckEmptyResponse() throws IOException {
        powerShellAsy.executeScript("Get-WmiObject Win32_1394Controller");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testErrorCase() throws IOException {
        powerShellAsy.executeScript("sfdsfdsf");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMultipleCalls() throws IOException {
        powerShellAsy.executeScript("dir");
        powerShellAsy.executeScript("Get-Process");
        powerShellAsy.executeScript("Get-WmiObject Win32_BIOS");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}