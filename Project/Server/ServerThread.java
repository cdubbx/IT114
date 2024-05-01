package Project.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import Project.Client.ClientPlayer;
import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.DMPayload;
import Project.Common.MutePayload;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.PointsPayload;
import Project.Common.PositionPayload;
import Project.Common.ReadyPayload;
import Project.Common.RoomResultsPayload;
import Project.Common.TextFX;
import Project.Common.TurnStatusPayload;
import Project.Common.UnMutePayload;
import Project.Common.TextFX.Color;

/**
 * A server-side representation of a single client
 */
public class ServerThread extends Thread {
    private Socket client;
    private String clientName;
    private boolean isRunning = false;
    private long clientId = Constants.DEFAULT_CLIENT_ID;
    private ObjectOutputStream out;// exposed here for send()
    // private Server server;// ref to our server so we can call methods on it
    // more easily
    private Room currentRoom;
    private Logger logger = Logger.getLogger(ServerThread.class.getName());
    private ConcurrentHashMap<Long, ClientPlayer> clientMuteList = new ConcurrentHashMap<Long, ClientPlayer>();


    private void info(String message) {
        logger.info(String.format("Thread[%s]: %s", getClientName(), message));
    }

    public ServerThread(Socket myClient/* , Room room */) {
        info("Thread created");
        // get communication channels to single client
        this.client = myClient;
        // this.currentRoom = room;

    }

    protected void setClientId(long id) {
        clientId = id;
        if (id == Constants.DEFAULT_CLIENT_ID) {
            logger.info(TextFX.colorize("Client id reset", Color.WHITE));
        }
        sendClientId(id);
    }

    protected boolean isRunning() {
        return isRunning;
    }
    protected void setClientName(String name) {
        if (name == null || name.isBlank()) {
            logger.severe("Invalid client name being set");
            return;
        }
        clientName = name;
    }

    protected String getClientName() {
        return clientName;
    }

    protected synchronized Room getCurrentRoom() {
        return currentRoom;
    }

    protected synchronized void setCurrentRoom(Room room) {
        if (room != null) {
            currentRoom = room;
        } else {
            info("Passed in room was null, this shouldn't happen");
        }
    }

    public void disconnect() {
        info("Thread being disconnected by server");
        isRunning = false;
        cleanup();
    }

    // send methods
    protected boolean sendPoints(long clientId, int changedPoints, int currentPoints) {
        PointsPayload pp = new PointsPayload();
        pp.setPayloadType(PayloadType.POINTS);
        pp.setClientId(clientId);
        pp.setChangedPoints(changedPoints);
        pp.setCurrentPoints(currentPoints);
        return send(pp);
    }
    protected boolean sendRoll(long clientId, int roll) {
        TurnStatusPayload tsp = new TurnStatusPayload();
        tsp.setPayloadType(PayloadType.ROLL);
        tsp.setClientId(clientId);
        tsp.setDidTakeTurn(true);
        tsp.setRoll(roll);
        return send(tsp);
    }
    public boolean sendGridDimensions(int x, int y) {
        PositionPayload pp = new PositionPayload(x, y);
        pp.setPayloadType(PayloadType.GRID);
        return send(pp);
    }

    public boolean sendPlayerPosition(long clientId, int x, int y) {
        PositionPayload pp = new PositionPayload(x, y);
        pp.setClientId(clientId);
        return send(pp);
    }
    protected boolean sendCurrentPlayerTurn(long clientId) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.CURRENT_TURN);
        p.setClientId(clientId);
        return send(p);
    }

    protected boolean sendResetLocalReadyState() {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.RESET_READY);
        return send(p);
    }

    protected boolean sendResetLocalTurns() {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.RESET_TURNS);
        return send(p);
    }

    protected boolean sendPlayerTurnStatus(long clientId, boolean didTakeTurn) {
        TurnStatusPayload tsp = new TurnStatusPayload();
        tsp.setClientId(clientId);
        tsp.setDidTakeTurn(didTakeTurn);
        return send(tsp);
    }
    protected boolean sendReadyState(long clientId, boolean isReady) {
        ReadyPayload rp = new ReadyPayload();
        rp.setReady(isReady);
        rp.setClientId(clientId);
        return send(rp);
    }

    protected boolean sendPhase(String phase) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.PHASE);
        p.setMessage(phase);
        return send(p);
    }
    protected boolean sendClientMapping(long id, String name) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.SYNC_CLIENT);
        cp.setClientId(id);
        cp.setClientName(name);
        return send(cp);
    }

    protected boolean sendJoinRoom(String roomName) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.JOIN_ROOM);
        p.setMessage(roomName);
        return send(p);
    }

    protected boolean sendClientId(long id) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientId(id);
        cp.setClientName(clientName);
        return send(cp);
    }
    private boolean sendListRooms(List<String> potentialRooms) {
        RoomResultsPayload rp = new RoomResultsPayload();
        rp.setRooms(potentialRooms);
        if (potentialRooms == null) {
            rp.setMessage("Invalid limit, please choose a value between 1-100");
        } else if (potentialRooms.size() == 0) {
            rp.setMessage("No rooms found matching your search criteria");
        }
        return send(rp);
    }

    public boolean sendMessage(long from, String message) {
        // if(message.contains("@")){
        //     int endIndex = message.indexOf(" ");
        //     String username;
        //     String payloadMessage;
        //     if (endIndex == -1) { // No space found, so the whole string is the username
        //         username = message.substring(1);
        //         payloadMessage = "";
        //     } else {
        //         username = message.substring(1, endIndex);
        //         payloadMessage = message.substring(endIndex + 1);
        //     }
        //     DMPayload dm = new DMPayload();
        //     dm.setMessage(payloadMessage);
        //     dm.setReceiver(username);
        //     dm.setClientId(from);

        //     return send(dm);
        // }


        Payload p = new Payload();
        p.setPayloadType(PayloadType.MESSAGE);
        // p.setClientName(from);
        p.setClientId(from);
        p.setMessage(message);
        return send(p);
    }

    public boolean sendDM(long from, String message) {
        DMPayload dm = new DMPayload();
        // p.setClientName(from);
        // monkey type 
        dm.setClientId(from);
        dm.setMessage(message);
        return send(dm);
    }
 // cw72 04/30/24 
    public boolean sendMute(long from, String message, Long receiver, String name) {
        // Check if the receiver is already muted by this client

        if (from == receiver) {
            System.out.println("Attempted self-mute prevented.");
            return false;
        }
        if (!clientMuteList.containsKey(receiver)) {
            // Not already muted, add to mute list
            ClientPlayer cp = new ClientPlayer();
            cp.setClientId(receiver); // Use the receiver's ID
            cp.setClientName(name);
            clientMuteList.put(receiver, cp);
    
            // Prepare a mute confirmation message
            MutePayload mute = new MutePayload();
            mute.setClientId(receiver); // ID of the client being muted
            mute.setMessage("User " + name + " has been muted.");
            return send(mute);
        } else {
            // Already muted, maybe send a notification back?
            MutePayload mute = new MutePayload();
            mute.setClientId(receiver); // ID of the sender
            mute.setMessage("User " + name + " is already muted.");
            return send(mute);
        }
    }

    public boolean sendUnMute(long from, String message, Long receiver, String name) {
        // Check if the receiver is already muted by this client

        if (from == receiver) {
            System.out.println("Attempted self-mute prevented.");
            return false;
        }
        if (clientMuteList.containsKey(receiver)) {
    
            clientMuteList.remove(receiver);
    
            // Prepare a mute confirmation message
            UnMutePayload unmute = new UnMutePayload();
            unmute.setClientId(receiver); // ID of the client being unmuted
            return send(unmute);
        } else {
            // Already unmuted, maybe send a notification back?
            UnMutePayload unmute = new UnMutePayload();
            unmute.setMessage("User was never muted");
            unmute.setClientId(receiver); // ID of the sender
            return send(unmute);
        }
    }
    
    
       
        // p.setClientName(from);
   
       public ConcurrentHashMap<Long, ClientPlayer> getMuteList(){
            return clientMuteList;
       }
    
       public String Bold(String string){
        return string.replaceAll("\\*(.*?)\\*", "<b>$1</b>");
    }


    /**
     * Used to associate client names and their ids from the server perspective
     * 
     * @param whoId       id of who is connecting/disconnecting
     * @param whoName     name of who is connecting/disconnecting
     * @param isConnected status of connection (true connecting, false,
     *                    disconnecting)
     * @return
     */
    public boolean sendConnectionStatus(long whoId, String whoName, boolean isConnected) {
        ConnectionPayload p = new ConnectionPayload(isConnected);
        // p.setClientName(who);
        p.setClientId(whoId);
        p.setClientName(whoName);
        p.setMessage(isConnected ? "connected" : "disconnected");
        return send(p);
    }

    private boolean send(Payload payload) {
        // added a boolean so we can see if the send was successful
        try {
            out.writeObject(payload);
            return true;
        } catch (IOException e) {
            info("Error sending message to client (most likely disconnected)");
            // comment this out to inspect the stack trace
            // e.printStackTrace();
            cleanup();
            return false;
        } catch (NullPointerException ne) {
            info("Message was attempted to be sent before outbound stream was opened");
            return true;// true since it's likely pending being opened
        }
    }

    public String diceRoll() {
        Random random = new Random();
        int randint = random.nextInt(6) + 1;  
        String rollOutcome = Integer.toString(randint);  
        return rollOutcome;  
    }


    public String flip(){
        Random random= new Random();
        String flipOutcome;
        int randFlip= random.nextInt(2);

        if (randFlip == 1){
            flipOutcome = "Heads";
            return flipOutcome;
        }
        else{
            flipOutcome = "Tails";
            return flipOutcome;
        }

    }

    // end send methods
    @Override
    public void run() {
        info("Thread starting");
        try (ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream());) {
            this.out = out;
            isRunning = true;
            Payload fromClient;
            while (isRunning && // flag to let us easily control the loop
                    (fromClient = (Payload) in.readObject()) != null // reads an object from inputStream (null would
                                                                     // likely mean a disconnect)
            ) {

                info("Received from client: " + fromClient);
                processPayload(fromClient);

            } // close while loop
        } catch (Exception e) {
            // happens when client disconnects
            e.printStackTrace();
            info("Client disconnected");
        } finally {
            isRunning = false;
            info("Exited thread loop. Cleaning up connection");
            cleanup();
        }
    }

    /**
     * Used to process payloads from the client and handle their data
     * 
     * @param p
     */
    private void processPayload(Payload p) {
        switch (p.getPayloadType()) {
            case CONNECT:
                try {
                    ConnectionPayload cp = (ConnectionPayload) p;
                    setClientName(cp.getClientName());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            case DISCONNECT:
                if (currentRoom != null) {
                    Room.disconnectClient(this, currentRoom);
                }
                break;
            case MESSAGE: 
                if (currentRoom != null) {
                    System.out
                            .println(TextFX.colorize("ServerThread received message: " + p.getMessage(), Color.YELLOW));
                    currentRoom.sendMessage(this, p.getMessage());
                } else {
                    // TODO migrate to lobby
                    Room.joinRoom(Constants.LOBBY, this);
                }
                break;
            case DM:
                DMPayload dm = (DMPayload) p;
                if (currentRoom != null) {
                    System.out
                            .println(TextFX.colorize("ServerThread received message: " + dm.getMessage(), Color.YELLOW));
                    currentRoom.sendDM(this, dm.getClientId(),dm.getMessage(), dm.getId());
                } else {
                    Room.joinRoom(Constants.LOBBY, this);
                }
                break;

                case MUTE:
                try{
                    MutePayload mp = (MutePayload) p;
                    if (currentRoom != null) {
                        System.out
                                .println(TextFX.colorize("ServerThread received message: " + mp.getMessage(), Color.YELLOW));
                        currentRoom.sendMute(this, mp.getClientId(), mp.toString(), mp.getId());
                    } else {
                        Room.joinRoom(Constants.LOBBY, this);
                    }
                    break;
                } catch (Exception e){
                    e.printStackTrace();
                }
                    
               
                case UNMUTE:
                try{
                    UnMutePayload mp = (UnMutePayload) p;
                    if (currentRoom != null) {
                        System.out
                                .println(TextFX.colorize("ServerThread received message: " + mp.getMessage(), Color.YELLOW));
                        currentRoom.sendUmute(this, mp.getClientId(), mp.toString(), mp.getId());
                    } else {
                        Room.joinRoom(Constants.LOBBY, this);
                    }
                    break;
                } catch (Exception e){
                    e.printStackTrace();
                } 
                break;
            case CREATE_ROOM:
                Room.createRoom(p.getMessage(), this);
                break;
            case JOIN_ROOM:
                Room.joinRoom(p.getMessage(), this);
                break;
            case LIST_ROOMS:
                String searchString = p.getMessage() == null ? "" : p.getMessage();
                int limit = 10;
                try {
                    RoomResultsPayload rp = ((RoomResultsPayload) p);
                    limit = rp.getLimit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                List<String> potentialRooms = Room.listRooms(searchString, limit);
                this.sendListRooms(potentialRooms);
                break;
            case READY:
                try {
                    ((GameRoom) currentRoom).setReady(this);
                } catch (Exception e) {
                    e.printStackTrace();
                    this.sendMessage(Constants.DEFAULT_CLIENT_ID,
                            "You can only use the /ready commmand in a GameRoom and not the Lobby");
                }

                break;

                case ROLL:
                try{
                    if (currentRoom != null) {
                        String flipOutcome = flip();
                        System.out
                                .println(TextFX.colorize("ServerThread received message: " + p.getMessage(), Color.YELLOW));
                        currentRoom.sendMessage(this, diceRoll());
                    } else {
                        Room.joinRoom(Constants.LOBBY, this);
                    }
            
                } catch (Exception e){
                    e.printStackTrace();
    
                }
                break;
    
                
             
                case FLIP:
                
                    try{
                        if (currentRoom != null) {
                            String flipOutcome = flip();
                            System.out
                                    .println(TextFX.colorize("ServerThread received message: " + p.getMessage(), Color.YELLOW));
                            currentRoom.sendMessage(this, flipOutcome);
                        } else {
                            Room.joinRoom(Constants.LOBBY, this);
                        }
                
                    } catch (Exception e){
                        e.printStackTrace();
        
                    }
                    break;
            case BOLD:
                try{
                    if (currentRoom != null){
                        String boldAction = Bold(p.getMessage());
                        currentRoom.sendMessage(this, boldAction);
                    }
                   
    
                } catch (Exception e){
                    e.printStackTrace();
                }
                break;
            default:
                break;

        }

    }

    private void cleanup() {
        info("Thread cleanup() start");
        try {
            client.close();
        } catch (IOException e) {
            info("Client already closed");
        }
        info("Thread cleanup() complete");
    }

    public long getClientId() {
        return clientId;
    }
}