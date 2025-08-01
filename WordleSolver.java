import java.io.*;
import java.util.*;

public class WordleSolver {
    private final List<String> originalWords;
    private List<String> possibleWords;

    private final boolean[][] grayLetters = new boolean[5][26]; // Direct array lookup
    private final Map<Integer, Set<Character>> yellowPositions = new HashMap<>(5);
    private final char[] knownPositions = new char[5];
    private final Map<Character, Integer> minLetterCount = new HashMap<>();
    private final Map<Character, Integer> maxLetterCount = new HashMap<>();

    private final Map<String, Double> precomputedScores = new HashMap<>();
    private final Map<String, Boolean> hasDoubleLettersCache = new HashMap<>();
    private final Map<Character, Integer> staticLetterFreq = new HashMap<>();

    private final boolean[] tempUsed = new boolean[26];
    private final int[] tempCount = new int[26];
    private final char[] tempFeedback = new char[5];
    private final boolean[] tempSolutionUsed = new boolean[5];

    public WordleSolver(String dictionaryPath) throws IOException {
        Arrays.fill(knownPositions, ' ');
        this.originalWords = loadDictionary(dictionaryPath);
        this.possibleWords = new ArrayList<>(originalWords);
        precomputeOptimizations();
    }

    public WordleSolver(List<String> words) {
        this.originalWords = new ArrayList<>(words);
        this.possibleWords = new ArrayList<>(words);
        Arrays.fill(knownPositions, ' ');
        precomputeOptimizations();
    }

    public static List<String> loadDictionary(String filePath) throws IOException {
        List<String> words = new ArrayList<>(15000);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath), 8192)) {
            String word;
            while ((word = reader.readLine()) != null) {
                if (word.length() == 5) {

                    char[] chars = word.toCharArray();
                    boolean needsConversion = false;
                    for (int i = 0; i < 5; i++) {
                        if (chars[i] >= 'A' && chars[i] <= 'Z') {
                            chars[i] = (char) (chars[i] + 32);
                            needsConversion = true;
                        }
                    }
                    String lowerWord = needsConversion ? new String(chars) : word;
                    words.add(lowerWord);
                }
            }
        }

        return words;
    }


    private void precomputeOptimizations() {

        for (String word : originalWords) {
            Arrays.fill(tempUsed, false);
            for (int i = 0; i < 5; i++) {
                int idx = word.charAt(i) - 'a';
                if (!tempUsed[idx]) {
                    staticLetterFreq.merge(word.charAt(i), 1, Integer::sum);
                    tempUsed[idx] = true;
                }
            }
        }

        for (String word : originalWords) {
            precomputedScores.put(word, calculateWordScore(word));
            hasDoubleLettersCache.put(word, hasDoubleLetters(word));
        }
    }

    private double calculateWordScore(String word) {
        Arrays.fill(tempUsed, false);
        double score = 0.0;

        for (int i = 0; i < 5; i++) {
            int idx = word.charAt(i) - 'a';
            if (!tempUsed[idx]) {
                int freq = staticLetterFreq.getOrDefault(word.charAt(i), 0);
                score += Math.log(freq + 1);
                tempUsed[idx] = true;
            }
        }
        return score;
    }

    private boolean hasDoubleLetters(String word) {
        Arrays.fill(tempUsed, false);
        for (int i = 0; i < 5; i++) {
            int idx = word.charAt(i) - 'a';
            if (tempUsed[idx])
                return true;
            tempUsed[idx] = true;
        }
        return false;
    }

    public void updateConstraints(String guess, String feedback) {

        Arrays.fill(tempCount, 0);
        int[] matchCount = new int[26];

        for (int i = 0; i < 5; i++) {
            tempCount[guess.charAt(i) - 'a']++;
        }

        for (int i = 0; i < 5; i++) {
            char g = guess.charAt(i);
            char fb = feedback.charAt(i);
            int gIdx = g - 'a';

            if (fb == 'G') {
                knownPositions[i] = g;
                matchCount[gIdx]++;
            } else if (fb == 'Y') {
                yellowPositions.computeIfAbsent(i, k -> new HashSet<>(3)).add(g);
                matchCount[gIdx]++;
            } else if (fb == 'X') {
                grayLetters[i][gIdx] = true;
            }
        }

        for (int i = 0; i < 26; i++) {
            if (tempCount[i] > 0) {
                char letter = (char) ('a' + i);
                int guessed = tempCount[i];
                int matched = matchCount[i];

                if (matched > 0) {
                    minLetterCount.merge(letter, matched, Integer::max);
                }

                if (matched < guessed) {
                    maxLetterCount.put(letter, Math.min(
                            maxLetterCount.getOrDefault(letter, Integer.MAX_VALUE), matched));
                } else if (!maxLetterCount.containsKey(letter)) {
                    maxLetterCount.put(letter, Integer.MAX_VALUE);
                }
            }
        }

        filterWords();
    }

    private void filterWords() {
        // Collect required yellows once
        Set<Character> requiredYellows = new HashSet<>();
        for (Set<Character> s : yellowPositions.values()) {
            requiredYellows.addAll(s);
        }

        List<String> filtered = new ArrayList<>(possibleWords.size());

        wordLoop: for (String word : possibleWords) {
            for (int i = 0; i < 5; i++) {
                char c = word.charAt(i);
                int cIdx = c - 'a';

                if (knownPositions[i] != ' ' && c != knownPositions[i]) {
                    continue wordLoop;
                }

                if (grayLetters[i][cIdx]) {
                    continue wordLoop;
                }

                Set<Character> yellowsAtPos = yellowPositions.get(i);
                if (yellowsAtPos != null && yellowsAtPos.contains(c)) {
                    continue wordLoop;
                }
            }

            if (!checkLetterCountConstraints(word, requiredYellows)) {
                continue;
            }

            filtered.add(word);
        }

        possibleWords = filtered;
        sortWordsByScore();
    }

    private boolean checkLetterCountConstraints(String word, Set<Character> requiredYellows) {
        Arrays.fill(tempCount, 0);

        for (int i = 0; i < 5; i++) {
            tempCount[word.charAt(i) - 'a']++;
        }

        for (Map.Entry<Character, Integer> entry : minLetterCount.entrySet()) {
            if (tempCount[entry.getKey() - 'a'] < entry.getValue()) {
                return false;
            }
        }

        for (Map.Entry<Character, Integer> entry : maxLetterCount.entrySet()) {
            int allowed = entry.getValue();
            if (allowed != Integer.MAX_VALUE && tempCount[entry.getKey() - 'a'] > allowed) {
                return false;
            }
        }

        for (char req : requiredYellows) {
            if (tempCount[req - 'a'] == 0) {
                return false;
            }
        }

        return true;
    }

    private void sortWordsByScore() {
        possibleWords.sort((w1, w2) -> {
            boolean d1 = hasDoubleLettersCache.get(w1);
            boolean d2 = hasDoubleLettersCache.get(w2);

            if (d1 != d2) {
                return d1 ? 1 : -1;
            }

            double score1 = precomputedScores.get(w1);
            double score2 = precomputedScores.get(w2);
            return Double.compare(score2, score1);
        });
    }

    public List<String> getPossibleWords() {
        return possibleWords;
    }

    private String pickDecisionGuess(List<String> candidates) {
        if (candidates.size() == 1)
            return candidates.get(0);

        String bestWord = candidates.get(0);
        int bestMaxPartition = Integer.MAX_VALUE;

        List<String> guessPool = candidates.size() < 50 ? candidates
                : originalWords.subList(0, Math.min(1000, originalWords.size()));

        for (String guess : guessPool) {
            Map<String, Integer> partition = new HashMap<>(32);

            for (String solution : candidates) {
                String feedback = simulateFeedback(guess, solution);
                partition.merge(feedback, 1, Integer::sum);
            }

            int worstBranch = partition.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (worstBranch < bestMaxPartition) {
                bestMaxPartition = worstBranch;
                bestWord = guess;
                if (bestMaxPartition == 1)
                    break; // Optimal found
            }
        }
        return bestWord;
    }

    private String simulateFeedback(String guess, String solution) {
        Arrays.fill(tempFeedback, (char) 0);
        Arrays.fill(tempSolutionUsed, false);

        for (int i = 0; i < 5; i++) {
            if (guess.charAt(i) == solution.charAt(i)) {
                tempFeedback[i] = 'G';
                tempSolutionUsed[i] = true;
            }
        }

        for (int i = 0; i < 5; i++) {
            if (tempFeedback[i] == 0) {
                char g = guess.charAt(i);
                boolean found = false;

                for (int j = 0; j < 5; j++) {
                    if (!tempSolutionUsed[j] && solution.charAt(j) == g) {
                        found = true;
                        tempSolutionUsed[j] = true;
                        break;
                    }
                }
                tempFeedback[i] = found ? 'Y' : 'X';
            }
        }

        return new String(tempFeedback);
    }

    private static String pickInformationGainWord(
            List<String> candidates,
            char[] knownPositions,
            Map<Integer, Set<Character>> yellowPositions,
            Set<Character> testedLetters) {

        Set<Character> mustIncludeYellows = new HashSet<>();
        for (Set<Character> ys : yellowPositions.values()) {
            mustIncludeYellows.addAll(ys);
        }

        String bestWordNoGreens = null;
        int bestScoreNoGreens = -1;
        String bestWordWithGreens = null;
        int bestScoreWithGreens = -1;

        boolean[] seenInWord = new boolean[26];

        for (String w : candidates) {
            boolean usesGreen = false;

            for (int i = 0; i < 5; i++) {
                if (knownPositions[i] != ' ' && w.charAt(i) == knownPositions[i]) {
                    usesGreen = true;
                    break;
                }
            }

            int score = 0;
            Arrays.fill(seenInWord, false);

            for (int i = 0; i < 5; i++) {
                char c = w.charAt(i);
                int cIdx = c - 'a';

                if (!testedLetters.contains(c) && !seenInWord[cIdx]) {
                    score += 2;
                    seenInWord[cIdx] = true;
                }

                Set<Character> yellowsAtPos = yellowPositions.get(i);
                if (yellowsAtPos != null && yellowsAtPos.contains(c)) {
                    score -= 1;
                } else if (mustIncludeYellows.contains(c)) {
                    score += 1;
                }
            }

            if (!usesGreen && score > bestScoreNoGreens) {
                bestScoreNoGreens = score;
                bestWordNoGreens = w;
            }

            if (score > bestScoreWithGreens) {
                bestScoreWithGreens = score;
                bestWordWithGreens = w;
            }
        }

        String chosen = (bestWordNoGreens != null) ? bestWordNoGreens : bestWordWithGreens;
        for (int i = 0; i < 5; i++) {
            testedLetters.add(chosen.charAt(i));
        }
        return chosen;
    }

    boolean isValidInput(String guess, String feedback) {
        if ("ERR".equals(feedback) || guess.length() != 5 || feedback.length() != 5) {
            possibleWords.removeIf(word -> word.equals(guess));
            System.out.println("invalid word removed, try again.....");
            return false;
        }
        if (feedback.length() != 5) {
            System.out.println(
                    "invalid feedback langth, ensure exactly 5 lettrs. Valid feed back includes \"X\" \"G\" \"Y\"");
            return false;
        }

        for (int i = 0; i < feedback.length(); i++) {
            char l = feedback.toLowerCase().charAt(i);
            if (l != 'x' && l != 'g' && l != 'y') {
                System.out.println("invalid feedback letter, ensure only \"X\" \"G\" \"Y\" are used");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
        WordleSolver solver = new WordleSolver("words_alpha_size_5.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        int guessCount = 0;
        Set<Character> testedLetters = new HashSet<>(26);
        String nextGuess = "";
        
        while (true) {
            boolean gameSolved = false;
            boolean gameFailed = false;
            
            while (solver.getPossibleWords().size() > 1) {
                if (guessCount == 0) {
                    nextGuess = "salet";
                    for (int i = 0; i < 5; i++)
                        testedLetters.add(nextGuess.charAt(i));
                } else if (guessCount == 1) {
                    nextGuess = "frogs";
                    for (int i = 0; i < 5; i++)
                        testedLetters.add(nextGuess.charAt(i));
                } else if (guessCount == 2) {
                    nextGuess = pickInformationGainWord(
                            solver.getPossibleWords(),
                            solver.knownPositions,
                            solver.yellowPositions,
                            testedLetters);
                } else if (solver.getPossibleWords().size() <= 8 && guessCount <= 5) {
                    nextGuess = solver.pickDecisionGuess(solver.getPossibleWords());
                } else {
                    nextGuess = solver.getPossibleWords().get(0);
                }
                
                System.out.println(solver.getPossibleWords().size() + " possible words remain.");
                System.out.println("Try: " + nextGuess);
                System.out.print("Enter feedback (G=Green, Y=Yellow, X=Gray): ");
                String feedback = reader.readLine().toUpperCase(Locale.ROOT);
                
                if (!solver.isValidInput(nextGuess, feedback)) {
                    continue;
                }
                
                if ("GGGGG".equals(feedback)) {
                    gameSolved = true;
                    guessCount++;
                    break;
                }
                
                solver.updateConstraints(nextGuess, feedback);
                guessCount++;
                
                if (solver.getPossibleWords().isEmpty()) {
                    System.out.println("Failed to solve — no possible words remain.");
                    gameFailed = true;
                    break;
                }
            }
            
            if (solver.getPossibleWords().size() == 1 && !gameSolved && !gameFailed) {
                nextGuess = solver.getPossibleWords().get(0);
                System.out.println("1 possible word remains.");
                System.out.println("Try: " + nextGuess);
                System.out.print("Enter feedback (G=Green, Y=Yellow, X=Gray): ");
                String feedback = reader.readLine().toUpperCase(Locale.ROOT);
                
                if (!solver.isValidInput(nextGuess, feedback)) {
                    continue;
                }
                
                guessCount++;
                if ("GGGGG".equals(feedback)) {
                    gameSolved = true;
                } else {
                    gameFailed = true;
                    System.out.println("Game failed - the feedback doesn't match the expected solution.");
                }
            }
            
            if (gameSolved) {
                System.out.println("Solution: " + nextGuess);
                System.out.println("Total guesses: " + guessCount);
                System.out.println("Wordle solved! Enter 'exit' to quit or press Enter to continue.");
            } else if (gameFailed) {
                System.out.println("Game failed after " + guessCount + " guesses.");
                System.out.println("Enter 'exit' to quit or press Enter to start a new game.");
            } else {
                System.out.println("Game interrupted. Enter 'exit' to quit or press Enter to start a new game.");
            }

            String input = reader.readLine();
            if ("exit".equalsIgnoreCase(input)) {
                break;
            }
            
            guessCount = 0;
            testedLetters.clear();
            solver = new WordleSolver(solver.originalWords);
            nextGuess = "";
            System.out.println("New game started.");
        }

        reader.close();
    }
}