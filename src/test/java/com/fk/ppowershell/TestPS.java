package com.fk.ppowershell;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

public class TestPS {
    static PowerShell powerShell;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.out.println("BeforeClass");
        powerShell = PowerShell.openProcess();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        System.out.println("AfterClass");
        powerShell.close();
    }

    @Test
    public void testListDir() throws IOException {
        HashMap<String, String> head = new HashMap<>();
        head.put("testListDir","testListDir");
        powerShell.executeScript(head,"dir");
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
        powerShell.executeScript(head,"Get-Process");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCheckBIOSByWMI() throws IOException {
        powerShell.executeScript("Get-WmiObject Win32_BIOS");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCheckEmptyResponse() throws IOException {
        powerShell.executeScript("Get-WmiObject Win32_1394Controller");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testErrorCase() throws IOException {
        powerShell.executeScript("sfdsfdsf");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMultipleCalls() throws IOException {
        powerShell.executeScript("dir");
        powerShell.executeScript("Get-Process");
        powerShell.executeScript("Get-WmiObject Win32_BIOS");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}