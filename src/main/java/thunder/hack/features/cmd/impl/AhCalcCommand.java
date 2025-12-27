package thunder.hack.features.cmd.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.NotNull;
import thunder.hack.features.cmd.Command;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class AhCalcCommand extends Command {
    public AhCalcCommand() {
        super("ahcalc");
    }

    @Override
    public void executeBuild(@NotNull LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(arg("expr", StringArgumentType.greedyString()).executes(context -> {
            String expr = context.getArgument("expr", String.class).trim();
            String[] parts = expr.split("\\s+");
            
            if (parts.length != 3) {
                sendMessage("Usage: /ahcalc <number> <operator> <number>");
                sendMessage("Operators: + - * /");
                return -1;
            }
            
            long a, b;
            try {
                a = Long.parseLong(parts[0]);
                b = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                sendMessage("Invalid numbers!");
                return -1;
            }
            
            String op = parts[1];
            long res = 0;
            
            switch (op) {
                case "*" -> res = a * b;
                case "/" -> {
                    if (b != 0) res = a / b;
                    else {
                        sendMessage("Division by zero!");
                        return -1;
                    }
                }
                case "+" -> res = a + b;
                case "-" -> res = a - b;
                default -> {
                    sendMessage("Invalid operator! Use: + - * /");
                    return -1;
                }
            }
            
            if (res > 0 && mc.player != null) {
                mc.player.networkHandler.sendCommand("ah sell " + res);
                sendMessage("Sent: /ah sell " + res);
            }
            
            return SINGLE_SUCCESS;
        }));
    }
}

