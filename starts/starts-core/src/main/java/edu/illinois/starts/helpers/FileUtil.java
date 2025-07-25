/*
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.
 */

package edu.illinois.starts.helpers;

import edu.illinois.starts.changelevel.Macros;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File handling utility methods.
 */
public class FileUtil {
    public static void delete(File file) {
        if (file.isDirectory()) {
            for (File childFile : file.listFiles()) {
                delete(childFile);
            }
        }
        file.delete();
    }

    public static String urlToClassName(String urlExternalForm){
        int i = urlExternalForm.indexOf("target/classes/");
        if (i == -1)
            i = urlExternalForm.indexOf("target/test-classes/") + "target/test-classes/".length();
        else
            i = i + + "target/classes/".length();
        String internalName = urlExternalForm.substring(i, urlExternalForm.length()-6);
        return internalName;
    }

    public static String urlToSerFilePath(String urlExternalForm){
        int index = urlExternalForm.indexOf("target");
        urlExternalForm = urlExternalForm.substring(index).replace(".class", "");
        StringBuffer sb = new StringBuffer();
        String[] array = urlExternalForm.split("/");
        for (int i = 2; i < array.length; i++){
            sb.append(array[i]);
            sb.append(".");
        }
        sb.append("ser");
        return System.getProperty("user.dir") + "/" + Macros.STARTS_ROOT_DIR_NAME + "/" +
            org.ekstazi.Names.CHANGE_TYPES_DIR_NAME + "/" + sb.toString();
    }

    public static void extractJar(Path jarPath, Path destinationDir) throws IOException {
        Files.createDirectories(destinationDir);

        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(jarPath))) {
            ZipEntry entry;

            while ((entry = zipStream.getNextEntry()) != null) {
                Path entryPath = destinationDir.resolve(entry.getName());

                // Security check to prevent zip slip attacks
                if (!entryPath.normalize().startsWith(destinationDir.normalize())) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                try {
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zipStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (FileAlreadyExistsException ex) {
                    // Ignore
                }
            }
        }
    }

    public static boolean recursiveDelete(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                recursiveDelete(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
