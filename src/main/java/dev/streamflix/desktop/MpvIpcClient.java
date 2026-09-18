package dev.streamflix.desktop;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronous mpv JSON IPC client for Windows named pipes.
 * Requests are deliberately serialized: Win32 synchronous pipe handles are most
 * reliable when one request owns the write/read cycle at a time.
 */
final class MpvIpcClient implements AutoCloseable {
    private final WinNT.HANDLE handle;
    private final AtomicLong sequence = new AtomicLong();
    private final ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream(1024);
    private volatile boolean closed;

    private MpvIpcClient(WinNT.HANDLE handle) {
        this.handle = handle;
    }

    static MpvIpcClient connect(String pipePath, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        int slice = 120;

        while (System.nanoTime() < deadline) {
            Kernel32.INSTANCE.WaitNamedPipe(pipePath, slice);
            WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                    pipePath,
                    WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                    0,
                    null,
                    WinNT.OPEN_EXISTING,
                    0,
                    null
            );
            if (handle != null && !WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                return new MpvIpcClient(handle);
            }
            Thread.sleep(60);
        }

        throw new IOException("No se pudo conectar con el reproductor.");
    }

    Object getProperty(String name) throws Exception {
        return request(List.of("get_property", name), 2200).get("data");
    }

    void setProperty(String name, Object value) throws Exception {
        request(List.of("set_property", name, value), 2200);
    }

    void command(List<?> command) throws Exception {
        request(command, 2200);
    }

    synchronized Map<String, Object> request(List<?> command, long timeoutMillis) throws Exception {
        ensureOpen();
        long requestId = sequence.incrementAndGet();

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("request_id", requestId);
        write(Json.stringify(payload) + "\n");

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            String line = readLine(deadline);
            if (line == null || line.isBlank()) continue;

            Object parsed;
            try { parsed = Json.parse(line); }
            catch (RuntimeException ignored) { continue; }
            if (!(parsed instanceof Map<?, ?>)) continue;

            Map<String, Object> response = Json.object(parsed);
            Integer responseId = Json.integer(response.get("request_id"));
            if (responseId == null || responseId.longValue() != requestId) continue;

            String error = Json.string(response.get("error"));
            if (!error.isBlank() && !"success".equalsIgnoreCase(error)) {
                throw new IOException("mpv: " + error);
            }
            return response;
        }

        throw new TimeoutException("El reproductor no respondió a tiempo.");
    }

    private void write(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        IntByReference written = new IntByReference();
        boolean ok = Kernel32.INSTANCE.WriteFile(handle, bytes, bytes.length, written, null);
        if (!ok || written.getValue() != bytes.length) {
            throw new IOException("No se pudo enviar un comando al reproductor (Win32 "
                    + Kernel32.INSTANCE.GetLastError() + ").");
        }
    }

    private String readLine(long deadline) throws IOException, TimeoutException {
        while (System.nanoTime() < deadline) {
            int newline = indexOfNewline(pendingBytes.toByteArray());
            if (newline >= 0) return consumeLine(newline);

            byte[] buffer = new byte[4096];
            IntByReference read = new IntByReference();
            boolean ok = Kernel32.INSTANCE.ReadFile(handle, buffer, buffer.length, read, null);
            int error = ok ? WinError.ERROR_SUCCESS : Kernel32.INSTANCE.GetLastError();

            if (!ok && error != WinError.ERROR_MORE_DATA) {
                throw new IOException("El reproductor cerró el canal de control (Win32 " + error + ").");
            }
            if (read.getValue() > 0) pendingBytes.write(buffer, 0, read.getValue());
        }
        throw new TimeoutException("El reproductor no respondió a tiempo.");
    }

    private String consumeLine(int newline) {
        byte[] all = pendingBytes.toByteArray();
        int length = newline;
        if (length > 0 && all[length - 1] == '\r') length--;

        String line = new String(all, 0, length, StandardCharsets.UTF_8);
        pendingBytes.reset();
        if (newline + 1 < all.length) {
            pendingBytes.write(all, newline + 1, all.length - newline - 1);
        }
        return line;
    }

    private static int indexOfNewline(byte[] data) {
        for (int i = 0; i < data.length; i++) if (data[i] == '\n') return i;
        return -1;
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("IPC cerrado");
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Kernel32.INSTANCE.CloseHandle(handle);
        pendingBytes.reset();
    }
}
