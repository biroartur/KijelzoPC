package utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Power-cut-safe file replacement: the data goes to a temporary file that is flushed to disk,
 * then atomically renamed over the target. Readers see either the old or the new file, never a
 * partly written one.
 */
public final class SafeFile {

    private SafeFile() {
    }

    public static void write(File target, byte[] data) throws IOException {
        File dir = target.getAbsoluteFile().getParentFile();
        File tmp = new File(dir, target.getName() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(data);
            out.getFD().sync();
        } finally {
            out.close();
        }
        Files.move(tmp.toPath(), target.getAbsoluteFile().toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
