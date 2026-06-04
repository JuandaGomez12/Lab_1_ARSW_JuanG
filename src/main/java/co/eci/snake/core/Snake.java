package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Snake {
  private final Deque<Position> body = new ArrayDeque<>();
  private volatile Direction direction;
  private volatile boolean alive = true;
  private int maxLength = 5;

  private Snake(Position start, Direction dir) {
    body.addFirst(start);
    this.direction = dir;
  }

  public static Snake of(int x, int y, Direction dir) {
    return new Snake(new Position(x, y), dir);
  }

  public Direction direction() { return direction; }

  // synchronized: el EDT (teclado) y el runner (maybeTurn) pueden llamar turn() al mismo tiempo.
  // Sin esto hay un check-then-act sobre direction: ambos pasan el check y uno sobreescribe al otro.
  public synchronized void turn(Direction dir) {
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  public boolean isAlive() { return alive; }
  public void kill() { alive = false; }

  // synchronized: head(), occupies() y snapshot() pueden ser llamados desde el hilo de UI
  // mientras advance() es llamado desde el runner -> necesitan el mismo monitor
  public synchronized Position head() { return body.peekFirst(); }

  public synchronized boolean occupies(Position p) { return body.contains(p); }

  public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }

  public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();
  }

  public synchronized int getMaxLength() { return maxLength; }
}
