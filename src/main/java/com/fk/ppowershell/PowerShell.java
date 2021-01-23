package com.fk.ppowershell;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PowerShell implements AutoCloseable {
    //Declare logger
    private static final Logger logger = Logger.getLogger(PowerShell.class.getName());
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    // Process to store PowerShell process
    private Process p;
    //PID of the process
    private long pid = -1;
    // Writer to send commands
    private PrintWriter commandWriter;
    //process state
    private boolean closed = false;
    // Config values
    private Integer startProcessWaitTime;
    private Boolean isAsync;
    private Map<String, Map<String, String>> headCache;
    private Integer headCacheInitialCapacity;
    private File tempFolder;
    static final String END_SCRIPT_STRING = "--END-JPOWERSHELL-SCRIPT--";
    static final String START_SCRIPT_STRING = "--START-JPOWERSHELL-SCRIPT--";

    private PowerShell() {
    }

    public void configuration(Map<String, String> config) {
        try {
            if (config == null) {
                config = new HashMap<>();
            }
            Properties properties = PowerShellConfig.getConfig();
            this.tempFolder = config.get(Constant.TEMP_FOLDER) != null ? getTempFolder(config.get(Constant.TEMP_FOLDER))
                    : getTempFolder(properties.getProperty(Constant.TEMP_FOLDER));
            this.isAsync = Boolean.parseBoolean(config.get(Constant.IS_ASYNC) != null ? config.get(Constant.IS_ASYNC)
                    : properties.getProperty(Constant.IS_ASYNC));
            this.headCacheInitialCapacity = Integer.parseInt((config.get(Constant.HEAD_CACHE_INITIAL_CAPACITY)) != null ? config.get(Constant.HEAD_CACHE_INITIAL_CAPACITY)
                    : properties.getProperty(Constant.HEAD_CACHE_INITIAL_CAPACITY));
            this.startProcessWaitTime = Integer.parseInt(config.get(Constant.START_PROCESS_WAIT_TIME) != null ? config.get(Constant.START_PROCESS_WAIT_TIME)
                    : properties.getProperty(Constant.START_PROCESS_WAIT_TIME));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not read configuration. Using default values.");
            throw e;
        }
    }

    public static PowerShell openProcess() throws IOException {
        return openProcess(null, () -> new OperationService[]{});
    }

    public static PowerShell openProcess(Supplier<OperationService[]> supplier) throws IOException {
        return openProcess(null, supplier);
    }

    /**
     * create a powershell process
     *
     * @param pSExecutablePath Different systems have different powershell execution path
     * @param supplier         Specifies the implementation class that handles output support
     * @return PowerShell process
     */
    public static PowerShell openProcess(String pSExecutablePath, Supplier<OperationService[]> supplier) throws IOException {
        PowerShell powerShell = new PowerShell();
        powerShell.configuration(null);
        String executablePath = pSExecutablePath != null ? pSExecutablePath : IS_WINDOWS ? "powershell.exe" : "pwsh.exe";
        PowerShell initialize = powerShell.initialize(executablePath);
        OperationServiceManager.loadOperationServiceImpl(supplier.get());
        return initialize;
    }

    public static PowerShell openProcess(String pSExecutablePath) throws IOException {
        return openProcess(pSExecutablePath, () -> new OperationService[]{});
    }

    private PowerShell initialize(String pSExecutePath) throws IOException {
        String codePage = PowerShellCodepage.getIdentifierByCodePageName(Charset.defaultCharset().name());
        //Start powershell executable in process
        ProcessBuilder pb;
        if (IS_WINDOWS) {
            pb = new ProcessBuilder("cmd.exe", "/c", "chcp", codePage, ">", "NUL", "&", pSExecutePath, "-ExecutionPolicy", "Bypass", "-NoExit", "-NoProfile", "-Command", "-");
        } else {
            pb = new ProcessBuilder(pSExecutePath, "-nologo", "-noexit", "-Command", "-");
        }

        //Merge standard and error streams
        pb.redirectErrorStream(true);
        try {
            p = pb.start();
            if (p.waitFor(startProcessWaitTime, TimeUnit.SECONDS) && !p.isAlive()) {
                throw new PowerShellNotAvailableException("Cannot execute PowerShell. Please make sure that it is installed in your system. Errorcode:" + p.exitValue());
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PowerShellNotAvailableException("Cannot execute PowerShell. Please make sure that it is installed in your system", ex);
        }

        this.commandWriter = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(p.getOutputStream())), true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        //Getting processes from the PowerShell environment
        this.pid = getPID(reader);

        headCache = new ConcurrentHashMap<>(headCacheInitialCapacity);
        //Prepare writer that will be used to send commands to powershell
        CompletableFuture.runAsync(() -> new PowerShellCommandProcessor(reader, isAsync, headCache).run());
        //Get and store the PID of the process
        return this;
    }

    //Use Powershell command '$PID' in order to recover the process identifier
    private int getPID(BufferedReader reader) throws IOException {
        this.commandWriter.println("$pid");
        String commandOutput = reader.readLine();
        commandOutput = commandOutput.replaceAll("\\D", "");
        if (!commandOutput.isEmpty()) {
            return Integer.parseInt(commandOutput);
        }
        return -1;
    }

    private void executeCommand(String command) {
        checkState();
        commandWriter.println(command);
    }

    public void executeScript(Map<String, String> head, String commandStr) {
        //0. Create temporary file
        File tmpFile;
        try {
            tmpFile = File.createTempFile("psscript_" + new Date().getTime(), ".ps1", this.tempFolder);
            if (!tmpFile.exists()) {
                return;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception creating temporary file", e);
            return;
        }

        //1. Put the temporary file absolute path in the header
        String absolutePath = tmpFile.getAbsolutePath();
        String name = tmpFile.getName();
        if (head == null) {
            head = new HashMap<>();
        }
        head.put(name, absolutePath);

        //2. Writing scripts to temporary files
        try (BufferedReader srcReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(commandStr.getBytes())));
             BufferedWriter tmpWriter = new BufferedWriter(new FileWriter(tmpFile))) {
            tmpWriter.write('"' + START_SCRIPT_STRING + '"');
            tmpWriter.newLine();
            tmpWriter.write('"' + name + '"');
            tmpWriter.newLine();
            String impl = head.remove("IMPL");
            impl = impl == null ? "defaultImpl" : impl;
            tmpWriter.write('"' + impl + '"');
            tmpWriter.newLine();
            String line;
            while ((line = srcReader.readLine()) != null) {
                tmpWriter.write(line);
                tmpWriter.newLine();
            }
            tmpWriter.write('"' + END_SCRIPT_STRING + '"');
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unexpected error while writing temporary PowerShell script", e);
            return;
        }

        //3. Cache the script header information and associate the file name information
        headCache.put(name, head);

        //4. Write commands to the PowerShell process
        executeCommand(absolutePath);
    }

    @Override
    public void close() {
        if (!this.closed) {
            try {
                commandWriter.println("exit");
                if (this.pid > 0) {
                    //If it can be closed, force kill the process
                    logger.info("Forcing PowerShell to close. PID: " + this.pid);
                    try {
                        Runtime.getRuntime().exec("taskkill.exe /PID " + pid + " /F /T");
                        this.closed = true;
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Unexpected error while killing powershell process", e);
                    }
                }
            } finally {
                commandWriter.close();
                try {
                    if (p.isAlive()) {
                        p.getInputStream().close();
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Unexpected error when when closing streams", ex);
                }
                this.closed = true;
            }
        }
    }

    //Checks if PowerShell have been already closed
    private void checkState() {
        if (this.closed) {
            throw new IllegalStateException("PowerShell is already closed. Please open a new session.");
        }
    }

    //Return the temp folder File object or null if the path does not exist
    private File getTempFolder(String tempPath) {
        if (tempPath != null) {
            File folder = new File(tempPath);
            if (folder.exists()) {
                return folder;
            }
        }
        return null;
    }
}