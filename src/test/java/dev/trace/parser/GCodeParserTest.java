package dev.trace.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GCodeParserTest {

    private final GCodeParser parser = new GCodeParser();

    @Test
    void parseBasicCommands() {
        String gcode = """
                G21
                G0 X10 Y20
                G1 Z-1.5 F100
                M30
                """;

        List<GCodeCommand> commands = parser.parse(gcode);

        assertThat(commands).hasSize(4);
        assertThat(commands.get(0).type()).isEqualTo(GCodeCommandType.G21);
        assertThat(commands.get(1).type()).isEqualTo(GCodeCommandType.G0);
        assertThat(commands.get(1).x()).isEqualTo(10.0);
        assertThat(commands.get(1).y()).isEqualTo(20.0);
        assertThat(commands.get(2).type()).isEqualTo(GCodeCommandType.G1);
        assertThat(commands.get(2).z()).isEqualTo(-1.5);
        assertThat(commands.get(2).feedRate()).isEqualTo(100.0);
        assertThat(commands.get(3).type()).isEqualTo(GCodeCommandType.M30);
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        String gcode = """
                ; isto é um comentário
                (também comentário)
                G21 ; inline comment

                G0 X5
                """;

        List<GCodeCommand> commands = parser.parse(gcode);

        assertThat(commands).hasSize(2);
        assertThat(commands.get(0).type()).isEqualTo(GCodeCommandType.G21);
        assertThat(commands.get(1).type()).isEqualTo(GCodeCommandType.G0);
    }

    @Test
    void detectsHoleOperations() {
        String gcode = """
                G0 X10 Y20
                G1 Z-1.5 F100
                G1 Z1.0
                G1 Z-2.0 F80
                """;

        List<GCodeCommand> commands = parser.parse(gcode);
        List<GCodeCommand> holes = parser.getHoleOperations(commands);

        assertThat(holes).hasSize(2);
        assertThat(holes).allMatch(GCodeCommand::isHoleOperation);
    }

    @Test
    void detectsEndOfProgram() {
        String gcodeM2 = "G21\nM2";
        String gcodeM30 = "G21\nM30";
        String gcodeNone = "G21\nG0 X10";

        assertThat(parser.hasEndOfProgram(parser.parse(gcodeM2))).isTrue();
        assertThat(parser.hasEndOfProgram(parser.parse(gcodeM30))).isTrue();
        assertThat(parser.hasEndOfProgram(parser.parse(gcodeNone))).isFalse();
    }
}
