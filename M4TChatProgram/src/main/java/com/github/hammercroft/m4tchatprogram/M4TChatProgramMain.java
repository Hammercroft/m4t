package com.github.hammercroft.m4tchatprogram;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

//TODO see https://picocli.info/ when you get to implementing launch params

/**
 * A terminal-based peer-to-peer messaging program over UDP.
 * @author hammercroft
 */
@Command(name = "M4TChatProgram", mixinStandardHelpOptions = true, version = "M4TChatProgram 1.1.1",
         description = "A terminal-based peer-to-peer messaging program over UDP.")
public class M4TChatProgramMain implements Callable<Integer>{

    @Override
    public Integer call() {
        M4TChatProgram app = new M4TChatProgram();
        app.state = new ProgramState();
        app.userInterface = new JLineUserInterface(app);
        app.run();
        app.waitUntilStopped(); // block here until shutdown
        return 0;
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new M4TChatProgramMain()).execute(args);
        System.exit(exitCode);
    }
}
