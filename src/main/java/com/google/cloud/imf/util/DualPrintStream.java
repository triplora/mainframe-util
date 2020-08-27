package com.google.cloud.imf.util;

import java.io.OutputStream;
import java.io.PrintStream;

public class DualPrintStream extends PrintStream {
    private PrintStream ps1;

    public DualPrintStream(PrintStream ps, PrintStream ps1) {
        super(ps, false);
        this.ps1 = ps1;
    }

    public DualPrintStream(PrintStream ps, OutputStream os) {
        this(ps, new PrintStream(os, false));
    }

    @Override
    public void write(int b) {
        ps1.write(b);
        super.write(b);
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        ps1.write(buf, off, len);
        super.write(buf, off, len);
    }

    @Override
    public void println(String x) {
        ps1.println(x);
        super.println(x);
    }

    @Override
    public void close() {
        ps1.close();
        super.close();
    }

    @Override
    public void flush() {
        ps1.flush();
        super.flush();
    }
}
