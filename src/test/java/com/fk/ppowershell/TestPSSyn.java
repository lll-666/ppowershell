package com.fk.ppowershell;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

public class TestPSSyn {
    static PowerShellSyn powerShellSyn;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.out.println("BeforeClass");
        powerShellSyn = PowerShellSyn.openProcess();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        System.out.println("AfterClass");
        powerShellSyn.close();
    }

    @Test
    public void testListDir() throws IOException {
        try(PowerShellSyn powerShell = PowerShellSyn.openProcess()){
            System.out.println(powerShell.executeScriptText("ls c:/|select -first 10;sleep 10;ls d:/ |select -first 10"));
        }catch (Exception ignored){}
        System.out.println("-----------------------------------------------------");
        System.out.println(PowerShellSyn.executeSingleCommand("ls c:/|select -first 10;sleep 10;ls d:/ |select -first 10"));
    }
}