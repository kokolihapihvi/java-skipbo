package mist2meat.javaskipbo.client;

import java.io.IOException;
import java.net.InetAddress;

import mist2meat.javaskipbo.Main;
import mist2meat.javaskipbo.client.drawable.AnimatedCard;
import mist2meat.javaskipbo.client.drawable.Card;
import mist2meat.javaskipbo.client.drawable.CardSlot;
import mist2meat.javaskipbo.client.game.Game;
import mist2meat.javaskipbo.client.game.LocalPlayer;
import mist2meat.javaskipbo.client.game.Player;
import mist2meat.javaskipbo.client.game.PlayerManager;
import mist2meat.javaskipbo.client.popups.EnterNamePopup;
import mist2meat.javaskipbo.enums.CardOperation;
import mist2meat.javaskipbo.enums.ServerLoginResponse;
import mist2meat.javaskipbo.network.ReceivedPacket;

import org.newdawn.slick.SlickException;

public class ClientEvents {

	public static void serverMessage(String msg) {
		Client.chatwindow.addLine(msg);
	}

	public static void serverLoginResponse(byte response, byte id) {
		if (response == ServerLoginResponse.LOGIN_SUCCESS) {
			Client.log("Login was successfull");
			LocalPlayer.id = id;
			Main.client.beginGame();
		} else if (response == ServerLoginResponse.LOGIN_NAME_TAKEN) {
			Client.log("Login failed: name is in use");
			new EnterNamePopup(LocalPlayer.getName());
		} else if (response == ServerLoginResponse.LOGIN_SERVER_FULL) {
			Client.log("Login failed: server is full");
			new EnterNamePopup(LocalPlayer.getName());
		} else if (response == ServerLoginResponse.LOGIN_INVALID_NAME_LENGTH) {
			Client.log("Login failed: invalid name length (2 >< 15)");
			new EnterNamePopup(LocalPlayer.getName());
		}
	}

	public static void serverPong(InetAddress ip) {
		try {
			Main.client.setServerAddress(ip);
			Main.client.start();
		} catch (SlickException e) {
			e.printStackTrace();
		}
	}

	public static void beginGame(byte gamemode) {
		Client.log("Game should begin!");
		Main.gamemode = gamemode;
		Main.client.prepareGame();
	}

	public static void cardOperation(ReceivedPacket pack) throws IOException {
		byte operation = pack.readByte();
		
		byte playerid,deckid,cardid,fromdeckid,todeckid;
		
		switch(operation){
			case CardOperation.DRAW_FROM_DECK:
				playerid = pack.readByte();
				deckid = pack.readByte();
				cardid = pack.readByte();
				
				CardSlot toslot;
				if(playerid == LocalPlayer.id){
					toslot = LocalPlayer.deckslots.get(deckid);
				}else{
					toslot = PlayerManager.getPlayerByID(playerid).deckslots.get(deckid);
				}
				
				new AnimatedCard(Game.deck,toslot,cardid);
				break;
			case CardOperation.DRAW_TO_HAND:
				byte slot = pack.readByte();
				cardid = pack.readByte();
				
				LocalPlayer.addToHand(slot,cardid);
				break;
			case CardOperation.PUT_TO_MIDDLE:
				playerid = pack.readByte();
				fromdeckid = pack.readByte();
				todeckid = pack.readByte();
				cardid = pack.readByte();
				
				byte wildcard = 0;
				
				if(cardid == 13){
					wildcard = pack.readByte();
				}
				
				if(fromdeckid >= 50){					
					if(playerid == LocalPlayer.id){
						LocalPlayer.handslots.get(fromdeckid-50).setCard(null);
					}
					
					fromdeckid = 0;//TODO: figure out a better way to represent placing cards from hand
				}else{
					if(playerid == LocalPlayer.id){
						if(fromdeckid >= 0 && fromdeckid <= 4){
							LocalPlayer.deckslots.get(fromdeckid).setCard(null);
						}
					}
				}
				
				if(playerid == LocalPlayer.id){
					new AnimatedCard(LocalPlayer.deckslots.get(fromdeckid),Game.middleDecks.get(todeckid),cardid,wildcard);
				}else{
					Player ply = PlayerManager.getPlayerByID(playerid);
					
					new AnimatedCard(ply.deckslots.get(0),Game.middleDecks.get(todeckid),cardid,wildcard);
					
					if(fromdeckid >= 1 && fromdeckid <= 4){
						ply.deckslots.get(fromdeckid).setCard(null);
					}
				}
				break;
			case CardOperation.PUT_TO_FREEDECK:
				playerid = pack.readByte();
				
				fromdeckid = pack.readByte();
				todeckid = pack.readByte();
				
				cardid = pack.readByte();
				
				if(fromdeckid >= 50){
					if(playerid == LocalPlayer.id){
						LocalPlayer.handslots.get(fromdeckid-50).setCard(null);
					}
					
					fromdeckid = 0;//TODO: figure out a better way to represent placing cards from hand
				}
				
				if(playerid == LocalPlayer.id){
					new AnimatedCard(LocalPlayer.deckslots.get(fromdeckid),LocalPlayer.deckslots.get(todeckid),cardid);
				}else{
					Player ply = PlayerManager.getPlayerByID(playerid);
					
					new AnimatedCard(ply.deckslots.get(fromdeckid),ply.deckslots.get(todeckid),cardid);
				}
				
				break;
			case CardOperation.SET_CARD:
				playerid = pack.readByte();
				deckid = pack.readByte();
				cardid = pack.readByte();
				boolean hascardunder = pack.readByte() == 1;

				Card card = null;

				if(cardid > 0){
					card = new Card(cardid);
				}else{
					hascardunder = false;
				}
				
				if(playerid == LocalPlayer.id){
					LocalPlayer.deckslots.get(deckid).setCard(card);
					LocalPlayer.deckslots.get(deckid).setHasCardUnder(hascardunder);
				}else{
					Player ply = PlayerManager.getPlayerByID(playerid);

					ply.deckslots.get(deckid).setCard(card);
					ply.deckslots.get(deckid).setHasCardUnder(hascardunder);
				}
				
				break;
			case CardOperation.EMPTY_DECK:
				deckid = pack.readByte();
				
				Game.middleDecks.get(deckid).setCard(null);
				break;
			default:
				Client.log("Unknown card operation: "+operation);
				break;
		}
	}

	public static void playersTurn(byte playerid) {
		PlayerManager.resetTurn();
		LocalPlayer.myTurn = false;
		if(playerid == LocalPlayer.id){
			LocalPlayer.myTurn = true;
			Client.log("Your turn");
		}else{
			Player pl = PlayerManager.getPlayerByID(playerid);
			pl.setTurn(true);
			Client.log(pl.getName()+"'s turn");
		}
	}

	public static void endGame(byte winnerid) {
		PlayerManager.resetTurn();
		LocalPlayer.myTurn = false;
		
		if(winnerid == LocalPlayer.id){
			Client.log("You won the game!");
			Client.chatwindow.addLine("You won the game!");
		}else{
			Player winner = PlayerManager.getPlayerByID(winnerid);
			Client.log(winner.getName()+" won the game!");
			Client.chatwindow.addLine(winner.getName()+" won the game!");
		}
		Client.log("Game over");
	}
}
