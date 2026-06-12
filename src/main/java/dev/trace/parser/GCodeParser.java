package dev.trace.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GCodeParser {

    private static final Logger log = Logger.getLogger(GCodeParser.class.getName());
    private static final Pattern PARAM_PATTERN = Pattern.compile("([XYZFS])(-?\\d+\\.?\\d*)");

    public List<GCodeCommand> parse(String gcode) {
        List<GCodeCommand> commands = new ArrayList<>();
        String[] lines = gcode.split("\\r?\\n");
        int lineNumber = 0;

        for (String rawLine : lines) {
            lineNumber++;
            String line = rawLine.trim();

            if (line.isEmpty() || line.startsWith(";") || line.startsWith("(")) {
                continue;
            }

            // Remove inline comments
            line = line.replaceAll(";.*$", "").replaceAll("\\(.*?\\)", "").trim();

            if (line.isEmpty()) {
                continue;
            }

            line = line.toUpperCase();

            String commandToken = line.split("\\s+")[0];
            GCodeCommandType type;
            try {
                type = GCodeCommandType.valueOf(commandToken);
            } catch (IllegalArgumentException e) {
                type = GCodeCommandType.UNKNOWN;
            }

            Double x = null, y = null, z = null, feedRate = null, spindleSpeed = null;

            Matcher m = PARAM_PATTERN.matcher(line);
            while (m.find()) {
                double value = Double.parseDouble(m.group(2));
                switch (m.group(1)) {
                    case "X" -> x = value;
                    case "Y" -> y = value;
                    case "Z" -> z = value;
                    case "F" -> feedRate = value;
                    case "S" -> spindleSpeed = value;
                }
            }

            commands.add(new GCodeCommand(lineNumber, type, x, y, z, feedRate, spindleSpeed, rawLine));
        }

        return commands;
    }

    public List<GCodeCommand> getHoleOperations(List<GCodeCommand> commands) {
        return commands.stream()
                .filter(GCodeCommand::isHoleOperation)
                .toList();
    }

    public boolean hasEndOfProgram(List<GCodeCommand> commands) {
        return commands.stream().anyMatch(GCodeCommand::isEndOfProgram);
    }
}
