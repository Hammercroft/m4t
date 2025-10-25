package com.github.hammercroft.m4tchatprogram;

/**
 * Dot-prefixed local commands recognized by M4TChatProgram.
 * <p>
 * These commands are interpreted internally rather than sent over the network.
 * </p>
 */
public enum LocalCommand {

    /** A local command. */
    L_EXIT(".EXIT"),

    /** A local command. */
    L_PROGRAM_STATE(".PROGRAMSTATE"),

    /** A local command. */
    L_SALVE(".SALVE"),

    /** A local command. */
    L_POKE(".POKE");

    private final String command;

    /**
     * Constructs a {@code LocalCommand} with its corresponding command string.
     *
     * @param command the dot-prefixed command representation
     */
    LocalCommand(String command) {
        this.command = command;
    }

    /**
     * Returns the string form of this command.
     *
     * @return the command string
     */
    public String command() {
        return command;
    }

    /**
     * Returns the command string representation of this enum constant.
     *
     * @return the command string
     */
    @Override
    public String toString() {
        return command;
    }
}
