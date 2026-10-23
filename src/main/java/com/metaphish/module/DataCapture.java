package com.metaphish.module;

import com.metaphish.util.JsonUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DataCapture {
    private final String FilePath;

    public DataCapture(String FilePath) {
        this.FilePath = FilePath;
        try {
            File Parent = new File(FilePath).getParentFile();
            if (Parent != null) Parent.mkdirs();
        } catch (Exception Ignored) {}
    }

    public synchronized void Save(Map<String, String> Data) {
        try (FileWriter Writer = new FileWriter(FilePath, true)) {
            Writer.write(JsonUtil.ToJson(Data));
            Writer.write(System.lineSeparator());
        } catch (IOException Ignored) {}
    }

    public synchronized List<Map<String, String>> LoadAll() {
        List<Map<String, String>> Result = new ArrayList<>();
        File CapFile = new File(FilePath);
        if (!CapFile.exists()) return Result;
        try {
            List<String> Lines = Files.readAllLines(Path.of(FilePath));
            for (String Line : Lines) {
                if (Line.isBlank()) continue;
                Map<String, String> Parsed = JsonUtil.ParseObject(Line);
                if (!Parsed.isEmpty()) Result.add(Parsed);
            }
        } catch (IOException Ignored) {}
        return Result;
    }

    public synchronized void Clear() {
        try {
            new FileWriter(FilePath, false).close();
        } catch (IOException Ignored) {}
    }

    public synchronized int Count() {
        return LoadAll().size();
    }
}
