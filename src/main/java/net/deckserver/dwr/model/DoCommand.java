/*
 * MkState.java
 *
 * Created on February 22, 2004, 3:50 PM
 */

package net.deckserver.dwr.model;

import net.deckserver.game.interfaces.state.Card;
import net.deckserver.game.interfaces.state.Location;
import net.deckserver.game.storage.cards.CardSearch;
import net.deckserver.storage.json.cards.CardSummary;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.slf4j.LoggerFactory.getLogger;

public class DoCommand {

    private final JolGame game;
    private final GameModel model;

    public DoCommand(JolGame game, GameModel model) {
        this.game = game;
        this.model = model;
    }

    public String doMessage(String player, String message, boolean isJudge) {
        if (message.isEmpty())
            return "No message received";
        game.sendMsg(player, message, isJudge);
        return "Sent message";
    }

    public void doCommand(String player, String command) throws CommandException {
        String[] cmdStr = command.trim().split("[\\s\n\r\f\t]");
        String cmd = cmdStr[0];
        CommandParser cmdObj = new CommandParser(cmdStr, 1, game);
        switch (cmd.toLowerCase()) {
            case "timeout":
                timeOut(player);
                break;
            case "vp":
                vp(cmdObj, player);
                break;
            case "choose":
                choose(cmdObj, player);
                break;
            case "reveal":
                reveal();
                break;
            case "label":
                label(cmdObj, player);
                break;
            case "votes":
                votes(cmdObj, player);
                break;
            case "random":
                random(cmdObj, player);
                break;
            case "flip":
                flip(player);
                break;
            case "discard":
                discard(cmdObj, player);
                break;
            case "draw":
                draw(cmdObj, player);
                break;
            case "edge":
                edge(cmdObj, player);
                break;
            case "play":
                play(cmdObj, player);
                break;
            case "influence":
                influence(cmdObj, player);
                break;
            case "move":
                move(cmdObj, player);
                break;
            case "burn":
                burn(cmdObj, player);
                break;
            case "pool":
                pool(cmdObj, player);
                break;
            case "blood":
                blood(cmdObj, player);
                break;
            case "contest":
                contest(cmdObj, player);
                break;
            case "disc":
                disciplines(cmdObj, player);
                break;
            case "capacity":
                capacity(cmdObj, player);
                break;
            case "unlock":
                unlock(cmdObj, player);
                break;
            case "lock":
                lock(cmdObj, player);
                break;
            case "order":
                order(cmdObj, player);
                break;
            case "show":
                show(cmdObj, player);
                break;
            case "shuffle":
                shuffle(cmdObj, player);
                break;
            case "transfer":
                transfer(cmdObj, player);
                break;
            case "rfg":
                rfg(cmdObj, player);
                break;
        }
    }

    void contest(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String targetCard = cmdObj.findCard(false, targetPlayer, targetRegion);
        boolean clear = cmdObj.consumeString("clear");
        game.contestCard(targetCard, clear);
    }

    void transfer(CommandParser cmdObj, String player) throws CommandException {
        String targetRegion = cmdObj.getRegion(JolGame.INACTIVE_REGION);
        String card = cmdObj.findCard(false, player, targetRegion);
        int amount = cmdObj.getAmount(1);
        if (amount == 0) throw new CommandException("Must transfer an amount");
        int pool = game.getPool(player);
        if (pool - amount < 0) throw new CommandException("Invalid amount to transfer.  Not enough pool.");
        game.transfer(player, card, amount);
    }

    void shuffle(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.LIBRARY);
        int num = cmdObj.getNumber(0);
        game.shuffle(targetPlayer, targetRegion, num);
    }

    void show(CommandParser cmdObj, String player) throws CommandException {
        String targetRegion = cmdObj.getRegion(JolGame.LIBRARY);
        int amt = cmdObj.getNumber(100);
        boolean all = cmdObj.consumeString("all");
        List<String> recipients = all ? game.getPlayers() : Collections.singletonList(cmdObj.getPlayer(player));
        Location loc = game.getState().getPlayerLocation(player, targetRegion);
        Card[] cards = loc.getCards();
        StringBuilder buf = new StringBuilder();
        int len = Math.min(cards.length, amt);
        buf.append(len);
        buf.append(" cards of ");
        buf.append(player);
        buf.append("'s ");
        buf.append(targetRegion);
        buf.append(":\n");
        for (int j = 0; j < len; j++) {
            buf.append("  ");
            buf.append(j + 1);
            buf.append(" ");
            buf.append(cards[j].getName());
            buf.append("\n");
        }
        String text = buf.toString();
        for (String recipient : recipients) {
            String old = game.getPrivateNotes(recipient);
            game.setPrivateNotes(recipient, old.isEmpty() ? text : old + "\n" + text);
            model.getView(recipient).privateNotesChanged();
        }
        String msg;
        if (player.equals(recipients.getFirst())) {
            msg = player + " looks at " + len + " cards of their " + targetRegion + ".";
        } else {
            msg = player + " shows " + (recipients.size() > 1 ? "everyone" : recipients.getFirst()) + " " + len + " cards of their " + targetRegion + ".";
        }
        game.addMessage(msg);
    }

    void order(CommandParser cmdObj, String player) throws CommandException {
        List<String> players = game.getPlayers();
        List<String> newOrder = new ArrayList<>();
        for (int j = 0; j < players.size(); j++) {
            int index = cmdObj.getNumber(0);
            if (index < 1 || index > players.size()) throw new CommandException("Bad number : " + index);
            newOrder.add(players.get(index - 1));
        }
        game.setOrder(newOrder);
    }

    void lock(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String card = cmdObj.findCard(true, targetPlayer, targetRegion);
        if (game.isTapped(card))
            throw new CommandException("Card is already locked");
        game.setLocked(player, card, true);
    }

    void unlock(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        if (!cmdObj.hasMoreArgs()) {
            game.unlockAll(targetPlayer);
        } else {
            String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
            String card = cmdObj.findCard(true, targetPlayer, targetRegion);
            game.setLocked(player, card, false);
        }
    }

    void capacity(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String targetCard = cmdObj.findCard(false, targetPlayer, targetRegion);
        int amount = cmdObj.getAmount(0);
        if (amount == 0) throw new CommandException("Must specify an amount of blood");
        game.changeCapacity(targetCard, amount, false);
    }

    void disciplines(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String targetCard = cmdObj.findCard(false, targetPlayer, targetRegion);
        if (cmdObj.consumeString("reset")) {
            CardSummary card = CardSearch.INSTANCE.get(game.getCard(targetCard).getCardId());
            List<String> disciplines = card.getDisciplines();
            game.setDisciplines(player, targetCard, disciplines, false);
        } else {
            Set<String> additions = new HashSet<>();
            Set<String> removals = new HashSet<>();
            while (cmdObj.hasMoreArgs()) {
                String next = cmdObj.nextArg();
                String type = next.substring(0, 1);
                String disc = next.substring(1);
                if (!ChatParser.isDiscipline(disc.toLowerCase())) {
                    throw new CommandException("Not a valid discipline");
                }
                if (type.equals("+")) {
                    additions.add(disc);
                } else if (type.equals("-")) {
                    removals.add(disc);
                } else {
                    throw new CommandException("Need to specify + or - to change disciplines");
                }
            }
            game.setDisciplines(player, targetCard, additions, removals);
        }
    }

    void blood(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String targetCard = cmdObj.findCard(false, false, targetPlayer, targetRegion);
        int amount = cmdObj.getAmount(0);
        if (amount == 0) throw new CommandException("Must specify an amount of blood");
        game.changeCounters(player, targetCard, amount, false);
    }

    void pool(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        int amount = cmdObj.getAmount(0);
        if (amount != 0) {
            game.changePool(targetPlayer, amount);
        } else {
            throw new CommandException("Must specify an amount of pool.");
        }
    }

    void burn(CommandParser cmdObj, String player) throws CommandException {
        String srcPlayer = cmdObj.getPlayer(player);
        String srcRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String cardId = cmdObj.findCard(true, srcPlayer, srcRegion);
        boolean random = Arrays.asList(cmdObj.args).contains("random");
        boolean top = Arrays.asList(cmdObj.args).contains("top");
        game.burn(player, cardId, srcPlayer, srcRegion, random);
    }

    void rfg(CommandParser cmdObj, String player) throws CommandException {
        String srcPlayer = cmdObj.getPlayer(player);
        String srcRegion = cmdObj.getRegion(JolGame.ASH_HEAP);
        String cardId = cmdObj.findCard(true, srcPlayer, srcRegion);
        boolean random = Arrays.asList(cmdObj.args).contains("random");
        game.rfg(player, cardId, srcPlayer, srcRegion, random);
    }

    void move(CommandParser cmdObj, String player) throws CommandException {
        String srcPlayer = cmdObj.getPlayer(player);
        String srcRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String srcCard = cmdObj.findCard(false, srcPlayer, srcRegion);
        String destPlayer = cmdObj.getPlayer(player);
        String destRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String destCard = cmdObj.findCard(false, false, destPlayer, destRegion);
        boolean top = Arrays.asList(cmdObj.args).contains("top");

        if (List.of(JolGame.READY_REGION, JolGame.INACTIVE_REGION, JolGame.TORPOR).contains(destRegion) && destCard != null) {
            game.moveToCard(player, srcCard, destCard);
        } else {
            game.moveToRegion(player, srcCard, destPlayer, destRegion, top);
        }
    }

    void influence(CommandParser cmdObj, String player) throws CommandException {
        String srcCard = cmdObj.findCard(false, player, JolGame.INACTIVE_REGION);
        game.influenceCard(player, srcCard, player, JolGame.READY_REGION);
    }

    void play(CommandParser cmdObj, String player) throws CommandException {
        boolean crypt = cmdObj.consumeString("vamp");
        if (crypt) {
            throw new CommandException("Invalid command. Use influence instead");
        }
        String srcRegion = cmdObj.getRegion(JolGame.HAND);
        String srcCard = cmdObj.findCard(false, player, srcRegion);

        String[] modes = null;
        boolean modeSpecified = cmdObj.consumeString("@");
        if (modeSpecified)
            modes = cmdObj.nextArg().split(",");

        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.ASH_HEAP);
        String targetCard = cmdObj.findCard(false, false, targetPlayer, targetRegion);
        boolean draw = cmdObj.consumeString("draw");
        game.playCard(player, srcCard, targetPlayer, targetRegion, targetCard, modes);
        if (draw) game.drawCard(player, JolGame.LIBRARY, JolGame.HAND);
    }

    void edge(CommandParser cmdObj, String player) throws CommandException {
        // edge [<player> | burn]
        if (cmdObj.consumeString("burn")) {
            game.burnEdge(player);
        } else {
            String edge = cmdObj.getPlayer(player);
            game.setEdge(edge);
        }
    }

    void draw(CommandParser cmdObj, String player) throws CommandException {
        boolean crypt = cmdObj.consumeString("crypt") || cmdObj.consumeString("vamp");
        int count = cmdObj.getNumber(1);
        if (count <= 0) throw new CommandException("Must draw at least 1 card.");
        for (int j = 0; j < count; j++) {
            if (crypt)
                game.drawCard(player, JolGame.CRYPT, JolGame.INACTIVE_REGION);
            else
                game.drawCard(player, JolGame.LIBRARY, JolGame.HAND);
        }
    }

    void label(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String card = cmdObj.findCard(false, targetPlayer, targetRegion);
        StringBuilder note = new StringBuilder();
        while (cmdObj.hasMoreArgs()) {
            note.append(" ");
            note.append(cmdObj.nextArg());
        }
        game.setLabel(card, note.toString(), false);
    }

    void random(CommandParser cmdObj, String player) throws CommandException {
        int limit = cmdObj.getNumber(-1);
        if (limit < 1) limit = 2;
        int result = ThreadLocalRandom.current().nextInt(1, limit + 1);
        game.random(player, limit, result);
    }

    void discard(CommandParser cmdObj, String player) throws CommandException {
        boolean random = Arrays.asList(cmdObj.args).contains("random");
        String card = cmdObj.findCard(true, player, JolGame.HAND);
        game.discard(player, card, random);
        if (cmdObj.consumeString("draw")) {
            game.drawCard(player, JolGame.LIBRARY, JolGame.HAND);
        }
    }

    void flip(String player) {
        String result = ThreadLocalRandom.current().nextInt(2) == 0 ? "Heads" : "Tails";
        game.flip(player, result);
    }

    void votes(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        String targetRegion = cmdObj.getRegion(JolGame.READY_REGION);
        String card = cmdObj.findCard(false, targetPlayer, targetRegion);
        game.setVotes(card, cmdObj.nextArg(), false);
    }

    void reveal() {
        game.getChoices();
    }

    void timeOut(String player) {
        game.requestTimeout(player);
    }

    void vp(CommandParser cmdObj, String player) throws CommandException {
        String targetPlayer = cmdObj.getPlayer(player);
        if (cmdObj.consumeString("withdraw")) {
            game.withdraw(targetPlayer);
        } else {
            int amount = cmdObj.getAmount(0);
            if (amount == 0) {
                throw new CommandException("No amount given use +/-");
            }
            game.updateVP(targetPlayer, amount);
        }
    }

    void choose(CommandParser cmdObj, String player) {
        String choice = cmdObj.nextArg();
        game.setChoice(player, choice);
    }
}
