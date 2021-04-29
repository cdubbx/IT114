package server;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import common.GameState;
import common.MarkerType;
import common.Player;
import common.Ship;
import common.ShipType;
import core.BaseGamePanel;
import core.Countdown;

public class Room extends BaseGamePanel implements AutoCloseable {
	private static SocketServer server;// used to refer to accessible server functions
	private String name;
	private final static Logger log = Logger.getLogger(Room.class.getName());

	// Commands
	private final static String COMMAND_TRIGGER = "/";
	private final static String CREATE_ROOM = "createroom";
	private final static String JOIN_ROOM = "joinroom";
	private final static String READY = "ready";
	private List<ClientPlayer> clients = new ArrayList<ClientPlayer>();
	static Dimension gameAreaSize = new Dimension(600, 600);
	List<Ship> ships = new ArrayList<Ship>();
	private int attackingPlayer = -1;
	final private int minPlayers = 2;
	GameState gameState = GameState.LOBBY;
	private Countdown gameResetter;

	public Room(String name, boolean delayStart) {
		super(delayStart);
		this.name = name;
		isServer = true;
	}

	public Room(String name) {
		this.name = name;
		// set this for BaseGamePanel to NOT draw since it's server-side
		isServer = true;
	}

	public static void setServer(SocketServer server) {
		Room.server = server;
	}

	public String getName() {
		return name;
	}

	private static Point getRandomStartPosition() {
		Point startPos = new Point();
		startPos.x = (int) (Math.random() * gameAreaSize.width);
		startPos.y = (int) (Math.random() * gameAreaSize.height);
		return startPos;
	}

	protected synchronized void addClient(ServerThread client) {
		client.setCurrentRoom(this);
		boolean exists = false;
		// since we updated to a different List type, we'll need to loop through to find
		// the client to check against
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			if (c.client == client) {
				exists = true;
				if (c.player == null) {
					log.log(Level.WARNING, "Client " + client.getClientName() + " player was null, creating");
					Player p = new Player();
					p.setName(client.getClientName());
					c.player = p;
					syncClient(c);
				}
				break;
			}
		}

		if (exists) {
			log.log(Level.INFO, "Attempting to add a client that already exists");
		} else {
			// create a player reference for this client
			// so server can determine position
			Player p = new Player();
			p.setName(client.getClientName());
			// add Player and Client reference to ClientPlayer object reference
			ClientPlayer cp = new ClientPlayer(client, p);
			clients.add(cp);// this is a "merged" list of Clients (ServerThread) and Players (Player)
			// objects
			// that's so we don't have to keep track of the same client in two different
			// list locations
			syncClient(cp);

		}
	}

	private void syncClient(ClientPlayer cp) {
		if (cp.client.getClientName() != null) {
			cp.client.sendClearList();
			sendConnectionStatus(cp.client, true, "joined the room " + getName());
			// calculate random start position
			Point startPos = Room.getRandomStartPosition();
			cp.player.setPosition(startPos);
			// tell our client of our server determined position
			cp.client.sendPosition(cp.client.getClientName(), startPos);
			// tell everyone else about our server determiend position
			sendPositionSync(cp.client, startPos);
			// get the list of connected clients (for ui panel)
			updateClientList(cp.client);
			// get dir/pos of existing players
			updatePlayers(cp.client);
			cp.client.sendGameAreaSize(gameAreaSize);
		}
	}

	/***
	 * Syncs the existing players in the room with our newly connected player
	 * 
	 * @param client
	 */
	private synchronized void updatePlayers(ServerThread client) {
		// when we connect, send all existing clients current position and direction so
		// we can locally show this on our client
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			if (c.client != client) {
				boolean messageSent = client.sendDirection(c.client.getClientName(), c.player.getDirection());
				if (messageSent) {
					messageSent = client.sendPosition(c.client.getClientName(), c.player.getPosition());
				}
			}
		}
	}

	/**
	 * Syncs the existing clients in the room with our newly connected client
	 * 
	 * @param client
	 */
	private synchronized void updateClientList(ServerThread client) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			if (c.client != client) {
				boolean messageSent = client.sendConnectionStatus(c.client.getClientName(), true, null);
			}
		}
	}

	protected synchronized void removeClient(ServerThread client) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			if (c.client == client) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + c.client.getClientName() + " from " + getName());
			}
		}
		if (clients.size() > 0) {
			sendConnectionStatus(client, false, "left the room " + getName());
			checkPlayers();

		} else {
			cleanupEmptyRoom();
		}
	}

	private void checkPlayers() {
		int activePlayers = 0;
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			if (c.player.isReady() && c.player.getShips() > 0) {
				activePlayers++;
			}
		}
		if (activePlayers < minPlayers) {
			// TODO end the game
			sendSystemMessage("Battle Over, not enough players. Restarting session");
			resetGame();
		}
	}

	private void cleanupEmptyRoom() {
		// If name is null it's already been closed. And don't close the Lobby
		if (name == null || name.equalsIgnoreCase(SocketServer.LOBBY)) {
			return;
		}
		try {
			log.log(Level.INFO, "Closing empty room: " + name);
			close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void joinRoom(String room, ServerThread client) {
		server.joinRoom(room, client);
	}

	protected void joinLobby(ServerThread client) {
		server.joinLobby(client);
	}

	protected void createRoom(String room, ServerThread client) {
		if (server.createNewRoom(room)) {
			sendMessage(client, "Created a new room");
			joinRoom(room, client);
		}
	}

	/// ship req/res
	protected void placeShip(Point coords, ServerThread client) {
		if (gameState != GameState.PLACEMENT) {
			return;
		}
		if (coords.x >= 0 && coords.x <= gameAreaSize.width && coords.y >= 0 && coords.y <= gameAreaSize.height) {
			ClientPlayer cp = getCP(client);
			if (!cp.player.isReady()) {
				sendSystemMessage("Spectators can't place ships.", cp.client.getClientName());
				return;
			}
			Integer count = cp.player.getShips();// countShipsForPlayer(client);
			Ship s = null;
			if (count < 10) {
				s = new Ship(true);
				s.setOwner(cp);
				s.setSize(50, 50);
				s.setName(ShipType.GUNNER.toString());// TODO allow various types
				s.setPosition(coords);// TODO verify in bounds
				ships.add(s);
				s.setId(ships.size());
				cp.player.addShip();
			}
			if (s != null) {
				sendPlacement(s, client);
				sendRemainingShipCount(client, cp.player.getShips());
			}
		}
	}

	private void sendPlacement(Ship s, ServerThread owner) {
		owner.sendShipPlacement(ShipType.valueOf(s.getName()).ordinal(), s.getId(), s.getPosition(), s.getMaxHealth());
	}
	/// ship req/res

	/// attack req/res
	protected void attack(Point coords, ServerThread client) {
		if (gameState != GameState.TURNS) {
			return;
		}
		// TODO only attack ships you don't own
		Iterator<Ship> iter = ships.iterator();
		boolean didHit = false;
		ClientPlayer cp = getCP(client);
		int attacksRemaining = cp.player.getAttacks();
		if (attacksRemaining > 0) {
			cp.player.setAttacks(attacksRemaining - 1);
			System.out.println("Remaining attacks: " + cp.player.getAttacks());
			sendAttackRadius(coords, 50);
			while (iter.hasNext()) {
				Ship s = iter.next();
				if (s.getHealth() > 0 && !s.getOwner().client.equals(client)) {
					if (collision(s, coords, 50)) {
						didHit = true;
						sendShipStatus(s.getOwner().client, s.getId(), s.getHealth());
						if (!s.isActive()) {
							s.getOwner().player.removeShip();
						}
						sendRemainingShipCount(s.getOwner().client, s.getOwner().player.getShips());
					}
				}
			}
			sendAttack(coords, didHit ? MarkerType.HIT : MarkerType.MISS, client);
		}
		if (cp.player.getAttacks() <= 0) {
			setNextPlayer();
		}
	}

	private void sendAttack(Point coords, MarkerType marker, ServerThread attacker) {
		attacker.sendAttackStatus(coords, marker.ordinal());
	}

	private void sendShipStatus(ServerThread owner, int shipId, int life) {
		owner.sendShipStatus(shipId, life);
	}

	private void sendAttackRadius(Point coords, int radius) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			boolean messageSent = c.client.sendAttackRadius(coords, radius);

		}
	}

	private void sendCanAttack(ServerThread client, int attacks) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			boolean messageSent = c.client.sendCanAttack(client.getClientName(), attacks);

		}
	}

	@Deprecated
	private int countShipsForPlayer(ServerThread client) {
		int count = 0;
		for (Ship ship : ships) {
			if (ship.isActive() && ship.getOwner().client.equals(client)) {
				count++;
			}
		}
		return count;
	}

	/// attack req/res
	private void sendRemainingShipCount(ServerThread client, int count) {

		// int count = countShipsForPlayer(client);

		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			boolean messageSent = c.client.sendRemainingShipCount(client.getClientName(), count);
		}

	}

	boolean collision(Ship ship, Point coords, int blastRadius) {
		Point p = ship.getCenter();

		System.out.println(getName());
		System.out.println("P: " + p);
		System.out.println("T: " + coords);
		System.out.println("Dist: " + coords.distance(p));
		// add the two halfwidths together to get the max distance between the two until
		// a collision occurs
		int dist = (int) ((ship.getSize().height * .5) + (blastRadius));

		// NOTE:
		// same as below IF: if(tp.distance(p) <= dist)
		// (more expensive - calcs square root)
		if (coords.distanceSq(p) <= (dist * dist)) { // (cheaper - doesn't need to calc square root)
			// ticket is within range, do the exchange/pickup
			ship.hit();
			return true;
		}
		return false;
	}

	private void resetGame() {
		if(gameResetter != null) {
			gameResetter.cancel();
			gameResetter = null;
		}
		// reset server local ships
		Iterator<Ship> iter = ships.iterator();
		while (iter.hasNext()) {
			iter.next();
			iter.remove();
		}
		// reset attacking player
		attackingPlayer = -1;

		String winner = "[Draw]";
		// tell clients to reset their "boards"
		Iterator<ClientPlayer> iter2 = clients.iterator();
		while (iter2.hasNext()) {
			ClientPlayer cp = iter2.next();
			int remaining = cp.player.getShips();
			if (remaining > 0) {
				winner = cp.client.getClientName();
			}
			cp.player.reset();
			cp.client.sendReset(winner);
		}
		gameState = GameState.POST_GAME;
		new Countdown("", 10, (x) -> {
			gameState = GameState.LOBBY;
		});
	}

	private ClientPlayer getCP(ServerThread client) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer cp = iter.next();
			if (cp.client == client) {
				return cp;
			}
		}
		return null;
	}

	private void readyCheck() {
		Iterator<ClientPlayer> iter = clients.iterator();
		int total = clients.size();
		int ready = 0;
		while (iter.hasNext()) {
			ClientPlayer cp = iter.next();
			if (cp != null && cp.player.isReady()) {
				ready++;
			}
		}
		if (ready >= minPlayers) {
			// start
			System.out.println("Everyone's ready, let's do this!");
			if(gameResetter != null) {
				gameResetter.cancel();
				gameResetter = null;
			}
			gameResetter = new Countdown("", 60*60, (x)->{
				sendSystemMessage("Game Ending due to extensive duration");
				resetGame();
			});
			new Countdown("", 5, (x) -> {
				/*
				 * Iterator<ClientPlayer> iter2 = clients.iterator(); int total2 =
				 * clients.size(); System.out.println("Total moving " + total2); boolean
				 * isCreated = false; List<ClientPlayer> pending = new
				 * ArrayList<ClientPlayer>(); while (iter2.hasNext()) { ClientPlayer cp =
				 * iter2.next(); System.out.println("Moving player"); if (cp != null &&
				 * cp.player.isReady()) { pending.add(cp); } } synchronized(pending) {
				 * Iterator<ClientPlayer> p = pending.iterator(); while(p.hasNext()) {
				 * ClientPlayer cp = p.next(); if(!isCreated) { isCreated = true;
				 * createRoom("game", cp.client); } else { joinRoom("game", cp.client); } } }
				 */

				sendSystemMessage("Commencing Game...");
				moveToPlaceShipPhase();
				// Note, initial sub 1 since function increments
				// attackingPlayer = (int)(Math.random() * (clients.size()-1)) - 1;
				// setNextPlayer();
			});

		}
	}

	private void moveToPlaceShipPhase() {
		Iterator<ClientPlayer> iter = clients.iterator();
		gameState = GameState.PLACEMENT;
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			boolean messageSent = c.client.sendPhaseUpdate(GameState.PLACEMENT);
		}
		new Countdown("", 15, (x) -> {
			moveToTurnPhase();
		});
	}

	private void moveToTurnPhase() {
		Iterator<ClientPlayer> iter = clients.iterator();
		gameState = GameState.TURNS;
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			boolean messageSent = c.client.sendPhaseUpdate(GameState.TURNS);
		}
		attackingPlayer = (int) (Math.random() * (clients.size() - 1)) - 1;
		setNextPlayer();
	}

	private void setNextPlayer() {
		attackingPlayer++;
		if (attackingPlayer >= clients.size()) {
			attackingPlayer = 0;
		}
		boolean assignedAttacker = false;
		for (int i = 0; i < clients.size(); i++) {
			ClientPlayer cp = clients.get(i);
			if (cp.player.getShips() > 0) {
				cp.player.setAttacks(i == attackingPlayer ? 1 : 0);
				sendCanAttack(cp.client, cp.player.getAttacks());
				if(i == attackingPlayer) {
					assignedAttacker = true;
				}
			}
		}
		if(!assignedAttacker) {
			Iterator<ClientPlayer> iter = clients.iterator();
			int playersWithShips = 0;
			while (iter.hasNext()) {
				ClientPlayer _cp = iter.next();
				if (_cp.player.getShips() > 0) {
					playersWithShips++;
				}
			}
			if (playersWithShips > 1) {
				// skip players who are "out"
				setNextPlayer();
			} else {
				System.out.println("Reset game, not enough players");
				resetGame();
			}
		}
	}

	/***
	 * Helper function to process messages to trigger different functionality.
	 * 
	 * @param message The original message being sent
	 * @param client  The sender of the message (since they'll be the ones
	 *                triggering the actions)
	 */
	private String processCommands(String message, ServerThread client) {
		String response = null;
		try {

			if (message.startsWith(COMMAND_TRIGGER)) {
				String[] comm = message.split(COMMAND_TRIGGER);
				log.log(Level.INFO, message);
				String part1 = comm[1];
				String[] comm2 = part1.split(" ");
				String command = comm2[0];
				if (command != null) {
					command = command.toLowerCase();
				}
				String roomName;
				ClientPlayer cp = null;
				switch (command) {
				case CREATE_ROOM:
					roomName = comm2[1];
					cp = getCP(client);
					if (cp != null) {
						createRoom(roomName, cp.client);
					}
					break;
				case JOIN_ROOM:
					roomName = comm2[1];
					cp = getCP(client);
					if (cp != null) {
						joinRoom(roomName, cp.client);
					}
					break;
				case READY:
					if (gameState == GameState.LOBBY) {
						cp = getCP(client);
						if (cp != null) {
							cp.player.setReady(true);
							readyCheck();
						}
						response = "Ready to go!";
					}
					break;
				case "sayhi":
					response = "hi";
					break;
				default:
					// not a command, let's fix this function from eating messages
					response = message;
					break;
				}
			} else {
				// not a command, let's fix this function from eating messages
				// response = message;
				String alteredMessage = message;

				// good bye bold
				alteredMessage = alteredMessage.replaceAll("<b>", "").replaceAll("</b>", "");
				System.out.println("Debold: " + alteredMessage);

				// skeleton private message
				// duplicate sendMessage and only "broadcast" it to a client/user
				// if they are within this list
				if (alteredMessage.indexOf("@") > -1) {
					String[] ats = alteredMessage.split("@");
					List<String> usersToWhisper = new ArrayList<String>();
					for (int i = 0; i < ats.length; i++) {
						if (i % 2 != 0) {
							String[] data = ats[i].split(" ");
							String user = data[0];
							usersToWhisper.add(user);
						}
					}
				}

				if (alteredMessage.indexOf("*") > -1) {
					String[] s1 = alteredMessage.split("\\*");
					String m = "";
					for (int i = 0; i < s1.length; i++) {
						if (i % 2 == 0) {
							m += s1[i];
						} else {
							m += "<b>" + s1[i] + "</b>";
						}
						System.out.println(s1[i]);
					}
					alteredMessage = m;
				}
				if (alteredMessage.indexOf("[r]") > -1) {
					String[] s1 = alteredMessage.split("\\[r\\]");
					String m = "";
					for (int i = 0; i < s1.length; i++) {
						if (i % 2 == 0) {
							m += s1[i];
						} else {
							m += "<font color='red'>" + s1[i] + "</font>";
						}
						System.out.println(s1[i]);
					}
					alteredMessage = m;
				}
				response = alteredMessage;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	protected void sendConnectionStatus(ServerThread client, boolean isConnect, String message) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer c = iter.next();
			boolean messageSent = c.client.sendConnectionStatus(client.getClientName(), isConnect, message);
			if (!messageSent) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + c.client.getId());
			}
		}
	}

	/***
	 * Takes a sender and a message and broadcasts the message to all clients in
	 * this room. Client is mostly passed for command purposes but we can also use
	 * it to extract other client info.
	 * 
	 * @param sender  The client sending the message
	 * @param message The message to broadcast inside the room
	 */
	protected void sendMessage(ServerThread sender, String message) {
		log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
		String resp = processCommands(message, sender);
		if (resp == null) {
			// it was a command, don't broadcast
			return;
		}
		message = resp;
		// map shortcuts to html characters/elements
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer client = iter.next();
			boolean messageSent = client.client.send(sender.getClientName(), message);
			if (!messageSent) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + client.client.getId());
			}
		}
	}

	protected void sendSystemMessage(String message, String target) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer client = iter.next();
			if (target == null || client.client.getClientName().equals(target)) {
				boolean messageSent = client.client.send("[Announcer]", message);
				if (!messageSent) {
					iter.remove();
					log.log(Level.INFO, "Removed client " + client.client.getId());
				}
				if(target != null) {
					break;
				}
			}
		}
	}

	protected void sendSystemMessage(String message) {
		sendSystemMessage(message, null);
	}

	/**
	 * Broadcasts this client/player direction to all connected clients/players
	 * 
	 * @param sender
	 * @param dir
	 */
	protected void sendDirectionSync(ServerThread sender, Point dir) {
		boolean changed = false;
		// first we'll find the clientPlayer that sent their direction
		// and update the server-side instance of their direction
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer client = iter.next();
			// update only our server reference for this client
			// if we don't have this "if" it'll update all clients (meaning everyone will
			// move in sync)
			if (client.client == sender) {
				changed = client.player.setDirection(dir.x, dir.y);
				break;
			}
		}
		// if the direction is "changed" (it should be, but check anyway)
		// then we'll broadcast the change in direction to all clients
		// so their local movement reflects correctly
		if (changed) {
			iter = clients.iterator();
			while (iter.hasNext()) {
				ClientPlayer client = iter.next();
				boolean messageSent = client.client.sendDirection(sender.getClientName(), dir);
				if (!messageSent) {
					iter.remove();
					log.log(Level.INFO, "Removed client " + client.client.getId());
				}
			}

		}
	}

	/**
	 * Broadcasts this client/player position to all connected clients/players
	 * 
	 * @param sender
	 * @param pos
	 */
	protected void sendPositionSync(ServerThread sender, Point pos) {
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer client = iter.next();
			boolean messageSent = client.client.sendPosition(sender.getClientName(), pos);
			if (!messageSent) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + client.client.getId());
			}
		}
	}

	public List<String> getRooms(String search) {
		return server.getRooms(search);
	}

	/***
	 * Will attempt to migrate any remaining clients to the Lobby room. Will then
	 * set references to null and should be eligible for garbage collection
	 */
	@Override
	public void close() throws Exception {
		int clientCount = clients.size();
		if (clientCount > 0) {
			log.log(Level.INFO, "Migrating " + clients.size() + " to Lobby");
			Iterator<ClientPlayer> iter = clients.iterator();
			Room lobby = server.getLobby();
			while (iter.hasNext()) {
				ClientPlayer client = iter.next();
				lobby.addClient(client.client);
				iter.remove();
			}
			log.log(Level.INFO, "Done Migrating " + clients.size() + " to Lobby");
		}
		server.cleanupRoom(this);
		name = null;
		isRunning = false;
		// should be eligible for garbage collection now
	}

	@Override
	public void awake() {
		// TODO Auto-generated method stub

	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		log.log(Level.INFO, getName() + " start called");
	}

	long frame = 0;

	void checkPositionSync(ClientPlayer cp) {
		// determine the maximum syncing needed
		// you do NOT need it every frame, if you do it could cause network congestion
		// and
		// lots of bandwidth that doesn't need to be utilized
		if (frame % 120 == 0) {// sync every 120 frames (i.e., if 60 fps that's every 2 seconds)
			// check if it's worth sycning the position
			// again this is to save unnecessary data transfer
			if (cp.player.changedPosition()) {
				sendPositionSync(cp.client, cp.player.getPosition());
			}
		}

	}

	@Override
	public void update() {
		// We'll make the server authoritative
		// so we'll calc movement/collisions and send the action to the clients so they
		// can visually update. Client's won't be determining this themselves
		Iterator<ClientPlayer> iter = clients.iterator();
		while (iter.hasNext()) {
			ClientPlayer p = iter.next();
			if (p != null) {
				// have the server-side player calc their potential new position
				p.player.move();
				// determine if we should sync this player's position to all other players
				checkPositionSync(p);
			}
		}

	}

	// don't call this more than once per frame
	private void nextFrame() {
		// we'll do basic frame tracking so we can trigger events
		// less frequently than each frame
		// update frame counter and prevent overflow
		if (Long.MAX_VALUE - 5 <= frame) {
			frame = Long.MIN_VALUE;
		}
		frame++;
	}

	@Override
	public void lateUpdate() {
		nextFrame();
	}

	@Override
	public void draw(Graphics g) {
		// this is the server, we won't be using this unless you're adding this view to
		// the Honor's student extra section
	}

	@Override
	public void quit() {
		// don't call close here
		log.log(Level.WARNING, getName() + " quit() ");
	}

	@Override
	public void attachListeners() {
		// no listeners either since server side receives no input
	}

}