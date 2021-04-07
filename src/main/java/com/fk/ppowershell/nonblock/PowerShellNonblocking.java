package com.fk.ppowershell.nonblock;

import com.fk.ppowershell.Constant;
import com.fk.ppowershell.PowerShellCodepage;
import com.fk.ppowershell.PowerShellConfig;
import com.fk.ppowershell.PowerShellException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.fk.ppowershell.Constant.*;

/**
 * 1、Write commands/scripts asynchronously to the powershell process
 * 2、A fixed thread has been monitoring the output of powershell commands or scripts
 * 3、Asynchronous thread takes over the output to process
 * <p>
 * 概括：
 * 1、业务线程X 向powershell进程中 异步 写入命令
 * 2、有一个 固定线程F 监听powershell进程输出
 * 3、线程F 再将输出数据托管给其他的 业务线程Y 处理
 */
public final class PowerShellNonblocking implements AutoCloseable {
    //Declare logger
    private static final Logger log = Logger.getLogger(PowerShellNonblocking.class.getName());
    //Process to store PowerShell process
    private Process p;
    //PID of the process
    long pid = -1;
    //Writer to send commands
    PrintWriter commandWriter;
    //process state
    private boolean closed = false;
    //Config values
    private Integer startProcessWaitTime = 1;
    private Boolean isAsync = false;
    private final LinkedList<Map<String, String>> headCache = new LinkedList<>();
    private File tempFolder;

    private PowerShellNonblocking() {
    }

    Process getP() {
        return p;
    }

    public void configuration(Map<String, String> config) {
        try {
            if (config == null) {
                config = new HashMap<>();
            }
            Properties properties = PowerShellConfig.getConfig();
            this.tempFolder = config.get(TEMP_FOLDER) != null ? getTempFolder(config.get(TEMP_FOLDER))
                    : getTempFolder(properties.getProperty(TEMP_FOLDER));
            this.isAsync = Boolean.parseBoolean(config.get(IS_ASYNC) != null ? config.get(IS_ASYNC)
                    : properties.getProperty(IS_ASYNC));
            this.startProcessWaitTime = Integer.parseInt(config.get(START_PROCESS_WAIT_TIME) != null ? config.get(START_PROCESS_WAIT_TIME)
                    : properties.getProperty(START_PROCESS_WAIT_TIME));
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not read configuration. Using default values . the reason is {0}", e.getMessage());
        }
    }

    public static PowerShellNonblocking openProcess() throws IOException {
        return openProcess(null, () -> new OperationService[]{});
    }

    public static PowerShellNonblocking openProcess(Supplier<OperationService[]> supplier) throws IOException {
        return openProcess(null, supplier);
    }

    /**
     * create a powershell process
     *
     * @param pSExecutablePath Different systems have different powershell execution path
     * @param supplier         Specifies the implementation class that handles output support
     * @return PowerShell process
     */
    public static PowerShellNonblocking openProcess(String pSExecutablePath, Supplier<OperationService[]> supplier) throws IOException {
        PowerShellNonblocking powerShellNonblocking = new PowerShellNonblocking();
        powerShellNonblocking.configuration(null);
        String executablePath = pSExecutablePath != null && pSExecutablePath.length() > 0 ? pSExecutablePath : IS_WINDOWS ? "powershell.exe" : "pwsh.exe";
        PowerShellNonblocking initialize = powerShellNonblocking.initialize(executablePath);
        OperationServiceManager.loadOperationServiceImpl(supplier.get());
        return initialize;
    }

    public static PowerShellNonblocking openProcess(String pSExecutablePath) throws IOException {
        return openProcess(pSExecutablePath, () -> new OperationService[]{});
    }

    private PowerShellNonblocking initialize(String pSExecutePath) throws IOException {
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
                throw new PowerShellException("Cannot execute PowerShell. Please make sure that it is installed in your system. Errorcode:" + p.exitValue());
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PowerShellException("Cannot execute PowerShell. Please make sure that it is installed in your system", ex);
        }

        this.commandWriter = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(p.getOutputStream())), true);
        //Start the powershell processor
        new Thread(new ProcessorNonblocking(this, isAsync, headCache)).start();
        //Get and store the PID of the process
        return this;
    }

    private void executeCommand(String command) {
        checkState();
        commandWriter.println(command);
    }

    public void executeScript(String commandStr) {
        executeScript(null, commandStr);
    }

    public void executeScript(Map<String, String> head, String commandStr) {
        //1. Create temporary file
        File tmpFile;
        try {
            tmpFile = File.createTempFile("psscript_" + new Date().getTime(), ".ps1", this.tempFolder);
            if (!tmpFile.exists()) {
                return;
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Exception creating temporary file", e);
            return;
        }


        //2. Put the temporary file absolute path in the header
        String absolutePath = tmpFile.getAbsolutePath();
        String identify = tmpFile.getName();
        if (head == null) {
            head = new HashMap<>();
        }
        head.put(identify, absolutePath);

        //3. Writing scripts to temporary files
        try (BufferedReader srcReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(commandStr.getBytes())));
             BufferedWriter tmpWriter = new BufferedWriter(new FileWriter(tmpFile))) {
            tmpWriter.write(Constant.DOUBLE_QUOTE + START_SCRIPT_STRING + Constant.DOUBLE_QUOTE);
            tmpWriter.newLine();
            tmpWriter.write(Constant.DOUBLE_QUOTE + identify + Constant.DOUBLE_QUOTE);
            tmpWriter.newLine();
            String line;
            while ((line = srcReader.readLine()) != null) {
                tmpWriter.write(line);
                tmpWriter.newLine();
            }
            tmpWriter.write(Constant.DOUBLE_QUOTE + END_SCRIPT_STRING + Constant.DOUBLE_QUOTE);
            tmpWriter.newLine();
            tmpWriter.write(Constant.DOUBLE_QUOTE + identify + Constant.DOUBLE_QUOTE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unexpected error while writing temporary PowerShell script", e);
            return;
        }

        //4. Cache the script header information and associate the file identify information
        headCache.add(head);

        //5. Write commands to the PowerShell process
        executeCommand(absolutePath);
    }

    @Override
    public void close() {
        if (!this.closed) {
            try {
                commandWriter.println("exit");
                if (this.pid > 0) {
                    log.log(Level.INFO, "Forcing PowerShell to close. PID: {0}", this.pid);
                    try {
                        Runtime.getRuntime().exec("taskkill.exe /PID " + pid + " /F /T");
                        this.closed = true;
                    } catch (IOException e) {
                        log.log(Level.SEVERE, "Unexpected error while killing powershell process", e);
                    }
                }
            } finally {
                commandWriter.close();
                try {
                    if (p.isAlive()) {
                        p.getInputStream().close();
                    }
                } catch (IOException ex) {
                    log.log(Level.SEVERE, "Unexpected error when when closing streams", ex);
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