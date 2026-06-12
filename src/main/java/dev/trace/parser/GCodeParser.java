package dev.trace.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GCodeParser {

    private static final Logger log = Logger.getLogger(GCodeParser.class.getName());
    private static final Pattern PARAM_PATTERN = Pattern.compile("([XYZRFS])(-?\\d+\\.?\\d*)");
    private static final Pattern CODE_PATTERN = Pattern.compile("([GM])(\\d+)");

    // Motion codes têm prioridade sobre modal codes em linhas com múltiplos G-codes
    private static final Set<GCodeCommandType> MOTION_CODES = Set.of(
            GCodeCommandType.G0, GCodeCommandType.G1,
            GCodeCommandType.G80, GCodeCommandType.G81
    );

    public List<GCodeCommand> parse(String gcode) {
        List<GCodeCommand> commands = new ArrayList<>();
        String[] lines = gcode.split("\\r?\\n");
        int lineNumber = 0;
        boolean inDrillCycle = false;

        for (String rawLine : lines) {
            lineNumber++;
            String line = rawLine.trim();

            if (line.isEmpty() || line.equals("%") || line.startsWith(";") || line.startsWith("(")) {
                continue;
            }

            // Remove inline comments
            line = line.replaceAll(";.*$", "").replaceAll("\\(.*?\\)", "").trim();

            if (line.isEmpty()) {
                continue;
            }

            line = line.toUpperCase();

            GCodeCommandType type = resolveType(line, inDrillCycle);

            if (type == GCodeCommandType.G81) inDrillCycle = true;
            if (type == GCodeCommandType.G80) inDrillCycle = false;

            Double x = null, y = null, z = null, r = null, feedRate = null, spindleSpeed = null;

            Matcher m = PARAM_PATTERN.matcher(line);
            while (m.find()) {
                double value = Double.parseDouble(m.group(2));
                switch (m.group(1)) {
                    case "X" -> x = value;
                    case "Y" -> y = value;
                    case "Z" -> z = value;
                    case "R" -> r = value;
                    case "F" -> feedRate = value;
                    case "S" -> spindleSpeed = value;
                }
            }

            commands.add(new GCodeCommand(lineNumber, type, x, y, z, r, feedRate, spindleSpeed, rawLine));
        }

        return commands;
    }

    /**
     * Resolve o tipo do comando considerando:
     * 1. Linhas sem G/M code dentro do bloco G81 → G81 (ponto de continuação)
     * 2. Linhas com múltiplos G-codes → motion codes têm prioridade
     */
    private GCodeCommandType resolveType(String line, boolean inDrillCycle) {
        Matcher m = CODE_PATTERN.matcher(line);

        GCodeCommandType found = null;
        while (m.find()) {
            // Normaliza zeros à esquerda: G01→G1, M00→M0, G81→G81
            String token = m.group(1) + Integer.parseInt(m.group(2));
            GCodeCommandType candidate;
            try {
                candidate = GCodeCommandType.valueOf(token);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MOTION_CODES.contains(candidate)) {
                return candidate; // motion code tem prioridade máxima
            }
            if (found == null) {
                found = candidate; // guarda o primeiro código válido encontrado
            }
        }

        if (found != null) return found;

        // Nenhum G/M code na linha — pode ser continuação de ciclo G81
        if (inDrillCycle) return GCodeCommandType.G81;

        return GCodeCommandType.UNKNOWN;
    }

    public List<GCodeCommand> getHoleOperations(List<GCodeCommand> commands) {
        return commands.stream()
                .filter(GCodeCommand::isHoleOperation)
                .toList();
    }

    public List<GCodeCommand> getTrackOperations(List<GCodeCommand> commands) {
        return commands.stream()
                .filter(GCodeCommand::isTrack)
                .toList();
    }

    public boolean hasEndOfProgram(List<GCodeCommand> commands) {
        return commands.stream().anyMatch(GCodeCommand::isEndOfProgram);
    }
}
