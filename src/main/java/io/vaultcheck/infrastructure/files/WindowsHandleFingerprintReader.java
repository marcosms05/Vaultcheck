package io.vaultcheck.infrastructure.files;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import io.vaultcheck.domain.FileFingerprint;
import io.vaultcheck.application.RootedFingerprintReader;
import io.vaultcheck.domain.ManifestPath;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Local Windows pilot. Retains ancestor handles without write/delete sharing until reading ends.
 * Not a snapshot guarantee, and not yet qualified for network providers or hostile drive remapping.
 */
public final class WindowsHandleFingerprintReader implements RootedFingerprintReader {
    private static final int READ_ATTRIBUTES = 0x80;
    private static final int GENERIC_READ = 0x80000000;
    private static final int SHARE_READ = 1;
    private static final int OPEN_EXISTING = 3;
    private static final int BACKUP_SEMANTICS = 0x02000000;
    private static final int OPEN_REPARSE_POINT = 0x00200000;
    private static final int DIRECTORY = 0x10;
    private static final int REPARSE_POINT = 0x400;

    @Override public FileFingerprint read(Path selectedRoot, ManifestPath relative, BooleanSupplier cancelled)
            throws IOException {
        Objects.requireNonNull(selectedRoot);
        Objects.requireNonNull(relative);
        Objects.requireNonNull(cancelled);
        if (!Platform.isWindows() || selectedRoot.getFileSystem() != FileSystems.getDefault()) {
            throw new IOException("Native reader requires the Windows default filesystem");
        }
        Path root = selectedRoot.toAbsolutePath().normalize();
        Path volume = root.getRoot();
        if (volume == null || !volume.toString().matches("[A-Za-z]:\\\\")) {
            throw new IOException("Network and device roots are not yet supported by this reader");
        }
        Kernel api = KernelHolder.API;
        if (api.GetDriveTypeW(new WString(volume.toString())) != 3) {
            throw new IOException("Only fixed local drives are qualified for this pilot");
        }
        // Bound and validate the complete chain, including components of the selected root.
        String suffix = root.getNameCount() == 0 ? relative.value()
                : root.toString().substring(volume.toString().length()).replace('\\', '/') + "/" + relative.value();
        new ManifestPath(suffix);
        checkCancelled(cancelled);
        try (var handles = new Handles(api)) {
            Path current = volume;
            handles.open(current, true);
            String[] components = suffix.split("/");
            Pointer file = null;
            for (int i = 0; i < components.length; i++) {
                checkCancelled(cancelled);
                current = current.resolve(components[i]);
                file = handles.open(current, i < components.length - 1);
            }
            Info before = info(api, file);
            if (before.links != 1) throw new IOException("Hard-linked files are not supported by this pilot");
            long expected = before.fileSize();
            MessageDigest digest = sha256();
            byte[] buffer = new byte[64 * 1024];
            var transferred = new IntByReference();
            long count = 0;
            while (true) {
                checkCancelled(cancelled);
                if (!api.ReadFile(file, buffer, buffer.length, transferred, null)) throw failure("ReadFile");
                int read = transferred.getValue();
                if (read < 0 || read > buffer.length) throw new IOException("Invalid native read length");
                if (read == 0) break;
                if (read > expected - count) throw new IOException("File grew during reading");
                count += read;
                digest.update(buffer, 0, read);
            }
            Info after = info(api, file);
            if (count != expected || after.fileSize() != expected || before.writeLow != after.writeLow
                    || before.writeHigh != after.writeHigh || after.links != 1) {
                throw new IOException("File changed during reading");
            }
            return new FileFingerprint(count, HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Verification cancelled");
        }
    }

    /** Retain the full root chain, then retain only one extra handle per nested directory. */
    static DirectoryLease retainDirectory(Path selectedRoot) throws IOException {
        if (!Platform.isWindows() || selectedRoot.getFileSystem() != FileSystems.getDefault()) {
            throw new IOException("Windows default filesystem required");
        }
        Path root = selectedRoot.toAbsolutePath().normalize();
        Path volume = root.getRoot();
        if (volume == null || !volume.toString().matches("[A-Za-z]:\\\\")
                || KernelHolder.API.GetDriveTypeW(new WString(volume.toString())) != 3) {
            throw new IOException("Only fixed local drives supported");
        }
        if (root.getNameCount() > 0) new ManifestPath(root.toString().substring(volume.toString().length()).replace('\\', '/'));
        var handles = new Handles(KernelHolder.API);
        try {
            Path current = volume;
            handles.open(current, true);
            for (Path name : root) { current = current.resolve(name); handles.open(current, true); }
            return new DirectoryLease(root, handles);
        } catch (IOException | RuntimeException | Error failure) {
            try { handles.close(); } catch (IOException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    static final class DirectoryLease implements AutoCloseable {
        private final Path path;
        private final Handles handles;
        private boolean closed;
        private DirectoryLease(Path path, Handles handles) { this.path = path; this.handles = handles; }
        java.nio.file.DirectoryStream<Path> entries() throws IOException {
            if (closed) throw new IOException("Directory lease is closed");
            return java.nio.file.Files.newDirectoryStream(path);
        }
        DirectoryLease child(String name) throws IOException {
            if (closed) throw new IOException("Directory lease is closed");
            new ManifestPath(name);
            if (name.contains("/")) throw new IOException("Expected a single component");
            var childHandles = new Handles(KernelHolder.API);
            try {
                Path child = path.resolve(name);
                childHandles.open(child, true);
                return new DirectoryLease(child, childHandles);
            } catch (IOException | RuntimeException | Error failure) {
                try { childHandles.close(); } catch (IOException close) { failure.addSuppressed(close); }
                throw failure;
            }
        }
        @Override public void close() throws IOException {
            if (!closed) { closed = true; handles.close(); }
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static IOException failure(String operation) {
        return new IOException(operation + " failed (Windows error " + Native.getLastError() + ")");
    }

    private static Info info(Kernel api, Pointer handle) throws IOException {
        var result = new Info();
        if (!api.GetFileInformationByHandle(handle, result)) throw failure("GetFileInformationByHandle");
        return result;
    }

    private static final class Handles implements AutoCloseable {
        private final Kernel api;
        private final ArrayList<Pointer> opened = new ArrayList<>();
        Handles(Kernel api) { this.api = api; }

        Pointer open(Path path, boolean directory) throws IOException {
            // Every parent is already retained. OPEN_REPARSE_POINT addresses the last component itself.
            Pointer handle = api.CreateFileW(new WString(path.toString()), directory ? READ_ATTRIBUTES : GENERIC_READ,
                    SHARE_READ, null, OPEN_EXISTING, BACKUP_SEMANTICS | OPEN_REPARSE_POINT, null);
            if (handle == null || Pointer.nativeValue(handle) == -1L) throw failure("CreateFileW");
            opened.add(handle);
            Info attributes = info(api, handle);
            if ((attributes.attributes & REPARSE_POINT) != 0
                    || ((attributes.attributes & DIRECTORY) != 0) != directory) {
                throw new IOException("Redirected path or unsupported file type");
            }
            return handle;
        }

        @Override public void close() throws IOException {
            IOException error = null;
            for (int i = opened.size() - 1; i >= 0; i--) {
                if (!api.CloseHandle(opened.get(i))) {
                    IOException next = failure("CloseHandle");
                    if (error == null) error = next; else error.addSuppressed(next);
                }
            }
            opened.clear();
            if (error != null) throw error;
        }
    }

    // Explicit W entry points and DWORD layout; no Java reflection into JDK file descriptors.
    public interface Kernel extends StdCallLibrary {
        Pointer CreateFileW(WString name, int access, int sharing, Pointer security, int disposition, int flags, Pointer template);
        boolean GetFileInformationByHandle(Pointer handle, Info information);
        boolean ReadFile(Pointer handle, byte[] buffer, int capacity, IntByReference read, Pointer overlapped);
        boolean CloseHandle(Pointer handle);
        int GetDriveTypeW(WString root);
    }

    private static final class KernelHolder {
        private static final Kernel API = Native.load("kernel32", Kernel.class);
    }

    @Structure.FieldOrder({"attributes", "creationLow", "creationHigh", "accessLow", "accessHigh",
            "writeLow", "writeHigh", "volume", "sizeHigh", "sizeLow", "links", "indexHigh", "indexLow"})
    public static class Info extends Structure {
        public int attributes, creationLow, creationHigh, accessLow, accessHigh, writeLow, writeHigh;
        public int volume, sizeHigh, sizeLow, links, indexHigh, indexLow;
        long fileSize() throws IOException {
            if (sizeHigh < 0) throw new IOException("File exceeds supported size");
            return ((long) sizeHigh << 32) | Integer.toUnsignedLong(sizeLow);
        }
    }
}
