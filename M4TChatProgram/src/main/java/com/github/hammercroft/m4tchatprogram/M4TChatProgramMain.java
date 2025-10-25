package com.github.hammercroft.m4tchatprogram;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

// TODO see https://picocli.info/ when you get to implementing launch params

/**
 * A terminal-based peer-to-peer messaging program over UDP.
 * <p>
 * This class serves as the command-line entry point for launching the
 * M4TChatProgram using the Picocli framework.
 * </p>
 * 
 * @author hammercroft
 */
@Command(
    name = "M4TChatProgram",
    mixinStandardHelpOptions = true,
    version = "M4TChatProgram 1.1.2",
    description = "A terminal-based peer-to-peer messaging program over UDP."
)
public class M4TChatProgramMain implements Callable<Integer> {

    /**
     * Executes the main program workflow.
     * <p>
     * Initialises the program state, sets up the user interface,
     * and blocks until the session terminates.
     * </p>
     *
     * @return the program exit code (0 for normal termination)
     */
    @Override
    public Integer call() {
        M4TChatProgram app = new M4TChatProgram();
        app.state = new ProgramState();
        app.userInterface = new JLineUserInterface(app);
        app.run();
        app.waitUntilStopped(); // block here until shutdown
        return 0;
    }

    /**
     * Application entry point.
     * <p>
     * Delegates execution to Picocli for command-line parsing and setup,
     * then terminates with the returned exit code.
     * </p>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new M4TChatProgramMain()).execute(args);
        System.exit(exitCode);
    }
}