package primefinder;

import java.util.Scanner;

public class Control extends Thread {

    private final static int NTHREADS = 3;
    private final static int MAXVALUE = 30000000;
    private final static int TMILISECONDS = 1000;

    private static Control control;
    private final int NDATA = MAXVALUE / NTHREADS;
    private final Scanner scanner = new Scanner(System.in);
    // volatile para visibilidad inmediata entre hilos sin entrar al monitor
    private volatile boolean pause = false;

    private PrimeFinderThread pft[];

    private Control() {
        super();
        this.pft = new PrimeFinderThread[NTHREADS];

        int i;
        for(i = 0; i < NTHREADS - 1; i++) {
            pft[i] = new PrimeFinderThread(i * NDATA, (i + 1) * NDATA);
        }
        pft[i] = new PrimeFinderThread(i * NDATA, MAXVALUE + 1);
    }

    public static Control newControl() {
        if (control == null) control = new Control();
        return control;
    }

    @Override
    public void run() {
        for(int i = 0; i < NTHREADS; i++) {
            pft[i].start();
        }
        try {
            while(true) {
                Thread.sleep(TMILISECONDS);
                pauseThreads();
                System.out.println("Primos encontrados hasta ahora: " + amountOfPrimesFound());
                System.out.println("Presione ENTER para continuar...");
                scanner.nextLine();
                resumeThreads();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int amountOfPrimesFound() {
        int total = 0;
        for (PrimeFinderThread t : pft) total += t.getAmountOfPrimes();
        return total;
    }

    public synchronized void pauseThreads() {
        pause = true;
    }

    public synchronized void resumeThreads() {
        pause = false;
        notifyAll();
    }

    // Monitor: la instancia Control (this).
    // Condicion: pause == true -> el hilo llama wait() y libera el lock.
    // El while evita lost wakeups: si despierta y pause sigue true, vuelve a esperar.
    public synchronized void checkPause() {
        while(pause) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
