package pro.devstudio.mobile.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {

    public static String readFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException e) { /* ignore */ }
        return sb.toString();
    }

    public static void writeFile(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
    }

    public static void deleteRecursive(File f) {
        if (f.isDirectory()) for (File c : f.listFiles()) deleteRecursive(c);
        f.delete();
    }

    /** Zip the entire directory into outputZip. */
    public static File zipDirectory(File dir, File outputZip) throws IOException {
        outputZip.getParentFile().mkdirs();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {
            addToZip(dir, dir.getName() + "/", zos);
        }
        return outputZip;
    }

    private static void addToZip(File f, String prefix, ZipOutputStream zos) throws IOException {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children == null) return;
            for (File child : children) addToZip(child, prefix + child.getName() + (child.isDirectory() ? "/" : ""), zos);
        } else {
            zos.putNextEntry(new ZipEntry(prefix));
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
