package io.vaultcheck.desktop;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/** Optional native decoration. Retains Windows close, resize, snap and accessibility controls. */
final class WindowsWindowTheme {
    private WindowsWindowTheme() { }
    static boolean apply(String title) {
        if (!Platform.isWindows()) return false;
        try {
            var user = Native.load("user32", User.class);
            var dwm = Native.load("dwmapi", Dwm.class);
            try (var contrast = new Memory(8 + Native.POINTER_SIZE)) {
                contrast.clear(); contrast.setInt(0, (int) contrast.size());
                if (!user.SystemParametersInfoW(0x42, (int) contrast.size(), contrast, 0)
                        || (contrast.getInt(4) & 1) != 0) return false;
            }
            boolean[] applied = {false};
            user.EnumWindows((window, ignored) -> {
                var pid = new IntByReference(); user.GetWindowThreadProcessId(window, pid);
                if (Integer.toUnsignedLong(pid.getValue()) != ProcessHandle.current().pid()) return true;
                char[] text = new char[512]; user.GetWindowTextW(window, text, text.length);
                if (!title.equals(Native.toString(text))) return true;
                dwm.DwmSetWindowAttribute(window, 20, new IntByReference(1), 4);
                int caption = dwm.DwmSetWindowAttribute(window, 35, new IntByReference(0x001a1a1a), 4);
                dwm.DwmSetWindowAttribute(window, 36, new IntByReference(0x00ffffff), 4);
                applied[0] = caption == 0;
                return false;
            }, null);
            return applied[0];
        } catch (LinkageError | RuntimeException unavailable) {
            return false; // Decoration failure must not prevent verification or change system settings.
        }
    }
    interface WindowCallback extends StdCallLibrary.StdCallCallback { boolean invoke(Pointer window, Pointer data); }
    interface User extends StdCallLibrary {
        boolean EnumWindows(WindowCallback callback, Pointer data);
        int GetWindowThreadProcessId(Pointer window, IntByReference pid);
        int GetWindowTextW(Pointer window, char[] text, int maximum);
        boolean SystemParametersInfoW(int action, int parameter, Pointer value, int flags);
    }
    interface Dwm extends StdCallLibrary {
        int DwmSetWindowAttribute(Pointer window, int attribute, IntByReference value, int size);
    }
}



