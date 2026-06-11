import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HospitalGUI extends JFrame {
    private static final int TOTAL_NODOS = 5;
    private static final String HOST = "localhost";
    private static final Color COLOR_VIVO = new Color(200, 255, 200);
    private static final Color COLOR_MUERTO = new Color(255, 200, 200);
    private static final Color COLOR_COORDINADOR = new Color(255, 215, 0);
    private static final Color COLOR_INICIANDO = new Color(255, 255, 200);

    private final NodePanel[] nodos = new NodePanel[TOTAL_NODOS];
    private final JTextArea logArea = new JTextArea(20, 70);
    private final Map<Integer, Process> procesos = new ConcurrentHashMap<>();
    private final JLabel coordLabel = new JLabel("Coordinador: --");
    private Timer timer;

    public HospitalGUI() {
        setTitle("Red Hospitalaria - Algoritmo Bully");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(Color.WHITE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(Color.WHITE);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(Color.WHITE);

        JLabel titulo = new JLabel("Simulacion del Algoritmo Bully", SwingConstants.CENTER);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        topPanel.add(titulo, BorderLayout.NORTH);

        coordLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        coordLabel.setHorizontalAlignment(SwingConstants.CENTER);
        coordLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        topPanel.add(coordLabel, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, TOTAL_NODOS, 12, 0));
        grid.setBackground(Color.WHITE);
        for (int i = 0; i < TOTAL_NODOS; i++) {
            nodos[i] = new NodePanel(i + 1);
            grid.add(nodos[i]);
        }
        mainPanel.add(grid, BorderLayout.CENTER);

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Registro de eventos"));
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(245, 245, 245));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(800, 250));
        logPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        controlPanel.setBackground(Color.WHITE);

        JButton btnIniciarTodos = new JButton("Iniciar todos");
        JButton btnDetenerTodos = new JButton("Detener todos");
        JButton btnLimpiar = new JButton("Limpiar log");

        btnIniciarTodos.addActionListener(e -> iniciarTodos());
        btnDetenerTodos.addActionListener(e -> detenerTodos());
        btnLimpiar.addActionListener(e -> logArea.setText(""));

        btnIniciarTodos.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnDetenerTodos.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnLimpiar.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        controlPanel.add(btnIniciarTodos);
        controlPanel.add(btnDetenerTodos);
        controlPanel.add(btnLimpiar);
        mainPanel.add(controlPanel, BorderLayout.PAGE_END);

        add(mainPanel);

        timer = new Timer(1500, e -> actualizarEstados());
        timer.start();

        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                timer.stop();
                detenerTodos();
            }
        });
    }

    private void iniciarTodos() {
        new Thread(() -> {
            for (int i = TOTAL_NODOS; i >= 1; i--) {
                iniciarNodo(i);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private void iniciarNodo(int id) {
        if (procesos.containsKey(id)) {
            log("Nodo " + id + " ya esta en ejecucion");
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "HospitalNode", String.valueOf(id));
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            procesos.put(id, p);
            nodos[id - 1].setEstado(Estado.INICIANDO);

            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        final String msg = line;
                        SwingUtilities.invokeLater(() -> {
                            log(msg);
                            if (msg.contains("SOY EL NUEVO COORDINADOR")) {
                                actualizarCoordinador(id);
                            }
                            if (msg.contains("Nuevo coordinador: Nodo")) {
                                String[] partes = msg.split("Nodo ");
                                if (partes.length > 1) {
                                    try {
                                        actualizarCoordinador(Integer.parseInt(partes[1].trim()));
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        });
                    }
                } catch (IOException ignored) {}
                procesos.remove(id);
                SwingUtilities.invokeLater(() -> {
                    if (obtenerCoordinadorActual() == id) {
                        actualizarCoordinador(-1);
                    }
                });
            }).start();

            log("Nodo " + id + " iniciado");
        } catch (IOException e) {
            log("Error al iniciar Nodo " + id + ": " + e.getMessage());
        }
    }

    private void detenerNodo(int id) {
        try (Socket s = new Socket(HOST, 5000 + id);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("SHUTDOWN");
            log("Enviado SHUTDOWN a Nodo " + id);
        } catch (IOException e) {
            log("Nodo " + id + " no responde: " + e.getMessage());
        }
        procesos.remove(id);
        nodos[id - 1].setEstado(Estado.MUERTO);
    }

    private void detenerTodos() {
        for (int i = 1; i <= TOTAL_NODOS; i++) {
            detenerNodo(i);
        }
    }

    private void actualizarEstados() {
        for (int i = 1; i <= TOTAL_NODOS; i++) {
            try (Socket s = new Socket(HOST, 5000 + i);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                s.setSoTimeout(1500);
                out.println("PING");
                String resp = in.readLine();
                if ("PONG".equals(resp)) {
                    if (nodos[i - 1].getEstado() != Estado.COORDINADOR
                        && nodos[i - 1].getEstado() != Estado.INICIANDO) {
                        nodos[i - 1].setEstado(Estado.VIVO);
                    }
                } else {
                    nodos[i - 1].setEstado(Estado.MUERTO);
                }
            } catch (IOException e) {
                if (nodos[i - 1].getEstado() == Estado.VIVO
                    || nodos[i - 1].getEstado() == Estado.COORDINADOR) {
                    nodos[i - 1].setEstado(Estado.MUERTO);
                }
            }
        }
    }

    private void actualizarCoordinador(int id) {
        // Reset all coordinators first
        for (int i = 0; i < TOTAL_NODOS; i++) {
            if (nodos[i].getEstado() == Estado.COORDINADOR) {
                nodos[i].setEstado(Estado.VIVO);
            }
        }
        if (id >= 1 && id <= TOTAL_NODOS) {
            nodos[id - 1].setEstado(Estado.COORDINADOR);
            coordLabel.setText("Coordinador: Nodo " + id);
            coordLabel.setForeground(new Color(180, 140, 0));
        } else {
            coordLabel.setText("Coordinador: --");
            coordLabel.setForeground(Color.BLACK);
        }
    }

    private int obtenerCoordinadorActual() {
        for (int i = 0; i < TOTAL_NODOS; i++) {
            if (nodos[i].getEstado() == Estado.COORDINADOR) return i + 1;
        }
        return -1;
    }

    private void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        logArea.append("[" + ts + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // --- Modelo ---
    enum Estado { VIVO, MUERTO, INICIANDO, COORDINADOR }

    // --- Panel de cada nodo ---
    class NodePanel extends JPanel {
        private final int id;
        private final JLabel lblEstado;
        private final JButton btnAccion;
        private Estado estado = Estado.MUERTO;

        public NodePanel(int id) {
            this.id = id;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Color.GRAY, 2, true),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)));
            setPreferredSize(new Dimension(130, 180));

            JLabel lblId = new JLabel("NODO " + id, SwingConstants.CENTER);
            lblId.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblId.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lblPuerto = new JLabel("Puerto " + (5000 + id), SwingConstants.CENTER);
            lblPuerto.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblPuerto.setForeground(Color.DARK_GRAY);
            lblPuerto.setAlignmentX(Component.CENTER_ALIGNMENT);

            lblEstado = new JLabel("Muerto", SwingConstants.CENTER);
            lblEstado.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblEstado.setAlignmentX(Component.CENTER_ALIGNMENT);

            btnAccion = new JButton("Iniciar");
            btnAccion.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            btnAccion.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnAccion.addActionListener(e -> toggle());

            add(Box.createVerticalGlue());
            add(lblId);
            add(Box.createRigidArea(new Dimension(0, 4)));
            add(lblPuerto);
            add(Box.createRigidArea(new Dimension(0, 8)));
            add(lblEstado);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(btnAccion);
            add(Box.createVerticalGlue());

            setEstado(Estado.MUERTO);
        }

        private void toggle() {
            if (estado == Estado.MUERTO) {
                iniciarNodo(id);
            } else {
                detenerNodo(id);
            }
        }

        public void setEstado(Estado e) {
            this.estado = e;
            switch (e) {
                case VIVO:
                    setBackground(COLOR_VIVO); lblEstado.setText("Vivo");
                    lblEstado.setForeground(new Color(0, 100, 0)); btnAccion.setText("Matar"); break;
                case MUERTO:
                    setBackground(COLOR_MUERTO); lblEstado.setText("Muerto");
                    lblEstado.setForeground(Color.RED); btnAccion.setText("Iniciar"); break;
                case INICIANDO:
                    setBackground(COLOR_INICIANDO); lblEstado.setText("Iniciando...");
                    lblEstado.setForeground(Color.ORANGE); btnAccion.setText("Matar"); break;
                case COORDINADOR:
                    setBackground(COLOR_COORDINADOR); lblEstado.setText("COORDINADOR");
                    lblEstado.setForeground(new Color(140, 100, 0)); btnAccion.setText("Matar"); break;
            }
            repaint();
        }

        public Estado getEstado() { return estado; }

        protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HospitalGUI().setVisible(true));
    }
}
