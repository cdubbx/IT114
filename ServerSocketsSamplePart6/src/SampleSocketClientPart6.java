import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;


public class SampleSocketClientPart6 {
	private Socket server;
	private OnReceiveMessage listener;
	public void registerListener(OnReceiveMessage listener) {
		this.listener = listener;
	}
	private Queue<PayloadPart6> toServer = new LinkedList<PayloadPart6>();
	private Queue<PayloadPart6> fromServer = new LinkedList<PayloadPart6>();
	
	public static SampleSocketClientPart6 connect(String address, int port) {
		SampleSocketClientPart6 client = new SampleSocketClientPart6();
		client._connect(address, port);
		Thread clientThread =  new Thread() {
			@Override
			public void run() {
				client.start();
			}
		};
		clientThread.start();
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return client;
	}
	private void _connect(String address, int port) {
		try {
			server = new Socket(address, port);
			System.out.println("Client connected");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void start() {
		if(server == null) {
			return;
		}
		System.out.println("Client Started");
		//listen to console, server in, and write to server out
		try(	ObjectOutputStream out = new ObjectOutputStream(server.getOutputStream());
				ObjectInputStream in = new ObjectInputStream(server.getInputStream());){
			Thread inputThread = new Thread() {
				@Override
				public void run() {
					try {
						while(!server.isClosed()) {
							PayloadPart6 p = toServer.poll();
							if(p != null) {
								out.writeObject(p);
							}
							else {
								try {
									Thread.sleep(8);
								}
								catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					}
					catch(Exception e) {
						System.out.println("Client shutdown");
					}
					finally {
						close();
					}
				}
			};
			inputThread.start();//start the thread
			
			//Thread to listen for responses from server so it doesn't block main thread
			Thread fromServerThread = new Thread() {
				@Override
				public void run() {
					try {
						PayloadPart6 p;
						//while we're connected, listen for payloads from server
						while(!server.isClosed() && (p = (PayloadPart6)in.readObject()) != null) {
							//System.out.println(fromServer);
							//processPayload(fromServer);
							fromServer.add(p);
						}
						System.out.println("Stopping server listen thread");
					}
					catch (Exception e) {
						if(!server.isClosed()) {
							e.printStackTrace();
							System.out.println("Server closed connection");
						}
						else {
							System.out.println("Connection closed");
						}
					}
					finally {
						close();
					}
				}
			};
			fromServerThread.start();//start the thread
			
			
			Thread payloadProcessor = new Thread(){
				@Override
				public void run() {
					while(!server.isClosed()) {
						PayloadPart6 p = fromServer.poll();
						if(p != null) {
							processPayload(p);
						}
						else {
							try {
								Thread.sleep(8);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			};
			payloadProcessor.start();
			//Keep main thread alive until the socket is closed
			//initialize/do everything before this line
			while(!server.isClosed()) {
				Thread.sleep(50);
			}
			System.out.println("Exited loop");
			System.exit(0);//force close
			//TODO implement cleaner closure when server stops
			//without this, it still waits for input before terminating
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			close();
		}
	}
	public void doClick() {
		PayloadPart6 payload = new PayloadPart6();
		payload.setPayloadType(PayloadTypePart6.MESSAGE);
		toServer.add(payload);
	}
	public void sendMessage(String message) {
		PayloadPart6 payload = new PayloadPart6();
		payload.setPayloadType(PayloadTypePart6.MESSAGE);
		payload.setMessage(message);
		toServer.add(payload);
	}
	private void processPayload(PayloadPart6 payload) {
		System.out.println(payload);
		switch(payload.getPayloadType()) {
		case CONNECT:
			System.out.println(
					String.format("Client \"%s\" connected", payload.getMessage())
			);
			break;
		case DISCONNECT:
			System.out.println(
					String.format("Client \"%s\" disconnected", payload.getMessage())
			);
			break;
		case MESSAGE:
			System.out.println(
					String.format("%s", payload.getMessage())
			);
			if (listener != null) {
				listener.onReceived();
			}
			break;
		default:
			System.out.println("Unhandled payload type: " + payload.getPayloadType().toString());
			break;
		}
	}
	private void close() {
		if(server != null) {
			try {
				server.close();
				System.out.println("Closed socket");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	public static void main(String[] args) {
		SampleSocketClientPart6 client = new SampleSocketClientPart6();
		client.connect("127.0.0.1", 3001);
		try {
			//if start is private, it's valid here since this main is part of the class
			client.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

interface OnReceiveMessage{
	void onReceived();
}