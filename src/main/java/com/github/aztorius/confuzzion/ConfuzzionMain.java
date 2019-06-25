package com.github.aztorius.confuzzion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Scene;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ConfuzzionMain {
    private Path resultFolder;

    private static final long MAIN_LOOP_ITERATIONS = 1000;
    private static final int TIMEOUT = 1000;
    private static final int STACK_LIMIT = Integer.MAX_VALUE;
    private static final boolean WITH_JVM = true;
    private static final boolean JASMIN_BACKEND = false;
    private static final Logger logger = LoggerFactory.getLogger(ConfuzzionMain.class);

    public ConfuzzionMain(Path resultFolder) {
        this.resultFolder = resultFolder;
    }

    public static void main(String args[]) {
        final Options options = configParameters();
        CommandLineParser parser = new DefaultParser();

        Path resultFolder = Paths.get("confuzzionResults/");
        long main_loop_iterations = ConfuzzionMain.MAIN_LOOP_ITERATIONS;
        int timeout = ConfuzzionMain.TIMEOUT;
        int stackLimit = ConfuzzionMain.STACK_LIMIT;
        boolean withJVM = ConfuzzionMain.WITH_JVM;
        String javahome = System.getProperty("java.home");
        Path seedFile = null;
        boolean jasmin_backend = ConfuzzionMain.JASMIN_BACKEND;

        try {
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("confuzzion", options);
                return;
            }
            if (line.hasOption("o")) {
                resultFolder = Paths.get(line.getOptionValue("o"));
            }
            if (line.hasOption("i")) {
                main_loop_iterations = Long.parseLong(line.getOptionValue("i"));
            }
            if (line.hasOption("t")) {
                timeout = Integer.parseInt(line.getOptionValue("t"));
            }
            if (line.hasOption("l")) {
                stackLimit = Integer.parseInt(line.getOptionValue("l"));
            }
            withJVM = !line.hasOption("threads");
            if (line.hasOption("j")) {
                javahome = line.getOptionValue("j");
            }
            if (line.hasOption("s")) {
                seedFile = Paths.get(line.getOptionValue("s"));
            }
            jasmin_backend = line.hasOption("jasmin");

            if (!Files.exists(resultFolder)) {
                Files.createDirectories(resultFolder);
            }
        } catch (ParseException e) {
            logger.error("Options parsing failed", e);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("confuzzion", options);
            System.exit(1);
        } catch (IOException e) {
            logger.error("Error", e);
            System.exit(1);
        }

        ConfuzzionMain conf = new ConfuzzionMain(resultFolder);

        conf.startMutation(main_loop_iterations, timeout, stackLimit, withJVM, javahome, seedFile, jasmin_backend);
    }

    private static Options configParameters() {
        final Option outputOption = Option.builder("o")
                .longOpt("output")
                .desc("Output directory")
                .hasArg(true)
                .argName("output")
                .required(false)
                .build();

        final Option iterationsOption = Option.builder("i")
                .longOpt("iterations")
                .desc("Main loop iterations / -1 by default")
                .hasArg(true)
                .argName("iterations")
                .required(false)
                .build();

        final Option timeoutOption = Option.builder("t")
                .longOpt("timeout")
                .desc("Timeout per program execution / 1000 ms by default")
                .hasArg(true)
                .argName("timeout")
                .required(false)
                .build();

        final Option runnerOption = Option.builder("threads")
                .longOpt("threads")
                .desc("Use threads in spite of JVM to run programs")
                .hasArg(false)
                .required(false)
                .build();

        final Option jvmOption = Option.builder("j")
                .longOpt("jvm")
                .desc("JAVA_HOME for execution / same as running JAVA_HOME by default")
                .hasArg(true)
                .argName("jvm")
                .required(false)
                .build();

        final Option stackLimitOption = Option.builder("l")
                .longOpt("stack-limit")
                .desc("Mutations stack size limit / default no limit")
                .hasArg(true)
                .argName("stacklimit")
                .required(false)
                .build();

        final Option seedOption = Option.builder("s")
                .longOpt("seed")
                .desc("Seed file to start mutations from")
                .hasArg(true)
                .argName("seed")
                .required(false)
                .build();

        final Option jasminOption = Option.builder("jasmin")
                .desc("Use Jasmin backend instead of ASM")
                .hasArg(false)
                .required(false)
                .build();

        final Option helpOption = Option.builder("h")
                .longOpt("help")
                .desc("Print this message")
                .hasArg(false)
                .required(false)
                .build();

        final Options options = new Options();

        options.addOption(outputOption);
        options.addOption(iterationsOption);
        options.addOption(timeoutOption);
        options.addOption(runnerOption);
        options.addOption(jvmOption);
        options.addOption(stackLimitOption);
        options.addOption(seedOption);
        options.addOption(jasminOption);
        options.addOption(helpOption);

        return options;
    }

    public void startGeneration(long mainloop_turn) {
        RandomGenerator rand = new RandomGenerator();
        MutantGenerator generator = new MutantGenerator(rand, "Test");
        ArrayList<Contract> contracts = new ArrayList<Contract>();
        contracts.add(new ContractTypeConfusion());

        for (long loop1 = 0; loop1 < mainloop_turn; loop1++) {
            logger.info("===Loop {}===", loop1);
            generator.generate("java.lang.Object");
            generator.addContractsChecks(contracts);
        }
    }

    public void startMutation(long mainloop_turn, int timeout, int stackLimit, boolean withJVM, String javahome, Path seedFile, boolean jasmin_backend) {
        Scene.v().loadBasicClasses();
        Scene.v().extendSootClassPath(Util.getJarPath());
        logger.info("Soot Class Path: {}", Scene.v().getSootClassPath());
        logger.info("Default java.home: {}", System.getProperty("java.home"));
        logger.info("Target java.home: {}", javahome);

        RandomGenerator rand = new RandomGenerator();

        Program currentProg = null;
        if (seedFile != null) {
            logger.info("Seed file: {}", seedFile);
            Path seedFolder = seedFile.getParent().toAbsolutePath().normalize();
            Scene.v().extendSootClassPath(seedFolder.toString());
            String seedName = seedFile.getFileName().toString();
            seedName = seedName.substring(0, seedName.lastIndexOf("."));

            Mutant seedMutant = Mutant.loadClass(seedName);
            currentProg = new Program(rand, seedMutant);
        } else {
            currentProg = new Program(rand, "Test");
        }

        ArrayList<Contract> contracts = new ArrayList<Contract>();
        contracts.add(new ContractTypeConfusion());
        Stack<Mutation> mutationsStack = new Stack<Mutation>();

        // Refresh Status in command line each second
        Timer timer = new Timer();
        StatusScreen statusScreen = new StatusScreen();
        timer.schedule(statusScreen, 0, 1000);

        for (long loop1 = 0; loop1 < mainloop_turn || mainloop_turn < 0; loop1++) {
            Mutation mutation = null;

            try {
                // Random mutation (program level | class level | method level)
                mutation = currentProg.randomMutation();
            } catch (MutationException e) {
                logger.warn("Exception while applying mutation", e);
                e.undoMutation();
                statusScreen.newMutation(e.getMutationClass(), Status.FAILED, 0);
                continue;
            }

            logger.info("Mutation: {}", mutation.getClass().toString());

            // Add contracts checks
            ArrayList<BodyMutation> contractsMutations =
                    currentProg.addContractsChecks(contracts, mutation);
            // Save current classes to unique folder
            Path folder = Paths.get(
                    resultFolder.toAbsolutePath().toString(),
                    mutation.getClass().getSimpleName() + "-" + loop1);
            Boolean keepFolder = false;
            try {
                // Instantiation and launch
                if (withJVM) {
                    try {
                        Files.createDirectories(folder);
                    } catch(IOException e2) {
                        logger.error("Printing last program generated:\n{}", currentProg.toString(), e2);
                        break;
                    }
                    currentProg.genAndLaunchWithJVM(javahome, folder.toString(), timeout, jasmin_backend);
                } else { //with threads
                    currentProg.genAndLaunch(timeout, jasmin_backend);
                }
                // Remove contracts checks for next turn
                currentProg.removeContractsChecks(contractsMutations);
                // Add mutation to the stack
                mutationsStack.push(mutation);
                // Update status screen
                statusScreen.newMutation(mutation.getClass(), Status.SUCCESS, 2);
            } catch(Throwable e) {
                logger.warn("Exception while executing program", e);
                Throwable cause = Util.getCause(e);
                if (cause instanceof ContractCheckException) {
                    keepFolder = true;
                    // Save current classes also as jimple files
                    currentProg.saveAsJimpleFiles(folder.toString());
                    // Update status screen
                    statusScreen.newMutation(mutation.getClass(),
                        Status.VIOLATES, 2);
                } else if (cause instanceof InterruptedException) {
                    // Update status screen
                    statusScreen.newMutation(mutation.getClass(),
                        Status.INTERRUPTED, 2);
                } else {
                    // Update status screen
                    statusScreen.newMutation(mutation.getClass(),
                        Status.CRASHED, 2);
                }
                // Remove contracts checks
                currentProg.removeContractsChecks(contractsMutations);
                // Bad sample, revert mutation
                mutation.undo();
            } finally {
                if (withJVM && !keepFolder) {
                    // Remove folder
                    try {
                        Util.deleteDirectory(folder);
                    } catch(IOException e2) {
                        logger.error("Error while deleting directory {}", folder, e2);
                        break;
                    }
                }
            }

            if ((statusScreen.isStalled() && mutationsStack.size() > 0) || mutationsStack.size() >= stackLimit) {
                // Revert a random number of mutations
                int toRevert = rand.nextUint(mutationsStack.size());
                while(toRevert-- > 0) {
                    mutationsStack.pop().undo();
                }
                // Refresh stack size on status screen
                statusScreen.newStackSize(mutationsStack.size());
            }
        }
        // Stop automatic call to status.run()
        timer.cancel();
        // Print a last time the status screen
        statusScreen.run();
    }
}
