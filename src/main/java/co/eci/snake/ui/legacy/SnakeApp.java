package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton startButton;
  private final JButton pauseButton;
  private final JButton resumeButton;
  private final JLabel statusLabel;
  private final GameClock clock;
  private final List<Snake> snakes = new ArrayList<>();
  private final List<SnakeRunner> threads = new ArrayList<>();
  // Orden de muerte: el primero en entrar fue el primero en morir
  private final List<Snake> deathOrder = Collections.synchronizedList(new ArrayList<>());

  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 5);
    // Distribuir serpientes en cuadricula para que no se choquen al inicio
    int cols = (int) Math.ceil(Math.sqrt(N));
    int rows = (int) Math.ceil((double) N / cols);
    for (int i = 0; i < N; i++) {
      int col = i % cols;
      int row = i / cols;
      int x = board.width()  / (cols + 1) * (col + 1);
      int y = board.height() / (rows + 1) * (row + 1);
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }

    this.gamePanel = new GamePanel(board, () -> snakes);
    this.startButton  = new JButton("Iniciar");
    this.pauseButton  = new JButton("Pausar");
    this.resumeButton = new JButton("Reanudar");
    this.statusLabel  = new JLabel(" ");

    JPanel southButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    southButtons.add(startButton);
    southButtons.add(pauseButton);
    southButtons.add(resumeButton);
    southButtons.add(statusLabel);

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(southButtons, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    for (Snake s : snakes) {
      final int idx = snakes.indexOf(s);
      SnakeRunner runner = new SnakeRunner(s, board, snakes, () -> onSnakeDeath(idx));
      threads.add(runner);
    }

    startButton.addActionListener((ActionEvent e) -> toggleStart());
    pauseButton.addActionListener((ActionEvent e) -> togglePause());
    resumeButton.addActionListener((ActionEvent e) -> toggleResume());

    pauseButton.setEnabled(false);
    resumeButton.setEnabled(false);

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    var player = snakes.get(0);
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"),  "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"),    "up");
    im.put(KeyStroke.getKeyStroke("DOWN"),  "down");
    am.put("left", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.LEFT); }
    });
    am.put("right", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.RIGHT); }
    });
    am.put("up", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.UP); }
    });
    am.put("down", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.DOWN); }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.LEFT); }
      });
      am.put("p2-right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.RIGHT); }
      });
      am.put("p2-up", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.UP); }
      });
      am.put("p2-down", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.DOWN); }
      });
    }

    setVisible(true);
  }

  // Llamado desde el hilo del runner cuando una serpiente muere
  private void onSnakeDeath(int idx) {
    Snake s = snakes.get(idx);
    deathOrder.add(s);
    System.out.println("Serpiente " + idx + " murio. Longitud maxima: " + s.getMaxLength());
  }

  private void toggleStart() {
    System.out.println("Iniciando juego con " + snakes.size() + " serpientes");
    var exec = Executors.newVirtualThreadPerTaskExecutor();
    for (SnakeRunner runner : threads) {
      exec.submit(runner);
    }
    clock.start();
    startButton.setEnabled(false);
    pauseButton.setEnabled(true);
    resumeButton.setEnabled(true);
  }

  private void togglePause() {
    System.out.println("Pausando juego");
    threads.forEach(SnakeRunner::pause);
    clock.pause();
    refreshStatus();
  }

  private void toggleResume() {
    System.out.println("Reanudando juego");
    threads.forEach(SnakeRunner::resume);
    clock.resume();
    statusLabel.setText(" ");
  }

  // Calcula y muestra las estadisticas en consola y en el label de la UI
  private void refreshStatus() {
    Comparator<Snake> byLength = Comparator.comparingInt(Snake::getMaxLength);
    var longest = snakes.stream().filter(Snake::isAlive).max(byLength);
    String longestText = longest.map(s -> "Serpiente " + snakes.indexOf(s) + " (largo: " + s.getMaxLength() + ")")
                                .orElse("ninguna viva");

    String firstDead;
    synchronized (deathOrder) {
      firstDead = deathOrder.isEmpty() ? "ninguna"
          : "Serpiente " + snakes.indexOf(deathOrder.get(0));
    }

    System.out.println("Serpiente viva mas larga: " + longestText);
    System.out.println("Primera serpiente en morir: " + firstDead);
    statusLabel.setText("Mas larga: " + longestText + "  |  Primera en morir: " + firstDead);
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleports (flechas rojas)
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo (rayos)
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes — solo se dibujan las vivas
      var snakes = snakesSupplier.get();
      int idx = 0;
      for (Snake s : snakes) {
        if (!s.isAlive()) { idx++; continue; }
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
        idx++;
      }
      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
