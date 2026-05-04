package com.vesper.app;

import javax.swing.JOptionPane;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Launcher {
    public static void main(String[] args) {
        try {
            VesperApp.main(args);
        } catch (Throwable t) {
            // Если всё упало, покажем окно с ошибкой (через Swing, он надежнее при падении JFX)
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            JOptionPane.showMessageDialog(null, "Критическая ошибка запуска:\n" + sw.toString(),
                    "Vesper Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}