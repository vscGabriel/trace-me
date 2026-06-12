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
                G21 (inline em parênteses)
                %

                G0 X5
                """;

        List<GCodeCommand> commands = parser.parse(gcode);

        assertThat(commands).hasSize(3);
        assertThat(commands.get(0).type()).isEqualTo(GCodeCommandType.G21);
        assertThat(commands.get(1).type()).isEqualTo(GCodeCommandType.G21);
        assertThat(commands.get(2).type()).isEqualTo(GCodeCommandType.G0);
    }

    @Test
    void detectsHoleOperations() {
        String gcode = """
                G0 X10 Y20
                G98 G81 X5.00 Y5.00 Z-2.00 R1.50 F150
                X5.00 Y7.54
                X5.00 Y10.08
                G80
                G1 Z-0.12 F100
                """;

        List<GCodeCommand> commands = parser.parse(gcode);
        List<GCodeCommand> holes = parser.getHoleOperations(commands);

        assertThat(holes).hasSize(3);
        assertThat(holes).allMatch(GCodeCommand::isHoleOperation);
    }

    @Test
    void distinguishesHolesFromTracks() {
        String gcode = """
                G01 Z-0.12 F100
                G01 X5.00 Y20.00 F300
                G98 G81 X5.00 Y5.00 Z-2.00 R1.50 F150
                X5.00 Y7.54
                G80
                """;

        List<GCodeCommand> commands = parser.parse(gcode);

        List<GCodeCommand> holes = parser.getHoleOperations(commands);
        List<GCodeCommand> tracks = parser.getTrackOperations(commands);

        assertThat(holes).hasSize(2);
        assertThat(tracks).hasSize(2);
        assertThat(holes).noneMatch(GCodeCommand::isTrack);
        assertThat(tracks).noneMatch(GCodeCommand::isHoleOperation);
    }

    @Test
    void parsesG81WithMultipleCodesOnSameLine() {
        String gcode = "G98 G81 X5.00 Y5.00 Z-2.00 R1.50 F150";

        List<GCodeCommand> commands = parser.parse(gcode);

        assertThat(commands).hasSize(1);
        GCodeCommand cmd = commands.get(0);
        assertThat(cmd.type()).isEqualTo(GCodeCommandType.G81);
        assertThat(cmd.x()).isEqualTo(5.0);
        assertThat(cmd.y()).isEqualTo(5.0);
        assertThat(cmd.z()).isEqualTo(-2.0);
        assertThat(cmd.r()).isEqualTo(1.5);
        assertThat(cmd.feedRate()).isEqualTo(150.0);
    }

    @Test
    void detectsDrillContinuationLines() {
        String gcode = """
                G81 X1.00 Y1.00 Z-2.00 R1.50 F150
                X2.00 Y2.00
                X3.00 Y3.00
                G80
                """;

        List<GCodeCommand> commands = parser.parse(gcode);

        assertThat(commands).hasSize(4);
        assertThat(commands.get(0).type()).isEqualTo(GCodeCommandType.G81);
        assertThat(commands.get(1).type()).isEqualTo(GCodeCommandType.G81);
        assertThat(commands.get(2).type()).isEqualTo(GCodeCommandType.G81);
        assertThat(commands.get(3).type()).isEqualTo(GCodeCommandType.G80);
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
