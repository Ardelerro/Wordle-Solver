import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class WordleSolverTester {

    private static String generateFeedback(String guess, String solution) {
        char[] feedback = new char[5];
        boolean[] used = new boolean[5];

        for (int i = 0; i < 5; i++) {
            if (guess.charAt(i) == solution.charAt(i)) {
                feedback[i] = 'G';
                used[i] = true;
            } else {
                feedback[i] = 'X';
            }
        }

        for (int i = 0; i < 5; i++) {
            if (feedback[i] == 'G')
                continue;
            char g = guess.charAt(i);
            for (int j = 0; j < 5; j++) {
                if (!used[j] && solution.charAt(j) == g) {
                    feedback[i] = 'Y';
                    used[j] = true;
                    break;
                }
            }
        }

        return new String(feedback);
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        String dictionaryPath = "words_alpha_size_5.txt";
        List<String> allWords = WordleSolver.loadDictionary(dictionaryPath);
        int totalWords = allWords.size();

        int numWorkers = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

        AtomicInteger solvedCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();
        AtomicInteger totalGuesses = new AtomicInteger();
        ConcurrentMap<Integer, AtomicInteger> solvedAtGuessCount = new ConcurrentHashMap<>();

        PrintStream silentOut = new PrintStream(OutputStream.nullOutputStream());

        List<Future<?>> futures = new ArrayList<>();
        int chunkSize = (totalWords + numWorkers - 1) / numWorkers;

        for (int w = 0; w < numWorkers; w++) {
            final int workerId = w;
            int start = workerId * chunkSize;
            int end = Math.min(start + chunkSize, totalWords);
            List<String> sublist = allWords.subList(start, end);

            futures.add(executor.submit(() -> {
                PrintStream originalOut = System.out;
                for (int i = 0; i < sublist.size(); i++) {
                    String solution = sublist.get(i);

                    System.setOut(silentOut);
                    WordleSolver solver = new WordleSolver(allWords);
                    System.setOut(originalOut);

                    boolean solved = false;
                    boolean firstGuess = true;
                    boolean secondGuess = false;
                    int guessCount = 0;

                    System.setOut(silentOut);
                    while (solver.getPossibleWords().size() > 0) {
                        String guess;
                        if (firstGuess) {
                            guess = "salet";
                        } else if (secondGuess) {
                            guess = "frogs";
                        } else {
                            guess = solver.getPossibleWords().get(0);
                        }

                        guessCount++;
                        if (guess.equals(solution)) {
                            solved = true;
                            break;
                        }

                        String feedback = generateFeedback(guess, solution);
                        solver.updateConstraints(guess, feedback);
                        firstGuess = false;
                        secondGuess = false;
                    }
                    System.setOut(originalOut);

                    if (solved) {
                        solvedCount.incrementAndGet();
                        totalGuesses.addAndGet(guessCount);
                        solvedAtGuessCount
                                .computeIfAbsent(guessCount, k -> new AtomicInteger())
                                .incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                    }

                    if ((i + 1) % 500 == 0) {
                        System.out.printf("Thread %d tested %d/%d%n", workerId + 1, i + 1, sublist.size());
                    }
                }
            }));
        }

        for (Future<?> f : futures)
            f.get();

        System.out.println("\n=== TEST RESULTS ===");
        System.out.println("Total words: " + totalWords);
        System.out.println("Solved: " + solvedCount.get());
        System.out.println("Failed: " + failedCount.get());
        double avgGuesses = solvedCount.get() > 0
                ? (double) totalGuesses.get() / solvedCount.get()
                : 0;
        System.out.printf("Average guesses for solved words: %.2f%n", avgGuesses);
        System.out.printf("Success Rate: %.2f%%%n", 100.0 * solvedCount.get() / totalWords);
        System.out.printf("Failed Rate: %.2f%%%n", 100.0 * failedCount.get() / totalWords);

        System.out.println("\nPercent solved by guess count:");
        int cumulativeSolved = 0;
        for (int i = 1; i <= 10; i++) {
            int countAtGuess = solvedAtGuessCount.getOrDefault(i, new AtomicInteger()).get();
            cumulativeSolved += countAtGuess;
            double percentSolved = (double) cumulativeSolved / totalWords * 100;
            System.out.printf("Guess %d: %.2f%% solved, %d failed\n",
                    i, percentSolved, totalWords - cumulativeSolved);
            if (percentSolved >= 100)
                break;
        }

        System.out.println("====================");
        executor.shutdown();

    }
}
