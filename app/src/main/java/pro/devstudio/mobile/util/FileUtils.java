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

    // ✅ readFile - IOException ကို handle လုပ်ပြီး null ပြန်ပါ
    public static String readFile(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            // ✅ exception ကို handle လုပ်ပြီး null ပြန်ပါ
            return null;
        }
    }

    // ✅ writeFile - throws IOException
    public static void writeFile(File f, String content) throws IOException {
        if (f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(content);
        }
    }

    // ✅ deleteRecursive
    public static void deleteRecursive(File f) {
        if (f == null) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        f.delete();
    }

    // ✅ zipDirectory
    public static File zipDirectory(File dir, File outputZip) throws IOException {
        if (outputZip.getParentFile() != null && !outputZip.getParentFile().exists()) {
            outputZip.getParentFile().mkdirs();
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {
            addToZip(dir, "", zos);
        }
        return outputZip;
    }

    // ✅ addToZip
    private static void addToZip(File dir, String basePath, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                addToZip(file, basePath + file.getName() + "/", zos);
            } else {
                String entryName = basePath + file.getName();
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    // ✅ extensionOf
    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
