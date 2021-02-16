package com.fk.ppowershell;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class TestPSSyn {
    static PowerShellSyn powerShellSyn;

//    @BeforeClass
//    public static void setUpBeforeClass() throws Exception {
//        System.out.println("BeforeClass");
//        powerShellSyn = PowerShellSyn.openProcess();
//    }
//
//    @AfterClass
//    public static void tearDownAfterClass() {
//        System.out.println("AfterClass");
//        powerShellSyn.close();
//    }

    @Test
    public void testListDir() throws IOException {
        try (PowerShellSyn powerShell = PowerShellSyn.openProcess()) {
            CompletableFuture.runAsync(() -> powerShell.executeScriptText("ls c:/|select -first 2;sleep 3;ls d:/ |select -first 2"));
            CompletableFuture.runAsync(() -> powerShell.executeScriptText("ls e:/ |select -first 2"));
            CompletableFuture.runAsync(() -> powerShell.executeScriptText("get-service|select -first 2"));
            CompletableFuture.runAsync(() -> powerShell.executeScriptText("ls f:/|select -first 2;sleep 3;ls e:/ |select -last 2"));
            Thread.sleep(10000);
            String path = System.getProperty("user.dir");
            System.out.println(path);
            System.out.println(powerShell.executeScriptFile(path + "/src/test/java/com/fk/ppowershell/listC.ps1"));
        } catch (Exception ignored) {
        }
        System.out.println(PowerShellSyn.executeSingleCommand("$a=12"));
        System.out.println("-----------------------------------------------------");
        System.out.println(PowerShellSyn.executeSingleCommand("ls f:/|select -first 2;ls e:/ |select -last 2"));
    }
}