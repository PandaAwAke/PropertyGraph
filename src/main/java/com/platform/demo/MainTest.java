package com.platform.demo;

import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainTest {

    public static List<File> getFiles(final File file) {

        final List<File> files = new ArrayList<File>();

        if (file.isFile() && file.getName().endsWith(".java")) {
            files.add(file);
        }

        else if (file.isDirectory()) {
            for (final File child : file.listFiles()) {
                final List<File> children = getFiles(child);
                files.addAll(children);
            }
        }

        return files;
    }

    public static void deleteFile(File[] files) {
        for (File file : files){
            if (file.isFile()){
                file.delete();
            }
            else if (file.isDirectory()){
                deleteFile(file.listFiles());
            }
        }
    }

}

