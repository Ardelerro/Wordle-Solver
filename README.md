# Wordle Solver

Wordle Solver is a Java-based tool to automatically solve the popular word game Wordle. It uses frequency analysis, feedback-based constraint narrowing, and optimized filtering to guess the solution in as few tries as possible.

## Usage

**Initial Setup:** Compile and run `WordleSolver.java`. Make sure the word list (`words_alpha_size_5.txt`) is available in the root directory.

**Solving a Game:** The solver will start with an initial guess and refine future guesses based on feedback (`G`, `Y`, `X` for green, yellow, black). It will continue until the correct word is found or all possibilities are exhausted.

**Feedback Format:** Feedback must be a 5-letter string composed of:
- `G` – Correct letter and position  
- `Y` – Correct letter, wrong position  
- `X` – Letter not in the word  

**Failure Handling:** If no solution is found, the program will notify the user and prompt to start a new game.

