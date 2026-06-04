package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final List<Snake> allSnakes;
  private final Runnable onDeath;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  private volatile boolean paused = false;
  private final Object pauseLock = new Object();

  public SnakeRunner(Snake snake, Board board, List<Snake> allSnakes, Runnable onDeath) {
    this.snake = snake;
    this.board = board;
    this.allSnakes = allSnakes;
    this.onDeath = onDeath;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
        synchronized (pauseLock) {
          while (paused) pauseLock.wait();
        }
        maybeTurn();
        var res = board.step(snake, allSnakes);
        if (res == Board.MoveResult.HIT_SNAKE) {
          snake.kill();
          break; // onDeath se llama al final
        } else if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        }
        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0) turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    // Punto unico de notificacion de muerte: cubre HIT_SNAKE directo
    // y tambien el caso donde otra serpiente mato a esta (cabeza-cabeza)
    if (!snake.isAlive()) onDeath.run();
  }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }

  public void pause() { paused = true; }

  public void resume() {
    synchronized (pauseLock) {
      paused = false;
      pauseLock.notifyAll();
    }
  }
}
